use std::io::{Read, Seek, SeekFrom};

use crate::{
    helper::errors::{AppError, AppResult},
    read_le,
    reader::{PayloadReader, Reader},
};

pub(crate) struct ZipReader<'a> {
    inner: &'a mut PayloadReader,
}

impl<'a> ZipReader<'a> {
    pub(crate) fn new(inner: &'a mut PayloadReader) -> Self {
        Self { inner }
    }

    pub(crate) fn payload_offset(&mut self) -> AppResult<u64> {
        let reader = &mut self.inner;

        let eocd_len = 22usize;
        let max_search_len = 65557usize;

        let file_size = reader
            .len()
            .ok_or_else(|| AppError::Other("File size not found".into()))?
            as usize;

        if file_size < max_search_len {
            return Err(AppError::Other("File is too small".into()));
        }

        let scan_len = file_size.min(max_search_len);

        let start_search = file_size - scan_len;

        let mut buf = vec![0; scan_len];

        reader.seek(SeekFrom::Start(start_search as u64))?;
        reader.read(&mut buf)?;

        let eocd_pos = (0..=(scan_len.saturating_sub(eocd_len)))
            .rev()
            .find(|&i| {
                if &buf[i..i + 4] != b"PK\x05\x06" {
                    return false;
                }
                let comment_len = read_le!(buf[i + 20..i + 22], u16) as usize;

                let trailling = file_size.saturating_sub(start_search + i + eocd_len);

                trailling == comment_len
            })
            .ok_or_else(|| AppError::Other("EOCD not found".into()))?;

        let eocd = &buf[eocd_pos..eocd_pos + eocd_len];
        let cd_size = read_le!(eocd[12..16], u32);
        let cd_offset = read_le!(eocd[16..20], u32) as u64;

        let mut cd = vec![0; cd_size as usize];

        reader.seek(SeekFrom::Start(cd_offset))?;
        reader.read(&mut cd)?;

        let mut pos = 0usize;

        loop {
            if pos + 46 > cd.len() {
                return Err(AppError::Other("Central directory too small".into()));
            }

            if &cd[pos..pos + 4] != b"PK\x01\x02" {
                return Err(AppError::Other("Invalid central directory".into()));
            }

            let compression = read_le!(cd[pos + 10..pos + 12], u16);
            let file_name_len = read_le!(cd[pos + 28..pos + 30], u16) as usize;

            let extra_len = read_le!(cd[pos + 30..pos + 32], u16) as usize;

            let comment_len = read_le!(cd[pos + 32..pos + 34], u16) as usize;

            let lhdr_off = read_le!(cd[pos + 42..pos + 46], u32) as u64;

            let file_name_bytes = &cd[pos + 46..(pos + 46 + file_name_len as usize).min(cd.len())];

            let file_name = str::from_utf8(file_name_bytes).unwrap_or("");

            if file_name == "payload.bin" {
                if compression != 0 {
                    return Err(AppError::Other("payload.bin is compressed".into()));
                }

                let mut lhdr = vec![0; 30];
                reader.seek(SeekFrom::Start(lhdr_off))?;
                reader.read(&mut lhdr)?;

                if &lhdr[0..4] != b"PK\x03\x04" {
                    return Err(AppError::Other("Invalid local file header".into()));
                }

                let lhdr_name_len = read_le!(lhdr[26..28], u16) as u64;
                let lhdr_extra_len = read_le!(lhdr[28..30], u16) as u64;

                let data_start = lhdr_off + 30 + lhdr_name_len + lhdr_extra_len;

                return Ok(data_start as u64);
            }

            pos += 46 + file_name_len + extra_len + comment_len;
        }
    }
}
