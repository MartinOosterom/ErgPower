## Context

A stored session holds decoded per-characteristic NDJSON (general status, additional status, stroke,
split, force curve) plus a `session.json` manifest and `summary.json` totals — designed so "any subset
of files recombines by time and/or stroke." That's exactly what a FIT activity needs: a per-sample
record stream, per-split laps, and a session summary. This change maps that to FIT and serves it as a
download.

## Goals / Non-Goals

**Goals:** a valid, uploadable FIT rowing activity from a stored session (records + laps + session);
faithful SI→FIT units and timestamps; self-contained encoder (no external SDK); a per-session download
in the UI.

**Non-Goals:** force curves / per-stroke data via FIT developer fields (later); other formats
(TCX/CSV); any change to capture/storage; editing or merging sessions.

## Decisions

### D1: Hand-rolled FIT encoder (no external SDK)
Write a minimal FIT writer in Java. The FIT *format* is openly documented; only Garmin's SDK *code*
carries the FIT license (commercial-OK but not open-source, and not on Maven Central officially).
Hand-rolling keeps the project self-contained and MIT-clean — consistent with the hand-rolled PM5
decoder and the choice of MIT btleplug over BUSL SimpleBLE. The cost is correctness risk, paid down by
a validation test (D5).
- Scope of the writer: little-endian encoding, definition + data messages, FIT base types, the FIT
  epoch (`631065600` s = 1989-12-31 UTC), and the two-stage CRC-16.

### D2: A rowing activity, records + laps + session
Emit `file_id (type=activity)` → `event(timer start)` → `record…` → `lap…` (one per split) →
`session (sport=rowing)` → `activity`. Record fields: `timestamp`, `distance`, `speed` (from pace:
`500 / paceSeconds` m/s), `power` (W), `heart_rate` (bpm), `cadence` (stroke rate, spm). Session:
totals + avg/max power, HR, cadence, speed. Force curves / per-stroke are deferred to developer fields.

### D3: Build records by recombining the stored per-characteristic files by `pmTime`
Read `status-general` (timestamp/distance), `status-additional1` (pace/spm/HR), and `stroke-additional`
(power), aligning by `pmTime` into one record per status sample (carrying the latest stroke power).
This works for every stored session (no dependence on `raw.ndjson`) and uses the storage's intended
recombination. Laps come from `split.ndjson`; missing summary averages are computed from the records.

### D4: A read-only GET download endpoint, hand-written controller
`GET /api/v1/sessions/{id}/export.fit` → `200` with the FIT bytes,
`Content-Type: application/vnd.ant.fit` (or `application/octet-stream`) and a
`Content-Disposition: attachment; filename="<id>.fit"`; `404` for an unknown session. It's a read, so
`live-api`'s read-only requirement is untouched. The path is documented in `api/openapi.yaml`, but the
controller is hand-written (like the SSE `/live/stream`) because the codegen models binary streaming
awkwardly. File I/O + encoding run off the event loop.

### D5: Validation — decode the output back
Because the encoder is hand-rolled, a test SHALL encode the reference-fixture session and then decode
the produced FIT (header, CRC, message structure) to assert it's a valid rowing activity with records
and a session message, with correct scaling and epoch. A manual upload to Garmin Connect / Strava /
Concept2 Logbook confirms real-world acceptance (documented, not automated).

## Risks / Trade-offs

- **FIT validity** (base types, scale/offset, epoch, CRC) → the D5 decode-back test + manual upload.
- **Rowing specifics** — `sport = rowing`; `cadence` carries strokes/min (accepted by consumers);
  `speed` derived from pace; `power` held between strokes. Pin the exact enum/scale values in code.
- **Sessions lacking some streams** (e.g. no HR belt) → those record fields are simply omitted (FIT
  allows sparse fields); export still valid.

## Migration Plan

Additive: a new endpoint + encoder + a UI download button. No changes to capture, storage, or existing
endpoints.

## Open Questions

- Content type: `application/vnd.ant.fit` vs `application/octet-stream` (both work; the former is the
  registered FIT type).
- Whether to also compute/emit `avg/max` fields the PM5 summary doesn't provide, from the records.
- Later: force-curve / per-stroke as FIT developer fields, and additional formats (TCX/CSV).
