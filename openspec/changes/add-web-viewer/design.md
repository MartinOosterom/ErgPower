## Context

The `live-api` capability serves `/api/v1` (connection status, a live snapshot, and a multiplexed SSE
stream: connection/workout/metrics/stroke/forceCurve/heartbeat). This change builds the browser viewer
on top of it. The end user must be able to **choose and arrange** which metrics/graphs to see, so the
UI is a configurable widget dashboard, not a fixed layout.

## Goals / Non-Goals

**Goals:** a live, glanceable dashboard while rowing; end-user-composable (add/remove/arrange/configure
widgets); one shared data layer; spec-generated types; a points-only ghosted force curve.

**Non-Goals:** backend/API work (depends on `add-source-control` for source + sessions); offline
history/review/compare, end-of-piece summary (await further API growth); server-persisted dashboards; auth.

## Decisions

### D1: React + Vite + TypeScript
A configurable drag/resize dashboard leans hard on React's ecosystem (notably `react-grid-layout`).
Vite for dev/build; TypeScript for contract safety end-to-end.

### D2: One shared LiveStore (selector subscriptions)
A single `EventSource(/live/stream)` plus an initial `/live/snapshot` feed one reactive store
(Zustand or equivalent). It holds current state (connection, workout, metrics, lastStroke, forceCurve)
plus **rolling buffers** (`history{power,pace,hr}`, `recentCurves`) for trends and ghosting. Widgets
subscribe to slices via selectors, so a metrics tick only re-renders the widgets that use it. On SSE
reconnect the store re-fetches the snapshot to resync.
- **Why not per-widget connections:** N EventSources would hammer the server and desync; one store is
  simpler and consistent.

### D3: Self-describing widgets + registry
Each widget is a `WidgetDef { type, name, category, requires: DataKey[], defaultConfig, configSchema,
render }`. A registry catalogs them. `requires[]` lists the store fields a widget needs.
- **Availability-aware palette:** widgets whose `requires` aren't satisfied by the current API/data are
  shown **disabled** — so split/summary/history widgets can be *registered now, enabled later* when the
  API grows, with no rewrite.
- **Parameterized, not hardcoded:** one `StatTile` configured to any metric; one `Trend` configured to
  any numeric field — few components, many widgets.

### D4: Configurable dashboard (Phase 2 in scope)
A dashboard is `{ name, widgets: [{ type, config, layout{x,y,w,h} }] }`. Edit mode: palette to add,
`react-grid-layout` to drag/resize, a per-widget config panel. View mode: clean. Persisted in
**localStorage**; ship **presets** ("Minimal HUD", "Full panel"). Server-persisted/shareable dashboards
are a future API slice.

### D5: ECharts for charts
Covers the force-curve scatter, streaming trends, and any gauges in one lib, canvas-rendered with good
real-time behaviour. `uPlot` can replace the trend renderer later if long high-rate pieces stutter.

### D6: ForceCurve = points-only, ghosted previous, stable axes
Render **measured samples as points, no connecting line**. Keep the **previous** stroke as a faded gray
point-cloud behind the **current** (bright) one, so stroke-to-stroke differences are visible.
**Stable axes are essential** — auto-rescaling per stroke hides the difference:
- Y (force N): fixed to a rolling **session peak** (+headroom), not per-stroke max.
- X: **sample index** (≈ drive time, absolute — matches the PM5, shows drive length) by default;
  **normalized 0–100%** available to compare shape only.
Config: `{ xAxis: index|normalized, yScale: session-peak|fixed, ghosts: 1..3 (default 1),
pointSize, connect: false }`.

### D7: Spec-led types + dev harness
`openapi-typescript` generates types from `api/openapi.yaml`; the app consumes only those (drift fails
the TS build). `openapi-fetch` for REST; native `EventSource` for SSE, typed by the generated event
payloads. Dev: Vite proxy to a running `serve` (or a replay-fed `serve` for hardware-free work).

### D8: `web/` subproject
The app lives in `web/`, independent of the Java/Maven build (its own Node toolchain), like `ble-bridge/`.

### D9: Source-selection screen (depends on `add-source-control`)
Before the dashboard, an entry screen lets the user pick the source: **Connect to erg** (`GET /devices`
→ `POST /source {type:ble}`) or **Replay a session** (`GET /sessions` → `POST /source {type:replay,
sessionId}`). Both then feed the same `/live/stream`, so the dashboard is identical regardless of source.
A source indicator (live vs replaying "<session>") shows which is active; the store resets on source
switch. This depends on the `add-source-control` API and unlocks hardware-free development (replay a
saved session and build the whole UI against it).

## Risks / Trade-offs

- **High-frequency re-renders** → selector-based store subscriptions; ECharts `setOption` with
  `notMerge:false`; cap history buffers by time.
- **SSE reconnect / desync** → snapshot re-fetch on (re)connect; the store is the single source.
- **Availability drift** (widget expects data the API doesn't send) → `requires[]` gating + graceful
  "no data" states.
- **Scope creep toward analysis/history** → explicitly out; those need API changes first, and the
  widget model lets them slot in later as new registered widgets.

## Migration Plan

Additive, separate subproject. No Java changes. Delivered in phases (0 foundations → 1 widget system +
core widgets → 2 configurability); this change carries through Phase 2.

## Open Questions

- Store lib: Zustand vs Valtio vs React context+reducer (lean Zustand for selector ergonomics).
- Grid lib specifics: `react-grid-layout` vs `dnd-kit` + CSS grid.
- Whether a tiny **replay-fed `serve`** mode is worth adding for hardware-free UI dev (small server task).
