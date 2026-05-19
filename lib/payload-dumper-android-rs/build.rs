use std::path::PathBuf;

fn main() {
    let path = PathBuf::from(".");

    println!("Cargo:rerun-if-changed=update_metadata.proto");

    prost_build::Config::new()
        .out_dir(path.join("src/engine").canonicalize().unwrap())
        .compile_protos(
            &["update_metadata.proto", "part_manifest.proto"],
            &[path.join(".")],
        )
        .unwrap();
}
