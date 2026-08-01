use std::{
    collections::{HashMap, HashSet},
    fs::{self, File},
    io::{self, BufRead},
    path::Path,
    process::Command,
    thread,
    time::Duration,
};

use log::{info, warn};
use serde::{Deserialize, Serialize};

use crate::{defs, lua};

const MAGISK_SCONTEXT: &str = "u:r:magisk:s0";

#[derive(Deserialize, Serialize, Clone)]
pub struct PackageConfig {
    pub pkg: String,
    pub exclude: i32,
    pub allow: i32,
    pub uid: i32,
    pub to_uid: i32,
    pub sctx: String,
}

fn list_user_packages() -> HashSet<String> {
    let commands: [(&str, &[&str]); 2] = [
        ("cmd", &["package", "list", "packages", "-3"]),
        ("pm", &["list", "packages", "-3"]),
    ];

    for (program, args) in commands {
        let output = match Command::new(program).args(args).output() {
            Ok(output) if output.status.success() => output,
            Ok(output) => {
                warn!(
                    "User package query {} {:?} failed: {:?}",
                    program,
                    args,
                    output.status.code()
                );
                continue;
            }
            Err(e) => {
                warn!("User package query {} {:?} failed: {}", program, args, e);
                continue;
            }
        };

        return String::from_utf8_lossy(&output.stdout)
            .lines()
            .filter_map(|line| line.strip_prefix("package:"))
            .map(str::trim)
            .filter(|pkg| !pkg.is_empty())
            .map(ToOwned::to_owned)
            .collect();
    }

    HashSet::new()
}

fn read_plugin_known(path: &Path) -> (String, HashSet<String>) {
    let mut fingerprint = String::new();
    let mut known = HashSet::new();
    if let Ok(content) = fs::read_to_string(path) {
        let mut lines = content.lines();
        if let Some(first) = lines.next() {
            if let Some(value) = first.strip_prefix("fp=") {
                fingerprint = value.to_string();
            }
        }
        for line in lines {
            let pkg = line.trim();
            if !pkg.is_empty() {
                known.insert(pkg.to_string());
            }
        }
    }
    (fingerprint, known)
}

fn write_plugin_known(
    path: &Path,
    fingerprint: &str,
    packages: &HashSet<String>,
) -> io::Result<()> {
    let mut sorted: Vec<_> = packages.iter().cloned().collect();
    sorted.sort();
    let mut content = format!("fp={fingerprint}\n");
    for pkg in sorted {
        content.push_str(&pkg);
        content.push('\n');
    }
    // Atomic write: the watchdog may restart this process at any moment, and a
    // torn write would look like a truncated baseline on the next refresh.
    let tmp = path.with_extension("tmp");
    fs::write(&tmp, content)?;
    fs::rename(&tmp, path)
}

fn apply_new_package_plugins(
    package_configs: &mut Vec<PackageConfig>,
    uid_map: &HashMap<String, i32>,
) -> io::Result<bool> {
    let current_user_packages = list_user_packages();
    if current_user_packages.is_empty() {
        warn!("[package_plugin] user package query returned no packages");
        return Ok(false);
    }

    // Only handle applications that appeared after the package plugin set was
    // last (re)activated. The fingerprint changes whenever a package_added
    // plugin is installed, enabled, disabled or removed, which rebuilds the
    // baseline so pre-existing apps are never auto-profiled.
    let fingerprint = crate::lua::active_package_plugins().join(",");
    let known_path = Path::new(defs::PACKAGE_PLUGIN_KNOWN_FILE);
    let (stored_fp, mut known) = read_plugin_known(known_path);

    // Safety: never bulk-profile every app. A baseline is only usable when it
    // covers most current user packages. If the known file is missing,
    // truncated, or somehow only covers a small fraction of installed apps,
    // rebuild it instead of treating the whole device as "newly installed".
    let covered = current_user_packages
        .iter()
        .filter(|pkg| known.contains(*pkg))
        .count();
    let baseline_usable = known_path.exists()
        && stored_fp == fingerprint
        && !known.is_empty()
        && covered * 2 >= current_user_packages.len();

    info!(
        "[package_plugin] fingerprint='{}' stored='{}' known={} covered={}/{} usable={}",
        fingerprint,
        stored_fp,
        known.len(),
        covered,
        current_user_packages.len(),
        baseline_usable
    );

    let mut changed = false;
    if baseline_usable {
        let mut new_packages: Vec<_> = current_user_packages
            .iter()
            .filter(|pkg| !known.contains(*pkg))
            .cloned()
            .collect();
        new_packages.sort();
        info!(
            "[package_plugin] scanned {} user packages, {} new packages",
            current_user_packages.len(),
            new_packages.len()
        );

        for pkg in new_packages {
            let Some(&uid) = uid_map.get(&pkg) else {
                // UID not ready yet: keep the package out of the known set so
                // it is retried on the next refresh instead of being dropped.
                warn!("[package_plugin] Missing uid for package {}, skip", pkg);
                continue;
            };

            let (allow, exclude, sctx, mode_name) = match lua::new_package_profile(&pkg, uid) {
                Some("root") => (1, 0, MAGISK_SCONTEXT.to_string(), "root"),
                Some("exclude") => (0, 1, "u:r:untrusted_app:s0".to_string(), "exclude"),
                _ => continue,
            };

            info!(
                "[package_plugin] apply {} profile: {} ({})",
                mode_name, pkg, uid
            );
            package_configs.push(PackageConfig {
                pkg,
                exclude,
                allow,
                uid,
                to_uid: 0,
                sctx,
            });
            changed = true;
        }
    } else {
        info!(
            "[package_plugin] building/rebuilding package baseline of {} packages",
            current_user_packages.len()
        );
    }

    // Drop packages that are no longer installed, so re-installing an app is
    // treated as a new install and the package_added callback runs again.
    known.retain(|pkg| current_user_packages.contains(pkg));
    // Only mark packages whose UID was resolved as known; packages whose UID
    // was missing during the install race stay pending for the next refresh.
    let known_uids_ready: HashSet<_> = current_user_packages
        .iter()
        .filter(|pkg| uid_map.contains_key(*pkg))
        .cloned()
        .collect();
    known.extend(known_uids_ready);
    write_plugin_known(&known_path, &fingerprint, &known)?;
    Ok(changed)
}

