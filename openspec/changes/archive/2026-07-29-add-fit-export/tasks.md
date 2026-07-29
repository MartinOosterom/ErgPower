## 1. Contract

- [x] 1.1 Document `GET /sessions/{id}/export.fit` in `api/openapi.yaml` (binary response, attachment;
      404 unknown). The controller is hand-written (binary streaming), so this is for documentation.

## 2. FIT encoder (self-contained)

- [x] 2.1 Minimal FIT writer: header, definition + data messages, base-type encoding, FIT epoch
      (631065600 s), little-endian, two-stage CRC-16
- [x] 2.2 Rowing-activity builder: `file_id` → `event(start)` → `record` (timestamp/distance/speed/
      power/heart_rate/cadence) → `lap` (per split) → `session` (sport=rowing, totals + avg/max) →
      `activity`; correct SI→FIT scaling (speed = 500/paceSeconds)

## 3. Session → FIT

- [x] 3.1 `SessionFitExporter`: read a stored session, recombine `status-general` + `status-additional1`
      + `stroke-additional` by `pmTime` into records; laps from `split.ndjson`; totals from
      `summary.json` (compute missing averages from records)
- [x] 3.2 Export controller: `GET /sessions/{id}/export.fit` → FIT bytes with `attachment` disposition,
      404 on unknown; run file I/O + encoding off the event loop

## 4. Frontend

- [x] 4.1 "Download .fit" button/link per session in the picker (points at the export URL)

## 5. Verify

- [x] 5.1 Encode the reference-fixture session, then decode the FIT back and assert a valid rowing
      activity (header + CRC ok, records + session present, units/epoch correct)
- [x] 5.2 Manual: upload to Garmin Connect / Strava / Concept2 Logbook to confirm acceptance; `web`/main
      README note
