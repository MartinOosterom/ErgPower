## ADDED Requirements

### Requirement: Optional, pluggable LLM provider
The LLM coach SHALL be optional and provider-pluggable. A provider SHALL be selected by configuration
(at least: a local **Ollama** provider and one or more cloud providers, with model and endpoint/API-key
settings). When no provider is configured, the coach SHALL be disabled and the deterministic technique
analysis SHALL be entirely unaffected. Credentials SHALL come from local configuration, never the
repository.

#### Scenario: Disabled by default
- **WHEN** no LLM provider is configured
- **THEN** the coaching feature is unavailable and the deterministic analysis still works fully

#### Scenario: Provider is swappable
- **WHEN** the configured provider is changed (e.g. Ollama → another provider)
- **THEN** coaching uses the new provider with no change to the analysis or the coaching logic

### Requirement: Grounded coaching from the analysis
When a provider is configured, the coach SHALL produce natural-language rowing-technique coaching for a
stored session by consuming that session's **deterministic analysis** (features, Kleshnev scorecard,
fault flags, drift trends) plus a biomechanics rubric — not the raw force curves — and SHALL be
instructed to comment only on the provided numbers. The coaching SHALL prioritise the most important
issue and MAY suggest drills.

#### Scenario: Grounded in the numbers
- **WHEN** coaching is generated for a session
- **THEN** it is derived from that session's analysis features/scores/flags and a rubric, prioritising
  the key issue, without inventing observations beyond the provided data

#### Scenario: The coach never sees raw curves
- **WHEN** the coaching request is built
- **THEN** only the structured analysis (not raw curve samples) is sent to the provider

### Requirement: Coaching endpoint
The system SHALL expose `GET /api/v1/sessions/{id}/coach` returning the coaching text and the model used.
It SHALL return a clear "not configured" response when no provider is set, and not-found for an unknown
session.

#### Scenario: Coach a session
- **WHEN** a provider is configured and the endpoint is called for a stored session
- **THEN** it returns coaching text and the model that produced it

#### Scenario: Not configured
- **WHEN** no provider is configured and the endpoint is called
- **THEN** it responds that the coach is not configured, rather than erroring obscurely

### Requirement: Optional AI panel
The analysis view SHALL present an "AI coach" panel **only when a provider is configured**, generating
coaching on demand. The deterministic analysis view SHALL remain complete and usable when it is absent.

#### Scenario: Panel appears only when configured
- **WHEN** the user opens a session's analysis with a provider configured
- **THEN** an AI coach panel is available; with no provider configured, the panel is absent and the rest
  of the analysis is unchanged
