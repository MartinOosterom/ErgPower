## Context

The BLE transport is the only non-Java runtime dependency: a uv-managed Python (`bleak`) process that
scans/connects/subscribes and forwards **raw notification frames** as NDJSON over stdout, taking control
commands on stdin. Everything downstream — `FrameCodec`, `Pm5Decoder`, storage, REST+SSE, the viewer —
is pure Java and depends only on the frame contract, not on the transport. This change replaces the
Python transport with a native one without disturbing that seam.

```
   PM5 ~BLE~►  native BLE stack  ◄─ bridge (Rust/btleplug)   ← the only non-JVM part
                       │  NDJSON frames (stdout) / commands (stdin)   ← UNCHANGED seam
                       ▼
        [ Java: FrameCodec → Pm5Decoder → events → API → viewer ]   ← untouched
```

## Goals / Non-Goals

**Goals:** remove Python/`uv` from runtime; one self-contained artifact (`java -jar`); run on macOS,
Linux, Windows; preserve the raw-frame stdio seam, the process isolation, and commercial-friendly
licensing; leave the decoder/API/viewer and their tests unchanged.

**Non-Goals:** in-process BLE (Panama/JNA) — sacrifices crash isolation and is high-effort;
remote/socket bridge + Docker-with-host-Bluetooth — a deliberate follow-on; a pure-JVM BLE stack —
impossible on macOS; any decoder/API/viewer change or new BLE feature.

## Decisions

### D1: Rust + `btleplug` for the native bridge
One codebase spans CoreBluetooth (macOS), BlueZ/D-Bus (Linux), WinRT (Windows). MIT/Apache-2.0 keeps
commercial distribution open (SimpleBLE's BUSL was the reason bleak was chosen originally). Active,
widely used.
- **Rejected — Swift:** macOS-only, defeats the cross-platform goal.
- **Rejected — SimpleBLE (C++):** BUSL licensing.
- **Rejected — pure-Java BLE:** BlueCove/TinyB/blessed-bluez are dead or Linux-only; none do macOS BLE.
- **Rejected — in-process Panama/JNA:** removes crash isolation, per-platform native memory/threading
  and delegate/run-loop complexity, and it could never be containerized on a Mac (see D2).

### D2: Keep the subprocess + NDJSON stdio seam, unchanged
The bridge stays a child process speaking the same frames/commands. Why: (1) the entire JVM side is
reused as-is; (2) a native BLE crash kills only the child — the JVM, the in-flight recording and the SSE
stream survive, and restart-on-exit still applies; (3) the seam is what a future remote/gateway/Docker
transport would extend rather than replace.

### D3: Ship prebuilt binaries, bundle all in the jar, select at runtime
Build to `native/<os>-<arch>/ergpower-bridge[.exe]`, bundle **all shipped binaries** on the classpath,
resolve by `os.name`/`os.arch` at launch, extract to a temp dir, mark executable, exec. v1 ships only
the macOS binaries (D8); the layout accepts more without code change. Result: runtime needs only the
JVM — no interpreter, no package manager.

### D4: Freeze the wire protocol at the current bridge's contract
The bridge's stdout frames (`hostTime`, `mono`, `uuid`, hex `bytes`), the `meta` lines (device
name/address/firmware from `0x0014`; connection-state transitions), the stdin commands (`sample_rate`,
generic `write`), and the `--scan` JSON output are the **contract**. The Rust bridge reproduces them
exactly, so `FrameCodec`/`Pm5Decoder` and the reference-fixture tests do not change.

### D5: Platform-neutral device identity
Advertised name/serial selection is unchanged. The cached fast-reconnect handle is described generically
— CoreBluetooth peripheral UUID on macOS, the platform's stable device id (e.g. adapter/address handle)
elsewhere — and discovery lists whatever identifier is usable for configuration on that platform.

### D6: One `mvn package` builds everything, including the bridge
A `cargo` build of the Rust bridge is bound into the Maven build (like the frontend step, at
`prepare-package` so `test` stays fast), producing the host-platform binary and bundling it into the
jar. A single `./mvnw package` therefore builds **Java + web + bridge**. A `-Dnative.skip` flag reuses
already-built binaries for offline/air-gapped builds. Additional-platform binaries (later) come from a
CI cross-compile matrix, not every local build.

### D7: Spike before commit
First prove `btleplug` connects to the real PM5 on macOS and streams frames that decode identically to
today's bridge. Only then build out the full bridge, integration, and packaging.

### D8: macOS first, structured so other platforms are a binary-only add
v1 targets and ships **macOS only** (arm64/x64). Because `btleplug` is cross-platform and the binary is
selected at runtime by `os.name`/`os.arch` from a `native/<os>-<arch>/` layout, adding Linux or Windows
later is "add a build target + its prebuilt binary + a CI job" — **no application-code change**. All
shipped binaries are bundled in the one jar.

## Risks / Trade-offs

- **`btleplug` platform parity** — notify/GATT and reconnection semantics differ per backend (macOS uses
  peripheral UUIDs not MACs — already how the design works; WinRT has pairing quirks; BlueZ needs D-Bus).
  Mitigate with the D7 spike and by treating macOS as the validated primary, Linux/Windows as
  field-test-pending.
- **A new language in the tree** — trades Python for Rust (Java + Rust + a little TS). Fewer *runtime*
  deps, one more *build* toolchain.
- **Cross-compilation / CI** — producing mac-arm64/x64, linux-x64/arm64, win-x64 binaries reliably.
- **Jar size** — bundled binaries add a few MB each (open question: bundle all vs host-only + fetch).
- **Development-time coverage** — during development the Python bridge may stay until the Rust bridge
  passes on macOS, but it is **not retained as a shipped fallback**: once Rust is validated, `ble-bridge/`
  is deleted (no permanent Python option).

## Migration Plan

Additive during development: add the Rust `bridge` subproject and binary selection alongside the
existing Python bridge; switch `BlePm5Source` to the native binary; validate on macOS against the real
PM5 and via the frame fixture; **then delete `ble-bridge/` outright** and move the reference `captures/`
fixtures to `src/test/resources/`. The frame protocol is stable throughout, so replay, storage, and the
decoder tests keep passing at every step.

## Resolved decisions (were open questions)

- **Platform scope:** macOS only in v1, structured for later platforms (D8).
- **Bundling:** bundle all shipped binaries in the one jar (D3/D8).
- **Build:** a single `./mvnw package` builds Java + web + bridge (D6).
- **Python fallback:** none — delete `ble-bridge/` once Rust is validated.
- **Fixtures:** the reference `captures/` move to `src/test/resources/`.

## Open Questions (deferred until they matter)

- Minimum supported BlueZ (Linux) and Windows versions, and Windows pairing — deferred until those
  platforms are actually added (D8).
- macOS binary **signing/notarization** — skipped for now (bundled inside a jar the user already runs);
  revisit only for public/signed distribution.
