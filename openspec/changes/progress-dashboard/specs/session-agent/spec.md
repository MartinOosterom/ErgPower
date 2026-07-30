## MODIFIED Requirements

### Requirement: Tool access to session data, single and cross-session
The agent SHALL reach the stored data through read-only tools rather than a fixed prompt: a session's
overview, deterministic analysis, metrics over a time window, per-stroke data, and a specific stroke's
force curve; plus cross-session listing/filtering and comparison via the cross-session index. Tools SHALL be
confined to the session store (no access outside it). The agent's reach SHALL be **scoped by context**: on a
single session's Analysis view it SHALL have only that session's tools and cannot reach other sessions; on
the Progress dashboard it SHALL additionally have the cross-session tools, focused on the **selected set** of
sessions.

#### Scenario: Session-scoped on the analysis view
- **WHEN** the agent is used on a single session's Analysis view
- **THEN** it answers about that session using its session tools and does not reach other sessions

#### Scenario: Set-scoped on the progress dashboard
- **WHEN** the agent is used on the Progress dashboard with a selected set of sessions
- **THEN** it uses the cross-session tools focused on that selected set to answer comparative questions

#### Scenario: Tools are read-only and confined
- **WHEN** a tool runs
- **THEN** it only reads data within the session store and cannot mutate anything or escape it
