## ADDED Requirements

### Requirement: Markdown rendering of agent chat
The chat panels (single-session and set-scoped) SHALL render the agent's answers as Markdown, using a
renderer that does not inject raw HTML (safe by default). The coach panel SHALL remain plain prose.

#### Scenario: Agent answers render as Markdown
- **WHEN** the agent returns a Markdown answer in the chat
- **THEN** it is rendered with its headings, tables, and emphasis rather than shown as raw text

#### Scenario: Coach stays prose
- **WHEN** the coach returns its coaching
- **THEN** it is shown as plain prose, unchanged
