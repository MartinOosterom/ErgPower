## Why

The first FIT export is a valid MVP (records + laps with time/distance + a session summary), but it
drops most of what a session holds. The user wants **as much data as possible** in the file. Nearly all
of it maps to standard FIT fields or FIT developer fields — so fill the file.

## What Changes

- **Rich laps**: each split lap gains avg power, avg/max heart rate, avg cadence, avg/max speed, total
  calories, and total strokes (from `split` + `split-additional`, maxes computed from records).
- **Session totals**: add `total_calories` and `total_strokes` (`total_cycles`) alongside the existing
  averages/maxes.
- **Records**: add `cycle_length` (stroke distance).
- **`device_info`**: erg + firmware provenance.
- **Developer fields** (custom, preserved even if some consumers ignore them): `drag_factor` per record,
  and per-stroke `stroke_count`, `drive_time`, `recovery_time`, `drive_length`, `peak_drive_force`,
  `avg_drive_force` (carried at their latest values), plus `firmware` on the session.
- The FIT **encoder** gains developer-field support (`developer_data_id` + `field_description`
  messages, developer fields in definitions/data) and string/byte writing.

Out of scope: **force curves** (they don't map to FIT — variable-length arrays consumers ignore); a
separate curve export could come later. No new endpoint; the download URL is unchanged.

## Capabilities

### Modified Capabilities
- `session-export`: the exported FIT is enriched — richer laps and session totals, `cycle_length`,
  device/firmware, and rowing-specific per-stroke/drag data via developer fields.

## Impact

- Backend only: `Fit` gains developer-field + string/byte support; `SessionFitExporter` reads more
  streams (`split-additional`, `stroke`) and emits the extra fields/messages. No API/contract change,
  no frontend change. The decode-back validation test is extended to parse developer fields and assert
  the new messages.
