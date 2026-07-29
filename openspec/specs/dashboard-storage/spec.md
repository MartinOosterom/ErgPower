# dashboard-storage Specification

## Purpose
This capability provides server-side persistence of dashboard profiles. Each profile's configuration is
stored as JSON, one file per profile under a configurable directory, and is stored and returned opaquely
without validating the widget schema so the browser's widget model can evolve independently. It is exposed
as a small REST CRUD surface for listing, fetching, creating-or-replacing, and deleting profiles, with
profile names constrained to be safe so they cannot escape the dashboards directory.

## Requirements
### Requirement: Server-side dashboard persistence
Dashboard profiles SHALL be persisted server-side as JSON, one file per profile (e.g.
`dashboards/<name>.json`, under a configurable directory). A REST API SHALL let clients list profiles,
fetch one by name, create-or-replace one by name, and delete one. The server SHALL store and return a
profile's configuration **opaquely** — it SHALL NOT require the configuration to conform to a widget
schema — so the browser's widget model can evolve without a contract change.

#### Scenario: Create or replace persists to a file
- **WHEN** a client creates or replaces a profile by name with a JSON config
- **THEN** the config is written to that profile's JSON file and is returned unchanged on a later fetch

#### Scenario: List and delete
- **WHEN** a client lists profiles
- **THEN** it receives the stored profile names
- **AND** deleting a profile removes its file so it no longer appears in the list

#### Scenario: Config is stored opaquely
- **WHEN** a profile is stored with an arbitrary widget-config shape
- **THEN** the server round-trips it unchanged without validating the widget internals

#### Scenario: Unknown profile
- **WHEN** a client fetches or deletes a profile name that does not exist
- **THEN** the API returns a not-found response

### Requirement: Safe profile names
Because a profile name maps to a file, the server SHALL reject or sanitize names that could escape the
dashboards directory (path separators, `..`, absolute paths), rather than writing outside it.

#### Scenario: Path traversal rejected
- **WHEN** a client uses a profile name containing a path separator or `..`
- **THEN** the server rejects it with a client error and writes nothing outside the dashboards directory
