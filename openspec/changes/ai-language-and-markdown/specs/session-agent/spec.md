## ADDED Requirements

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
