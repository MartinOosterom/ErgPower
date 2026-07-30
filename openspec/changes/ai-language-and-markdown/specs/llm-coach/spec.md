## ADDED Requirements

### Requirement: Configurable response language
The coach SHALL produce its coaching in a language chosen by configuration (a natural-language name; when
unset, English). Only the narration SHALL be translated — the metric names and numeric values SHALL be
kept as given so the coaching stays grounded and citable. The coach's prose format SHALL be unchanged.

#### Scenario: Coach in the configured language
- **WHEN** a response language is configured and coaching is generated
- **THEN** the prose is in that language while the metric names and numbers are unchanged

#### Scenario: Default English
- **WHEN** no response language is configured
- **THEN** the coaching is in English, as before
