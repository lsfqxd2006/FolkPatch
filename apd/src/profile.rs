use anyhow::{Context, Result};
use log::{info, warn};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::fs;
use std::path::Path;

use crate::package::read_ap_package_config;
use crate::package::PackageConfig;

/* ─── Data Structures (matches kernel struct ap_profile_data) ─── */

const AP_MAX_GROUPS: u32 = 32;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Capabilities {
    pub effective: u64,
    pub permitted: u64,
    pub inheritable: u64,
}

impl Default for Capabilities {
    fn default() -> Self {
        Self {
            effective: 0,
            permitted: 0,
            inheritable: 0,
        }
    }
}

/// Full root profile — what KernelSU calls root_profile
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
            uid: 0,
            gid: 0,
            groups: vec![0],
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

/// Non-root profile
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NonRootProfileConfig {
    pub umount_modules: bool,
}

impl Default for NonRootProfileConfig {
    fn default() -> Self {
        Self {
            umount_modules: true,
        }
    }
}

/// Per-app full profile
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppProfile {
    /// Package name (key)
    pub pkg: String,
    /// Current UID
    pub uid: i32,
    /// Whether SU is allowed
    pub allow_su: bool,
    /// Whether this app is excluded from module mounting
    pub exclude_modules: bool,
    /// Root profile settings (only used if allow_su is true)
    pub root_profile: RootProfileConfig,
    /// Non-root profile settings (only used if allow_su is false)
    pub non_root_profile: NonRootProfileConfig,
}

impl Default for AppProfile {
    fn default() -> Self {
        Self {
            pkg: String::new(),
            uid: 0,
            allow_su: false,
            exclude_modules: false,
            root_profile: RootProfileConfig::default(),
            non_root_profile: NonRootProfileConfig::default(),
        }
    }
}

/* ─── Kernel Data Layout (for supercall) ─── */

/*
 * Must match KP's struct ap_profile_data exactly.
 * Layout: uid(4) + groups(128) + groups_count(4) + cap*(24) + sctx(96) + ns(4) + flag(1)
 */
#[repr(C)]
struct KernelProfileData {
    uid: i32,
    groups: [i32; AP_MAX_GROUPS as usize],
    groups_count: u32,
    cap_effective: u64,
    cap_permitted: u64,
    cap_inheritable: u64,
    selinux_domain: [u8; 0x60],  // SUPERCALL_SCONTEXT_LEN
    namespaces: i32,
    has_root_config: u8,
}

impl From<&AppProfile> for KernelProfileData {
    fn from(profile: &AppProfile) -> Self {
        let mut groups = [0i32; AP_MAX_GROUPS as usize];
        let groups_count = if profile.allow_su {
            let count = std::cmp::min(profile.root_profile.groups.len() as u32, AP_MAX_GROUPS);
            for i in 0..count as usize {
                groups[i] = profile.root_profile.groups[i];
            }
            count
        } else {
            0
        };

        let (caps_eff, caps_perm, caps_inh) = if profile.allow_su {
            (
                profile.root_profile.capabilities.effective,
                profile.root_profile.capabilities.permitted,
                profile.root_profile.capabilities.inheritable,
            )
        } else {
            (0, 0, 0)
        };

        let ns = if profile.allow_su {
            profile.root_profile.namespaces
        } else {
            0
        };

        let mut sdomain = [0u8; 0x60];
        let sctx = if profile.allow_su {
            profile.root_profile.selinux_domain.as_bytes()
        } else {
            &[]
        };
        let copy_len = std::cmp::min(sctx.len(), sdomain.len() - 1);
        sdomain[..copy_len].copy_from_slice(&sctx[..copy_len]);

        KernelProfileData {
            uid: profile.uid,
            groups,
            groups_count,
            cap_effective: caps_eff,
            cap_permitted: caps_perm,
            cap_inheritable: caps_inh,
            selinux_domain: sdomain,
            namespaces: ns,
            has_root_config: if profile.allow_su { 1 } else { 0 },
        }
    }
}

/* ─── Storage ─── */

const PROFILES_DIR: &str = "/data/adb/ap/profiles";

fn profile_path(pkg: &str) -> String {
    // Sanitize package name (dots are OK in filenames on Linux)
    format!("{}/{}.json", PROFILES_DIR, pkg)
}

