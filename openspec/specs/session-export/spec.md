# session-export Specification

## Purpose
The session-export capability lets a client download a stored session as a downloadable activity file —
a Garmin FIT rowing activity — encoded server-side from the persisted session data. The export is a
read-only operation that faithfully maps the session's measurements (distance, speed, power, heart rate,
stroke cadence) into FIT's scaled units and derives each record's timestamp from the session start time
plus the sample's elapsed time in the FIT epoch, producing a valid activity that standard FIT decoders
can parse.

## Requirements

### Requirement: Download a stored session as FIT
The system SHALL expose a read-only endpoint to download a stored session as a Garmin `.FIT` activity
file (`GET /api/v1/sessions/{id}/export.fit`). The response SHALL be the FIT bytes as a file attachment.
The activity SHALL contain: file identification, a time-series of records, one lap per stored split, and
a session summary for a rowing sport. Requesting an unknown session SHALL return not-found.

#### Scenario: Download a session as FIT
- **WHEN** a client requests the FIT export of a stored session
- **THEN** it receives a `.fit` file attachment containing the session's records, per-split laps, and a
  rowing session summary

#### Scenario: Unknown session
- **WHEN** the requested session id does not exist
- **THEN** the endpoint returns a not-found response

#### Scenario: Export is a read
- **WHEN** the export endpoint is invoked
- **THEN** it only reads stored session data and modifies nothing

### Requirement: Valid, faithful FIT output
The produced file SHALL be a valid FIT activity that a standard FIT decoder can parse without header or
CRC errors, decoding as a rowing activity with record and session messages. Values SHALL be mapped
faithfully: distance, speed (derived from pace), power, heart rate, and stroke cadence in FIT's scaled
units, and record timestamps SHALL be the session start time plus each sample's elapsed time, expressed
in the FIT epoch.

#### Scenario: Output decodes as a rowing activity
- **WHEN** the exported file is decoded by a standard FIT decoder
- **THEN** it parses with a valid header and CRC, and yields a rowing session with a stream of records

#### Scenario: Units and time are correct
- **WHEN** a record is decoded
- **THEN** its distance, speed, power, heart rate, and cadence match the session's values in FIT units
- **AND** its timestamp equals the session start plus that sample's elapsed time in the FIT epoch

#### Scenario: Sparse fields tolerated
- **WHEN** a session lacks a stream (e.g. no heart-rate belt was worn)
- **THEN** that record field is omitted and the file remains a valid activity
