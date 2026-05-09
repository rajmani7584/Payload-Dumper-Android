use jni::{
    EnvUnowned, jni_mangle,
    objects::{JClass, JString},
    sys::jstring,
};

#[jni_mangle("com.rajmani7584.payloaddumper.nativeHelper.PayloadDumper")]
pub fn init_native(mut e: EnvUnowned, _class: JClass) -> jstring {
    let res =
        e.with_env(|env| -> Result<_, _> { JString::from_str(env, "Hello from rust - jni!") });

    res.resolve::<jni::errors::ThrowRuntimeExAndDefault>()
        .into_raw()
}
