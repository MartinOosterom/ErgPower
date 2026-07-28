## Context

ErgPower today is a CLI that captures one run and exits, built on a `Pm5Source` producing a
`Flux<Pm5Event>` (live via `BlePm5Source`, or replayed). The next feature is a browser live dashboard,
which must consume a **REST + SSE API** where the **OpenAPI document is leading** (server + client are
generated from it). This change designs that API and the server that serves it.

The leading artifact is `api/openapi.yaml` (drafted in this change folder as `openapi.yaml`; moves to
`api/openapi.yaml` on implementation).

## Goals / Non-Goals

**Goals:**
- One OpenAPI doc drives both the Spring server interfaces and the TS client — no drift.
- A v1 read surface sufficient for a live dashboard: connection status, a full snapshot, and a
  multiplexed SSE stream (metrics + force curves + lifecycle).
- Reuse the existing `Flux<Pm5Event>`; the SSE stream is just another subscriber alongside storage.

**Non-Goals:** the web pages / charting; commands (connect/disconnect/scan); stored-session browsing;
auth.

## Decisions

### D1: SSE for live, inside one OpenAPI doc
Live data is push; OpenAPI is request/response. **SSE** (`GET → text/event-stream`) rides on plain
HTTP, so it stays in the one OpenAPI doc (WebSocket would need a second spec — AsyncAPI — breaking
"spec is leading"), is browser-native (`EventSource`), delivers per-stroke force curves as ordered
events, and maps directly onto `Flux<Pm5Event> → Flux<ServerSentEvent>`.
- OpenAPI can't fully express "one stream, many event types": each payload is a `components/schema`,
  the multiplexing is documented, and a `LiveEvent` `oneOf` enumerates the payloads. Codegen emits
  the payload types; the ~10 lines of `EventSource` dispatch are hand-written in the browser.

### D2: One multiplexed stream + snapshot-on-load
`GET /live/stream` carries named events (`connection|workout|metrics|stroke|forceCurve|heartbeat`) on
one connection (ordered, single reconnect). The client `GET /live/snapshot` once for the full picture,
then subscribes; on SSE reconnect it re-fetches the snapshot to resync.

### D3: `metrics` is a full snapshot object each tick
The server keeps a **rolling `LiveState`** by merging incoming events (power from strokes, pace/spm
from status, target from general status, …) and emits a complete `LiveMetrics` at the sample rate —
idempotent and simple for the browser (no delta-stitching).

### D4: OpenAPI 3.0.3 (not 3.1) for codegen maturity
3.0.3 has the most mature `openapi-generator` (Spring) + `openapi-typescript` support and `nullable:
true` works cleanly. Revisit 3.1 later if needed.

### D5: WebFlux + a long-running `serve` mode
Adds `spring-boot-starter-webflux`; SSE is `Flux<ServerSentEvent<…>>`. A new `serve` command runs the
app as a service that owns a live `Pm5Source` and multicasts to storage **and** the API. `capture`/
`replay` stay as-is.

### D6: Spec-first codegen
`openapi-generator-maven-plugin` (generator `spring`, `interfaceOnly=true`, delegate pattern) →
implement the delegates. Browser (later change) uses `openapi-typescript` for types + `openapi-fetch`,
and native `EventSource` typed by the event schemas. `/api/v1` in the path; version bumped additively.

### D7: `/api/v1`, SI units, RFC 7807 errors, no auth
Path-versioned. All quantities SI (m, s, W, N, bpm); pace = seconds per 500 m — fixed in the contract.
Errors as `application/problem+json`. No auth in v1 (localhost); flagged as future.

### D8: `metrics` cadence follows the PM5 sample rate
A `metrics` SSE event is emitted whenever a status update arrives from the PM5 (the configured `0x0034`
rate — 500 ms default, tunable to 250/100 ms), rather than a fixed decoupled UI rate. The browser sees
exactly what the erg sends.

### D9: Exact Workout State → `WorkoutPhase` mapping (0x31 byte 8, rev 1.30)
By destination (transition states name where they're heading):
- **WAITING**: 0 WAITTOBEGIN, 2 COUNTDOWNPAUSE
- **ROWING**: 1 WORKOUTROW, 4 INTERVALWORKTIME, 5 INTERVALWORKDISTANCE, 6 INTERVALRESTENDTOWORKTIME, 7 INTERVALRESTENDTOWORKDISTANCE
- **RESTING**: 3 INTERVALREST, 8 INTERVALWORKTIMETOREST, 9 INTERVALWORKDISTANCETOREST
- **ENDED**: 10 WORKOUTEND, 11 TERMINATE, 12 WORKOUTLOGGED

`RowingState` (INACTIVE/ACTIVE) may refine "currently pulling" within ROWING but does not change the phase.

### D10: The API is just another subscriber (storage is independent)
The `Pm5Source` is a multicast `Flux<Pm5Event>` designed for multiple independent subscribers. Storage
(`SessionManager`) is one subscriber; the **SSE broadcaster is another**. They are decoupled — the API
does not "also persist"; recording happens because the storage subscriber is attached to the same
running source. In `serve` mode both are attached, giving live viewing and an auto-recorded session.

**Wiring change (not a design change):** today `CaptureService` makes storage the *single blocking
consumer* (`…blockLast()`), which drives the stream. For the server we run the source independently and
let storage **and** the SSE endpoint `subscribe()` on their own — realising "N independent subscribers"
rather than one consumer that happens to write to disk. Same components, restructured lifecycle.

## Risks / Trade-offs

- **SSE not first-class in OpenAPI** → document payloads as schemas + a `oneOf`; hand-write the small
  `EventSource` dispatch. Accepted to keep one spec.
- **Long-running server vs CLI** → new `serve` mode; the live source is shared via the multicast sink,
  storage and SSE both subscribe. Lifecycle (start/stop, reconnect) reuses existing `BlePm5Source`.
- **3.0.3 vs 3.1** → chose 3.0.3 for tooling; a future migration is additive.
- **Contract churn during design** → cheap now (no server yet); once codegen is wired, changes ripple
  to both ends and fail compilation on mismatch (the point).

## Migration Plan

Additive. `serve` is new; `capture`/`replay` unchanged. Codegen runs at build; generated sources are
not committed. The browser viewer is a separate later change consuming this contract.

## Open Questions

- Resolved: metrics cadence (D8, follows PM5 sample rate), Workout State mapping (D9), serve-records
  (D10, pending final confirmation).
- `LiveMetrics` field set — final review (e.g. add `intervalNumber` / `restTimeS` / `strokeState`?).
