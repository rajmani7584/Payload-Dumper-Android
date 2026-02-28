use jni::objects::{JClass, JObject, JString, JValue};
use jni::JNIEnv;
use payload::Payload;
use std::cell::RefCell;
use std::error::Error;

use jni::sys::jstring;

mod chromeos_update_engine;
mod payload;

#[no_mangle]
pub extern "system" fn Java_com_rajmani7584_payloaddumper_models_PayloadDumper_getPartitionList<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
) -> jstring {
    let mut msg: String = Default::default();

    let mut payload = match Payload::new(env.get_string(&path).expect("Error: msg").into()) {
        Ok(p) => p,
        Err(err) => {
            return env
                .new_string(format!("Error: {}", err))
                .expect("Error: Can't create rust_string")
                .into_raw();
        }
    };

    let _ = match payload.get_partition_list() {
        Ok(res) => {
            msg.insert_str(msg.len(), &res);
        }
        Err(err) => {
            return env
                .new_string(format!("Error: {}", err))
                .expect("Error: Can't create rust_string")
                .into_raw();
        }
    };

    // let max_len = msg.len();
    // let msg = if msg.len() > max_len {
    //     msg.chars().take(max_len).collect::<String>()
    // } else {
    //     msg
    // };

    let msg = env.new_string(msg).expect("Error: Can't create rust_string").into_raw();

    return msg;
}

#[no_mangle]
pub extern "system" fn Java_com_rajmani7584_payloaddumper_models_PayloadDumper_extractPartition(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
    partition: JString,
    out_path: JString,
    callback: JObject,
) -> jstring {
    let path: String = match env.get_string(&path) {
        Ok(p) => p.into(),
        Err(_) => {
            return env
                .new_string("Error: Failed to get path")
                .unwrap()
                .into_raw()
        }
    };

    let partition: String = match env.get_string(&partition) {
        Ok(p) => p.into(),
        Err(_) => {
            return env
                .new_string("Error: Failed to get partition")
                .unwrap()
                .into_raw()
        }
    };

    let out_path: String = match env.get_string(&out_path) {
        Ok(p) => p.into(),
        Err(_) => {
            return env
                .new_string("Error: Failed to get output path")
                .unwrap()
                .into_raw()
        }
    };

    let env_c = RefCell::new(env);

    let msg: String = Default::default();
    let msg_c = RefCell::new(msg.clone());
    let result = (|| -> Result<String, Box<dyn Error>> {
        let mut payload = Payload::new(path)?;

        let e = payload.extract(&partition, &out_path, &|progress| {
            let mut env_cloned = env_c.borrow_mut();
            if let Err(err) = env_cloned.call_method(
                &callback,
                "onProgressCallback",
                "(J)V",
                &[JValue::from(progress as i64)],
            ) {
                msg_c
                    .borrow_mut()
                    .insert_str(msg.len(), format!("{}", err).as_str());
            }
        }, &|verifi_status| {
            let mut env_cloned = env_c.borrow_mut();
            if let Err(err) = env_cloned.call_method(
                &callback,
                "onVerifyCallback",
                "(I)V",
                &[JValue::from(verifi_status as i32)],
            ) {
                msg_c
                    .borrow_mut()
                    .insert_str(msg.len(), format!("{}", err).as_str());
            }
        })?;

        if !msg_c.borrow().is_empty() {
            return Err(format!("{}", msg).as_str().into());
        }

        Ok(e)
    })();

    let msg = match result {
        Ok(msg) => msg,
        Err(err) => format!("Error: {}", err),
    };
    let x = env_c.borrow().new_string(msg).unwrap().into_raw();
    x
}

#[no_mangle]
pub extern "system" fn Java_com_rajmani7584_payloaddumper_models_PayloadDumper_getRawData<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
) -> jstring {
    let mut msg: String = Default::default();

    let mut payload = match Payload::new(env.get_string(&path).expect("Error: msg").into()) {
        Ok(p) => p,
        Err(err) => {
            return env
                .new_string(format!("Error: {}", err))
                .expect("Error: Can't create rust_string")
                .into_raw();
        }
    };

    let _ = match payload.get_raw_data() {
        Ok(res) => {
            msg.insert_str(msg.len(), &res);
        }
        Err(err) => {
            return env
                .new_string(format!("Error: {}", err))
                .expect("Error: Can't create rust_string")
                .into_raw();
        }
    };
    let msg = env.new_string(msg).expect("Error: Can't create rust_string").into_raw();

    return msg;
}