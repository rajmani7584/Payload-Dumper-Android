use bzip2::read::BzDecoder;
use liblzma::read::XzDecoder;
use sha2::{Digest, Sha256};
use std::{
    error::Error,
    fs::File,
    io::{self, BufReader, Read, Seek, SeekFrom, Write},
    str,
};
use zip::ZipArchive;

use crate::chromeos_update_engine::{
    install_operation::Type, DeltaArchiveManifest, PartitionUpdate,
};

const PAYLOAD_HEADER_MAGIC: &str = "CrAU";
const BRILLO_MAJOR_PAYLOAD_VERSION: u64 = 2;
const BLOCK_SIZE: u64 = 4096;

#[derive(Debug)]
struct CError(String);

impl Error for CError {}

impl std::fmt::Display for CError {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        write!(f, "{}", self.0)
    }
}

pub struct Payload {
    path: String,
    file: File,
    zip_offset: u64,
    header: Option<PayloadHeader>,
    manifest: Option<DeltaArchiveManifest>,
}

pub struct PayloadHeader {
    version: u64,
    size: u64,
    manifest_len: u64,
    signature_len: u32,
    data_offset: u64,
    metadata_size: u64,
}

impl Payload {
    pub fn new(path: String) -> Result<Payload, Box<dyn Error>> {
        let mut file = match File::open(path.clone()) {
            Ok(f) => f,
            Err(err) => {
                return Err(format!("Err: {}", err).into());
            }
        };
        let mut offset: u64 = 0;
        if path.ends_with(".zip") {
            let mut archive = ZipArchive::new(&mut file)?;
            offset = archive
                .by_name("payload.bin")
                .or(Err("/payload.bin not found inside zip"))?
                .data_start();
        }
        Ok(Payload {
            path,
            file,
            zip_offset: offset,
            header: None,
            manifest: None,
        })
    }

    fn init(&mut self) -> Result<(), Box<dyn Error>> {
        let _ = self.file.seek(SeekFrom::Start(self.zip_offset))?;

        match self.read_header() {
            Ok(header) => self.header = Some(header),
            Err(err) => {
                return Err(err);
            }
        }

        match self.read_manifest() {
            Ok(manifest) => self.manifest = Some(manifest),
            Err(err) => {
                return Err(err);
            }
        }
        Ok(())
    }

    fn read_header(&mut self) -> Result<PayloadHeader, Box<dyn Error>> {
        let mut buf = [0; 4];

        self.file.read_exact(&mut buf)?;

        if str::from_utf8(&buf)? != PAYLOAD_HEADER_MAGIC {
            return Err("Invalid Payload magic".into());
        }
        let mut header = PayloadHeader {
            version: 0,
            manifest_len: 0,
            signature_len: 0,
            size: 0,
            data_offset: 0,
            metadata_size: 0,
        };

        let mut buf = [0; 8];
        self.file.read_exact(&mut buf)?;
        header.version = u64::from_be_bytes(buf);

        if header.version != BRILLO_MAJOR_PAYLOAD_VERSION {
            return Err("Unsupported payload version".into());
        }

        let mut buf = [0; 8];
        self.file.read_exact(&mut buf)?;
        header.manifest_len = u64::from_be_bytes(buf);

        let mut buf = [0; 4];
        self.file.read_exact(&mut buf)?;
        header.signature_len = u32::from_be_bytes(buf);

        header.size = 24;
        header.metadata_size = header.size + header.manifest_len;
        header.data_offset = header.signature_len as u64 + header.metadata_size;

        Ok(header)
    }

    fn read_manifest(&mut self) -> Result<DeltaArchiveManifest, Box<dyn Error>> {
        let manifest_len = self
            .header
            .as_ref()
            .ok_or(Box::new(CError("header not found".into())))?
            .manifest_len as usize;
        let mut manifest_buf = vec![0; manifest_len];

        self.file.read_exact(&mut manifest_buf)?;

        let delta_manifest: DeltaArchiveManifest = prost::Message::decode(&manifest_buf[..])?;

        Ok(delta_manifest)
    }

