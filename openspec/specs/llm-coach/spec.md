# llm-coach Specification

## Purpose
TBD - created by archiving change add-llm-coach. Update Purpose after archive.
## Requirements
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

### Requirement: Grounded coaching from the analysis
When a provider is configured, the coach SHALL produce natural-language rowing-technique coaching for a
stored session by consuming that session's **deterministic analysis** (features, Kleshnev scorecard,
fault flags, drift trends) plus a biomechanics rubric — not the raw force curves. It SHALL additionally
consume a distilled **session context** drawn from the stored session — workout type/target, total
distance and time, average/peak power, average pace and stroke rate, drag factor, a compact per-split
summary (pace, power, stroke rate, and heart rate per split), and — when heart rate was recorded —
average HR and its drift across the piece — provided as a handful of interpretable values, never raw
per-sample series. The coach SHALL be instructed to comment only on the provided numbers, to keep the
force-curve **technique** as the primary subject, and to use the session context to interpret that
technique (relating force-curve and feature changes to pacing, fatigue, and drag; reading HR only as
effort/drift; weighing the workout type). The coaching SHALL prioritise the most important issue and MAY
suggest drills. Session-context elements that are absent for a session (e.g. no HR belt, a single split,
no target) SHALL simply be omitted.

#### Scenario: Grounded in the numbers
- **WHEN** coaching is generated for a session
- **THEN** it is derived from that session's analysis features/scores/flags, its distilled session
  context, and a rubric, prioritising the key issue, without inventing observations beyond the provided
  data

#### Scenario: The coach never sees raw curves
- **WHEN** the coaching request is built
- **THEN** only the structured analysis and distilled session context (not raw curve samples or raw
  per-sample time series) are sent to the provider

#### Scenario: Technique stays the focus, context explains it
- **WHEN** the session context shows a pacing, drag, or heart-rate pattern
- **THEN** the coach uses it to explain the force-curve technique (why it changed), rather than becoming
  a pacing or training-load advisor

#### Scenario: Missing context degrades gracefully
- **WHEN** a session has no heart-rate data, a single split, or no workout target
- **THEN** those context elements are omitted and coaching is still produced from what is available

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

