use std::path::PathBuf;

use crate::{
    helper::{
        constants::BLOCK_SIZE,
        errors::{AppError, AppResult},
    },
    payload::Payload,
};

mod engine;
mod helper;
mod payload;
mod reader;

#[tokio::main]
async fn main() -> AppResult<()> {
    let payload = Payload::from_file("/home/rajmani/otas/c344950e5349e27118c73340703ea15a.zip");

    // let payload = Payload::from_url(
    //     "http://10.223.107.181:3000/Files%20by%20Google/lr83XRvh8xjbCD4YBFyCIWIsuc1I.zip",
    // );

    // let b = dumper.get_manifest_bytes()?;

    // let manifest: DeltaArchiveManifest =
    //     prost::Message::decode(&*b).map_err(|e| AppError::Other(e.to_string()))?;

    // let sig = dumper.get_signatures_bytes()?;

    // let sig = Signatures::decode(sig.as_slice()).unwrap();
    // for (b, idx) in sig.signatures[0].data().iter().enumerate() {
    //     print!("{}{idx:02x} ", if b % 16 == 0 { "\n" } else { "" });
    // }
    // println!();

    // dbg!(&sig.signatures[0]);

    // let mut handles = Vec::new();

    // let p = ["boot", "vbmeta", "vendor_boot", "init_boot", "dtbo"];
    // for (i, p) in p.iter().enumerate() {
    //     let p = p.to_string();
    //     let path = PathBuf::from("./out").join(format!("{p}.img"));

    //     let mut dumper = payload::PayloadDumper::new(payload.clone())?;
    //     let handle = tokio::task::spawn(async move {
    //         match dumper
    //             .dump(
    //                 &p,
    //                 &path,
    //                 false,
    //                 Some(|a| {
    //                     println!("{}:{i}. Progress: {}%\r\r", p, a);
    //                 }),
    //             )
    //             .await
    //         {
    //             Ok(()) => {}
    //             Err(_) => println!("skipping: {}", p),
    //         }
    //     });

    //     handles.push(handle);
    // }

    // for task in handles {
    //     task.await.unwrap()
    // }

    let mut dumper = payload::PayloadDumper::new(payload.clone())?;

    let header = dumper.get_header()?;
    let manifest = dumper.get_part_manifest()?;

    let partition = manifest
        .partitions
        .iter()
        .find(|p| p.partition_name == "boot")
        .ok_or(AppError::Other("Boot partition not found".to_string()))?;

    if partition.incremental {
        panic!("incremental");
    }

    dumper
        .dump(
            partition.clone(),
            manifest.block_size.unwrap_or(BLOCK_SIZE) as u64,
            &PathBuf::from("./out").join("boot.img"),
            header,
            false,
            Some(|p| {
                println!("{p}%\r");
                true
            }),
        )
        .await?;

    Ok(())
}
