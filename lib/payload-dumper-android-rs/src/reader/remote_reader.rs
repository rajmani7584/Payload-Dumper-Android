use std::{
    io::{self, Read, Seek},
    thread::sleep,
};

use ureq::{Agent, ResponseExt as _};

use crate::{
    helper::{
        constants::{MAX_RETRIES, READ_TIMEOUT, REQUEST_TIMEOUT},
        errors::{AppError, AppResult},
    },
    reader::Reader,
};

pub(crate) struct RemotePayloadReader {
    url: String,
    agent: Agent,
    response_reader: Option<Box<dyn Read + Send + Sync>>,
    offset: u64,
    total_size: Option<u64>,
    buffer: Vec<u8>,
    buffer_start: u64,
    buffer_pos: usize,
    buffer_len: usize,
    retries: usize,
    eof: bool,
    http: bool,
}

impl RemotePayloadReader {
    pub(crate) fn new(url: impl Into<String>, buf_size: usize) -> AppResult<Self> {
        let agent = ureq::Agent::config_builder()
            .timeout_connect(Some(std::time::Duration::from_secs(REQUEST_TIMEOUT)))
            .timeout_recv_response(Some(std::time::Duration::from_secs(READ_TIMEOUT)))
            .timeout_recv_body(Some(std::time::Duration::from_secs(READ_TIMEOUT)))
            .user_agent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36")
            .build()
            .new_agent();

        let mut reader = Self {
            url: url.into(),
            agent,
            response_reader: None,
            offset: 0,
            total_size: None,
            buffer: vec![0; buf_size],
            buffer_start: 0,
            buffer_pos: 0,
            buffer_len: 0,
            retries: 0,
            eof: false,
            http: false,
        };

        let is_http = reader.connect()?;
        reader.http = is_http;

        Ok(reader)
    }

    fn connect(&mut self) -> AppResult<bool> {
        let mut req = self.agent.get(&self.url);

        if self.offset > 0 {
            req = req.header("Range", &format!("bytes={}-", self.offset));
        }

        let response = req.call().map_err(|e| AppError::Io(e.into_io()))?;

        let is_http = matches!(response.get_uri().scheme_str(), Some("http"));

        let status = response.status().as_u16();

        match status {
            200 if self.offset > 0 => {
                return Err(AppError::Other(
                    "Server does not support byte ranges".into(),
                ));
            }
            200 | 206 => {}
            _ => {
                return Err(AppError::Other(format!("HTTP error {}", status)));
            }
        }

        let headers = response.headers();

        match status {
            206 => {
                let content_range = headers
                    .get("Content-Range")
                    .ok_or_else(|| AppError::Other("206 response missing Content-Range".into()))?
                    .to_str()
                    .map_err(|_| AppError::Other("Invalid Content-Range".into()))?;

                let range = content_range
                    .strip_prefix("bytes ")
                    .ok_or_else(|| AppError::Other("Invalid Content-Range format".into()))?;

                let (range_part, total_part) = range
                    .split_once('/')
                    .ok_or_else(|| AppError::Other("Invalid Content-Range".into()))?;

                let (start, _end) = range_part
                    .split_once('-')
                    .ok_or_else(|| AppError::Other("Invalid Content-Range range".into()))?;

                let start = start
                    .parse::<u64>()
                    .map_err(|_| AppError::Other("Invalid range start".into()))?;

                if start != self.offset {
                    return Err(AppError::Other(format!(
                        "Server resumed from wrong offset: expected {}, got {}",
                        self.offset, start
                    )));
                }

                if total_part != "*" {
                    if let Ok(total) = total_part.parse::<u64>() {
                        self.total_size = Some(total);
                    }
                }
            }

            200 => {
                if let Some(content_length) = headers.get("Content-Length") {
                    let len = content_length
                        .to_str()
                        .map_err(|_| AppError::Other("Invalid Content-Length".into()))?
                        .parse::<u64>()
                        .map_err(|_| AppError::Other("Invalid Content-Length".into()))?;

                    self.total_size = Some(len);
                }
            }

            _ => unreachable!(),
        }

        let reader = response.into_body().into_reader();

        self.response_reader = Some(Box::new(reader));

        self.buffer_pos = 0;
        self.buffer_len = 0;

        Ok(is_http)
    }