/// Load all profiles from disk
pub fn load_all_profiles() -> HashMap<String, AppProfile> {
    let dir = Path::new(PROFILES_DIR);
    if !dir.exists() {
        return HashMap::new();
    }

    let mut profiles = HashMap::new();
    if let Ok(entries) = fs::read_dir(dir) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.extension().and_then(|s| s.to_str()) != Some("json") {
                continue;
            }
            let content = match fs::read_to_string(&path) {
                Ok(c) => c,
                Err(e) => {
                    warn!("[profile] Failed to read {}: {}", path.display(), e);
                    continue;
                }
            };
            match serde_json::from_str::<AppProfile>(&content) {
                Ok(profile) => {
                    profiles.insert(profile.pkg.clone(), profile);
                }
                Err(e) => {
                    warn!("[profile] Failed to parse {}: {}", path.display(), e);
                }
            }
        }
    }
    profiles
}

/// Load a single profile
pub fn load_profile(pkg: &str) -> Option<AppProfile> {
    let path = profile_path(pkg);
    let content = fs::read_to_string(&path).ok()?;
    serde_json::from_str(&content).ok()
}

/// Save a profile to disk
pub fn save_profile(profile: &AppProfile) -> Result<()> {
    let dir = Path::new(PROFILES_DIR);
    fs::create_dir_all(dir).context("Failed to create profiles directory")?;

    let path = profile_path(&profile.pkg);
    let tmp_path = format!("{}.tmp", path);
    let json = serde_json::to_string_pretty(profile)
        .context("Failed to serialize profile")?;

    fs::write(&tmp_path, &json).context("Failed to write temp profile")?;
    fs::rename(&tmp_path, &path).context("Failed to rename profile file")?;

    info!("[profile] Saved profile for {}", profile.pkg);
    Ok(())
}

/// Delete a profile from disk
pub fn delete_profile(pkg: &str) -> Result<()> {
    let path = profile_path(pkg);
    if Path::new(&path).exists() {
        fs::remove_file(&path).context("Failed to remove profile file")?;
        info!("[profile] Deleted profile for {}", pkg);
    }
    Ok(())
}

/// Sync a single profile to the kernel via supercall
pub fn sync_profile_to_kernel(profile: &AppProfile) -> Result<()> {
    let key = std::ffi::CStr::from_bytes_with_nul(b"su\0")
        .map_err(|_| anyhow::anyhow!("Invalid superkey"))?;

    let kernel_data = KernelProfileData::from(profile);
    let data_ptr = &kernel_data as *const KernelProfileData as *const std::ffi::c_void;

    let cmd_val = crate::supercall::ver_and_cmd_val(0x1201); // SUPERCALL_AP_SET_PROFILE

    let rc = crate::supercall::sc_ap_set_profile(&key, data_ptr, cmd_val);
    if rc != 0 {
        warn!("[profile] Kernel sync failed for {} (uid={}): rc={}", profile.pkg, profile.uid, rc);
        return Err(anyhow::anyhow!("Kernel sync failed with rc={}", rc));
    }
    info!("[profile] Synced {} to kernel (uid={})", profile.pkg, profile.uid);
    Ok(())
}

/// Delete a profile from kernel
fn delete_kernel_profile(uid: i32) -> Result<()> {
    let key = std::ffi::CStr::from_bytes_with_nul(b"su\0")
        .map_err(|_| anyhow::anyhow!("Invalid superkey"))?;

    let cmd_val = crate::supercall::ver_and_cmd_val(0x1203); // SUPERCALL_AP_DEL_PROFILE
    let rc = crate::supercall::sc_ap_del_profile(&key, uid, cmd_val);
    if rc != 0 && rc != -2 { // -ENOENT is OK
        warn!("[profile] Kernel delete failed for uid={}: rc={}", uid, rc);
    }
    Ok(())
}

/* ─── CLI Commands ─── */

/// Initialize default profiles from existing package config
pub fn init_from_package_config() -> Result<()> {
    let configs = read_ap_package_config();
    let mut profiles = load_all_profiles();
    let mut changed = false;

    for config in &configs {
        if profiles.contains_key(&config.pkg) {
            continue; // Already has a profile
        }

        let allow_su = config.allow == 1;
        let exclude = config.exclude == 1;

        let mut profile = AppProfile {
            pkg: config.pkg.clone(),
            uid: config.uid,
            allow_su,
            exclude_modules: exclude,
            ..Default::default()
        };

        if allow_su {
            profile.root_profile.selinux_domain = config.sctx.clone();
        }

        save_profile(&profile)?;
        sync_profile_to_kernel(&profile)?;
        profiles.insert(config.pkg.clone(), profile);
        changed = true;
    }

    if changed {
        info!("[profile] Initialized profiles from package config");
    }
    Ok(())
}

