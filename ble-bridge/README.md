# ble-bridge

The BLE side of ErgPower — a small **uv-managed Python** project that talks to the Concept2 PM5
over Bluetooth (via `bleak`, MIT) and forwards **raw** notification frames to the JVM. It does no
decoding; all parsing lives in the Java app. See `../openspec/changes/add-pm5-capture-storage/`.

Contents:
- `bridge.py` — the real bridge: pure NDJSON frames on stdout, logs on stderr; the JVM
  (`BlePm5Source`) launches it via `uv run` and decodes the frames.
- `spike.py` — the original de-risking validation tool (task 1).

## Live capture (bridge → JVM → session folder)

From the **repo root** (so `ble-bridge/` and `uv` resolve), with the PM5 on and rowing — build the
jar once, then use the CLI:

```sh
export JAVA_HOME=/path/to/jdk-26          # build/run on JDK 26
./mvnw -q -DskipTests package             # -> target/ErgPower-0.0.1-SNAPSHOT.jar

java -jar target/ErgPower-0.0.1-SNAPSHOT.jar capture --seconds=120
#   → writes sessions/live-<timestamp>/ with per-characteristic NDJSON + session.json + summary.json
java -jar target/ErgPower-0.0.1-SNAPSHOT.jar replay <capture.ndjson>   # decode a saved capture
java -jar target/ErgPower-0.0.1-SNAPSHOT.jar                            # usage
```

Omit `--seconds` to record until the PM5 disconnects or Ctrl-C. Settings come from `ergpower.ble.*`
(see `src/main/resources/application.properties`); override on the CLI, e.g. pin an erg with
`--ergpower.ble.device.match=name --ergpower.ble.device.name="PM5 432234859 Row"`.

**Long pieces:** the bridge **auto-reconnects** with exponential backoff if the PM5 drops (the erg
keeps its own clock, so `pmTime` stays continuous). Control it via `ergpower.ble.connect.auto-reconnect`
and `ergpower.ble.capture.sample-rate` (500ms/250ms/… → PM5 `0x0034`). It also reads a **command
channel** on stdin — one JSON per line, e.g. `{"cmd":"sample_rate","ms":250}` or
`{"cmd":"write","uuid":"…","hex":"…"}` (CSAFE) — exposed from Java via `BlePm5Source.sendCommand(...)`.

The bridge can also be run standalone: `uv run python bridge.py --scan` /
`uv run python bridge.py --sample-rate-ms 250 --backoff-max 15`.

## Prerequisites

- **`uv`** installed (`https://docs.astral.sh/uv/`).
- **PM5 powered on and idle** (not already connected to a phone/app — it advertises when idle).
- **macOS Bluetooth ON**, and the **terminal app** running this granted Bluetooth permission:
  System Settings → Privacy & Security → **Bluetooth**. The first BLE call usually triggers the
  prompt; **zero devices found almost always means this permission is missing.**

## Run the spike

```sh
cd ble-bridge

uv run python spike.py --scan                 # 1) list nearby BLE devices — find your "PM5 <serial>"
uv run python spike.py                          # 2) auto-connect to the first PM5, log raw frames 60s
uv run python spike.py --name "PM5 430123456"   #    ...or target a specific PM5 by name
uv run python spike.py --seconds 120 --out capture.ndjson   # log longer and SAVE raw frames
```

`uv run` builds the environment from `pyproject.toml` on first use (downloads `bleak`).

## What success looks like

While you row, you should see notifications streaming, and the `general-status(0x31)` lines show
`t=` (elapsed) and `dist=` climbing:

```
[  12] general-status(0x31)      19B  a3010000...   t=  12.30s  dist=    45.6m
[   3] stroke-data(0x35)         18B  ...
[   2] force-curve(0x3D)         26B  ...
```

That confirms: bleak ↔ CoreBluetooth ↔ PM5 works, and the characteristic UUIDs from rev 1.30 are
correct. Save a run with `--out capture.ndjson` — that file is both the decoder reference (incl. the
multi-packet force curve) and future replay fuel.
