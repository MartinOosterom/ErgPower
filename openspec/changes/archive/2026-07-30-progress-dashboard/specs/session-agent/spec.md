## MODIFIED Requirements

### Requirement: Tool access to session data, single and cross-session
The agent SHALL reach the stored data through read-only tools rather than a fixed prompt: a session's
overview, deterministic analysis, metrics over a time window, per-stroke data, and a specific stroke's
force curve; plus cross-session listing/filtering and comparison via the cross-session index. Tools SHALL be
confined to the session store (no access outside it). The agent's reach SHALL be **scoped by context**: on a
single session's Analysis view it SHALL have only that session's tools and cannot reach other sessions; on
the Progress dashboard it SHALL additionally have the cross-session tools, focused on the **selected set** of
sessions.

#### Scenario: Pulls the data a question needs
- **WHEN** a question concerns a time window, a single stroke, or the analysis
- **THEN** the agent calls the corresponding tool and answers from its result

#### Scenario: Session-scoped on the analysis view
- **WHEN** the agent is used on a single session's Analysis view
- **THEN** it answers about that session using its session tools and does not reach other sessions

#### Scenario: Roams across sessions when asked
- **WHEN** a comparative or historical question is asked on the Progress dashboard with a selected set
- **THEN** the agent uses the cross-session listing/compare tools, focused on that selected set

#### Scenario: Tools are read-only and confined
- **WHEN** a tool runs
- **THEN** it only reads data within the session store and cannot mutate anything or escape it