pub fn read_ap_package_config() -> Vec<PackageConfig> {
    let max_retry = 5;
    for _ in 0..max_retry {
        let file = match File::open("/data/adb/ap/package_config") {
            Ok(file) => file,
            Err(e) => {
                warn!("Error opening file: {}", e);
                thread::sleep(Duration::from_secs(1));
                continue;
            }
        };

        let mut reader = csv::Reader::from_reader(file);
        let mut package_configs = Vec::new();
        let mut success = true;

        for record in reader.deserialize() {
            match record {
                Ok(config) => package_configs.push(config),
                Err(e) => {
                    warn!("Error deserializing record: {}", e);
                    success = false;
                    break;
                }
            }
        }

        if success {
            return package_configs;
        }
        thread::sleep(Duration::from_secs(1));
    }
    Vec::new()
}

pub fn write_ap_package_config(package_configs: &[PackageConfig]) -> io::Result<()> {
    let max_retry = 5;
    for _ in 0..max_retry {
        let temp_path = "/data/adb/ap/package_config.tmp";
        let file = match File::create(temp_path) {
            Ok(file) => file,
            Err(e) => {
                warn!("Error creating temp file: {}", e);
                thread::sleep(Duration::from_secs(1));
                continue;
            }
        };

        let mut writer = csv::Writer::from_writer(file);
        let mut success = true;

        for config in package_configs {
            if let Err(e) = writer.serialize(config) {
                warn!("Error serializing record: {}", e);
                success = false;
                break;
            }
        }

        if !success {
            thread::sleep(Duration::from_secs(1));
            continue;
        }

        if let Err(e) = writer.flush() {
            warn!("Error flushing writer: {}", e);
            thread::sleep(Duration::from_secs(1));
            continue;
        }

        if let Err(e) = std::fs::rename(temp_path, "/data/adb/ap/package_config") {
            warn!("Error renaming temp file: {}", e);
            thread::sleep(Duration::from_secs(1));
            continue;
        }
        return Ok(());
    }
    Err(io::Error::new(
        io::ErrorKind::Other,
        "Failed after max retries",
    ))
}

fn read_lines<P>(filename: P) -> io::Result<io::Lines<io::BufReader<File>>>
where
    P: AsRef<Path>,
{
    File::open(filename).map(|file| io::BufReader::new(file).lines())
}

pub fn synchronize_package_uid() -> io::Result<()> {
    info!("[synchronize_package_uid] Start synchronizing root list with system packages...");

    let max_retry = 5;
    for _ in 0..max_retry {
        match read_lines("/data/system/packages.list") {
            Ok(lines) => {
                let lines: Vec<_> = lines.filter_map(|line| line.ok()).collect();

                let mut package_configs = read_ap_package_config();
                let uid_map: HashMap<String, i32> = lines
                    .iter()
                    .filter_map(|line| {
                        let words: Vec<&str> = line.split_whitespace().collect();
                        if words.len() < 2 {
                            return None;
                        }
                        words[1]
                            .parse::<i32>()
                            .ok()
                            .map(|uid| (words[0].to_string(), uid))
                    })
                    .collect();

                let system_packages: Vec<String> = lines
                    .iter()
                    .filter_map(|line| line.split_whitespace().next())
                    .map(|pkg| pkg.to_string())
                    .collect();

                let original_len = package_configs.len();
                package_configs.retain(|config| system_packages.contains(&config.pkg));
                let removed_count = original_len - package_configs.len();

                if removed_count > 0 {
                    info!(
                        "Removed {} uninstalled package configurations",
                        removed_count
                    );
                }

                let mut updated = false;

                if apply_new_package_plugins(&mut package_configs, &uid_map)? {
                    updated = true;
                }

                for line in &lines {
                    let words: Vec<&str> = line.split_whitespace().collect();
                    if words.len() >= 2 {
                        let pkg_name = words[0];
                        if let Ok(uid) = words[1].parse::<i32>() {
                            if let Some(config) = package_configs
                                .iter_mut()
                                .find(|config| config.pkg == pkg_name)
                            {
                                if config.uid % 100000 != uid % 100000 {
                                    let uid = config.uid / 100000 * 100000 + uid % 100000;
                                    info!(
                                        "Updating uid for package {}: {} -> {}",
                                        pkg_name, config.uid, uid
                                    );
                                    config.uid = uid;
                                    updated = true;
                                }
                            }
                        } else {
                            warn!("Error parsing uid: {}", words[1]);
                        }
                    }
                }

                if updated || removed_count > 0 {
                    write_ap_package_config(&package_configs)?;
                }
                return Ok(());
            }
            Err(e) => {
                warn!("Error reading packages.list: {}", e);
                thread::sleep(Duration::from_secs(1));
            }
        }
    }
    Err(io::Error::new(
        io::ErrorKind::Other,
        "Failed after max retries",
    ))
}
