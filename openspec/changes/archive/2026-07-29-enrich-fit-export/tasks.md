## 1. Encoder

- [x] 1.1 Extend `Fit`: `developer_data_id` + `field_description` messages, the developer-data flag on
      definitions, developer fields in definitions + values in data, and string/byte writing

## 2. Enriched export

- [x] 2.1 Rich laps: avg power, avg/max HR, avg cadence, avg/max speed, total_calories, total_cycles
      (from `split` + `split-additional`; maxes from records in each split's window)
- [x] 2.2 Session totals (`total_calories`, `total_cycles`) + record `cycle_length` (stroke distance)
- [x] 2.3 `device_info` (erg + firmware) and developer fields: `drag_factor` per record; per-stroke
      `stroke_count`/`drive_time`/`recovery_time`/`drive_length`/`peak_drive_force`/`avg_drive_force`
      (carried latest); `firmware` on the session

## 3. Verify

- [x] 3.1 Extend the decode-back test to parse developer fields and assert the new messages
      (developer_data_id, field_description, device_info) and a rich lap; whole suite green
- [x] 3.2 Runtime: download an enriched .fit; README note. Manual: re-upload to Garmin/Strava/Logbook
