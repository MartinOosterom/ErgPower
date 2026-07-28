## ADDED Requirements

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

### Requirement: Python BLE bridge as a raw transport
The system SHALL communicate with the PM5 through a uv-managed Python bridge that connects over BLE and forwards raw notification frames. The bridge SHALL NOT decode payloads; each frame it emits SHALL carry the source characteristic identifier, a host receive timestamp, and the raw notification bytes.

#### Scenario: Bridge forwards raw frames
- **WHEN** the PM5 sends a BLE notification on a subscribed characteristic
- **THEN** the bridge emits one frame containing the characteristic id, a host timestamp taken at receipt, and the raw bytes (base64-encoded)
- **AND** the bridge performs no interpretation of the byte payload

#### Scenario: Bridge accepts control commands
- **WHEN** the JVM sends a control command (e.g. set sample rate, write CSAFE) to the bridge
- **THEN** the bridge writes it to the appropriate PM5 characteristic
- **AND** reports back a success or failure result

### Requirement: JVM supervises the bridge process
The JVM SHALL launch the Python bridge via `uv run`, exchange frames and commands over the child process's stdio, and treat its stdout as a pure frame channel and its stderr as a log/diagnostic channel. If the bridge process exits unexpectedly, the JVM SHALL restart it according to configured retry behaviour.

#### Scenario: Bridge process dies mid-session
- **WHEN** the bridge process exits unexpectedly while a source is active
- **THEN** the JVM detects the exit, emits a source-level connection-lost event, and restarts the bridge per the configured retry policy

#### Scenario: stdout and stderr are separated
- **WHEN** the bridge writes a log line
- **THEN** it goes to stderr and is not parsed as a frame
- **AND** malformed data on stdout is logged and skipped without crashing the source

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
