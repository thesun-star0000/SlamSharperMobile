use jni::objects::{JClass, JFloatArray, JString};
use jni::sys::{jdouble, jfloatArray, jint, jlong, jstring};
use jni::JNIEnv;
use once_cell::sync::Lazy;
use os_clap::{Library, Plugin as ClapPlugin};
use os_engine::HostedEffect;
use os_host::Plugin as HostPlugin;
use serde_json::json;
use std::collections::HashMap;
use std::path::Path;
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::{Arc, Mutex};

struct Instance { _library: Arc<Library>, plugin: ClapPlugin, rate: u32 }
static NEXT_ID: AtomicI64 = AtomicI64::new(1);
static INSTANCES: Lazy<Mutex<HashMap<i64, Instance>>> = Lazy::new(|| Mutex::new(HashMap::new()));

fn java_string(env: &mut JNIEnv, value: JString) -> Result<String, String> {
    env.get_string(&value).map(|s| s.into()).map_err(|e| e.to_string())
}
fn to_java<'a>(env: &mut JNIEnv<'a>, s: String) -> jstring {
    env.new_string(s).map(|v| v.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_slamsharper_mobile_clap_NativeClapHost_nativeScan(
    mut env: JNIEnv, _class: JClass, folder: JString,
) -> jstring {
    let result = (|| -> Result<String, String> {
        let folder = java_string(&mut env, folder)?;
        let mut found = Vec::new();
        for item in std::fs::read_dir(folder).map_err(|e| e.to_string())? {
            let path = item.map_err(|e| e.to_string())?.path();
            if path.extension().and_then(|x| x.to_str()) != Some("clap") { continue; }
            match Library::load(&path) {
                Ok(lib) => for d in &lib.plugins {
                    found.push(json!({"path":path.to_string_lossy(),"id":d.id,"name":d.name,"vendor":d.vendor,"version":d.version,"kind":format!("{:?}",d.kind)}));
                },
                Err(error) => found.push(json!({"path":path.to_string_lossy(),"error":error})),
            }
        }
        Ok(serde_json::to_string(&found).unwrap_or_else(|_| "[]".into()))
    })();
    to_java(&mut env, result.unwrap_or_else(|e| json!({"error":e}).to_string()))
}

#[no_mangle]
pub extern "system" fn Java_com_slamsharper_mobile_clap_NativeClapHost_nativeOpen(
    mut env: JNIEnv, _class: JClass, path: JString, plugin_id: JString, sample_rate: jint,
) -> jlong {
    let result = (|| -> Result<i64, String> {
        let path = java_string(&mut env, path)?;
        let plugin_id = java_string(&mut env, plugin_id)?;
        let lib = Library::load(Path::new(&path))?;
        let mut plugin = ClapPlugin::new(&lib, &plugin_id)?;
        if !plugin.activate(sample_rate.max(8000) as u32) { return Err("CLAP plugin refused activation".into()); }
        let id = NEXT_ID.fetch_add(1, Ordering::Relaxed);
        INSTANCES.lock().map_err(|e|e.to_string())?.insert(id, Instance { _library: lib, plugin, rate: sample_rate.max(8000) as u32 });
        Ok(id)
    })();
    result.unwrap_or(-1) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_slamsharper_mobile_clap_NativeClapHost_nativeParams(
    mut env: JNIEnv, _class: JClass, handle: jlong,
) -> jstring {
    let json_text = INSTANCES.lock().ok().and_then(|mut all| all.get_mut(&(handle as i64)).map(|i| {
        let mut values = Vec::new();
        for index in 0..i.plugin.param_count() {
            if let Some(p) = i.plugin.param_info(index) {
                values.push(json!({"id":p.id,"name":p.name,"group":p.group,"min":p.min,"max":p.max,"value":i.plugin.param_value(p.id).unwrap_or(p.default),"default":p.default,"steps":p.steps,"readOnly":p.read_only,"bypass":p.bypass}));
            }
        }
        serde_json::to_string(&values).unwrap_or_else(|_| "[]".into())
    })).unwrap_or_else(|| "[]".into());
    to_java(&mut env, json_text)
}

#[no_mangle]
pub extern "system" fn Java_com_slamsharper_mobile_clap_NativeClapHost_nativeSetParam(
    _env: JNIEnv, _class: JClass, handle: jlong, param_id: jint, value: jdouble,
) -> jint {
    INSTANCES.lock().ok().and_then(|mut all| all.get_mut(&(handle as i64)).map(|i| i.plugin.set_param(param_id as u32, value.clamp(0.0,1.0)) as jint)).unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_com_slamsharper_mobile_clap_NativeClapHost_nativeProcessCopy(
    mut env: JNIEnv, _class: JClass, handle: jlong, samples: JFloatArray,
) -> jfloatArray {
    let len = match env.get_array_length(&samples) { Ok(v)=>v as usize,Err(_)=>return std::ptr::null_mut() };
    let mut input=vec![0.0f32;len];
    if env.get_float_array_region(&samples,0,&mut input).is_err() { return std::ptr::null_mut(); }
    if input.len()%2!=0 { return std::ptr::null_mut(); }
    let mut left=Vec::with_capacity(input.len()/2);let mut right=Vec::with_capacity(input.len()/2);
    for pair in input.chunks_exact(2){left.push(pair[0]);right.push(pair[1]);}
    let ok=if let Ok(mut all)=INSTANCES.lock(){if let Some(i)=all.get_mut(&(handle as i64)){i.plugin.set_offline(true);i.plugin.process_fx(i.rate,&mut left,&mut right);true}else{false}}else{false};
    if !ok{return std::ptr::null_mut();}
    let mut output=Vec::with_capacity(input.len());for i in 0..left.len(){output.push(left[i]);output.push(right[i]);}
    let out=match env.new_float_array(output.len() as i32){Ok(v)=>v,Err(_)=>return std::ptr::null_mut()};
    if env.set_float_array_region(&out,0,&output).is_err(){return std::ptr::null_mut();}out.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_slamsharper_mobile_clap_NativeClapHost_nativeClose(
    _env: JNIEnv, _class: JClass, handle: jlong,
) { if let Ok(mut all)=INSTANCES.lock(){all.remove(&(handle as i64));} }
