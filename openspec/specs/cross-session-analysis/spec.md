# cross-session-analysis Specification

## Purpose
TBD - created by archiving change cross-session-index. Update Purpose after archive.
## Requirements
### Requirement: Cached per-session analysis
The system SHALL cache each session's deterministic technique analysis so it is computed at most once
per analyzer version. The cache SHALL be stored alongside the session, carry the analyzer version, and
be re-computed automatically when missing or stale. The cache SHALL be re-derivable from the stored
session data (deleting it and re-reading yields the same result).

#### Scenario: Compute once, reuse
- **WHEN** a session's analysis is requested more than once at the same analyzer version
- **THEN** it is computed once and served from the cache thereafter

#### Scenario: Invalidate on analyzer change
- **WHEN** the analyzer version changes
- **THEN** the cached analysis is recomputed rather than served stale

### Requirement: Cross-session index
The system SHALL maintain a queryable index of all sessions carrying, per session, its start time,
workout type/target, distance, duration, average/peak power, and key technique scores. The index SHALL
be rebuildable from the session folders and SHALL support listing hundreds of sessions without
re-running the analysis per query.

#### Scenario: Fast filtered listing
- **WHEN** sessions are listed or filtered (by workout type, target, distance band, or date range)
- **THEN** results include the technique scores and are served from the index without re-analysing

#### Scenario: Rebuildable
- **WHEN** the index is deleted and rebuilt from the session folders
- **THEN** it reproduces the same per-session rows

### Requirement: Type-aware trends over time
The system SHALL provide a metric-over-time trend across sessions. Technique-shape metrics (normalized
as a percentage of the drive) SHALL be trendable across all sessions; performance metrics (power, pace)
SHALL be trended within a single workout type/target, so heterogeneous pieces are not compared directly.

#### Scenario: Technique trend spans the log
- **WHEN** a technique-shape metric (e.g. catch gradient) is trended over time
- **THEN** it includes sessions of any distance or duration

#### Scenario: Performance trend stays within type
- **WHEN** a performance metric (e.g. average power) is trended over time
- **THEN** it is scoped to a single workout type/target rather than mixing incomparable pieces

