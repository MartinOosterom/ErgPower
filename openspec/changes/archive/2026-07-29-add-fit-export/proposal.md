## Why

Sessions are captured and replayable, but they're trapped in ErgPower's own NDJSON. **.FIT** is the
lingua franca of fitness activities — Strava, Garmin Connect, TrainingPeaks, and Concept2's own Logbook
all ingest it. Letting the user download a stored session as a FIT rowing activity makes the data
portable: pacing, power, and heart-rate graphs show up in whatever the user already uses.

Everything FIT needs is already stored — this is a mapping + encoding job, no new capture:

```
  stored session                              FIT
  ─────────────────────────────────────────   ─────────────────────────────
  session.json startedAt / device / firmware  file_id (activity, time_created)
  status-general (pmTime, distance)            record.timestamp, record.distance
  status-additional1 (pace→speed, spm, HR)     record.speed, record.cadence, record.heart_rate
  stroke-additional (strokePowerW)             record.power
  split.ndjson (per split)                     lap (one per split)
  summary.json (totals, avg/peak)              session (sport=rowing, totals + averages)
```

## What Changes

- **A download endpoint** `GET /api/v1/sessions/{id}/export.fit` → a `.fit` file (attachment). It's a
  read, so `live-api`'s read-only stance is unaffected.
- **A small self-contained FIT encoder** (Java): FIT header, definition + data messages, base-type
  encoding, the FIT timestamp epoch, and the CRC-16 — no external SDK, so no licensing/dependency
  entanglement (consistent with the project's MIT/self-contained ethos).
- **A rowing-activity builder** producing `file_id → event(start) → record… → lap… → session →
  activity`, `sport = rowing`, with correct SI→FIT scaling (distance, speed from pace, power, HR,
  stroke cadence) and timestamps.
- **Records are recombined from the stored per-characteristic files by `pmTime`** (the storage's stated
  design — "any subset of files recombines by time"); laps come from `split.ndjson`; totals/averages
  from `summary.json` (computed from records where the summary lacks them).
- **Frontend:** a **Download .fit** button per session in the picker (a link to the export URL).

Out of scope: force curves / per-stroke detail as FIT **developer fields** (most consumers ignore them —
a later addition); other export formats (TCX/CSV); editing/merging sessions.

## Capabilities

### New Capabilities
- `session-export`: download a stored session as a downloadable activity file (FIT rowing activity for
  now), encoded server-side with faithful units and timestamps.

## Impact

- **Backend:** a `Fit` encoder + a `SessionFitExporter` (session → activity model) + an export
  controller (hand-written, like the SSE controller, since the response is binary). No mutation, no new
  storage.
- **Contract:** `api/openapi.yaml` documents the new GET (binary response); the controller is
  hand-written because the generator models binary streaming awkwardly.
- **Frontend:** one download link per session.
- **Correctness risk** lives in the hand-rolled encoder (base types, scale/offset, epoch, CRC) — covered
  by a decode-the-output-back validation test, plus a manual upload check to Garmin/Strava/Logbook.
