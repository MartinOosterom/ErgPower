# source-control Specification

## Purpose
The source-control capability governs which source feeds the live pipeline and lets clients start or
stop it: either a live PM5 (over BLE) or a timed replay of a stored session. It also exposes the
supporting reads a client needs to drive that choice — listing the stored sessions available for
replay and scanning for nearby PM5 devices to connect to. The server starts idle, and a source becomes
active only when selected via the API.

## Requirements
### Requirement: List stored sessions
`GET /api/v1/sessions` SHALL return the stored sessions (from the `sessions/` directory), each with an
id, start time, and summary (distance, strokes, duration), and whether it is replayable (has raw frames).

#### Scenario: List sessions
- **WHEN** a client requests `/api/v1/sessions`
- **THEN** it receives one entry per stored session with id, started-at, distance, strokes, duration
- **AND** each entry indicates whether it can be replayed (has a `raw.ndjson`)

### Requirement: Select the active source
`POST /api/v1/source` SHALL select and start the source feeding the live pipeline, given a body of
either `{ "type": "ble", "device"? }` (connect to a PM5) or `{ "type": "replay", "sessionId", "speed"? }`
(replay a stored session). Starting a source SHALL stop any previously active source first. The live
stream and snapshot SHALL thereafter reflect the selected source.

#### Scenario: Connect to a live PM5
- **WHEN** a client POSTs `{ "type": "ble" }` (optionally a device)
- **THEN** the bridge connects to the PM5 and the live stream carries its data
- **AND** the session is recorded to storage (as in `capture`)

#### Scenario: Replay a stored session
- **WHEN** a client POSTs `{ "type": "replay", "sessionId": "<id>" }`
- **THEN** the session's recorded frames play back through the live pipeline (timed, ~real time)
- **AND** the replayed session is NOT re-recorded

#### Scenario: Switching sources
- **WHEN** a source is active and a client POSTs a different source
- **THEN** the previous source is stopped and the new one becomes active, and current live state resets

### Requirement: Stop the active source
`DELETE /api/v1/source` SHALL stop the active source; `GET /api/v1/source` SHALL report the current
source/connection status (including the source type when active).

#### Scenario: Stop
- **WHEN** a client DELETEs `/api/v1/source` while a source is active
- **THEN** the source stops and the status reports no active source

### Requirement: Timed session replay
Replaying a session SHALL emit its recorded events honouring the original inter-frame timing, scaled by
an optional `speed` (default real time), so a replayed session streams like a live one.

#### Scenario: Real-time playback
- **WHEN** a session is replayed with the default speed
- **THEN** events arrive on the stream at approximately the cadence they were originally captured

### Requirement: Device discovery
`GET /api/v1/devices` SHALL return nearby PM5s (advertised name, address, signal) to populate a
"connect to erg" picker.

#### Scenario: Scan
- **WHEN** a client requests `/api/v1/devices`
- **THEN** it receives the nearby PM5s currently advertising

### Requirement: Serve starts idle
On `serve`, the server SHALL start with no active source; a source begins only when selected via the
API (a configured default MAY auto-start).

#### Scenario: Idle on start
- **WHEN** `serve` starts with no configured default source
- **THEN** `GET /api/v1/source` reports no active source until one is selected
