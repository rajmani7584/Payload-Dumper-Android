use crate::{
    helper::{
        constants::{BRILLO_MAJOR_PAYLOAD_VERSION, HEADER_SIZE, PAYLOAD_HEADER_MAGIC},
        errors::{AppError, AppResult},
    },
    read_be,
};
use std::io::Read as _;

#[derive(Debug, Clone, Copy)]
pub(crate) struct PayloadHeader {
    pub(crate) version: u64,
    pub(crate) manifest_len: u64,
    pub(crate) signature_len: u32,
}

impl PayloadHeader {
    pub(crate) fn from_bytes(mut buffer: &[u8]) -> AppResult<PayloadHeader> {
        let mut header = PayloadHeader {
            version: 0,
            manifest_len: 0,
            signature_len: 0,
        };

        let magic = read_be!(buffer, [u8; 4]);

        if magic != PAYLOAD_HEADER_MAGIC {
            return Err(AppError::Other(format!(
                "Invalid payload magic `{}`",
                str::from_utf8(&magic).unwrap_or("")
            )));
        }

        header.version = read_be!(buffer, u64);

        if header.version != BRILLO_MAJOR_PAYLOAD_VERSION {
            return Err(AppError::Other("Unsupported payload version".into()));
        }

        header.manifest_len = read_be!(buffer, u64);
        header.signature_len = read_be!(buffer, u32);

        Ok(header)
    }

    pub(crate) fn size() -> usize {
        HEADER_SIZE
    }

    pub(crate) fn signature_offset(&self) -> u64 {
        Self::size() as u64 + self.manifest_len
    }

    pub(crate) fn data_offset(&self) -> u64 {
        self.signature_len as u64 + self.signature_offset()
    }
}
