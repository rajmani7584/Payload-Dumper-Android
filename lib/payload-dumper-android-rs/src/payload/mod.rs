mod dump;
mod header;

use std::io::{Read, Seek, SeekFrom};

use prost::Message;

use crate::{
    engine::{
        chromeos_update_engine::DeltaArchiveManifest,
        part_manifest::{DstExtent, Operation, PartInfo, PartManifest, UpdateInfo},
    },
    helper::{
        constants::{PAYLOAD_HEADER_MAGIC, ZIP_HEADER_MAGIC},
        errors::{AppError, AppResult},
    },
    payload::header::PayloadHeader,
    reader::{PayloadReader, Reader, zip_reader::ZipReader},
};

#[derive(Clone)]
pub enum Payload {
    File(String),
    Url(String),
}
impl Payload {
    pub fn from_url(url: &str) -> Self {
        Self::Url(url.to_string())
    }
    pub fn from_file(path: &str) -> Self {
        Self::File(path.to_string())
    }
}

pub struct PayloadDumper {
    reader: PayloadReader,
    header: Option<PayloadHeader>,
    manifest: Option<DeltaArchiveManifest>,
    signatures: Option<Vec<u8>>,
}

#[allow(dead_code)]
impl PayloadDumper {
    pub fn new(payload: Payload) -> AppResult<Self> {
        let mut reader = PayloadReader::new(payload)?;
        let mut buf = [0u8; 4];
        let _ = reader.read(&mut buf);
        let len = reader.len();

        if buf == PAYLOAD_HEADER_MAGIC {
            reader = reader.with_offset(0, len)?;
        } else if buf == ZIP_HEADER_MAGIC {
            let mut zip_reader = ZipReader::new(&mut reader);
            let offset = zip_reader.payload_offset()?;
            reader = reader.with_offset(offset, len)?;
        } else {
            return Err(AppError::Other("Invalid payload header".into()));
        }

        Ok(Self {
            reader,
            header: None,
            manifest: None,
            signatures: None,
        })
    }
    pub fn get_header(&mut self) -> AppResult<PayloadHeader> {
        if let Some(header) = self.header {
            return Ok(header);
        }
        let mut buf = vec![0u8; PayloadHeader::size()];
        self.reader.seek(SeekFrom::Start(0))?;
        self.reader.read(&mut buf)?;
        let header = PayloadHeader::from_bytes(&buf)?;
        self.header = Some(header);
        Ok(header)
    }
    pub fn get_signatures_bytes(&mut self) -> AppResult<Vec<u8>> {
        if let Some(signatures) = self.signatures.clone() {
            return Ok(signatures);
        }
        let header = self.get_header()?;
        let mut buf = vec![0u8; header.signature_len as usize];
        self.reader
            .seek(SeekFrom::Start(header.signature_offset()))?;
        self.reader.read(&mut buf)?;
        self.signatures = Some(buf.clone());
        Ok(buf)
    }
    pub fn get_manifest_bytes(&mut self) -> AppResult<Vec<u8>> {
        if let Some(manifest) = &self.manifest {
            return Ok(DeltaArchiveManifest::encode_to_vec(manifest));
        }
        let header = self.get_header()?;
        let mut buf = vec![0u8; header.manifest_len as usize];
        self.reader.read(&mut buf)?;
        Ok(buf)
    }
    pub fn get_manifest(&mut self) -> AppResult<DeltaArchiveManifest> {
        if let Some(manifest) = &self.manifest {
            return Ok(manifest.clone());
        }
        let manifest_bytes = self.get_manifest_bytes()?;
        let manifest = DeltaArchiveManifest::decode(&manifest_bytes[..])
            .map_err(|e| AppError::Other(e.to_string()))?;
        self.manifest = Some(manifest.clone());
        Ok(manifest)
    }

    pub fn get_part_manifest_bytes(&mut self) -> AppResult<Vec<u8>> {
        let manifest = self.get_part_manifest()?;
        Ok(PartManifest::encode_to_vec(&manifest))
    }
    pub fn get_part_manifest(&mut self) -> AppResult<PartManifest> {
        let manifest = self.get_manifest()?;

        let partitions = manifest
            .partitions
            .iter()
            .filter_map(|p| {
                let info = match &p.new_partition_info {
                    Some(info) => Some(PartInfo {
                        size: info.size,
                        hash: info.hash.clone(),
                    }),
                    None => return None,
                };

                let operations = p
                    .operations
                    .iter()
                    .map(|op| {
                        let extents = op
                            .dst_extents
                            .iter()
                            .map(|e| DstExtent {
                                num_blocks: e.num_blocks,
                            })
                            .collect::<Vec<DstExtent>>();

                        Operation {
                            data_offset: op.data_offset,
                            data_length: op.data_length,
                            sha_hash: hex::encode(op.data_sha256_hash()),
                            r#type: op.r#type,
                            dst_extents: extents,
                        }
                    })
                    .collect::<Vec<Operation>>();

                Some(UpdateInfo {
                    partition_name: p.partition_name.clone(),
                    new_partition_info: info,
                    operations,
                    incremental: p.old_partition_info.is_some(),
                })
            })
            .collect::<Vec<UpdateInfo>>();

        Ok(PartManifest {
            partitions,
            block_size: manifest.block_size,
        })
    }
}
