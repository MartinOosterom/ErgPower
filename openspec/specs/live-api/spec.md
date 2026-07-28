# live-api Specification

## Purpose
TBD - created by archiving change add-rest-api. Update Purpose after archive.
## Requirements
### Requirement: OpenAPI document is the leading contract
The HTTP API SHALL be defined by a single OpenAPI 3.0 document (`api/openapi.yaml`) that is the source
of truth. The Spring server interfaces SHALL be generated from it, and any implementation that diverges
from the contract SHALL fail the build.

#### Scenario: Server generated from the spec
- **WHEN** the project is built
- **THEN** the Spring API interfaces are generated from `api/openapi.yaml`
- **AND** the controllers implement those generated interfaces (a signature mismatch fails compilation)

#### Scenario: Browser depends only on the contract
- **WHEN** the web viewer is built (later)
- **THEN** it consumes only endpoints and schemas defined in `api/openapi.yaml`

### Requirement: Connection status endpoint
`GET /api/v1/connection` SHALL return the current PM5 connection status: a state of
`DISCONNECTED | SEARCHING | CONNECTING | CONNECTED | RECONNECTING`, plus device (name/serial/address),
firmware revision, and the active decode profile id when known.

#### Scenario: Connected
- **WHEN** the PM5 is connected and a client requests `/api/v1/connection`
- **THEN** the response state is `CONNECTED` with the device name, firmware, and profile id populated

#### Scenario: Not connected
- **WHEN** no PM5 is connected
- **THEN** the response state reflects it (`DISCONNECTED`/`SEARCHING`/…) with device fields null

### Requirement: Live snapshot endpoint
`GET /api/v1/live/snapshot` SHALL return the full current live state — connection, workout state, the
latest merged metrics, and the last stroke and force curve if any — so a dashboard can render fully on
load without waiting for stream events.

#### Scenario: Snapshot on load
- **WHEN** a client loads the dashboard and requests `/api/v1/live/snapshot`
- **THEN** it receives a coherent snapshot it can render immediately

### Requirement: Multiplexed live SSE stream
`GET /api/v1/live/stream` SHALL return a `text/event-stream` carrying named events —
`connection`, `workout`, `metrics`, `stroke`, `forceCurve`, `heartbeat` — each with a JSON payload of
the correspondingly-named schema. Metrics SHALL be emitted as a full `LiveMetrics` snapshot each tick.
The stream SHALL be resumable: after a reconnect the client re-fetches the snapshot to resync.

#### Scenario: Live metrics stream
- **WHEN** a client subscribes to `/api/v1/live/stream` while a PM5 is streaming
- **THEN** it receives `metrics` events (full snapshots) at the sample cadence
- **AND** `stroke` and `forceCurve` events once per stroke

#### Scenario: Force curve delivered as an event
- **WHEN** a stroke's force curve is reassembled
- **THEN** exactly one `forceCurve` event carrying the ordered force samples (Newtons) is emitted

#### Scenario: Connection changes are pushed
- **WHEN** the PM5 connection state changes (e.g. disconnect → reconnecting)
- **THEN** a `connection` event with the new status is emitted on the stream

### Requirement: SI units in the contract
All physical quantities in the API SHALL be SI and explicit in the schema: distance in metres, time in
seconds, power in watts, force in Newtons, heart rate in bpm; pace SHALL be seconds per 500&nbsp;m.

#### Scenario: Force is Newtons
- **WHEN** a `forceCurve` or stroke force is returned
- **THEN** the values are in Newtons, as documented in the schema

### Requirement: Read-only v1
The v1 API SHALL be read-only. Command operations (connect/disconnect, device scan) and stored-session
browsing are out of scope for v1 and are not exposed.

#### Scenario: No command endpoints in v1
- **WHEN** the v1 OpenAPI document is inspected
- **THEN** it exposes only the connection-status, snapshot, and stream reads (no mutating operations)

