use std::io::{Read, Seek, SeekFrom};

use crate::{
    helper::errors::{AppError, AppResult},
    read_le,
    reader::{PayloadReader, Reader},
};

const EOCD_LEN: usize = 22;
const MAX_SEARCH_LEN: usize = 65557;
const MIN_ZIP_SIZE: usize = EOCD_LEN;

pub(crate) struct ZipReader<'a> {
    inner: &'a mut PayloadReader,
}

impl<'a> ZipReader<'a> {
    pub(crate) fn new(inner: &'a mut PayloadReader) -> Self {
        Self { inner }
    }
    pub(crate) fn payload_offset(&mut self) -> AppResult<u64> {
        let reader = &mut self.inner;

        let file_size = reader
            .len()
            .ok_or_else(|| AppError::Other("File size not found".into()))?
            as usize;

        if file_size < MIN_ZIP_SIZE {
            return Err(AppError::Other(
                "File is too small to be a valid ZIP".into(),
            ));
        }

        let scan_len = file_size.min(MAX_SEARCH_LEN);
        let start_search = file_size - scan_len;

        let mut buf = vec![0u8; scan_len];
        reader.seek(SeekFrom::Start(start_search as u64))?;
        reader.read_exact(&mut buf)?;

        let eocd_pos = (0..=(scan_len.saturating_sub(EOCD_LEN)))
            .rev()
            .find(|&i| {
                if &buf[i..i + 4] != b"PK\x05\x06" {
                    return false;
                }
                let comment_len = read_le!(buf[i + 20..i + 22], u16) as usize;
                let trailing = file_size.saturating_sub(start_search + i + EOCD_LEN);
                trailing == comment_len
            })
            .ok_or_else(|| AppError::Other("EOCD not found".into()))?;

        let eocd32 = &buf[eocd_pos..eocd_pos + EOCD_LEN];
        let cd_offset32 = read_le!(eocd32[16..20], u32);

        let (cd_size, cd_offset) = if cd_offset32 == 0xFFFF_FFFFu32 {
            const LOC64_LEN: usize = 20;
            const EOCD64_LEN: usize = 56;

            if eocd_pos < LOC64_LEN {
                return Err(AppError::Other("No room for ZIP64 EOCD locator".into()));
            }

            let loc64 = &buf[eocd_pos - LOC64_LEN..eocd_pos];
            if &loc64[0..4] != b"PK\x06\x07" {
                return Err(AppError::Other(
                    "ZIP64 EOCD locator signature not found".into(),
                ));
            }

            let eocd64_off = read_le!(loc64[8..16], u64);
            let eocd64_file_pos = eocd64_off;

            let mut eocd64 = vec![0u8; EOCD64_LEN];
            reader.seek(SeekFrom::Start(eocd64_file_pos))?;
            reader.read_exact(&mut eocd64)?;

            if &eocd64[0..4] != b"PK\x06\x06" {
                return Err(AppError::Other("ZIP64 EOCD signature not found".into()));
            }

            let cd_size = read_le!(eocd64[40..48], u64) as usize;

            let cd_offset = eocd64_file_pos
                .checked_sub(cd_size as u64)
                .ok_or_else(|| AppError::Other("cd_size larger than eocd64 offset".into()))?;

            (cd_size, cd_offset)
        } else {
            let cd_size = read_le!(eocd32[12..16], u32) as usize;
            let cd_offset = cd_offset32 as u64;
            (cd_size, cd_offset)
        };

        let mut cd = vec![0u8; cd_size];
        reader.seek(SeekFrom::Start(cd_offset))?;
        reader.read_exact(&mut cd)?;

        let mut pos = 0usize;

        loop {
            if pos + 46 > cd.len() {
                return Err(AppError::Other(
                    "Central directory entry out of bounds".into(),
                ));
            }

            if &cd[pos..pos + 4] != b"PK\x01\x02" {
                return Err(AppError::Other(
                    "Invalid central directory signature".into(),
                ));
            }

            let compression = read_le!(cd[pos + 10..pos + 12], u16);
            let file_name_len = read_le!(cd[pos + 28..pos + 30], u16) as usize;
            let extra_len = read_le!(cd[pos + 30..pos + 32], u16) as usize;
            let comment_len = read_le!(cd[pos + 32..pos + 34], u16) as usize;
            let lhdr_off_raw = read_le!(cd[pos + 42..pos + 46], u32) as u64;

            let name_end = pos + 46 + file_name_len;
            if name_end > cd.len() {
                return Err(AppError::Other(
                    "File name field extends past central directory".into(),
                ));
            }

            let file_name = str::from_utf8(&cd[pos + 46..name_end]).unwrap_or("");

            if file_name == "payload.bin" {
                if compression != 0 {
                    return Err(AppError::Other("payload.bin is compressed".into()));
                }

                let lhdr_off = if lhdr_off_raw == 0xFFFF_FFFFu64 {
                    let extra_start = name_end;
                    let extra_end = (extra_start + extra_len).min(cd.len());
                    parse_zip64_lhdr_offset(&cd[extra_start..extra_end])?
                } else {
                    lhdr_off_raw
                };

                let mut lhdr = vec![0u8; 30];
                reader.seek(SeekFrom::Start(lhdr_off))?;
                reader.read_exact(&mut lhdr)?;

                if &lhdr[0..4] != b"PK\x03\x04" {
                    return Err(AppError::Other(
                        "Invalid local file header signature".into(),
                    ));
                }

                let lhdr_name_len = read_le!(lhdr[26..28], u16) as u64;
                let lhdr_extra_len = read_le!(lhdr[28..30], u16) as u64;
                let data_start = lhdr_off + 30 + lhdr_name_len + lhdr_extra_len;

                return Ok(data_start);
            }

            let entry_len = 46 + file_name_len + extra_len + comment_len;
            if entry_len == 0 {
                return Err(AppError::Other(
                    "Central directory entry has zero length".into(),
                ));
            }

            pos = pos
                .checked_add(entry_len)
                .filter(|&next| next <= cd.len())
                .ok_or_else(|| {
                    AppError::Other("Central directory entry overflows buffer".into())
                })?;
        }
    }
}

fn parse_zip64_lhdr_offset(extra: &[u8]) -> AppResult<u64> {
    let mut i = 0;
    while i + 4 <= extra.len() {
        let id = read_le!(extra[i..i + 2], u16);
        let size = read_le!(extra[i + 2..i + 4], u16) as usize;
        i += 4;

        if i + size > extra.len() {
            break;
        }

        if id == 0x0001 {
            if size >= 24 {
                return Ok(read_le!(extra[i + 16..i + 24], u64));
            }
            return Err(AppError::Other(
                "ZIP64 extra block too small to contain lhdr offset".into(),
            ));
        }

        i += size;
    }

    Err(AppError::Other("ZIP64 extra field not found".into()))
}
