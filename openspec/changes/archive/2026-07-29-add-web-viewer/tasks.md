## 0. Foundations

- [x] 0.1 Scaffold `web/` (Vite + React + TypeScript); dev proxy to `serve` (`/api/v1`)
- [x] 0.2 openapi-typescript codegen from `api/openapi.yaml` → generated types; openapi-fetch for REST
- [x] 0.3 `LiveStore` (Zustand): connect `EventSource(/live/stream)` + `/live/snapshot`; hold current state + rolling buffers (`history{power,pace,hr}`, `recentCurves`); reconnect → snapshot resync
- [x] 0.4 Source-selection screen: `GET /sessions` / `GET /devices`, `POST /source` (ble|replay), active-source indicator

## 1. Widget system

- [x] 1.1 `WidgetDef` contract (type, name, category, `requires[]`, defaultConfig, configSchema, render) + registry
- [x] 1.2 `DashboardRenderer(config)` → grid of widgets from the shared store

## 2. Core widgets

- [x] 2.1 `StatTile` (parameterized metric) + `ConnectionStatus` + `WorkoutPhase`
- [x] 2.2 `Trend` (parameterized numeric metric over time) via ECharts
- [x] 2.3 `GoalProgress` (time-left / distance-left + projected, from workout + metrics)
- [x] 2.4 `ForceCurve` — points-only, ghosted previous, stable axes; config `{xAxis,yScale,ghosts,pointSize,connect}`

## 3. Configurability (Phase 2)

- [x] 3.1 Widget palette (availability-aware from `requires[]`); add/remove/reorder
- [x] 3.2 Drag + resize grid (react-grid-layout); per-widget config panel
- [x] 3.3 Persist dashboard config (localStorage); built-in presets ("Minimal HUD", "Full panel")

## 4. Polish + verify

- [x] 4.1 Responsive layout; light/dark
- [x] 4.2 Run end-to-end against a replayed session via `serve` (hardware-free); verify tiles, trends, and the ghosted force curve update
- [x] 4.3 `web/README.md`: dev setup, codegen, pointing at `serve`
