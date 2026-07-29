## MODIFIED Requirements

### Requirement: Read-only v1
The live **views** (`GET /api/v1/connection`, `GET /api/v1/live/snapshot`, `GET /api/v1/live/stream`)
SHALL be read-only. The API MAY additionally expose **source-control** mutations (see the
`source-control` capability) — selecting which source feeds the live pipeline and stopping it — and
**dashboard-storage** mutations (see the `dashboard-storage` capability) — creating, replacing, and
deleting saved dashboard profiles. These SHALL be the only mutating operations, and no endpoint SHALL
modify stored session data.

#### Scenario: Live views are read-only
- **WHEN** the live-view endpoints (`/connection`, `/live/snapshot`, `/live/stream`) are inspected
- **THEN** they expose only reads

#### Scenario: Mutations are limited to source control and dashboard storage
- **WHEN** a mutating endpoint is present in the API
- **THEN** it either starts/stops the active source or creates/replaces/deletes a dashboard profile
- **AND** no endpoint alters stored session data
