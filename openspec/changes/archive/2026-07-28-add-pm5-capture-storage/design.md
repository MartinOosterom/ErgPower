## Context

ErgPower is a greenfield Spring Boot 4.1 / Java 26 application (`work.zing`). The goal is to connect to a Concept2 PM5 performance monitor over Bluetooth Low Energy, capture **all** data emitted during a rowing session (status, per-stroke metrics, and the per-stroke force/power curve), and store it faithfully for later viewing and analysis. Live visualisation is out of scope for now; it will later attach as an additional subscriber.

Key constraints:
- **macOS is the final platform.** BLE on macOS means Apple's CoreBluetooth. The JVM has no pure-Java CoreBluetooth binding; the only Java-API option (SimpleBLE) is BUSL-1.1 licensed, which the user wants to avoid for a possible commercial future.
- **The PM5 exposes multiple BLE characteristics**, each with its own cadence: timed status (`0x0031/0x0032/0x0033`, rate set via `0x0034`), per-stroke (`0x0035/0x0036`), per-split (`0x0037/0x0038`), workout summary (`0x0039/0x003A`, once at end), a dedicated force-curve characteristic (multi-packet, per stroke), and a control characteristic (`0x0021`, CSAFE).
- **The force-curve wire format changed in a Sept 2025 firmware** — the decoder must be versioned, and provenance recorded.
- Sessions are long (10k+ pieces ≈ 35–45 min, ~1,000–1,300 strokes each with a force array), so storage must be append-only and crash-safe, not "one big JSON written at the end."

## Goals / Non-Goals

**Goals:**
- Capture every PM5 data characteristic faithfully and store it for later recombination/analysis.
- Keep all domain logic (decode, pub/sub, storage) in Java; keep BLE isolated behind a thin, replaceable transport.
- Make the data source swappable (BLE / Simulated / Replay) so storage and pub/sub are buildable and testable without hardware.
- Commercial-friendly licensing throughout.

**Non-Goals:**
- Live/online visualisation (later subscriber), cloud upload, derived/analysis views, and CSAFE workout programming beyond capture needs.
- Multi-device profiles (single configured device for now).
- Cross-platform BLE (macOS only).

## Decisions

### D1: Python BLE bridge (`bleak`, MIT) over stdio, not in-JVM BLE
BLE runs in a small **uv-managed Python bridge** using `bleak` (MIT, solid CoreBluetooth support). The JVM launches it via `uv run` as a child process and exchanges data over **stdio**: NDJSON frames on the child's **stdout**, control commands on **stdin**, logs on **stderr**.
- **Why:** `bleak` is commercial-friendly (unlike BUSL SimpleBLE) and battle-tested on macOS. Keeping BLE in Python removes the JVM's CoreBluetooth gap. stdio gives the simplest lifecycle (spawn + pipes, no ports, no network exposure).
- **Alternatives:** SimpleBLE Java bindings (rejected: BUSL-1.1 commercial restriction); Java 26 FFM → CoreBluetooth (rejected for now: delegate/run-loop bridging is a project in itself); localhost WebSocket (viable and cleaner for a future standalone service, but more moving parts than stdio for v1). The frame/command contract is transport-agnostic, so a later stdio→WebSocket swap is cheap.

### D2: The bridge is a dumb pipe — all decoding is in Java
The bridge only scans, connects, subscribes, writes control commands, and forwards **raw** notification bytes with `{characteristicId, hostTime, bytesBase64}`. All payload parsing — little-endian fixed-point decode, force-curve reassembly, versioning — is Java.
- **Why:** the valuable, fiddly, firmware-sensitive logic stays in one testable place; the bridge never changes when Concept2 tweaks a payload. Bonus symmetry: the bridge frame == the `raw.ndjson` record == the replay unit.

