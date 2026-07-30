# rower-profile Specification

## Purpose
TBD - created by archiving change rower-profile. Update Purpose after archive.
## Requirements
### Requirement: Optional single-athlete profile
The system SHALL support an optional single-athlete profile from configuration — body weight, age,
optional sex, heart-rate max/resting, and a free-text goal. All fields SHALL be optional; unset fields
simply omit the values they would derive. The profile SHALL come from local configuration.

#### Scenario: Unset profile changes nothing
- **WHEN** no athlete profile is configured
- **THEN** the coach, agent, and trends behave exactly as before

### Requirement: Profile-derived physiology
When the profile provides the inputs, the system SHALL derive **watts/kg** (a session's average power ÷
body weight) and **heart-rate zones** (Z1–Z5 as a fraction of HR max, taken from configuration or
estimated as `220 − age`). watts/kg SHALL be computed from the current profile rather than cached per
session. Heart-rate zone context SHALL be produced only when heart rate was recorded and an HR max is
available.

#### Scenario: watts/kg in coaching context
- **WHEN** a body weight is configured and a session has an average power
- **THEN** the coach and agent context includes that session's watts/kg

#### Scenario: HR reported as a zone
- **WHEN** heart rate was recorded and an HR max is known
- **THEN** the recorded HR is reported as a zone (Z1–Z5), not just a raw number

### Requirement: Goal-aware framing without changing targets
When a goal is configured, the coach and agent SHALL frame their advice to that goal. The technique
targets and the grounded numbers SHALL be unchanged by the profile.

#### Scenario: Advice framed to the goal
- **WHEN** a goal is configured (e.g. a 2k test in six weeks)
- **THEN** the coaching is framed to that goal while the technique targets and numbers are unchanged

