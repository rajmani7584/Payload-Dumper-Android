use std::{
    fs::File,
    io::{Read as _, Seek, Write as _},
    path::PathBuf,
};

use sha2::Digest;

use crate::{
    engine::part_manifest::{self, UpdateInfo},
    helper::errors::{AppError, AppResult},
    payload::{PayloadDumper, header::PayloadHeader},
};

impl PayloadDumper {
    pub async fn dump<F>(
        &mut self,
        partition: UpdateInfo,
        block_size: u64,
        out_path: &PathBuf,
        header: PayloadHeader,
        verify: bool,
        on_progress: Option<F>,
    ) -> AppResult<()>
    where
        F: Fn(usize) -> bool,
    {
        let mut out_fd = File::create(out_path)?;

        let total_ops = partition.operations.len();
        let mut completed_ops = 0;

        for op in partition.operations.iter() {
            if op.dst_extents.is_empty() {
                return Err(AppError::Other("Invalid operation".into()));
            }

            let dst_extent = op.dst_extents[0];
            let offset = op
                .data_offset
                .ok_or_else(|| AppError::Other("Invalid operation".into()))?
                + header.data_offset();
            let data_len = op
                .data_length
                .ok_or_else(|| AppError::Other("Invalid operation".into()))?;
            let exp_size = dst_extent
                .num_blocks
                .ok_or_else(|| AppError::Other("Invalid operation".into()))?
                * block_size;

            let mut sha256 = sha2::Sha256::new();

            let mut buf = vec![0u8; data_len as usize];
            self.reader.seek(std::io::SeekFrom::Start(offset))?;
            self.reader.read(&mut buf)?;

            if verify {
                sha256.update(&buf);
            }

            let bytes_written: u64;

            match op.r#type() {
                part_manifest::operation::Type::Replace => {
                    bytes_written = std::io::copy(&mut buf.as_slice(), &mut out_fd)?;
                }
                part_manifest::operation::Type::ReplaceBz => {
                    let mut decoder = bzip2::bufread::BzDecoder::new(buf.as_slice());
                    bytes_written = std::io::copy(&mut decoder, &mut out_fd)?;
                }
                part_manifest::operation::Type::ReplaceXz => {
                    let mut decoder = liblzma::bufread::XzDecoder::new(buf.as_slice());
                    bytes_written = std::io::copy(&mut decoder, &mut out_fd)?;
                }
                part_manifest::operation::Type::ReplaceZstd => {
                    let mut decoder = zstd::Decoder::new(buf.as_slice())?;
                    bytes_written = std::io::copy(&mut decoder, &mut out_fd)?;
                }
                part_manifest::operation::Type::Zero => {
                    bytes_written = std::io::copy(&mut std::io::repeat(0), &mut out_fd)?;
                }
                _ => {
                    return Err(AppError::Other(format!(
                        "Unsupported operation type: {:?}",
                        op.r#type()
                    )));
                }
            }

            if bytes_written != exp_size {
                return Err(AppError::Other(format!(
                    "Mismatched data size: {} bytes written, {} bytes expected",
                    bytes_written, exp_size
                )));
            }

            completed_ops += 1;
            if let Some(ref on_progress) = on_progress {
                if !on_progress((completed_ops as f64 / total_ops as f64 * 100.0) as usize) {
                    return Err(AppError::Other("Cancelled by user".to_string()));
                }
            }

            if verify {
                let hash = sha256.finalize();
                let hash = hex::encode(hash);
                if hash != op.sha_hash {
                    return Err(AppError::Other(format!(
                        "Hash mismatch: {} != {}",
                        hash, op.sha_hash
                    )));
                }
            }
        }

        out_fd.flush()?;

        Ok(())
    }
}
