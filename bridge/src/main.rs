//! ErgPower BLE bridge — the machine-facing transport (cross-platform, via `btleplug`).
//!
//! Connects to a Concept2 PM5 over BLE and forwards **raw** notification frames as NDJSON on stdout,
//! one per line; all logs go to stderr. It does NO decoding — the JVM (`BlePm5Source`) parses
//! everything. This is a drop-in replacement for the previous Python/`bleak` bridge: the stdout frame
//! shape, the meta lines, and the stdin command protocol are byte-for-byte identical, so the JVM side
//! (`FrameCodec` → `Pm5Decoder` → …) is unchanged and existing captures still replay.
//!
//! stdout lines:
//!   frame : {"hostTime": ISO-8601, "mono": seconds, "uuid": full-uuid, "bytes": hex}
//!   meta  : {"meta": "device", "name": …, "address": …, "firmware": …|null}
//!           {"meta": "state",  "state": searching|connected|disconnected|reconnecting}
//!
//! stdin commands (one JSON per line, from the JVM):
//!   {"cmd": "sample_rate", "ms": 500}                 # write PM5 0x0034 (1000|500|250|100)
//!   {"cmd": "write", "uuid": "<uuid>", "hex": "<..>"} # generic write, e.g. CSAFE to 0x0021
//!
//! Usage:
//!   ergpower-bridge [--name "PM5 …"] [--seconds N] [--sample-rate-ms 500] [--no-reconnect]
//!                   [--backoff-min 2] [--backoff-max 30]
//!   ergpower-bridge --scan

use std::error::Error;
use std::io::Write;
use std::sync::Arc;
use std::time::{Duration, Instant};

use btleplug::api::{Central, CharPropFlags, Manager as _, Peripheral as _, ScanFilter, WriteType};
use btleplug::platform::{Adapter, Manager, Peripheral};
use futures::stream::StreamExt;
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::sync::Mutex;
use uuid::Uuid;

/// Concept2 base UUID `CE06XXXX-43E5-11E4-916C-0800200C9A66`; the low 16 bits are the char id.
const C2_BASE_TAIL: [u8; 12] = [
    0x43, 0xE5, 0x11, 0xE4, 0x91, 0x6C, 0x08, 0x00, 0x20, 0x0C, 0x9A, 0x66,
];
const SAMPLE_RATE_ID: u16 = 0x0034;
const FIRMWARE_ID: u16 = 0x0014;

/// Subscribe to ALL C2 data characteristics (incl. HR-belt 0x3B and summary-2 0x3C); the JVM decides
/// what to decode. Matches the previous bridge's `DATA_CHARS` exactly — nothing dropped at transport.
const DATA_IDS: [u16; 12] = [
    0x0031, 0x0032, 0x0033, 0x0035, 0x0036, 0x0037, 0x0038, 0x0039, 0x003A, 0x003B, 0x003C, 0x003D,
];

fn c2_uuid(id: u16) -> Uuid {
    Uuid::from_bytes([
        0xCE, 0x06, (id >> 8) as u8, (id & 0xff) as u8, 0x43, 0xE5, 0x11, 0xE4, 0x91, 0x6C, 0x08,
        0x00, 0x20, 0x0C, 0x9A, 0x66,
    ])
}

/// If `uuid` is a Concept2 characteristic, return its short id (e.g. 0x0031).
fn c2_short(uuid: &Uuid) -> Option<u16> {
    let b = uuid.as_bytes();
    if b[0] == 0xCE && b[1] == 0x06 && b[4..16] == C2_BASE_TAIL {
        Some(((b[2] as u16) << 8) | b[3] as u16)
    } else {
        None
    }
}

fn is_data_char(uuid: &Uuid) -> bool {
    c2_short(uuid).map(|s| DATA_IDS.contains(&s)).unwrap_or(false)
}

fn hex(bytes: &[u8]) -> String {
    let mut s = String::with_capacity(bytes.len() * 2);
    for b in bytes {
        s.push_str(&format!("{:02x}", b));
    }
    s
}

fn hex_to_bytes(hex: &str) -> Option<Vec<u8>> {
    if hex.len() % 2 != 0 {
        return None;
    }
    (0..hex.len() / 2)
        .map(|i| u8::from_str_radix(&hex[2 * i..2 * i + 2], 16).ok())
        .collect()
}

