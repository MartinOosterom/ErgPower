## ADDED Requirements

### Requirement: Progress dashboard with session selection
The viewer SHALL provide a **Progress dashboard**, distinct from the single-session Analysis view and
reachable from a top-level navigation switch. It SHALL let the user **select a set of sessions** — filtering
the cross-session index by workout type, distance band, and date range, and choosing specific sessions — and
SHALL present, for the selected set, cross-session trend charts, progress coaching, and a set-scoped chat
agent. The per-session **Analysis view SHALL remain single-session**: its coach covers only that session and
its agent cannot reach other sessions.

#### Scenario: Look across a chosen set of sessions
- **WHEN** the user opens the Progress dashboard and selects a set of sessions
- **THEN** trends, progress coaching, and the chat are scoped to that selected set

#### Scenario: Analysis view stays single-session
- **WHEN** the user opens a single session's Analysis view
- **THEN** it is about that one session only — no progress toggle and no cross-session chat
