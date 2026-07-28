## Why

We want to capture **everything** a Concept2 PM5 emits during a rowing session (10k+ pieces included) — status, per-stroke metrics, and the per-stroke force/power curve — and store it faithfully for later viewing and analysis. There is nothing in the project yet: this change establishes the first working slice, the capture-and-store pipeline. Live visualisation is deliberately deferred; it will attach later as just another subscriber.

## What Changes

- Introduce a **Python BLE bridge** (uv-managed, using MIT-licensed `bleak`) that connects to the PM5 over CoreBluetooth on macOS and forwards **raw** BLE notification frames to the JVM. It performs no decoding.
- Introduce a **JVM connection module** that launches and supervises the bridge, decodes the raw frames into typed domain events (including multi-packet force-curve reassembly), and publishes them as a stream.
- Define a transport-agnostic **`Pm5Source`** seam with three implementations: `Ble` (real, via the bridge), `Simulated` (synthetic), and `Replay` (re-emits a recorded session). Storage and future subscribers depend only on this seam.
- Introduce a **storage subscriber** that writes each session to its own folder, with **one NDJSON file per PM5 characteristic** (a faithful mirror of the wire), a `session.json` manifest, and a `summary.json`.
- Introduce **connection configuration** (Spring `@ConfigurationProperties`): device selection (by advertised name/serial, cached peripheral UUID, or first-found), connect behaviour (timeouts, auto-reconnect), sample rate, and force-curve toggle — plus a `--scan` discovery mode in the bridge.
- Add a Python subproject (`ble-bridge/`) alongside the existing Maven project, making the repo polyglot.

Non-goals (this change): live/online visualisation, cloud upload, analysis/derived views, CSAFE workout programming beyond what capture needs.

## Capabilities

### New Capabilities
- `pm5-ble-capture`: Connect to a PM5 via the Python bridge, receive and decode all data characteristics into typed events, and publish them through the swappable `Pm5Source` seam (including Simulated and Replay sources and force-curve reassembly).
- `ble-connection-config`: Configure which PM5 to connect to and how — device selection strategy, connect/reconnect behaviour, sample rate, force-curve toggle, and device discovery.
- `session-storage`: Persist a captured session to a per-session folder as one NDJSON file per PM5 characteristic, keyed for arbitrary recombination, with a manifest and summary.

### Modified Capabilities
<!-- None — greenfield project, no existing specs. -->

## Impact

- **New dependency:** Python `bleak` (MIT) + `uv` toolchain; a `ble-bridge/` uv subproject. No new JVM BLE dependency (BLE lives entirely in the bridge).
- **New JVM code:** connection module, decoder, `Pm5Source` implementations, event model, pub/sub, storage subscriber, configuration properties. Likely adds Spring `webflux`/Reactor (for `Flux`) — to be confirmed in design.
- **Process model:** the app spawns and supervises a child `uv run` process; requires `uv` on PATH and macOS Bluetooth permission for the process.
- **Platform:** macOS only (CoreBluetooth). No MAC-address addressing available; device identity is name/serial + cached peripheral UUID.
- **Firmware coupling:** the force-curve wire format changed in a Sept 2025 PM5 firmware; the decoder is versioned and the effective firmware/format is recorded per session.
