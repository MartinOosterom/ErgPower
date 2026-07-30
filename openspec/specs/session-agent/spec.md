# session-agent Specification

## Purpose
TBD - created by archiving change session-agent. Update Purpose after archive.
## Requirements
### Requirement: Optional interactive session agent
The system SHALL provide an optional chat agent that answers questions about a rowing session over
multiple turns, powered through the shared Spring AI client with tool calling. It SHALL be available only
when a tool-capable provider is configured; when it is absent, the deterministic analysis and the coach
SHALL be entirely unaffected.

#### Scenario: Available only when configured
- **WHEN** no tool-capable provider is configured
- **THEN** the agent is unavailable and the analysis and coach still work fully

#### Scenario: Multi-turn conversation
- **WHEN** the user asks a follow-up question in the same conversation
- **THEN** the agent answers in the context of the prior turns

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

### Requirement: Grounded, streaming, read-only answers
Agent answers SHALL be grounded in the tool results, stream to the client as they are produced with
visible tool-step status, and state when the agent is inferring beyond the data. Any web/background tool
SHALL be background-only, with the session tools remaining the source of truth. The conversation SHALL be
client-held and not persisted server-side, keeping the live API read-only.

#### Scenario: Grounded and transparent
- **WHEN** the agent answers
- **THEN** the answer derives from the tool results, streams with visible tool steps, and does not invent
  data beyond what the tools returned (and any web content is used only as general background)

#### Scenario: Nothing persisted
- **WHEN** a conversation happens
- **THEN** no server-side conversation state is stored and no session data is modified

### Requirement: Configurable language and Markdown answers
The agent SHALL answer in the configured response language (a natural-language name; English when unset),
translating only its narration while keeping metric names and numeric values as given. The agent SHALL
format its answers as **Markdown** so the client can render them.

#### Scenario: Answer in the configured language
- **WHEN** a response language is configured and the agent answers a question
- **THEN** the prose is in that language while the metric names and numbers are unchanged

#### Scenario: Markdown-formatted answers
- **WHEN** the agent answers
- **THEN** the answer is valid Markdown (e.g. headings, tables, emphasis) suitable for rendering

