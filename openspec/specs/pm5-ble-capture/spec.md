# pm5-ble-capture Specification

## Purpose
TBD - created by archiving change add-pm5-capture-storage. Update Purpose after archive.
## Requirements
### Requirement: PM5 data source abstraction
The system SHALL expose a transport-agnostic `Pm5Source` that produces a stream of typed PM5 events, so that subscribers depend on the event contract and never on the underlying transport. The system SHALL provide at least three interchangeable implementations: a real BLE source, a simulated source, and a replay source.

#### Scenario: Subscriber is transport-agnostic
- **WHEN** a subscriber consumes events from a `Pm5Source`
- **THEN** it receives the same typed event types regardless of whether the source is BLE, simulated, or replay
- **AND** switching the active source implementation requires no change to any subscriber

#### Scenario: Simulated source runs without hardware
- **WHEN** the simulated source is selected and started
- **THEN** it emits a plausible sequence of session, status, stroke, and force-curve events without any Bluetooth adapter or PM5 present

#### Scenario: Replay source reproduces a recorded session
- **WHEN** the replay source is pointed at a previously recorded session's raw frame log
- **THEN** it re-emits the captured frames through the same decoder and produces the same typed events as the original capture

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

### Requirement: Decode all PM5 data characteristics into typed events
The JVM SHALL decode raw frames into typed events for every subscribed PM5 data characteristic (general status, additional status, per-stroke data, interval/split data, and workout summary). Every event SHALL carry both the PM5 elapsed time (`pmTime`) parsed from the payload and the host receive time (`hostTime`) from the frame.

#### Scenario: Status frame decoded
- **WHEN** a raw general-status frame arrives
- **THEN** the decoder produces a typed general-status event with the fields defined by the PM5 interface (little-endian, correctly scaled) plus `pmTime` and `hostTime`

#### Scenario: Unknown or malformed frame
- **WHEN** a frame cannot be decoded (unknown characteristic or malformed payload)
- **THEN** the decoder logs it and skips it without terminating the stream, and (when raw logging is enabled) the raw frame is still preserved

### Requirement: Force-curve reassembly with resolved stroke keys
The system SHALL reassemble the per-stroke force/power curve, which the PM5 delivers across multiple BLE notifications, into a single force-curve event per stroke containing the ordered force samples. The decoder SHALL be versioned to accommodate PM5 firmware changes to the force-curve wire format. The emitted force-curve event SHALL carry the `strokeIndex` and `pmTime` of the stroke it belongs to, resolved at decode time by correlating with the concurrent stroke event (and/or arrival order), so that downstream matchability does NOT depend on the raw force-curve payload itself carrying a stroke count or timestamp.

#### Scenario: Multi-packet curve reassembled with stroke keys
- **WHEN** the notifications composing one stroke's force curve have all arrived
- **THEN** the system emits exactly one force-curve event containing the complete ordered array of force samples for that stroke, stamped with that stroke's `strokeIndex` and `pmTime`

#### Scenario: Correlation without a stroke count in the payload
- **WHEN** the force-curve payload does not itself carry a stroke count
- **THEN** the decoder assigns the curve to the current stroke by correlating with the concurrent stroke event / arrival order, and the raw frames remain preserved (when raw logging is enabled) so the correlation can be re-derived if needed

#### Scenario: Incomplete curve at session end
- **WHEN** a session ends with a partially received force curve
- **THEN** the incomplete curve is discarded (not emitted as complete) and the situation is logged

### Requirement: Session lifecycle derived from PM5 state
The system SHALL derive session start and end from PM5 workout/rowing state transitions and the workout-summary notification, and publish them as lifecycle events. Subscribers SHALL use these lifecycle events to bound a session.

#### Scenario: Session start detected
- **WHEN** the PM5 transitions from a pre-workout state into an active rowing state
- **THEN** the system emits a session-started lifecycle event before the first stroke event of that session

#### Scenario: Session end detected
- **WHEN** the PM5 signals workout end (state transition and/or workout-summary notification)
- **THEN** the system emits a session-ended lifecycle event after the final data event of that session

