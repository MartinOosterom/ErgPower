## ADDED Requirements

### Requirement: Per-session folder
The storage subscriber SHALL persist each rowing session into its own folder, created when a session-started lifecycle event is received and finalised when a session-ended lifecycle event is received. The folder name SHALL be derived from the session start time (and MAY include a distance/summary hint).

#### Scenario: Folder created on session start
- **WHEN** a session-started event is received
- **THEN** a new session folder is created and subsequent events for that session are written into it

#### Scenario: Session finalised on session end
- **WHEN** a session-ended event is received
- **THEN** the summary is written, all open files are flushed and closed, and the folder is left in a complete, self-describing state

### Requirement: One NDJSON file per PM5 characteristic
The storage subscriber SHALL write one newline-delimited JSON (NDJSON) file per PM5 data characteristic, faithfully mirroring the wire structure — status characteristics are NOT merged into a combined file. Each record SHALL be appended as it arrives (one JSON object per line) and flushed frequently so that an interrupted session retains all data received up to the interruption.

#### Scenario: Faithful per-characteristic files
- **WHEN** events from multiple characteristics are captured during a session
- **THEN** each characteristic's events are written to its own dedicated NDJSON file, named semantically, with the characteristic-to-file mapping recorded in the manifest

#### Scenario: Crash-safety via append
- **WHEN** the application is interrupted partway through a long session
- **THEN** the already-written NDJSON files contain every record received before the interruption (append-only, no rewrite of a single large document)

### Requirement: Temporal matchability across all data
Any stroke — together with its force/power curve — SHALL be matchable to the data captured in every other stream at that moment in the session. This guarantee is a property of the **keys carried by every record**, not of the file layout: it SHALL hold whether the data is stored across multiple per-characteristic files or in a single combined file. Concretely, every record SHALL carry `pmTime` (a monotonic, session-relative PM5 elapsed clock) and `hostTime` (host receive time); every per-stroke record (stroke metrics and the force curve) SHALL additionally carry a shared `strokeIndex`. These keys SHALL be sufficient to recombine any subset of the stored data by time and/or stroke without any additional information.

#### Scenario: A stroke and its curve match everything at that moment
- **WHEN** a specific stroke and its force curve are selected
- **THEN** the concurrent values from every other stream (status, pace, power, HR, split) at that moment can be located by aligning on `pmTime` (nearest-in-time), independent of how the data is split across files

#### Scenario: Status streams joinable on time
- **WHEN** two timed status streams from the same session are loaded
- **THEN** their records can be aligned on `pmTime` (nearest-in-time) without any file-specific logic

#### Scenario: Force curve joinable to stroke metrics
- **WHEN** the stroke stream and the force-curve stream from the same session are loaded
- **THEN** each force curve joins to its stroke metrics on `strokeIndex` (exact), the keys having been resolved and stamped at decode time rather than depending on the raw force-curve payload carrying a stroke count

#### Scenario: Layout is free provided the guarantee holds
- **WHEN** an alternative single combined file is used instead of per-characteristic files
- **THEN** the same temporal/stroke matchability holds via the same `pmTime`/`strokeIndex` keys, so the layout choice does not affect matchability

### Requirement: Session manifest
Each session folder SHALL contain a `session.json` manifest recording provenance: device identity, PM5 firmware and force-curve format version, effective connection configuration (selection, sample rate, force-curve on/off), start time, clock reference, application/decoder version, and the characteristic-to-file mapping.

#### Scenario: Manifest records capture provenance
- **WHEN** a session folder is created
- **THEN** `session.json` is written containing the effective configuration and device/firmware/decoder versions used for that capture

### Requirement: Workout summary persisted
Each session folder SHALL contain a `summary.json` written once at session end from the PM5 workout-summary data (totals, averages).

#### Scenario: Summary written at end
- **WHEN** a session-ended event with workout-summary data is received
- **THEN** `summary.json` is written with the final totals and averages for the session

### Requirement: Optional raw frame log
The storage subscriber SHALL optionally write a `raw.ndjson` of the exact bridge frames (characteristic id, host timestamp, raw bytes). When present, this log SHALL be replayable through the decoder to reproduce the session.

#### Scenario: Raw log replayable
- **WHEN** raw logging was enabled for a session
- **THEN** `raw.ndjson` contains every forwarded frame and can be consumed by the replay source to reproduce the decoded events
