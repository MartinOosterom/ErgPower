## REMOVED Requirements

### Requirement: Python BLE bridge as a raw transport
**Reason**: The transport is no longer a uv-managed Python (bleak) process. It is replaced by a native,
cross-platform bridge (see the added "Native BLE bridge as a raw transport"), which removes Python/`uv`
from the runtime and enables Linux/Windows support.
**Migration**: The raw-frame NDJSON protocol is unchanged, so decoders, storage, and replay are
unaffected; only the bridge implementation and how it is launched change.

## ADDED Requirements

### Requirement: Native BLE bridge as a raw transport
The system SHALL communicate with the PM5 through a **native bridge executable** that connects over BLE
using the host platform's BLE stack (CoreBluetooth on macOS, BlueZ on Linux, WinRT on Windows) and
forwards raw notification frames. The bridge SHALL NOT decode payloads; each frame it emits SHALL carry
the source characteristic identifier, a host receive timestamp, and the raw notification bytes. The
bridge's frame, meta, and control protocol SHALL be identical across all supported platforms and
unchanged from the prior transport, so JVM-side decoding is both transport- and platform-agnostic.

#### Scenario: Bridge forwards raw frames
- **WHEN** the PM5 sends a BLE notification on a subscribed characteristic
- **THEN** the bridge emits one frame containing the characteristic id, a host timestamp taken at
  receipt, and the raw bytes
- **AND** the bridge performs no interpretation of the byte payload

#### Scenario: Bridge accepts control commands
- **WHEN** the JVM sends a control command (e.g. set sample rate, write CSAFE) to the bridge
- **THEN** the bridge writes it to the appropriate PM5 characteristic and reports a success or failure

#### Scenario: Identical protocol across platforms
- **WHEN** the bridge runs on any supported platform (macOS, Linux, or Windows)
- **THEN** the frames, meta lines, control commands, and scan output on its stdio have the same shape
- **AND** the JVM decoder requires no platform-specific handling

### Requirement: Bundled native bridge binary selection
The system SHALL ship the BLE bridge as prebuilt native executable(s) for its supported platform(s),
bundle **all** of them with the application, and select the correct one at runtime by operating system
and CPU architecture. **macOS SHALL be the initially supported platform**; packaging and selection SHALL
be structured so that supporting an additional platform requires only adding its prebuilt binary and
build target, not changing application code. Running the application SHALL NOT require any language
runtime, interpreter, or package manager beyond the JVM.

#### Scenario: Correct binary selected per platform
- **WHEN** the application starts on a supported operating system and architecture
- **THEN** it resolves and launches the matching bundled bridge executable

#### Scenario: No interpreter required at runtime
- **WHEN** the application runs from its packaged artifact with no Python, `uv`, Node, or other
  interpreter present
- **THEN** the bridge still launches and streams frames

#### Scenario: Unsupported platform reported clearly
- **WHEN** no bundled bridge matches the current operating system and architecture
- **THEN** the system reports a clear error naming the platform, rather than failing obscurely

#### Scenario: Adding a platform is a binary-only change
- **WHEN** a new platform's prebuilt binary and build target are added under the `native/<os>-<arch>/`
  layout
- **THEN** that platform becomes selectable at runtime with no change to application code

## MODIFIED Requirements

### Requirement: JVM supervises the bridge process
The JVM SHALL launch the **bundled native bridge executable** selected for the current platform, exchange
frames and commands over the child process's stdio, and treat its stdout as a pure frame channel and its
stderr as a log/diagnostic channel. If the bridge process exits unexpectedly, the JVM SHALL restart it
according to configured retry behaviour.

#### Scenario: Bridge process dies mid-session
- **WHEN** the bridge process exits unexpectedly while a source is active
- **THEN** the JVM detects the exit, emits a source-level connection-lost event, and restarts the bridge
  per the configured retry policy

#### Scenario: stdout and stderr are separated
- **WHEN** the bridge writes a log line
- **THEN** it goes to stderr and is not parsed as a frame
- **AND** malformed data on stdout is logged and skipped without crashing the source

#### Scenario: Native binary is launched, not an interpreter
- **WHEN** the JVM starts the bridge
- **THEN** it executes the selected native binary directly (no `uv`/`python` or other interpreter
  invocation)
