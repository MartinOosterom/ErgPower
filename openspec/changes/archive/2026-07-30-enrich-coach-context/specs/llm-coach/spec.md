## MODIFIED Requirements

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
