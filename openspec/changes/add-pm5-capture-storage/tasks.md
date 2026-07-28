## 1. De-risk BLE (spike)

- [x] 1.1 Create the `ble-bridge/` uv project (`pyproject.toml`, `uv.lock`) with `bleak` as the only runtime dependency
- [x] 1.2 Write a throwaway `uv run` script that scans for the PM5, connects, subscribes to one status characteristic, and prints raw notifications — confirm BLE works on this Mac with this PM5. **DONE & VALIDATED against PM5 432234859 (RowErg): scan → connect → subscribe(all 10 chars) → live notifications at ~1 Hz. Byte lengths match rev 1.30 (0x31=19B, 0x32=17B). PM5 also advertises FTMS (0x1826). Stroke/force-curve frames pending an actual rowing capture (task 1.3).**
- [x] 1.3 Confirm the force-curve characteristic (`0x003D`) delivers data and capture a sample multi-packet sequence (header nibbles + sequence number + 16-bit samples) for decoder reference. **DONE: 289m/28-stroke capture kept as fixture; force curve = rev-1.30 format, reassembly validated (peak matches Peak Drive Force). Also found firmware drift: 0x35=20B, 0x36=15B — see design "Empirically validated".**
- [ ] 1.4 Obtain the **current** interface definition (extract from the Mac SDK `.dmg`) and confirm whether the Sept-2025 force-curve format extends `0x003D` or adds a new characteristic — the standalone rev 1.30 PDF predates it
- [ ] 1.5 Record findings (peripheral UUID behaviour, firmware version, force-curve format version) to feed the decoder and manifest

## 2. Python BLE bridge (raw transport)

- [~] 2.1 Implement device selection: by name/serial, cached peripheral UUID, and first-found (Concept2 rowing service). **name/serial + first-found done in `bridge.py`; cached peripheral UUID pending.**
- [x] 2.2 Implement `--scan` discovery mode listing nearby PM5s with name/serial + peripheral UUID
- [x] 2.3 Subscribe to all data characteristics and emit NDJSON frames on stdout (`{hostTime, mono, uuid, bytes-hex}`); logs on stderr. **Same schema as saved captures, so live/replay are interchangeable.**
- [x] 2.4 Accept control commands on stdin (set sample rate via 0x0034, write CSAFE) and report results. **Done: bridge reads one JSON command/line (`sample_rate`, generic `write`); `BlePm5Source.sendCommand(...)` writes to the child stdin.**
- [x] 2.5 Implement BLE-level auto-reconnect with configurable backoff; emit connection-state frames. **Done + validated: reconnect loop (exponential backoff, `--backoff-min/max`) survives PM5 drops; emits `{"meta":"state", …}` searching/connected/disconnected/reconnecting; pmTime stays continuous (erg keeps its clock).**
- [~] 2.6 Accept startup config via launch args. **`--name`, `--seconds`, `--sample-rate-ms`, `--no-reconnect`, `--backoff-*`, `--scan` done; connect timeouts + force-curve toggle still pending.**

## 3. JVM event model & source seam

- [x] 3.1 Define the sealed `Pm5Event` record hierarchy (`GeneralStatus`, `StrokeData`, `AdditionalStrokeData`, `ForceCurve`, `RawFrame` fallback) with `pmTime`/`hostTime`. **Done; split/summary/lifecycle variants deferred (currently preserved as `RawFrame`).**
- [x] 3.2 Define the `Pm5Source` interface (start/stop + event stream) and the Reactor multicast publisher. **Interface done (`Flux<Pm5Event> events()`); `BlePm5Source` uses `Sinks.many().multicast()`.**
- [ ] 3.3 Implement `SimulatedPm5Source` producing a plausible synthetic session (no hardware)
- [x] 3.4 Implement `ReplayPm5Source` (raw-frame-level replay through the real decoder) — **validated by `ReplayDecodeTest` against the real capture (295/53/28/26 frames, 206 W, 289.7 m).**

## 4. Storage subscriber

