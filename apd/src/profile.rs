use anyhow::{Context, Result};
use log::{info, warn};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::fs;
use std::path::Path;

use crate::package::read_ap_package_config;

/* ─── Data Structures ─── */

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Capabilities {
    pub effective: u64,
    pub permitted: u64,
    pub inheritable: u64,
}

impl Default for Capabilities {
    fn default() -> Self {
        Self { effective: 0, permitted: 0, inheritable: 0 }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RootProfileConfig {
    pub uid: i32,
    pub gid: i32,
    pub groups: Vec<i32>,
    pub capabilities: Capabilities,
    pub selinux_domain: String,
    pub namespaces: i32,
}

impl Default for RootProfileConfig {
    fn default() -> Self {
        Self {
            uid: 0, gid: 0, groups: vec![0],
            capabilities: Capabilities {
                effective: 0xffffffffffffffff,
                permitted: 0xffffffffffffffff,
                inheritable: 0,
            },
            selinux_domain: "u:r:magisk:s0".to_string(),
            namespaces: 0,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppProfile {
    pub pkg: String,
    pub uid: i32,
    pub allow_su: bool,
    pub exclude_modules: bool,
    pub root_profile: RootProfileConfig,
}

impl Default for AppProfile {
    fn default() -> Self {
        Self {
            pkg: String::new(), uid: 0, allow_su: false,
            exclude_modules: false, root_profile: RootProfileConfig::default(),
        }
    }
}

/* ─── Storage ─── */

const PROFILES_DIR: &str = "/data/adb/ap/profiles";

fn profile_path(pkg: &str) -> String {
    format!("{}/{}.json", PROFILES_DIR, pkg)
}

pub fn load_all_profiles() -> HashMap<String, AppProfile> {
    let dir = Path::new(PROFILES_DIR);
    if !dir.exists() { return HashMap::new(); }
    let mut profiles = HashMap::new();
    for entry in fs::read_dir(dir).into_iter().flatten() {
        let path = entry.unwrap().path();
        if path.extension().and_then(|s| s.to_str()) != Some("json") { continue; }
        let content = match fs::read_to_string(&path) {
            Ok(c) => c, Err(e) => { warn!("[profile] read error: {}", e); continue; }
        };
        match serde_json::from_str::<AppProfile>(&content) {
            Ok(p) => { profiles.insert(p.pkg.clone(), p); }
            Err(e) => { warn!("[profile] parse error: {}", e); }
        }
    }
    profiles
}

pub fn load_profile(pkg: &str) -> Option<AppProfile> {
    let content = fs::read_to_string(profile_path(pkg)).ok()?;
    serde_json::from_str(&content).ok()
}

pub fn save_profile(profile: &AppProfile) -> Result<()> {
    let dir = Path::new(PROFILES_DIR);
    fs::create_dir_all(dir).context("create profiles dir")?;
    let path = profile_path(&profile.pkg);
    let tmp = format!("{}.tmp", path);
    let json = serde_json::to_string_pretty(profile).context("serialize")?;
    fs::write(&tmp, &json).context("write tmp")?;
    fs::rename(&tmp, &path).context("rename")?;
    info!("[profile] saved: {}", profile.pkg);
    Ok(())
}

pub fn delete_profile(pkg: &str) -> Result<()> {
    let path = profile_path(pkg);
    if Path::new(&path).exists() {
        fs::remove_file(&path).context("remove")?;
        info!("[profile] deleted: {}", pkg);
    }
    Ok(())
}

/* ─── CLI Commands ─── */

pub fn init_from_package_config() -> Result<()> {
    let configs = read_ap_package_config();
    let mut profiles = load_all_profiles();
    for c in &configs {
        if profiles.contains_key(&c.pkg) { continue; }
        let p = AppProfile {
            pkg: c.pkg.clone(), uid: c.uid,
            allow_su: c.allow == 1,
            exclude_modules: c.exclude == 1,
            ..Default::default()
        };
        save_profile(&p)?;
        profiles.insert(c.pkg.clone(), p);
    }
    Ok(())
}

pub fn show_profile(pkg: &str) -> Result<()> {
    match load_profile(pkg) {
        Some(p) => { println!("{}", serde_json::to_string_pretty(&p)?); Ok(()) }
        None => Err(anyhow::anyhow!("not found: {}", pkg)),
    }
}

pub fn list_profiles() -> Result<()> {
    let profiles = load_all_profiles();
    if profiles.is_empty() { println!("no profiles"); return Ok(()); }
    println!("{:<25} {:<8} {:<6} {:<8}", "Package", "UID", "Allow", "Exclude");
    for (_, p) in &profiles {
        println!("{:<25} {:<8} {:<6} {:<8}",
            p.pkg, p.uid,
            if p.allow_su { "yes" } else { "no" },
            if p.exclude_modules { "yes" } else { "no" });
    }
    println!("total: {}", profiles.len());
    Ok(())
}

pub fn set_simple(pkg: &str, uid: i32, mode: &str, scontext: &str) -> Result<()> {
    let mut p = load_profile(pkg).unwrap_or_else(|| AppProfile { pkg: pkg.into(), uid, ..Default::default() });
    match mode {
        "allow" | "root" => { p.allow_su = true; p.exclude_modules = false; p.root_profile.selinux_domain = scontext.into(); }
        "deny" | "nobody" => { p.allow_su = false; p.exclude_modules = false; }
        "exclude" => { p.allow_su = false; p.exclude_modules = true; }
        _ => return Err(anyhow::anyhow!("mode: allow/root, deny/nobody, exclude")),
    }
    save_profile(&p)?;
    info!("[profile] set {} {} {}", pkg, uid, mode);
    Ok(())
}

/// `profile exec`: read profile → su → apply SELinux context → exec command
pub fn exec_profile(pkg: &str, uid: i32, cmd: &[String]) -> Result<()> {
    let profile = load_profile(pkg)
        .ok_or_else(|| anyhow::anyhow!("profile not found: {}", pkg))?;
    if !profile.allow_su {
        return Err(anyhow::anyhow!("{} not allowed su", pkg));
    }

    info!("[profile] exec: {} (uid={}) -> {} ({:?})", pkg, uid,
          profile.root_profile.selinux_domain, cmd);

    // Set SELinux context via /proc/self/attr/current
    // Root can write directly since SELinux is bypassed by KP's all_allow_sctx
    let ctx = &profile.root_profile.selinux_domain;
    if !ctx.is_empty() {
        if let Err(e) = fs::write("/proc/self/attr/current", ctx.as_bytes()) {
            warn!("[profile] set context failed: {} (context still applied via su)", e);
        }
    }

    // Execute the command
    let mut child = std::process::Command::new(&cmd[0]);
    if cmd.len() > 1 {
        child.args(&cmd[1..]);
    }
    let status = child.status().context("exec failed")?;
    std::process::exit(status.code().unwrap_or(1));
}
