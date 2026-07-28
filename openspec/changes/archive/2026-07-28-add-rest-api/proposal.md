## Why

The next feature is a **browser-based live viewer** (force curves, pace/power over time, connection
status). It will be built entirely against a **REST + SSE API served by ErgPower**, and the **OpenAPI
document is the leading artifact** — server interfaces (Spring) and the TypeScript client are
generated from it, so the contract can't drift. This change designs and stands up that API surface;
the web pages come later, in their own change.

## What Changes

- Add an **OpenAPI 3.0 document** (`api/openapi.yaml`) as the single source of truth for the HTTP
  contract, versioned under **`/api/v1`**.
- Implement the **v1 read surface** for a live dashboard:
  - `GET /api/v1/connection` — current PM5 connection status (state, device, firmware, decode profile).
  - `GET /api/v1/live/snapshot` — the full current live state, for initial render.
  - `GET /api/v1/live/stream` — a **multiplexed Server-Sent Events** stream (`text/event-stream`)
    carrying named events: `connection`, `workout`, `metrics`, `stroke`, `forceCurve`, `heartbeat`.
- Introduce a **long-running server mode**: ErgPower becomes a service that owns a live `Pm5Source`
  and fans its `Flux<Pm5Event>` out to storage **and** SSE subscribers (the "live display subscriber"
  anticipated from day one). Adds `spring-boot-starter-webflux` (SSE maps directly onto the existing `Flux`).
- Add **spec-first codegen**: an `openapi-generator` Maven step produces Spring server interfaces from
  the spec (implemented via delegates); the browser project (later) generates TS types via
  `openapi-typescript`.
- A **`LiveState` aggregator** that merges incoming `Pm5Event`s into a coherent current-state snapshot
  (so the browser never stitches characteristics together).

Non-goals (this change): the web pages / charting; **commands** (connect/disconnect, device scan) —
deferred to a later spec revision; **stored-session browsing** endpoints; authentication.

## Capabilities

### New Capabilities
- `live-api`: A contract-first `/api/v1` HTTP surface (OpenAPI-led) exposing PM5 connection status, a
  full live snapshot, and a multiplexed SSE event stream for a browser dashboard.

### Modified Capabilities
<!-- none in main specs yet -->

## Impact

- **New dependency:** `spring-boot-starter-webflux` (reactive HTTP + SSE); `openapi-generator-maven-plugin`.
- **New artifact:** `api/openapi.yaml` (leading); generated Spring interfaces under `target/generated-sources`.
- **Runtime shift:** a `serve` mode (long-running) alongside the existing `capture`/`replay` CLI; the
  live `Pm5Source` is shared between storage and the API.
- **No auth** (localhost only) — noted as a future concern.
- **Units:** all quantities SI (m, s, W, N, bpm); pace as seconds per 500 m — fixed in the contract.
