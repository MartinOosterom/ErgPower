## Context

`live-api` serves the live pipeline but `serve` auto-connects to a PM5 and exposes no controls. The
viewer needs to let the user connect to an erg or replay a saved session — both are just choosing the
**source** that feeds `LiveState`/SSE. The `Pm5Source` seam (`BlePm5Source`, `ReplayPm5Source`) already
makes sources interchangeable; this change exposes that choice over the API.

## Goals / Non-Goals

**Goals:** select the active source (live or replayed session) at runtime; list sessions to pick from;
timed replay so a session streams like live; keep the live views read-only.

**Non-Goals:** editing/deleting sessions; full stored-session *data* endpoints (analysis); multi-client
arbitration; auth.

## Decisions

### D1: Source as a single resource
`POST /source { type: "ble" | "replay", … }` selects and starts the active source; `DELETE /source`
stops it; `GET /source` reports it. One control point — the UI is literally choosing one source.
- **Alternative:** separate `POST /connection` + `POST /replay/{id}`. Rejected — the app has exactly one
  active source; a single resource models that cleanly and keeps the state machine obvious.

### D2: `serve` starts idle
`serve` no longer auto-connects; it starts the web server with no active source. A source begins on
`POST /source` (a configured default MAY auto-start for convenience). This makes "connect" and "replay"
symmetric and puts the user in control.

### D3: Both sources feed the same pipeline; only `ble` records
The chosen source's `Flux<Pm5Event>` feeds `LiveState` (→ SSE) exactly as today. A `ble` source also
attaches the storage subscriber (`SessionManager`). A `replay` source does **not** record — it is
replaying already-stored data. A `SourceManager` owns the single active source and its subscribers,
and swaps them on `POST /source` (stopping the previous first).

### D4: Timed replay
`ReplayPm5Source` gains a paced mode: emit each event honouring the inter-frame delta from the capture
(`mono`/`hostTime`), scaled by an optional `speed` (default 1×). So a replayed 500 m plays through
`/live/stream` at real time — the browser can't tell it from live. The fast "as-quick-as-possible" mode
stays for tests/offline decode.

### D5: Session catalog from the filesystem
`SessionCatalog` lists `sessions/*/` by reading each `session.json` + `summary.json` (id = folder name,
plus device/firmware/started-at and summary totals). No database — the folder *is* the store. Sessions
without a `raw.ndjson` can be listed but not replayed (flagged).

### D6: Live views stay read-only
`live-api`'s read-only requirement is relaxed to "read-only *views*; mutations limited to source
control." No endpoint mutates stored session data.

### D7: Contract-first, as before
New paths/schemas go into `api/openapi.yaml` (leading); Spring interfaces regenerate; the SSE stream is
unchanged (it just reflects whichever source is active).

## Risks / Trade-offs

- **Switching sources mid-stream** → `SourceManager` stops the old source and its subscribers before
  starting the new one; `LiveState` resets its rolling state; a `connection`/source event is pushed.
- **Timed replay accuracy** → pace off `mono` deltas; clamp huge gaps; `speed` for fast demos.
- **Replaying a session that lacks `raw.ndjson`** → older/live-recorded sessions without raw frames are
  listed as non-replayable (only the spike fixture + new `ble` sessions have `raw.ndjson`).
- **One active source only** → intentional (one erg, one screen); document it, no arbitration.

## Migration Plan

Additive except the `serve`-idle behavior change. Existing `capture`/`replay` CLI unchanged. The
`ReplayPm5Source` timed mode is opt-in (existing fast mode preserved for tests).

## Open Questions

- Should a configured default source auto-start on `serve` (convenience) or always require `POST /source`?
- `speed` bounds for replay (e.g. 0.5×–8×) and whether to support seek/pause (probably a later change).
- Session `id` scheme — folder name is stable but ugly; expose a friendly label from the manifest.
