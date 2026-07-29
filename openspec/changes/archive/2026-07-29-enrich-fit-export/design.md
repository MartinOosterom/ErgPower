## Context

`SessionFitExporter` already recombines the per-characteristic NDJSON into a FIT rowing activity. This
change fills in the data it currently omits, and teaches the `Fit` encoder about developer fields so
rowing-specific values (drag, drive force/time) survive in the file.

## Goals / Non-Goals

**Goals:** put as much session data as maps cleanly to FIT into the export — rich laps, session totals,
cycle length, device/firmware, and per-stroke/drag via developer fields; keep the file valid.

**Non-Goals:** force curves (Tier 3 — don't map to FIT); any endpoint/contract/frontend change.

## Decisions

### D1: Developer fields for rowing-specific data
FIT developer fields (`developer_data_id` + `field_description`) carry values with no standard field:
`drag_factor` (per record), and per-stroke `stroke_count`, `drive_time`, `recovery_time`,
`drive_length`, `peak_drive_force`, `avg_drive_force`, plus `firmware` (string) on the session. Per-
stroke values are carried at their latest onto the status-cadence records (like power). Consumers that
don't read developer fields simply ignore them; the data is preserved.

### D2: Standard fields wherever they exist
Prefer standard FIT fields over developer ones: rich laps (avg/max power/HR/cadence/speed, calories,
`total_cycles`), session `total_calories`/`total_cycles`, record `cycle_length` (stroke distance), and a
`device_info` message. Per-lap averages come from `split-additional`; per-lap maxes are computed from
the records in each split's time window.

### D3: Encoder gains developer-field + string/byte support
`Fit` learns to emit `developer_data_id`/`field_description`, set the developer-data flag on
definitions, append developer fields to definitions and values to data messages, and write string and
byte fields. Everything stays little-endian with the two-stage CRC.

### D4: Force curves stay out
Variable-length arrays don't fit FIT's fixed-size fields and every consumer ignores them; forcing them
in would bloat the file for no benefit. A dedicated curve export (CSV/JSON) is a separate future change.

## Risks / Trade-offs

- **Developer-field correctness** (the flag, field_description base types, value ordering) → the
  decode-back test parses developer fields and asserts the new messages; a manual upload confirms
  consumers still accept the file.
- **Per-lap max computation** depends on record coverage; if a stream is missing, that field is omitted
  (FIT invalid), file stays valid.

## Open Questions

- FIT manufacturer id for Concept2 vs `development` (255) — using development is safe; a real id is nicer.
- Units strings on developer fields (N, s, m) — include for readability vs keep minimal.
