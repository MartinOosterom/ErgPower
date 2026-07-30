## MODIFIED Requirements

### Requirement: Optional, pluggable LLM provider
The LLM coach SHALL be optional and provider-pluggable, realized through a single AI abstraction
(**Spring AI**) rather than bespoke per-provider HTTP clients. A provider SHALL be selected by
configuration (at least: a local **Ollama** provider and one or more cloud providers, with model and
endpoint/API-key settings). When no provider is configured, the coach SHALL be disabled and the
deterministic technique analysis SHALL be entirely unaffected. Credentials SHALL come from local
configuration, never the repository.

#### Scenario: Disabled by default
- **WHEN** no LLM provider is configured
- **THEN** the coaching feature is unavailable and the deterministic analysis still works fully

#### Scenario: Provider is swappable
- **WHEN** the configured provider is changed (e.g. Ollama → another provider)
- **THEN** coaching uses the new provider with no change to the analysis or the coaching logic

#### Scenario: Single AI abstraction
- **WHEN** the coach calls a model
- **THEN** it goes through the shared Spring AI client, not a hand-rolled per-provider HTTP client