/// Log to stderr (unbuffered). Frames/meta go to stdout via [`out`].
fn log(msg: &str) {
    eprintln!("[bridge] {}", msg);
}

/// Write one JSON value + newline to stdout and flush (the JVM reads frames line-by-line, promptly).
fn out(v: serde_json::Value) {
    let mut so = std::io::stdout().lock();
    let _ = writeln!(so, "{}", v);
    let _ = so.flush();
}

fn emit_state(s: &str) {
    out(serde_json::json!({ "meta": "state", "state": s }));
}

fn emit_device(name: &str, address: &str, firmware: Option<&str>) {
    out(serde_json::json!({ "meta": "device", "name": name, "address": address, "firmware": firmware }));
}

fn emit_frame(uuid: &Uuid, value: &[u8], start: Instant) {
    let mono = (start.elapsed().as_secs_f64() * 1e6).round() / 1e6;
    out(serde_json::json!({
        "hostTime": chrono::Utc::now().to_rfc3339_opts(chrono::SecondsFormat::Micros, true),
        "mono": mono,
        "uuid": uuid.to_string(),
        "bytes": hex(value),
    }));
}

struct Args {
    name: Option<String>,
    seconds: f64,
    sample_rate_ms: Option<u32>,
    reconnect: bool,
    backoff_min: f64,
    backoff_max: f64,
    scan: bool,
}

fn parse_args() -> Args {
    let mut a = Args {
        name: None,
        seconds: 0.0,
        sample_rate_ms: None,
        reconnect: true,
        backoff_min: 2.0,
        backoff_max: 30.0,
        scan: false,
    };
    let mut it = std::env::args().skip(1);
    while let Some(arg) = it.next() {
        match arg.as_str() {
            "--name" => a.name = it.next(),
            "--seconds" => a.seconds = it.next().and_then(|v| v.parse().ok()).unwrap_or(0.0),
            "--sample-rate-ms" => a.sample_rate_ms = it.next().and_then(|v| v.parse().ok()),
            "--no-reconnect" => a.reconnect = false,
            "--backoff-min" => a.backoff_min = it.next().and_then(|v| v.parse().ok()).unwrap_or(2.0),
            "--backoff-max" => a.backoff_max = it.next().and_then(|v| v.parse().ok()).unwrap_or(30.0),
            "--scan" => a.scan = true,
            other => log(&format!("ignoring unknown arg: {other}")),
        }
    }
    a
}

async fn write_char(pm5: &Peripheral, uuid: &Uuid, data: &[u8]) {
    match pm5.characteristics().into_iter().find(|c| &c.uuid == uuid) {
        Some(ch) => match pm5.write(&ch, data, WriteType::WithResponse).await {
            Ok(()) => log(&format!("wrote {} -> {}", hex(data), uuid)),
            Err(e) => log(&format!("write failed: {e}")),
        },
        None => log(&format!("write target not found: {uuid}")),
    }
}

async fn write_sample_rate(pm5: &Peripheral, ms: u32) {
    // 0x0034 sample-rate byte per interface definition rev 1.30.
    let b: u8 = match ms {
        1000 => 0,
        500 => 1,
        250 => 2,
        100 => 3,
        _ => {
            log(&format!("unsupported sample rate {ms}ms (use 1000/500/250/100); leaving default"));
            return;
        }
    };
    write_char(pm5, &c2_uuid(SAMPLE_RATE_ID), &[b]).await;
}

async fn read_firmware(pm5: &Peripheral) -> Option<String> {
    let ch = pm5
        .characteristics()
        .into_iter()
        .find(|c| c.uuid == c2_uuid(FIRMWARE_ID))?;
    match pm5.read(&ch).await {
        Ok(bytes) => {
            let s = String::from_utf8_lossy(&bytes).replace('\u{0}', "").trim().to_string();
            (!s.is_empty()).then_some(s)
        }
        Err(e) => {
            log(&format!("could not read firmware: {e}"));
            None
        }
    }
}