### D3: `Pm5Source` seam with sealed event model
A `Pm5Source` produces a stream of events; `Ble`, `Simulated`, and `Replay` implement it. Events are a **sealed hierarchy of records** (`GeneralStatus`, `AdditionalStatus`, `StrokeData`, `ForceCurve`, `SplitData`, `WorkoutSummary`, `Lifecycle`), each carrying `pmTime` + `hostTime`. Subscribers `switch` on the sealed type.
- **Why:** the event contract, not the transport, is what subscribers depend on — so storage/pub-sub are built and tested against Simulated/Replay before any Bluetooth works. Sealed records give exhaustive pattern-matching in the storage router.
- **Two replay grades:** replay decoded events (tests storage/display) and replay `raw.ndjson` frames through the real decoder (tests the decoder against real captures).

### D4: Pub/sub via Project Reactor `Flux`
The connection module publishes one internal multicast stream (`Sinks.many().multicast()`); subscribers take the whole `Flux` (storage) or a filtered view (future display).
- **Why:** matches the "streams you subscribe to" model directly, backpressure-aware, composable filters. Rates are low (tens/sec), so this is about ergonomics.
- **Alternatives:** Spring `ApplicationEventPublisher` (simple but sync/less stream-like); JDK `Flow` (no deps, more manual). Reactor chosen for the stream ergonomics; pulls in `spring-boot-starter-webflux` / reactor-core.

### D5: Storage = folder per session, one NDJSON file per characteristic
Faithful mirror of the wire: status characteristics are **not** merged. Append-only NDJSON, flushed frequently. Join contract: every record has `pmTime` + `hostTime`; per-stroke records add `strokeIndex`. Time joins status streams; `strokeIndex` welds stroke ↔ force-curve. A `session.json` manifest records provenance; `summary.json` at end; optional `raw.ndjson`.
- **Why:** "combinable arbitrarily" is really a shared-key contract, not a file format; per-characteristic faithfulness keeps capture lossless and lets the viewer do any merging. Append-only NDJSON is crash-safe for long pieces. The **temporal-matchability guarantee** (any stroke + its curve matchable to every other stream at that moment) is a property of the keys, so it holds identically for multi-file or single-file layouts — we choose multi-file for faithfulness/analysis ergonomics.
- **Alternative / fallback:** a single interleaved decoded event log (position preserves correlation) is an acceptable fallback if curve↔stroke correlation ever proves ambiguous; and `raw.ndjson` already provides the single, capture-ordered ground-truth layer regardless. Pre-merged semantic files (tidier but lossy) are rejected.

### D6: Session lifecycle derived from PM5 state, config owned by Java
Session start/end come from PM5 workout/rowing state transitions + the summary notification, surfaced as `Lifecycle` events that drive folder creation/finalisation. Connection configuration lives in Spring `@ConfigurationProperties` (`ergpower.ble.*`) as source of truth and is passed to the bridge at launch; the effective config is written into each `session.json`.

## Risks / Trade-offs

- **`bleak` reliability against this PM5 on this Mac is unproven** → De-risk with a throwaway spike (task 1) before any architecture leans on it. Fallback: Java 26 FFM → CoreBluetooth, contained behind `BleP m5Source`.
- **macOS has no MAC address; peripheral UUID is per-host and post-scan** → Identify by advertised name/serial; cache the resolved peripheral UUID for fast reconnect, fall back to scan if it no longer resolves.
- **Force-curve format changed (Sept 2025 firmware) and is multi-packet** → Version the decoder; record firmware + format version in the manifest; discard incomplete curves; reassemble via a per-stroke state machine.
- **Force-curve ↔ stroke correlation relies on arrival order** → Confirmed: `0x003D` carries no stroke count/timestamp (rev 1.30), so correlate the curve to the most recent stroke (its `0x0035`/`0x0036` Stroke Count) and stamp the resolved keys at decode; `raw.ndjson` preserves capture order for re-derivation.
- **Child-process fragility (bridge dies, uv not on PATH, BT permission prompt)** → JVM supervises and restarts per retry policy; surface connection-lost as a source event; fail fast with a clear message if `uv` is missing or BT permission denied.
- **Polyglot repo (Maven + uv subproject)** → Keep the bridge tiny and self-contained under `ble-bridge/`; pin deps via `uv.lock`.

