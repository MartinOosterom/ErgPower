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
force curve; plus cross-session listing/filtering and comparison via the cross-session index. Tools SHALL
be confined to the session store (no access outside it). The agent SHALL be anchored to the session being
viewed and reach other sessions only when a question requires it.

#### Scenario: Pulls the data a question needs
- **WHEN** a question concerns a time window, a single stroke, or the analysis
- **THEN** the agent calls the corresponding tool and answers from its result

#### Scenario: Roams across sessions when asked
- **WHEN** a question is comparative or historical (e.g. "versus my last 2k")
- **THEN** the agent uses the listing/compare tools to reach the relevant other sessions

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

