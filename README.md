# ErgPower

Capture **everything** a Concept2 PM5 emits during a rowing session over Bluetooth, and store it
faithfully as JSON for later viewing and analysis — including the per-stroke **force/power curve**.

ErgPower connects to a PM5 (RowErg / SkiErg / BikeErg) over Bluetooth Low Energy, decodes every data
stream the monitor broadcasts, and writes each piece to its own folder as one newline-delimited JSON
file per characteristic — power, pace, stroke rate, heart rate, splits, calories, drive metrics, and
the reassembled force curve — in **metric / SI units**.

> Status: capture + storage are complete and validated on real hardware (live and replay). A live
> viewer is planned. See [Roadmap](#roadmap).

## How it works

macOS has no usable pure-JVM Bluetooth stack, so BLE runs in a tiny **Python bridge** (uv + `bleak`,
talking to CoreBluetooth) that forwards *raw* notification frames. All the interesting logic —
decoding, session lifecycle, storage — lives in a **Spring Boot (Java 26)** app.

```
   Concept2 PM5 ──BLE──▶ ble-bridge/bridge.py ──raw NDJSON frames (stdout)──▶ JVM app
   (CoreBluetooth)       (uv · bleak, dumb pipe)                              │
                                                                             ▼
                                            FirmwareProfile decode → typed events (Flux)
                                                                             │
                                                          ┌──────────────────┴───────────────┐
                                                          ▼                                   ▼
                                                   SessionStorage                      (future) live viewer
                                                   per-characteristic NDJSON
                                                   + session.json + summary.json + raw.ndjson
```

The same typed event stream can be produced from a live PM5 (`BlePm5Source`) or by replaying a
recorded capture (`ReplayPm5Source`) — so the whole pipeline is testable without hardware.

## Requirements

- **macOS** (BLE via CoreBluetooth), with Bluetooth permission granted to your terminal
  (System Settings → Privacy & Security → Bluetooth).
- **JDK 21 (LTS) or newer** — the project targets Java 21, so it also runs on 24 / 25 / 26. If your
  default `java` isn't 21+, select one with `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
- **[uv](https://docs.astral.sh/uv/)** on your `PATH` (the app launches the bridge via `uv run`).
- A Concept2 **PM5** monitor.

## Build

```sh
./mvnw -q -DskipTests package     # → target/ErgPower-0.0.1-SNAPSHOT.jar
./mvnw test                       # run the test suite
```

## Usage

The jar is a small CLI. Run it from the repo root (so `ble-bridge/` and `uv` resolve).

```sh
# Live-capture: turn on the PM5, pick/row a piece — a session folder appears and closes itself.
java -jar target/ErgPower-0.0.1-SNAPSHOT.jar capture

# Decode a previously saved raw capture into a session folder.
java -jar target/ErgPower-0.0.1-SNAPSHOT.jar replay <capture-or-raw.ndjson>

# Usage / help.
java -jar target/ErgPower-0.0.1-SNAPSHOT.jar
```

Sessions **start and stop automatically** from the PM5 workout state — just row. Each piece is
written to `sessions/session-<timestamp>/`.

The bridge can also be run on its own:
```sh
cd ble-bridge
uv run python bridge.py --scan        # list nearby PM5s
```

## Configuration

All settings live under `ergpower.ble.*` (see `src/main/resources/application.properties`) and can
be overridden on the command line, e.g.:

```sh
java -jar target/ErgPower-0.0.1-SNAPSHOT.jar capture \
  --ergpower.ble.device.match=name \
  --ergpower.ble.device.name="PM5 432234859 Row" \
  --ergpower.ble.capture.sample-rate=250ms \
  --ergpower.ble.storage.dir=sessions
```

| Property | Default | Purpose |
|---|---|---|
| `device.match` | `first` | `first` \| `name` \| `peripheral-id` |
| `device.name` | – | advertised PM5 name/serial when `match=name` |
| `capture.sample-rate` | `500ms` | status sample rate (`1000ms`/`500ms`/`250ms`/`100ms`) |
| `capture.auto-session` | `true` | open/close a session folder from PM5 workout state |
| `connect.auto-reconnect` | `true` | survive PM5 drops mid-piece (exponential backoff) |
| `firmware.profile` | `auto` | `auto` \| `current` \| `reference` \| `<profile id>` |
| `storage.dir` | `sessions` | where session folders are written |

## What a session looks like

```
sessions/session-2026-07-28T15-33-35/
├── session.json              device, firmware, decoder profile, char→file map, provenance
├── summary.json              strokes, distance, duration, avg/peak power, force-curve count
├── status-general.ndjson     0x31: distance, states, workout target (time/distance), drag
├── status-additional1.ndjson 0x32: speed, stroke rate, heart rate, current/avg pace
├── status-additional2.ndjson 0x33: interval count, calories, split avg pace/power
├── stroke.ndjson             0x35: drive length/time, recovery, peak/avg drive force
├── stroke-additional.ndjson  0x36: power, stroke count, calories, projected time/distance
├── split.ndjson              0x37: per-split time/distance/type
├── split-additional.ndjson   0x38: per-split pace/power/spm/hr/speed
├── force-curve.ndjson         0x3D: reassembled per-stroke force curve (Newtons)
└── raw.ndjson                exact bridge frames — lets any session be re-decoded later
```

Every record carries the join keys `pmTime` (PM5 elapsed clock) + `hostTime`, and per-stroke records
carry `strokeCount` — so any subset of files recombines by time and/or stroke. **Units are SI**:
metres, seconds, watts, **Newtons** (force), bpm.

## Firmware profiles

The PM5 wire format is **firmware-dependent** (characteristic lengths/offsets drift between firmware
revisions). All firmware-specific interpretation is isolated behind a pluggable `FirmwareProfile`;
the correct one is selected per connection from the firmware revision (read from characteristic
`0x0014`), with a characteristic-length fingerprint fallback and a config override. `session.json`
records the firmware string and the profile used, and `raw.ndjson` makes captures re-decodable if a
profile is later corrected. Supporting a new firmware = adding one small profile class.

## Project layout

```
src/main/java/work/zing/ergpower/pm5/
├── event/       typed event model (Pm5Event sealed hierarchy)  — the stable contract
├── firmware/    FirmwareProfile + ReferenceRev130 / CurrentPm5 + registry
├── decode/      Pm5Decoder (orchestration: reassembly + correlation)
├── source/      Pm5Source seam: BlePm5Source (live), ReplayPm5Source
├── storage/     SessionStorageWriter, SessionStorage, SessionManager (auto start/stop)
├── capture/     CaptureService (drives source → storage)
├── config/      ErgPowerBleProperties (@ConfigurationProperties)
└── cli/         CaptureCommand (the CLI entry point)
ble-bridge/      uv project: bridge.py (transport), spike.py, captures/ (reference fixture)
openspec/        spec-driven change proposals + specs
docs/reference/  spec index + how to fetch the (git-ignored) vendor PDFs
```

## Development

- Tests decode a real captured row and assert exact values — the regression anchor for the decoder,
  force-curve reassembly, storage recombination, auto start/stop, and firmware-profile divergence.
- Targets **Java 21 (LTS)**; runs on 21 and newer. The bridge env is managed by `uv`.

## Reference specs

`docs/reference/README.md` indexes the Concept2 and Bluetooth SIG specifications used, with source
URLs. The PDFs themselves are **not committed** (third-party copyright) — fetch them from the listed
sources.

## Roadmap

- Typed decode of the workout **summary** characteristics (`0x39/3A/3C`) and HR-belt (`0x3B`).
- Firmware `claims(...)` matching once the current firmware string is catalogued.
- A **live viewer** for force curves and real-time metrics.

## License / attribution

Concept2 and Bluetooth SIG specifications are the property of their respective owners; ErgPower is an
independent project and is not affiliated with or endorsed by Concept2.
