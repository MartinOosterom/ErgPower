## ADDED Requirements

### Requirement: Multi-session progress coaching
The coach SHALL support an optional progress mode that coaches over multiple sessions in addition to the
single-session default. In progress mode it SHALL ground a compact history from the cross-session index —
recent same-type sessions' technique scores and their trend — and narrate improvement, plateau, or
regression relative to the athlete's own baseline, staying technique-first and commenting only on the
provided numbers. Comparison SHALL be like-for-like: technique-shape trends MAY span the whole log, while
performance context SHALL stay within a workout type/target. When no comparable history exists, the coach
SHALL fall back to single-session coaching.

#### Scenario: Narrate progress over same-type history
- **WHEN** progress coaching is requested for a session that has recent same-type sessions
- **THEN** the coaching references how the athlete's technique scores changed over those sessions,
  grounded in the cross-session index, without inventing beyond the provided numbers

#### Scenario: Single session stays the default
- **WHEN** coaching is requested without progress mode
- **THEN** the coach produces the single-session coaching unchanged

#### Scenario: Graceful without history
- **WHEN** progress is requested for a first or otherwise incomparable session
- **THEN** the coach falls back to single-session coaching rather than asserting a trend