    pub fn extract<'p>(
        &mut self,
        partition_to_extract: &str,
        out_file: &str,
        onprogress: &'p dyn Fn(usize),
        onverify: &'p dyn Fn(i8),
    ) -> Result<String, Box<dyn Error>> {
        if let Err(err) = self.init() {
            return Err(err);
        }

        if let Some(manifest) = &self.manifest {
            let mut partition: Option<&PartitionUpdate> = None;
            let partitions = manifest.partitions.clone();
            for (_, p) in partitions.iter().enumerate() {
                if partition_to_extract == p.partition_name {
                    partition = Some(p);
                    if let Err(err) = self.extract_selected(p, out_file, &onprogress, &onverify) {
                        return Err(err);
                    }
                };
            }
            if partition.is_none() {
                return Err(format!(
                    "partition: {} not found in {}",
                    partition_to_extract, &self.path
                )
                .into());
            }
        }

        Ok("Done".into())
    }

    fn extract_selected<'p>(
        &mut self,
        partition: &PartitionUpdate,
        out_file: &str,
        onprogress: &'p dyn Fn(usize),
        onverify: &'p dyn Fn(i8),
    ) -> Result<(), Box<dyn Error>> {
        let mut output_file = match File::create(out_file) {
            Ok(f) => f,
            Err(err) => {
                return Err(format!("file create error: {}", err).into());
            }
        };
        let name = &partition.partition_name;
        let total_operations = partition.operations.len();
        let size = partition
            .new_partition_info
            .as_ref()
            .ok_or(Box::new(CError("partition size not found".into())))?
            .size
            .expect("size not found");
        let mut progress_track: usize = 0;

        let mut reader = BufReader::new(&self.file);

        for operation in &partition.operations {
            if operation.dst_extents.is_empty() {
                return Err(format!("invalid dstextents for partition: {}", name).into());
            }

            let dst = operation.dst_extents[0];
            let data_offset = operation.data_offset.unwrap_or(0)
                + self
                    .header
                    .as_ref()
                    .ok_or(Box::new(CError("header not found".into())))?
                    .data_offset;
            let data_length = operation.data_length.unwrap_or(0);
            let expected_uncompress_block_size = dst.num_blocks() * BLOCK_SIZE;

            let _ = reader.seek(SeekFrom::Start(self.zip_offset + data_offset));
            let mut reader = Read::take(&mut reader, data_length);

            let mut sha_buf = Sha256::new();
            let mut buf = vec![0; data_length as usize];
            let _ = reader.read(&mut buf)?;

            sha_buf.update(&mut buf);

            let bytes_written: u64;
            match operation.r#type() {
                Type::Replace => {
                    bytes_written = io::copy(&mut buf.as_slice(), &mut output_file)?;
                }
                Type::ReplaceXz => {
                    let mut decoder = XzDecoder::new(buf.as_slice());
                    bytes_written = io::copy(&mut decoder, &mut output_file)?;
                }
                Type::ReplaceBz => {
                    let mut decoder = BzDecoder::new(buf.as_slice());
                    bytes_written = io::copy(&mut decoder, &mut output_file)?;
                }
                Type::Zero => {
                    let mut filler = io::repeat(0).take(expected_uncompress_block_size);
                    bytes_written = io::copy(&mut filler, &mut output_file)?;
                }
                _ => {
                    return Err(format!("Unsupported operation type: {}", operation.r#type).into());
                }
            }
            if bytes_written != expected_uncompress_block_size {
                return Err("Unexpected byte written".into());
            }
            let new_hash = hex::encode(sha_buf.finalize());
            let expected_hash = hex::encode(operation.data_sha256_hash());
            if expected_hash != "" {
                if new_hash != expected_hash {
                    return Err("Operation Hash mismatch error".into());
                }
            }
            progress_track += 1;
            onprogress((progress_track * 100) / total_operations);
        }

        output_file.flush()?;
        onverify(0);
        let mut hasher = Sha256::new();
        let file = File::open(&out_file)?;
        let mut reader = BufReader::new(file);
        let buf_size = if size > 1024 * 1024 {
            1024 * 1024
        } else {
            size as usize
        };
        let mut buf = vec![0; buf_size];
        loop {
            let bytes_read = reader.read(&mut buf)?;
            if bytes_read == 0 {
                break;
            }
            hasher.update(&buf[..bytes_read]);
        }
        let new_hash = hex::encode(hasher.finalize());
        let hash_encoded = partition
            .new_partition_info
            .as_ref()
            .ok_or(Box::new(CError("partition hash not found".into())))?
            .hash
            .as_ref()
            .ok_or(Box::new(CError("partition hash not found".into())))?
            .clone();
        let hash = hex::encode(hash_encoded);
        if hash != new_hash {
            onverify(2);
            return Err("Partition Hash mismatch error".into());
        }
        onverify(1);

        Ok(())
    }

    pub fn get_partition_list(&mut self) -> Result<String, Box<dyn Error>> {
        let mut msg: String = Default::default();

        if let Err(err) = self.init() {
            return Err(err);
        }

        if let Some(manifest) = &self.manifest {
            if let Some(header) = &self.header {
                msg.insert_str(
                    msg.len(),
                    format!(
                        "data:{}|{}:",
                        header.version,
                        manifest.security_patch_level()
                    )
                    .as_str(),
                );
            }
            let mut incremental = false;

            for (_i, partition) in manifest.partitions.iter().enumerate() {
                let partition_name = &partition.partition_name;
                let partition_size = partition
                    .new_partition_info
                    .as_ref()
                    .map_or(0, |info| info.size.expect("info size not found"));
                let partition_hash = partition
                    .new_partition_info
                    .as_ref()
                    .and_then(|info| info.hash.clone())
                    .expect("msg");

                if !incremental {
                    incremental = partition.old_partition_info.is_some()
                };

                let mg = format!(
                    "{}|{:?}|{},",
                    partition_name,
                    partition_size,
                    hex::encode(partition_hash)
                );
                msg.insert_str(msg.len(), mg.as_str());
            }
            msg.remove(msg.len() - 1);
            msg.insert_str(msg.len(), format!(":{}", incremental).as_str());
        } else {
            return Err("No Partition Found".into())
        }
        Ok(format!("{}", msg).into())
    }
    pub fn get_raw_data(&mut self) -> Result<String, Box<dyn Error>> {
        if let Err(err) = self.init() {
            return Err(err);
        }

        Ok(format!("{:?}", serde_json::to_string(&self.manifest).unwrap_or("{error: \"error\"}".to_string())))
    }
}