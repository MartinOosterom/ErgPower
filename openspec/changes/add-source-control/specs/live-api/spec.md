## MODIFIED Requirements

### Requirement: Read-only v1
The live **views** (`GET /api/v1/connection`, `GET /api/v1/live/snapshot`, `GET /api/v1/live/stream`)
SHALL be read-only. The API MAY additionally expose **source-control** mutations (see the
`source-control` capability) — selecting which source feeds the live pipeline (a live PM5 or a replayed
stored session) and stopping it — which SHALL be the only mutating operations. No endpoint SHALL modify
stored session data.

#### Scenario: Live views are read-only
- **WHEN** the live-view endpoints (`/connection`, `/live/snapshot`, `/live/stream`) are inspected
- **THEN** they expose only reads

#### Scenario: Mutations are limited to source control
- **WHEN** a mutating endpoint is present in the API
- **THEN** it only starts or stops the active source, and no endpoint alters stored session data