- [x] 4.1 Implement session-folder lifecycle (create on start, finalise on end). **Done: auto mode via `SessionManager` (workout-state driven, multi-session); manual whole-stream mode still available via `SessionStorage`/`CaptureService.run`.**
- [x] 4.2 Write one append-only, **per-line-flushed** NDJSON file per characteristic (semantic names + manifest mapping). **Done: status-general / status-additional1 / status-additional2 / stroke / stroke-additional / force-curve, plus raw-0xNN for still-undecoded chars; flushed each write for crash-safety; char→file map in manifest.**
- [x] 4.3 Ensure every record carries `pmTime`/`hostTime`, and per-stroke records carry `strokeIndex` (`strokeCount`). **Done + verified: files recombine on the keys (test asserts force curves join to strokes).**
- [x] 4.4 Write `session.json` manifest (device, firmware, decoder/app version, char→file map). **Done (force-curve format version + effective config to be added once wired to the real source/config).**
- [x] 4.5 Write `summary.json` at session end. **Done (derived: strokes/distance/duration/avg+peak power/curves; native 0x39 summary decode later).**
- [x] 4.6 Implement optional `raw.ndjson` frame logging (exact bridge frames). **Done: `BlePm5Source` taps each stdout frame to `SessionStorageWriter.writeRaw`, so every live session is re-decodable; replay uses the input capture as the raw log.**
- [~] 4.7 Verify storage + pub/sub end-to-end against Simulated and Replay sources (no Bluetooth). **Replay path verified (`StorageWriterTest`); Simulated pending (task 3.3).**

## 5. JVM connection module (real source)

- [x] 5.1 Implement `BlePm5Source`: launch/supervise the bridge via `uv run`, read stdout frames (virtual threads), decode → multicast, capture stderr. **Validated end-to-end via `LiveCapture` (launch → scan → clean shutdown → session written); stdin command channel pending (task 2.4).**
- [~] 5.2 Restart the bridge on unexpected exit per retry policy; surface connection-lost as a source event; fail clearly if `uv`/BT permission is missing. **BLE-level reconnect handled inside the bridge (survives PM5 drops); connection state surfaced to the JVM (`connectionState()`). JVM-level restart if the bridge *process* itself dies is still pending.**
- [~] 5.3 Implement the frame decoder for all data characteristics (little-endian fixed-point) → typed events. **Decoded + validated on real fixed pieces (500m + 1:00): 0x31 (+ workout type, time/distance target via duration-type), 0x32 (+ rest/machine type), 0x33, 0x35, 0x36 (+ calories/projections — projections matched actuals), 0x37/0x38 splits (100m splits verified: pace/power/spm/speed), 0x3D (force in N). Subscribing to 0x3B/0x3C. Remaining: typed decode of summary 0x39/3A/3C + HR-belt 0x3B (captured raw, re-decodable via raw.ndjson).**
- [x] 5.4 Implement versioned force-curve reassembly (per-stroke state machine; discard incomplete curves). **Done + validated (rev-1.30 `0x003D`): 26 curves reassembled, peak matches Peak Drive Force, incomplete curves discarded.**
- [x] 5.5 Derive session start/end lifecycle from PM5 workout/rowing state transitions. **Done: `SessionManager` opens a session folder when Workout State enters active (1..9) and finalises on end/idle (0, 10-12); multiple pieces per run → separate folders; unit-tested (`SessionManagerTest`). Enabled by `ergpower.ble.capture.auto-session` (default true).**

## 6. Configuration

- [x] 6.1 Add `ergpower.ble.*` `@ConfigurationProperties` (device selection, connect behaviour, capture params, bridge, storage). **Done: `ErgPowerBleProperties` (record + `@DefaultValue`), enabled on the app, documented in `application.properties`.**
- [~] 6.2 Pass BLE-relevant config to the bridge at launch; record effective config into `session.json`. **Device name passed to the bridge; the bridge now reports the *connected* device back (meta line) and it's recorded in the manifest. Sample-rate/force-curve flags + firmware + full effective config still pending.**
- [x] 6.3 Provide sensible defaults (faithful mode default). **Done via `@DefaultValue`; stronger validation pending.**

## 7. Wire-up & validation

- [~] 7.1 Select the active `Pm5Source` (Ble / Simulated / Replay) via configuration/profile. **CLI `capture` → Ble, `replay <file>` → Replay; Simulated + a `source` property pending.**
- [~] 7.2 End-to-end capture of a real rowing session to disk; verify files recombine on `pmTime`/`strokeIndex`. **Replay path fully verified on real data (`StorageWriterTest`); live path exercised end-to-end (pending a powered erg for real strokes).**
- [~] 7.3 Update project docs with uv setup, BT permission, `--scan`, and configuration reference. **`ble-bridge/README.md` covers all; top-level README/HELP still to update.**