/// Read one JSON command per stdin line and act on the currently-connected peripheral.
async fn read_commands(current: Arc<Mutex<Option<Peripheral>>>, rate: Arc<Mutex<Option<u32>>>) {
    let mut lines = BufReader::new(tokio::io::stdin()).lines();
    while let Ok(Some(line)) = lines.next_line().await {
        let line = line.trim();
        if line.is_empty() {
            continue;
        }
        let cmd: serde_json::Value = match serde_json::from_str(line) {
            Ok(v) => v,
            Err(_) => {
                log(&format!("bad command: {line}"));
                continue;
            }
        };
        match cmd.get("cmd").and_then(|c| c.as_str()) {
            Some("sample_rate") => {
                let ms = cmd.get("ms").and_then(|m| m.as_u64()).unwrap_or(500) as u32;
                *rate.lock().await = Some(ms);
                if let Some(p) = current.lock().await.as_ref() {
                    write_sample_rate(p, ms).await;
                }
            }
            Some("write") => {
                let uuid = cmd.get("uuid").and_then(|x| x.as_str());
                let h = cmd.get("hex").and_then(|x| x.as_str());
                if let (Some(u), Some(h)) = (uuid, h) {
                    if let (Ok(uuid), Some(bytes)) = (Uuid::parse_str(u), hex_to_bytes(h)) {
                        if let Some(p) = current.lock().await.as_ref() {
                            write_char(p, &uuid, &bytes).await;
                        }
                    }
                }
            }
            _ => log(&format!("ignored command: {line}")),
        }
    }
}

/// Remaining time to the deadline in seconds, clamped to [lo, hi]; `hi` if no deadline.
fn remaining_clamped(deadline: Option<Instant>, lo: f64, hi: f64) -> f64 {
    match deadline {
        Some(dl) => dl
            .checked_duration_since(Instant::now())
            .map(|d| d.as_secs_f64())
            .unwrap_or(0.0)
            .clamp(lo, hi),
        None => hi,
    }
}

async fn sleep_backoff(backoff: f64, deadline: Option<Instant>) -> bool {
    match deadline {
        Some(dl) => {
            let rem = dl
                .checked_duration_since(Instant::now())
                .map(|d| d.as_secs_f64())
                .unwrap_or(0.0);
            if rem <= 0.0 {
                return false;
            }
            tokio::time::sleep(Duration::from_secs_f64(backoff.min(rem))).await;
            Instant::now() < dl
        }
        None => {
            tokio::time::sleep(Duration::from_secs_f64(backoff)).await;
            true
        }
    }
}

async fn find_pm5(central: &Adapter, name: Option<&str>, timeout_s: f64) -> Option<Peripheral> {
    log(&format!(
        "scanning {:.0}s for a PM5{}...",
        timeout_s,
        name.map(|n| format!(" named {n:?}")).unwrap_or_default()
    ));
    let _ = central.start_scan(ScanFilter::default()).await;
    let deadline = Instant::now() + Duration::from_secs_f64(timeout_s);
    loop {
        if let Ok(list) = central.peripherals().await {
            for p in list {
                if let Ok(Some(props)) = p.properties().await {
                    let adv = props.local_name.unwrap_or_default();
                    let hit = match name {
                        Some(n) => adv == n,
                        None => adv.to_uppercase().starts_with("PM5"),
                    };
                    if hit {
                        let _ = central.stop_scan().await;
                        return Some(p);
                    }
                }
            }
        }
        if Instant::now() >= deadline {
            break;
        }
        tokio::time::sleep(Duration::from_millis(400)).await;
    }
    let _ = central.stop_scan().await;
    None
}

async fn scan(central: &Adapter) {
    let _ = central.start_scan(ScanFilter::default()).await;
    tokio::time::sleep(Duration::from_secs(8)).await;
    let _ = central.stop_scan().await;
    if let Ok(list) = central.peripherals().await {
        for p in list {
            if let Ok(Some(props)) = p.properties().await {
                let name = props.local_name.unwrap_or_default();
                if name.to_uppercase().starts_with("PM5") {
                    log(&format!("PM5: {name}  {}  rssi={:?}", p.id(), props.rssi));
                    out(serde_json::json!({ "name": name, "address": p.id().to_string(), "rssi": props.rssi }));
                }
            }
        }
    }
}

