## Why

The `live-api` v1 auto-connects to a PM5 on `serve` and is otherwise read-only. But the browser viewer
needs the user to **choose what feeds the live view** — connect to an erg, *or* replay a previously
recorded session. Both are the same thing: selecting the **source** behind the live pipeline. This
change adds that control to the API (reusing the `Pm5Source` seam — `BlePm5Source` / `ReplayPm5Source`
— that already exists), and a way to browse stored sessions to pick one to replay.

A bonus: **timed replay** makes the whole system (and the viewer) usable without a live erg — pick a
saved 500 m and watch it play back through the exact same live stream.

## What Changes

- **`serve` starts idle** — no auto-connect. The active source is chosen at runtime via the API (a
  default source MAY still be configured).
- New endpoints (added to the leading `api/openapi.yaml`):
  - `GET /api/v1/sessions` — list stored sessions (id, date, distance, strokes, duration, summary).
  - `POST /api/v1/source` — select + start a source: `{ type: "ble", device? }` **or**
    `{ type: "replay", sessionId, speed? }`. The active source feeds `LiveState` (and, for `ble`, storage).
  - `DELETE /api/v1/source` — stop the active source.
  - `GET /api/v1/devices` — scan for nearby PM5s (name/address/rssi), for the "connect to erg" picker.
  - `GET /api/v1/source` — current source/connection status (reuses `ConnectionStatus`, extended with source type).
- **Timed `ReplayPm5Source`** — replay a session's `raw.ndjson` at real-time pace (honouring inter-frame
  timing) with an optional `speed` multiplier, so a replayed session streams like a live one.
- A **`SessionCatalog`** reading the `sessions/` directory (`session.json` + `summary.json`) for the list.
- `ble` source records (existing `SessionManager`); `replay` source does **not** re-record (it is already stored).

Non-goals: editing/deleting stored sessions; stored-session *data* endpoints for offline analysis
(only the list + replay here); multi-client source arbitration; auth.

## Capabilities

### New Capabilities
- `source-control`: Choose and control the source feeding the live pipeline — list stored sessions,
  start a live (`ble`) or replayed (`replay`, timed) source, stop it, and scan for devices — all through `/api/v1`.

### Modified Capabilities
- `live-api`: the API is no longer strictly read-only — it gains source-control mutations (start/stop a
  source); the live *views* remain read-only, and no endpoint mutates stored session data.

## Impact

- **API:** new paths + schemas in `api/openapi.yaml`; regenerated Spring interfaces.
- **Server:** `serve` becomes idle-by-default; a `SourceManager` owns the active source lifecycle and
  wires it to `LiveState` (+ storage for `ble`); `SessionCatalog` service.
- **Source:** `ReplayPm5Source` gains a timed/paced mode.
- **Enables** the viewer's source-selection screen and hardware-free development/demo.
