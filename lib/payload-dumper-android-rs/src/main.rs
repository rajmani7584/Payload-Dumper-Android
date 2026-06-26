use std::path::PathBuf;

use crate::{helper::errors::AppResult, payload::Payload};

mod engine;
mod helper;
mod payload;
mod reader;

#[tokio::main]
async fn main() -> AppResult<()> {
    let payload =
        Payload::from_file("/home/rajmani/Downloads/a1e9cce40c97479d8f4b62d55988b72b.zip");

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

    for p in manifest.partitions {
        let partition_name = &p.partition_name;

        if partition_name == "boot" {
            let out = PathBuf::from("./out/boot.img");
            dumper
                .dump(
                    p.clone(),
                    4096,
                    &out,
                    header,
                    true,
                    Some(|a| {
                        println!("{}: Progress: {}%\r\r", partition_name, a);
                        true
                    }),
                )
                .await?;
        }

        let len = p.operations.iter().fold(0, |a, b| a + b.data_length());

        println!(
            "{}: {} B -> {} B",
            partition_name,
            len,
            p.new_partition_info.unwrap().size()
        );
    }

    Ok(())
}
