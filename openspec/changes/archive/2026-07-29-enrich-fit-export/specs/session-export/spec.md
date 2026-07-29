## MODIFIED Requirements

### Requirement: Download a stored session as FIT
The system SHALL expose a read-only endpoint to download a stored session as a Garmin `.FIT` activity
file (`GET /api/v1/sessions/{id}/export.fit`), returned as a file attachment. The activity SHALL be a
rowing activity that includes **as much of the session's data as maps to FIT**:

- **records** — timestamp, distance, speed, power, heart rate, stroke cadence, and stroke distance
  (`cycle_length`);
- **laps** — one per stored split, with total time/distance plus avg power, avg/max heart rate, avg
  cadence, avg/max speed, total calories, and total strokes;
- **a session summary** — sport = rowing, totals (distance, time, calories, strokes) and averages/maxes;
- **device info** — the erg and firmware;
- **developer fields** — drag factor and per-stroke drive force/time/length/recovery and stroke count.

Requesting an unknown session SHALL return not-found. (Force curves are intentionally excluded — they do
not map to FIT.)

#### Scenario: Download an enriched session
- **WHEN** a client requests the FIT export of a stored session
- **THEN** it receives a `.fit` attachment whose records, laps, and session carry the available metrics
  (including heart rate, per-lap stats, and per-stroke drive/force data)

#### Scenario: Rich laps
- **WHEN** the session has splits
- **THEN** each split becomes a lap carrying its avg power, avg heart rate, avg cadence, avg speed,
  calories, and strokes

#### Scenario: Rowing-specific data preserved
- **WHEN** the session has per-stroke and drag data
- **THEN** it is preserved as FIT developer fields (drag factor, drive force/time/length, stroke count),
  which consumers may ignore but which remain in the file

#### Scenario: Unknown session
- **WHEN** the requested session id does not exist
- **THEN** the endpoint returns a not-found response
