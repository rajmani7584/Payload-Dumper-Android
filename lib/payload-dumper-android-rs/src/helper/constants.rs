pub(crate) const HEADER_SIZE: usize = 24;
pub(crate) const BRILLO_MAJOR_PAYLOAD_VERSION: u64 = 2;
pub(crate) const BUF_SIZE: usize = 256 * 1024;
pub(crate) const MAX_RETRIES: usize = 3;
pub(crate) const REQUEST_TIMEOUT: u64 = 10;
pub(crate) const READ_TIMEOUT: u64 = 30;
pub(crate) const BLOCK_SIZE: u32 = 4096;

pub(crate) const PAYLOAD_HEADER_MAGIC: [u8; 4] = [b'C', b'r', b'A', b'U'];
pub(crate) const ZIP_HEADER_MAGIC: [u8; 4] = [b'P', b'K', b'\x03', b'\x04'];

pub mod status {
    pub const IDLE: u8 = 0;
    pub const PENDING: u8 = 1;
    pub const RUNNING: u8 = 2;
    pub const COMPLETED: u8 = 3;
    pub const FAILED: u8 = 4;
}
