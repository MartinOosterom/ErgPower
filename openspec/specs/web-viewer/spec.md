# web-viewer Specification

## Purpose
The browser viewer: a configurable, widget-based dashboard that renders live PM5 data on top of the
`live-api` and `source-control` contracts. One shared single-connection data layer feeds many widgets;
the palette is availability-aware; layouts persist and ship as presets; and the force curve is drawn as
points only with the previous stroke ghosted on stable axes. Frontend-only — types are generated from
`api/openapi.yaml`.
## Requirements
### Requirement: Single shared live data layer
The app SHALL maintain one shared live data store fed by a single `EventSource(/api/v1/live/stream)`
plus an initial `/api/v1/live/snapshot`; widgets SHALL subscribe to slices of it and SHALL NOT open
their own connections. On stream reconnect the store SHALL re-fetch the snapshot to resync.

#### Scenario: One connection, many widgets
- **WHEN** several widgets that use live data are displayed
- **THEN** exactly one SSE connection feeds them all through the shared store

#### Scenario: Reconnect resync
- **WHEN** the SSE stream reconnects
- **THEN** the store re-fetches the snapshot so widgets show current state

### Requirement: Configurable widget dashboard
The user SHALL be able to compose the dashboard: add widgets from a palette, remove them, arrange (drag)
and resize them on a grid, and configure per-widget options. Dashboards SHALL be saved as **named
profiles**: the user can create (save-as), switch between, rename, duplicate, and delete profiles, each
holding its own set and arrangement of panels. Profiles SHALL be persisted **server-side as JSON** via
the `dashboard-storage` API (not browser local storage), so they are durable and shared across browsers
and devices using the same server. The **active** profile selection SHALL be remembered **per device**
(client-side), so different devices may show different active profiles. Built-in presets SHALL be
selectable as templates that seed a new profile (without overwriting others); edits SHALL be saved to
the active profile. A pre-existing browser-local single layout SHALL be migrated into a server-side
default profile so no layout is lost.

#### Scenario: Profiles are shared across devices
- **WHEN** a profile is saved from one browser and the app is opened in another browser against the same
  server
- **THEN** that profile is available to select

#### Scenario: Active selection is per-device
- **WHEN** two devices select different active profiles
- **THEN** each device keeps its own active selection while sharing the same stored profiles

#### Scenario: Preset seeds a new profile
- **WHEN** the user creates a new profile from a preset (e.g. "Minimal HUD")
- **THEN** a new named profile is created from that preset without overwriting existing profiles

#### Scenario: Existing local layout migrated
- **WHEN** a user who previously had a single browser-local dashboard loads the app after this change
- **THEN** their layout appears intact as a server-side default profile

### Requirement: Availability-aware widget palette
Each widget/panel SHALL declare the data it requires; the palette SHALL show those whose required data
the current API does not provide as **disabled**. For metric panels the palette SHALL act as a
metric → display-mode picker: it offers each measurement's available display modes (value, and graph
only when the registry marks the metric graphable) rather than a flat, ever-growing list, and SHALL NOT
offer a display mode a metric does not support.

#### Scenario: Unavailable data is disabled
- **WHEN** a registered widget/panel requires data not present in the live API
- **THEN** the palette shows it disabled (not addable) rather than broken

#### Scenario: Graph mode only for graphable metrics
- **WHEN** the user browses display modes for a measurement in the palette
- **THEN** a graph mode is offered only if the registry marks that measurement graphable

### Requirement: Metric registry and display modes
The app SHALL describe every displayable measurement in a single registry entry declaring its label,
unit, how to read its current value, and which **display modes** are meaningful: a **value** tile and/or
a **value-vs-time graph**. The value and graph widgets SHALL both be driven by this registry, with no
per-metric hardcoding. For every metric marked graphable, the shared live store SHALL keep a bounded
rolling history so a graph can be added for it.

#### Scenario: Any graphable metric can be graphed
- **WHEN** a measurement is marked graphable in the registry
- **THEN** a value-vs-time graph can be added for it, and it updates at the stream cadence

#### Scenario: Non-graphable metric offers only a value
- **WHEN** a measurement is not graphable (e.g. elapsed time)
- **THEN** only a value tile is offered for it, not a graph

#### Scenario: One registry, no per-metric components
- **WHEN** a new measurement is added to the registry with its display modes
- **THEN** it becomes available as a value tile (and a graph if graphable) with no new widget component

### Requirement: Core widgets
The app SHALL provide these core widgets: a **value tile** and a **value-vs-time graph** — both
parameterized over the single metric registry, so any registered measurement can be shown as a value,
and any graphable measurement can also be shown as a graph — plus **ForceCurve**, **GoalProgress**,
**WorkoutPhase**, and **ConnectionStatus**. The set of values offered as tiles and the set offered as
graphs SHALL come from the registry (not a hardcoded per-metric list).

#### Scenario: A measurement as a value or a graph
- **WHEN** the user adds a panel for a graphable measurement (e.g. power)
- **THEN** they may choose a value tile or a value-vs-time graph, and it updates at the stream cadence

#### Scenario: Live tiles and graphs update
- **WHEN** metrics events arrive
- **THEN** the corresponding value tiles and graphs update at the stream cadence

### Requirement: Force-curve rendering
The ForceCurve widget SHALL plot the measured force samples as **points only (no connecting line)** and
SHALL show the **previous** stroke's curve ghosted (faded) behind the current one, on **stable axes**
(fixed force scale from a rolling session peak; drive axis by sample index by default) so stroke-to-
stroke differences are visible. Point count of ghosts and the drive-axis mode SHALL be configurable.

#### Scenario: Points with a ghost
- **WHEN** a new `forceCurve` event arrives
- **THEN** the new curve is drawn as bright points and the previous curve is drawn as faded points
- **AND** the axes do not rescale per stroke, so a weaker/shorter pull is visibly smaller/shorter

### Requirement: Source-selection entry
Before showing the dashboard, the app SHALL let the user select a source via the source-control API —
connect to an erg (`POST /source {type: ble}`) or replay a stored session (`GET /sessions` →
`POST /source {type: replay, sessionId}`) — and SHALL indicate the active source (live vs replaying).

#### Scenario: Connect or replay
- **WHEN** the user chooses "connect to erg" or selects a session to replay
- **THEN** the corresponding source is started and the dashboard renders that source's live stream

### Requirement: Contract-generated types
The app SHALL derive its API types from `api/openapi.yaml` (via openapi-typescript) and consume only
endpoints/schemas defined there; a contract mismatch SHALL fail the app's build.

#### Scenario: Types from the spec
- **WHEN** the app is built
- **THEN** its API types are generated from `api/openapi.yaml` and used throughout

### Requirement: Progress dashboard with session selection
The viewer SHALL provide a **Progress dashboard**, distinct from the single-session Analysis view and
reachable from a top-level navigation switch. It SHALL let the user **select a set of sessions** — filtering
the cross-session index by workout type, distance band, and date range, and choosing specific sessions — and
SHALL present, for the selected set, cross-session trend charts, progress coaching, and a set-scoped chat
agent. The per-session **Analysis view SHALL remain single-session**: its coach covers only that session and
its agent cannot reach other sessions.

#### Scenario: Look across a chosen set of sessions
- **WHEN** the user opens the Progress dashboard and selects a set of sessions
- **THEN** trends, progress coaching, and the chat are scoped to that selected set

#### Scenario: Analysis view stays single-session
- **WHEN** the user opens a single session's Analysis view
- **THEN** it is about that one session only — no progress toggle and no cross-session chat

