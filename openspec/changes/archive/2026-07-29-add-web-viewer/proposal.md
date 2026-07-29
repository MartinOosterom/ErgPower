## Why

ErgPower now serves a live REST + SSE contract (`live-api`, `/api/v1`), but there is no way to *see* the
data. This change adds the **browser viewer**: a React app that renders live rowing data — power/pace,
metrics, and the force/power curve — while you row. Crucially, the end user should **choose which
metrics and graphs to display and arrange them**, so the UI is a **configurable, widget-based
dashboard**, not a fixed screen.

## What Changes

- Add a **React + Vite + TypeScript** web app in a new `web/` subproject (sibling of `ble-bridge/`),
  consuming only the `/api/v1` contract. Types are generated from `api/openapi.yaml` via
  **openapi-typescript** (the spec stays leading).
- A **source-selection entry screen**: connect to an erg (`ble`) or pick a stored session to **replay**,
  both feeding the same dashboard — consuming the **`add-source-control`** API (this change depends on it).
- A single **LiveStore** fed by one `EventSource(/live/stream)` + `/live/snapshot`, holding the current
  state plus rolling history buffers; widgets subscribe to slices (selector-based, so only affected
  widgets re-render).
- A **widget system**: each widget is a self-describing `WidgetDef` (type, `requires[]` data keys,
  config schema, render). A **registry** catalogs them; the picker is **availability-aware** — widgets
  whose data the API doesn't yet provide are shown disabled.
- Core parameterized widgets: **StatTile** (power/pace/spm/hr/distance/elapsed/calories/drag),
  **ForceCurve**, **Trend** (any numeric metric), **GoalProgress**, **ConnectionStatus**, **WorkoutPhase**.
- The **ForceCurve** widget renders **measured points only (no line)** with the **previous stroke
  ghosted** behind the current, on **stable axes** so stroke-to-stroke differences are visible.
- **Configurability (Phase 2)**: add/remove widgets from the palette, **drag + resize** on a grid,
  per-widget config, **persisted** dashboard layout (localStorage), and built-in **presets**.
- Charts via **ECharts**; drag/resize via **react-grid-layout**.

**Depends on** `add-source-control` (the API for listing sessions + selecting a live/replay source).

Non-goals: backend/API work (this change is frontend-only — the source/session API comes from
`add-source-control`); offline history/analysis, session compare, end-of-piece summary (await further
API growth); server-persisted/shareable dashboards (localStorage for now); auth.

## Capabilities

### New Capabilities
- `web-viewer`: A React browser app presenting live PM5 data as a configurable, widget-based dashboard
  built on the `live-api` contract (one shared live data layer, availability-aware widget palette,
  persisted layouts, and a points-only ghosted force-curve widget).

### Modified Capabilities
<!-- none — consumes live-api unchanged -->

## Impact

- **New subproject** `web/` (Node toolchain: Vite, React, TypeScript, ECharts, react-grid-layout,
  openapi-typescript). Independent of the Java build.
- **Contract dependency:** generates TS types from `api/openapi.yaml`; drift fails the TS build.
- **Runtime:** the app talks to a running `serve` (`http://localhost:8080/api/v1`); dev proxy in Vite.
- **Scope boundary:** delivers a live HUD only — the "disabled until the API grows" widgets (splits,
  summary, history, controls) motivate later API changes but are out of scope here.