## Migration Plan

Greenfield — no migration/rollback. Build order de-risks incrementally: (1) BLE spike, (2) Java pipeline against Simulated/Replay with storage, (3) real bridge + decoder wired to the same seam. Each stage is independently runnable.

## Verified PM5 wire facts (official interface definition, rev 1.30, 2022-03-02)

Confirmed against the official *Concept2 PM Bluetooth Smart Communication Interface Definition*, rev 1.30. These resolve the earlier open questions:

- **Base UUID:** `CE06XXXX-43E5-11E4-916C-0800200C9A66` (base `CE060000-…`); `XXXX` is the 16-bit characteristic id. Device name characteristic `0x2A00` advertises `"PM5 <serial>"` (e.g. `PM5 430000000`) — this is our name/serial match key.
- **Elapsed time is 0.01 s (centisecond) resolution**, as a 3-byte little-endian field at the **start of every data characteristic** (`0x0031`, `0x0035`, `0x0036`, `0x0037`, …). → `pmTime` aligns streams to 10 ms. Match precision is not a concern.
- **`strokeIndex` is a first-class wire field:** both stroke data (`0x0035`) and additional stroke data (`0x0036`) carry a 2-byte **Stroke Count** plus elapsed time. Use Stroke Count as the canonical `strokeIndex`.
- **Force curve (`0x003D`) carries NO stroke count and NO timestamp** — this settles open question 1. It is delivered across **multiple successive notifications** (2–288 bytes total), each framed as: byte 0 = `[MS nibble = total # of packets for this curve | LS nibble = # of 16-bit points in this packet]`, byte 1 = **sequence number**, then little-endian 16-bit force samples (LS, MS pairs). Reassemble via total-packet-count + sequence number. Because the payload has no stroke identity, the curve **must** be correlated to its stroke by arrival order/timing (it arrives at end-of-drive, right after that stroke's `0x0035`/`0x0036`), and the decoder stamps the current Stroke Count + `pmTime` onto the emitted event — exactly the "resolved stroke keys" spec requirement. `raw.ndjson` preserves capture order as the re-derivation safety net.
- **Multiplexed characteristic (`0x0080`)** can carry `0x31/32/33/35/36/37/38/39/3A/3B/3C` on one notification (id byte prefix) — an Android notification-count workaround; we subscribe to the individual characteristics on macOS, so this is informational.

Source: `PM5_BluetoothSmartInterfaceDefinition.pdf` (concept2.co.in / concept2.cn mirrors; byte-identical), rev 1.30.

## Empirically validated against real hardware (2026-07-28 capture)

Captured a real 289.7 m / 28-stroke row from **PM5 432234859 (RowErg)** via `ble-bridge/spike.py`
(`ble-bridge/captures/2026-07-28_rowerg_432234859_289m_28strokes.ndjson` — kept as the decoder
reference + replay fixture). Findings:

- **Connect + subscribe + notify works end-to-end** (bleak 3.0.2 → CoreBluetooth). Status chars fire ~1 Hz; the PM5 also advertises **FTMS (`0x1826`)** alongside the C2 service.
- **Force curve is the rev-1.30 `0x003D` format** — confirmed on live firmware. Reassembly (MS-nibble = packet count, seq number, LE 16-bit samples) produced 26 clean curves; a curve's peak matches that stroke's independently-reported Peak Drive Force (curve peak 103 ↔ 103.6 lb; 158 ↔ 158.7 lb). **The decoder chain is validated.** (The Sept-2025 "new format" is not what `0x003D` emits by default here.)
- **Units:** the PM5 wire reports force in pounds-force (0.1-lb resolution); ErgPower converts all force to **SI Newtons** (×4.44822) at decode, so events/files expose `peakDriveForceN`, `avgDriveForceN`, `forcesN`. Distance (m), power (W), and time (s) are already metric.
- ⚠️ **This firmware's stroke characteristics have DRIFTED from rev 1.30 lengths** — decoding blindly at rev-1.30 offsets silently corrupts fields:
  - `0x0035` is **20 bytes (not 18)**: an extra 2-byte field sits at [16:18]; **Stroke Count is at [18:20]** (not [16:18]). Elapsed/dist/driveLen/driveTime/peakF[12:14]/avgF[14:16] still decode at rev-1.30 offsets.
  - `0x0036` is **15 bytes (not 17)**: Stroke Power [3:5] and Stroke Count [7:9] are at rev-1.30 positions (validated: power 0→206 W, count 0…27), but trailing fields differ.
  - `0x0035` fires ~2×/stroke; `0x0036` fires exactly **once per stroke** → **use `0x0036` Stroke Count as the canonical `strokeIndex`.**
- **Additional status decoded + validated (2026-07-28):** `0x0032` (17 B, **no drift**) → speed (3.6 m/s), stroke rate (21 spm), heart rate (255 → null/no belt), current & average pace (≈135 s/500 m) — all consistent with the row. `0x0033` (**20 B, drift**: a 2-byte field inserted at [6:8] shifts the split fields +2) → interval count, total calories, split avg pace (135 s), split avg power (141 W) validated at the shifted offsets. Undecoded `0x37/38/39/3A` still preserved as `RawFrame`.
- **Fixed-piece validation (2026-07-28, 500m + 1:00 pieces):** auto start/stop confirmed (session opens on Workout State → active, finalises at `WORKOUTEND`=10 at exactly 500.0 m). Targets validated: distance piece → `targetDistanceM=500` (Workout Duration field holds metres when duration-type=128; 0.01 s when type=0 → 60 s). Projections validated (`0x36`: projected time 119 s ≈ actual 118 s; projected distance 247 m ≈ actual 251 m). Splits validated (`0x37`/`0x38`: 100 m auto-splits, pace/power/spm/speed all consistent). The `0x33` trailing last-split fields decoded wrong and were dropped in favour of `0x37/38`. **The `raw.ndjson` safety net let these decoder fixes be reapplied to already-captured sessions with no re-rowing** — validating design decision A.
- **Takeaway:** the exact per-firmware field maps must come from the current interface definition (the Concept2 request is justified beyond just the force curve), and the decoder must be firmware-version-aware. Real-hardware capture (the spike) is the ground truth for offsets — keep the fixture for regression.

## Open Questions

- **Current firmware's exact characteristic field maps differ from rev 1.30** (confirmed above: `0x35`=20B, `0x36`=15B). Obtain the current *PM Bluetooth Smart Interface Definition* from Concept2 (request drafted in `docs/reference/concept2/REQUEST-latest-BLE-def.md`) to pin every field offset + the unknown `0x35` [16:18] field, and record the PM5 firmware version in `session.json`. Until then, offsets are reverse-engineered from the captured fixture. The force-curve `0x003D` format is settled (rev-1.30, validated on hardware); the decoder stays versioned (D2).
- Frame encoding on the bridge's stdout: base64 NDJSON (assumed) vs length-prefixed binary — base64 chosen for simplicity/readability unless volume proves it costly.
- Exact semantic filenames for the per-characteristic files (with the char→file map in the manifest) — finalise during implementation.
- Exact current force-curve wire layout (post-Sept-2025) — obtain the latest interface definition rather than a cached v1.22 copy.
- Frame encoding detail on stdout: base64 NDJSON (assumed) vs length-prefixed binary — base64 chosen for simplicity/readability unless volume proves it costly.
- Naming scheme for per-characteristic files (semantic names + manifest mapping) — finalise the exact filenames during specs→implementation.