/// Stream frames until the PM5 disconnects or the deadline passes. The notification pump runs as its
/// own task; this fn polls connection/deadline so a dropped link is detected promptly.
async fn stream_until_disconnect(pm5: &Peripheral, start: Instant, deadline: Option<Instant>) {
    let notif = match pm5.notifications().await {
        Ok(n) => n,
        Err(e) => {
            log(&format!("notification stream error: {e}"));
            return;
        }
    };
    let pump = tokio::spawn(async move {
        let mut notif = notif;
        while let Some(n) = notif.next().await {
            emit_frame(&n.uuid, &n.value, start);
        }
    });
    loop {
        tokio::time::sleep(Duration::from_millis(400)).await;
        if !pm5.is_connected().await.unwrap_or(false) {
            emit_state("disconnected");
            log("PM5 disconnected");
            break;
        }
        if let Some(dl) = deadline {
            if Instant::now() >= dl {
                break;
            }
        }
    }
    pump.abort();
}

async fn run(central: Adapter, args: Args) -> Result<(), Box<dyn Error>> {
    let start = Instant::now();
    let deadline = (args.seconds > 0.0).then(|| start + Duration::from_secs_f64(args.seconds));
    let current: Arc<Mutex<Option<Peripheral>>> = Arc::new(Mutex::new(None));
    let rate: Arc<Mutex<Option<u32>>> = Arc::new(Mutex::new(args.sample_rate_ms));
    tokio::spawn(read_commands(current.clone(), rate.clone()));

    let mut backoff = args.backoff_min;
    loop {
        if let Some(dl) = deadline {
            if Instant::now() >= dl {
                break;
            }
        }
        emit_state("searching");
        let scan_to = remaining_clamped(deadline, 1.0, 10.0);
        let pm5 = match find_pm5(&central, args.name.as_deref(), scan_to).await {
            Some(p) => p,
            None => {
                log("no PM5 found (on? idle? in range?)");
                if !args.reconnect {
                    break;
                }
                emit_state("reconnecting");
                if !sleep_backoff(backoff, deadline).await {
                    break;
                }
                backoff = (backoff * 2.0).min(args.backoff_max);
                continue;
            }
        };

        if let Err(e) = pm5.connect().await {
            log(&format!("connection error: {e}"));
        } else {
            let _ = pm5.discover_services().await;
            let name = pm5
                .properties()
                .await
                .ok()
                .flatten()
                .and_then(|p| p.local_name)
                .unwrap_or_default();
            let address = pm5.id().to_string();
            let firmware = read_firmware(&pm5).await;
            emit_device(&name, &address, firmware.as_deref());
            emit_state("connected");
            log(&format!("connected to {name} @ {address}"));

            if let Some(ms) = *rate.lock().await {
                write_sample_rate(&pm5, ms).await;
            }
            let mut subs = 0;
            for ch in pm5.characteristics() {
                if is_data_char(&ch.uuid) && ch.properties.contains(CharPropFlags::NOTIFY) {
                    if pm5.subscribe(&ch).await.is_ok() {
                        subs += 1;
                    } else {
                        log(&format!("skip {}: subscribe failed", ch.uuid));
                    }
                }
            }
            log(&format!("subscribed to {subs} characteristics; streaming frames on stdout"));
            *current.lock().await = Some(pm5.clone());
            backoff = args.backoff_min; // reset after a healthy connection

            stream_until_disconnect(&pm5, start, deadline).await;

            *current.lock().await = None;
            let _ = pm5.disconnect().await;
        }

        if !args.reconnect {
            break;
        }
        if let Some(dl) = deadline {
            if Instant::now() >= dl {
                break;
            }
        }
        emit_state("reconnecting");
        if !sleep_backoff(backoff, deadline).await {
            break;
        }
        backoff = (backoff * 2.0).min(args.backoff_max);
    }
    log("bridge stopping");
    Ok(())
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn Error>> {
    let args = parse_args();
    let manager = Manager::new().await?;
    let central = manager
        .adapters()
        .await?
        .into_iter()
        .next()
        .ok_or("no BLE adapter found")?;
    if args.scan {
        scan(&central).await;
        return Ok(());
    }
    run(central, args).await
}
