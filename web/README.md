# ErgPower web viewer

A React browser dashboard for live Concept2 PM5 data, built entirely on the `/api/v1` contract
([`../api/openapi.yaml`](../api/openapi.yaml)). It's a **configurable, widget-based** HUD: you choose
which metrics and graphs to show and arrange them on a grid. A live PM5 and a replayed stored session
feed the exact same view, so the whole UI can be developed **without hardware**.

## Stack

- **Vite + React 18 + TypeScript** (React 18 because `react-grid-layout` → `react-draggable` still uses
  `findDOMNode`, removed in React 19).
- **Zustand** — one shared `LiveStore` fed by a single `EventSource(/live/stream)` + `/live/snapshot`.
- **ECharts** — force-curve scatter and streaming trends.
- **react-grid-layout** — drag/resize dashboard.
- **openapi-typescript** — API types generated from the spec; **openapi-fetch** for REST.

## Two ways this runs

- **Production (default):** you don't run anything here. `./mvnw package` (from the repo root) compiles
  this app and **bundles it into the jar**, and the Java `serve` process hosts it at
  `http://localhost:8080/`. No Node/npm at runtime. This directory only matters at build time.
- **UI development (this dir):** the Vite dev server gives hot-reload while you edit the UI, proxying
  API calls to a running `serve`.

## Prerequisites (UI development only)

- Node 20+ (developed on 24).
- A running backend for the API: from the repo root, `java -jar target/ErgPower-0.0.1-SNAPSHOT.jar serve`
  (serves `http://localhost:8080`). `serve` starts **idle** — the app picks a source at runtime.

## Dev server

```sh
cd web
npm install
npm run dev        # Vite dev server on http://localhost:5173, proxying /api → :8080
```

Open the dev server, then on the entry screen either **Replay a session** (pick a stored session — no
PM5 needed) or **Connect to erg**. Both then render the same live dashboard. (For a non-dev run, just
open the `serve` port at `http://localhost:8080` — same UI, served from the jar.)

### Hardware-free development

You don't need an erg. Record a session once (or use the checked-in reference capture), then replay it:

1. Start `serve` (see above). It lists stored sessions under `sessions/` — any with raw frames is
   replayable. (To point at a scratch folder, add `--ergpower.ble.storage.dir=/some/dir`.)
2. In the app: **Replay a session** → choose one, set a speed multiplier (e.g. ×2), **Replay**.
3. The dashboard fills from the replayed stream: stat tiles, trends, workout phase/goal, and the
   points-only, ghosted **force curve** all update.

## Scripts

| Script            | What it does                                                             |
| ----------------- | ------------------------------------------------------------------------ |
| `npm run gen`     | Generate `src/api/schema.d.ts` from `../api/openapi.yaml`                 |
| `npm run dev`     | Vite dev server (proxy `/api` → `:8080`)                                  |
| `npm run build`   | `gen` → `tsc -b` → `vite build` — a **contract drift fails the build**    |
| `npm run typecheck` | Type-check only                                                        |
| `npm run preview` | Serve the production build                                               |

## How it fits together

```
EventSource(/live/stream) ─┐
                           ├─▶ LiveStore (Zustand)  ──selectors──▶  widgets
GET /live/snapshot ────────┘     current state + rolling buffers        │
                                                                        ▼
GET /sessions · /devices ─▶ SourceSelect ─▶ POST /source ─▶ (backend) ──┘
```

- **`src/api/`** — generated `schema.d.ts`, typed aliases, and the `openapi-fetch` client.
- **`src/store/liveStore.ts`** — the single SSE connection, snapshot resync on reconnect, rolling
  history buffers, and `AVAILABLE_DATA` (which data keys exist today).
- **`src/widgets/`** — `WidgetDef` contract + registry, and the core widgets (StatTile, Trend,
  ForceCurve, GoalProgress, ConnectionStatus, WorkoutPhase). Widgets declare `requires[]`; the palette
  disables any whose data the API doesn't serve yet (e.g. the placeholder splits/summary widgets).
- **`src/dashboard/`** — the grid renderer, palette, per-widget config panel, presets
  ("Minimal HUD", "Full panel"), and localStorage persistence.
- **`src/source/`** — the source-selection entry screen and the active-source indicator.

## Scope

This delivers a live HUD. Widgets that need data the API doesn't serve yet (splits, end-of-piece
summary, history/compare) are **registered but disabled** — they'll light up unchanged when the API
grows. Server-persisted/shareable dashboards and auth are out of scope (layouts persist to
localStorage).