/// Show profile for a package
pub fn show_profile(pkg: &str) -> Result<()> {
    match load_profile(pkg) {
        Some(profile) => {
            println!("{}", serde_json::to_string_pretty(&profile)?);
            Ok(())
        }
        None => Err(anyhow::anyhow!("Profile not found for {}", pkg)),
    }
}

/// List all profiles
pub fn list_profiles() -> Result<()> {
    let profiles = load_all_profiles();
    if profiles.is_empty() {
        println!("No profiles configured");
        return Ok(());
    }
    println!("{:<25} {:<8} {:<6} {:<8}", "Package", "UID", "Allow", "Exclude");
    println!("{}", "-".repeat(50));
    for (_, p) in profiles.iter() {
        println!("{:<25} {:<8} {:<6} {:<8}",
            p.pkg, p.uid,
            if p.allow_su { "yes" } else { "no" },
            if p.exclude_modules { "yes" } else { "no" });
    }
    println!("Total: {} profiles", profiles.len());
    Ok(())
}

/// Set root profile fields
pub fn set_root_profile(pkg: &str, app_uid: i32, root_uid: i32, root_gid: i32, groups: &[i32],
                        capabilities_eff: u64, capabilities_perm: u64, capabilities_inh: u64,
                        selinux_domain: &str, namespaces: i32) -> Result<()> {
    let mut profile = load_profile(pkg)
        .unwrap_or_else(|| AppProfile {
            pkg: pkg.to_string(),
            uid: app_uid,
            ..Default::default()
        });

    profile.uid = app_uid;
    profile.allow_su = true;
    profile.root_profile = RootProfileConfig {
        uid: root_uid,
        gid: root_gid,
        groups: if groups.is_empty() { vec![0] } else { groups.to_vec() },
        capabilities: Capabilities {
            effective: capabilities_eff,
            permitted: capabilities_perm,
            inheritable: capabilities_inh,
        },
        selinux_domain: selinux_domain.to_string(),
        namespaces,
    };

    save_profile(&profile)?;
    sync_profile_to_kernel(&profile)?;
    Ok(())
}

/// Set simple allow/deny (compatible with existing PackageConfig behavior)
pub fn set_simple(pkg: &str, uid: i32, mode: &str, scontext: &str) -> Result<()> {
    let mut profile = load_profile(pkg)
        .unwrap_or_else(|| AppProfile {
            pkg: pkg.to_string(),
            uid,
            ..Default::default()
        });

    match mode {
        "allow" | "root" => {
            profile.allow_su = true;
            profile.exclude_modules = false;
            profile.root_profile.selinux_domain = scontext.to_string();
        }
        "deny" | "nobody" => {
            profile.allow_su = false;
            profile.exclude_modules = false;
        }
        "exclude" => {
            profile.allow_su = false;
            profile.exclude_modules = true;
        }
        other => {
            return Err(anyhow::anyhow!("Unknown mode '{}'. Use: allow/root, deny/nobody, or exclude", other));
        }
    }

    save_profile(&profile)?;
    sync_profile_to_kernel(&profile)?;
    info!("[profile] Set {} {} mode={}", pkg, uid, mode);
    Ok(())
}

/// Import profiles from JSON stdin/file
pub fn import_profiles(json: &str) -> Result<()> {
    let profiles: Vec<AppProfile> = serde_json::from_str(json)
        .context("Failed to parse JSON")?;
    for p in &profiles {
        save_profile(p)?;
        sync_profile_to_kernel(p)?;
    }
    info!("[profile] Imported {} profiles", profiles.len());
    Ok(())
}

/// Sync all profiles from disk to kernel
pub fn sync_all_to_kernel() -> Result<()> {
    let profiles = load_all_profiles();
    let mut count = 0u32;
    for (_, p) in &profiles {
        match sync_profile_to_kernel(p) {
            Ok(()) => count += 1,
            Err(e) => warn!("[profile] Failed to sync {}: {}", p.pkg, e),
        }
    }
    info!("[profile] Synced {} profiles to kernel", count);
    println!("Synced {} profiles", count);
    Ok(())
}