    fn refill_buffer(&mut self) -> AppResult<()> {
        if self.eof {
            return Ok(());
        }

        if self.response_reader.is_none() {
            self.connect()?;
        }

        loop {
            let reader = self
                .response_reader
                .as_mut()
                .ok_or(AppError::Other("Missing response reader".into()))?;

            match reader.read(&mut self.buffer) {
                Ok(0) => {
                    if let Some(total) = self.total_size {
                        if self.offset >= total {
                            self.eof = true;
                            return Ok(());
                        }
                    }
                    self.retry_reconnect()?;
                }
                Ok(n) => {
                    self.buffer_start = self.offset;
                    self.buffer_pos = 0;
                    self.buffer_len = n;
                    self.offset += n as u64;
                    self.retries = 0;

                    return Ok(());
                }
                Err(e) => {
                    if is_retryable(&e) {
                        self.retry_reconnect()?;
                    } else {
                        return Err(AppError::Io(e));
                    }
                }
            }
        }
    }

    fn retry_reconnect(&mut self) -> AppResult<()> {
        self.response_reader = None;

        self.retries += 1;

        if self.retries > MAX_RETRIES {
            return Err(AppError::Other("max retries exceeded".into()));
        }

        let delay = std::time::Duration::from_millis(5000 * self.retries as u64);
        println!(
            "Conn lost, retrying in {:?} reconnect {}/{}",
            delay, self.retries, MAX_RETRIES
        );

        sleep(delay);

        let _ = self.connect()?;

        Ok(())
    }
}

impl Read for RemotePayloadReader {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        if buf.is_empty() {
            return Ok(0);
        }

        if self.eof {
            return Ok(0);
        }

        let mut written = 0;

        while written < buf.len() {
            let available = self.buffer_len.saturating_sub(self.buffer_pos);

            if available == 0 {
                self.refill_buffer()
                    .map_err(|e| io::Error::new(io::ErrorKind::Other, e))?;

                if self.eof {
                    break;
                }

                continue;
            }

            let remaining = buf.len() - written;

            let to_copy = available.min(remaining);

            let slice = &self.buffer[self.buffer_pos..self.buffer_pos + to_copy];

            buf[written..written + to_copy].copy_from_slice(slice);

            self.buffer_pos += to_copy;

            written += to_copy;
        }

        Ok(written)
    }
}

impl Seek for RemotePayloadReader {
    fn seek(&mut self, pos: std::io::SeekFrom) -> io::Result<u64> {
        let current = self.buffer_start + self.buffer_pos as u64;

        let new_offset = match pos {
            std::io::SeekFrom::Start(offset) => offset,
            std::io::SeekFrom::End(offset) => {
                if let Some(total) = self.total_size {
                    let target = total as i64 + offset;
                    if target < 0 {
                        return Err(io::Error::new(
                            io::ErrorKind::InvalidData,
                            "Negative offset underflow",
                        ));
                    }

                    target as u64
                } else {
                    return Err(io::Error::new(io::ErrorKind::InvalidData, "Unknown length"));
                }
            }
            std::io::SeekFrom::Current(offset) => {
                let offset = offset as i64;
                if offset >= 0 {
                    current + offset as u64
                } else {
                    current.checked_sub(offset.abs() as u64).ok_or_else(|| {
                        io::Error::new(io::ErrorKind::InvalidData, "Negative offset underflow")
                    })?
                }
            }
        };

        if self.buffer_start <= new_offset
            && new_offset < self.buffer_start + self.buffer_len as u64
        {
            self.buffer_pos = (new_offset - self.buffer_start) as usize;

            return Ok(new_offset);
        }
        self.response_reader = None;

        self.offset = new_offset;

        self.buffer_pos = 0;
        self.buffer_len = 0;

        self.eof = false;

        Ok(new_offset)
    }
}

impl Reader for RemotePayloadReader {
    fn len(&self) -> Option<u64> {
        self.total_size
    }

    fn is_http(&self) -> Option<bool> {
        Some(self.http)
    }
}

fn is_retryable(e: &io::Error) -> bool {
    matches!(
        e.kind(),
        io::ErrorKind::TimedOut
            | io::ErrorKind::UnexpectedEof
            | io::ErrorKind::ConnectionReset
            | io::ErrorKind::ConnectionAborted
            | io::ErrorKind::BrokenPipe
            | io::ErrorKind::Interrupted
    )
}
