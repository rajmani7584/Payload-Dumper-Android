use std::{
    fs::File,
    io::{BufReader, Read, Seek},
};

use crate::{
    helper::{constants::BUF_SIZE, errors::AppResult},
    reader::Reader,
};

pub(crate) struct LocalPayloadReader {
    file: BufReader<File>,
    len: u64,
}

impl LocalPayloadReader {
    pub(crate) fn new(path: &str) -> AppResult<Self> {
        let file = File::open(path)?;
        let len = file.metadata()?.len();
        Ok(Self {
            file: BufReader::with_capacity(BUF_SIZE, file),
            len,
        })
    }
}

impl Read for LocalPayloadReader {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        self.file.read(buf)
    }
}

impl Seek for LocalPayloadReader {
    fn seek(&mut self, pos: std::io::SeekFrom) -> std::io::Result<u64> {
        self.file.seek(pos)
    }
}

impl Reader for LocalPayloadReader {
    fn len(&self) -> Option<u64> {
        Some(self.len)
    }
}
