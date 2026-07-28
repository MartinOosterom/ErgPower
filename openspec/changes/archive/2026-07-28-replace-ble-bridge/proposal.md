## Why

The live BLE transport is a **uv-managed Python (bleak) process**. It's the only runtime dependency
beyond the JVM, and in practice it's macOS-only. Two goals push us off it:

- **No Python at runtime.** A packaged run should need only a JVM (plus, for a live PM5, whatever native
  BLE the OS already provides) — no interpreter, no `uv`, no virtualenv.
- **Multiple platforms.** We want to run on Linux and Windows later, not just macOS.

macOS BLE can only be reached through CoreBluetooth (native), so a *pure*-JVM transport is impossible;
**something native must sit between the JVM and the radio.** The win is to make that native part a small
**bundled binary** rather than a Python process. [Rust `btleplug`](https://github.com/deviceplug/btleplug)
covers CoreBluetooth (macOS), BlueZ (Linux) and WinRT (Windows) from one codebase, is **MIT/Apache-2.0**
(preserving the commercial-friendly stance that ruled out SimpleBLE's BUSL), and can speak the **exact
same raw-frame stdio protocol** the JVM already parses — so the decoder, API, storage, and viewer are
untouched.

The bridge stays a **separate process** on purpose: a native BLE crash takes down the child, not the JVM
(the in-flight recording and SSE stream survive, and restart-on-exit still applies). That isolation is a
feature, and keeping the seam also leaves the door open to a future remote/gateway bridge.

## What Changes

- **Reimplement the bridge in Rust (btleplug)**: scan → connect → discover the Concept2 service →
  subscribe to all data characteristics → forward raw NDJSON frames; plus the stdin control channel
  (sample-rate / raw write), auto-reconnect with backoff, and `--scan`. The frame + meta + command
  protocol is **byte-for-byte identical** to today's `bridge.py`.
- **Ship prebuilt binaries, bundle all in the jar** under `native/<os>-<arch>/`, selected at runtime by
  OS/arch, extracted and executed. **v1 ships macOS (arm64/x64) only**; the `native/<os>-<arch>/` layout
  + os/arch selection make adding Linux/Windows later a build-target-only change (no application code).
- **`BlePm5Source`** launches the selected native binary (by OS/arch) instead of `uv run python
  bridge.py`; **`ErgPowerBleProperties`** drops the uv/python knobs and gains a binary-path override.
- **Device identity becomes platform-neutral** in config and discovery (advertised name for selection is
  unchanged; the cached fast-reconnect handle is the CoreBluetooth peripheral UUID on macOS and the
  platform's stable device id elsewhere).
- **Retire the `ble-bridge/` Python subproject outright** (`bridge.py`, `pyproject.toml`, `uv`) once the
  native bridge is validated on macOS — **no fallback** — and move the reference `captures/` fixtures to
  `src/test/resources/`.
- **Add a native build step** (cargo) bound into the **single Maven build** (like the web build): one
  `./mvnw package` builds Java + web + bridge. Skippable (`-Dnative.skip`) to reuse prebuilt binaries;
  additional-platform binaries come from a CI matrix later.

**Out of scope (explicit non-goals, deferred):** running the bridge on a *remote* host / a socket
transport / Docker-with-host-Bluetooth (a follow-on, e.g. `pluggable-bridge-transport`); in-process
BLE via Panama/JNA (loses crash isolation, high effort); any change to the decoder, API, or viewer;
new BLE features.

## Capabilities

### Modified Capabilities

- `pm5-ble-capture`: the raw BLE transport becomes a **bundled cross-platform native binary** (no
  Python); the JVM supervises the native executable selected per platform; a new requirement covers
  cross-platform binary distribution/selection.
- `ble-connection-config`: device selection and discovery describe **platform-neutral** identifiers
  rather than macOS-specific CoreBluetooth UUIDs.

### New Capabilities

<!-- none — this replaces an existing transport in place; no new capability -->

## Impact

- **New Rust subproject + build toolchain** (cargo) at build time, and a CI cross-compile matrix —
  mirrors the Node/web build we already added. Removes Python + `uv` from both runtime and build.
- **Frame protocol unchanged** → `FrameCodec`, `Pm5Decoder`, and all decoder/storage tests are
  unaffected; the reference replay fixture still anchors the tests with no PM5 present.
- **Jar size** grows by the bundled binaries (a few MB each); with macOS-only in v1 that's arm64 + x64.
- **v1 is macOS-only.** Linux (BlueZ/D-Bus) and Windows (WinRT pairing) are a later build-target
  addition, not a code change. A **spike** to confirm `btleplug` talks to the real PM5 precedes the
  commitment.
