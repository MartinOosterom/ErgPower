# technique-analysis Specification

## Purpose

Provide deterministic, self-contained technique analysis of stored rowing sessions from their force curves — with no LLM or external model involved. The capability computes per-stroke shape features and their session aggregates, scores the aggregate curve against Kleshnev-grounded biomechanics target windows (a documented scorecard), and produces representations that stay legible for pieces of hundreds or thousands of strokes: a mean drive-force curve with a spread band, per-feature drift trends across the piece, and a whole-session heatmap. It surfaces common faults as deterministic, grounded flags, and exposes everything both through a read-only analysis endpoint and a dedicated session analysis view that renders fully without any AI configured.

## Requirements

### Requirement: Deterministic session analysis
The system SHALL compute a technique analysis of a stored session from its force curves **without any
external model or network call**, and expose it via a read-only endpoint
(`GET /api/v1/sessions/{id}/analysis`). The analysis SHALL include per-stroke shape features and their
session aggregates (average and consistency). Requesting an unknown session SHALL return not-found; a
session without force-curve data SHALL return a clear "no curve data" state rather than failing.

#### Scenario: Analyse a stored session
- **WHEN** a client requests the analysis of a stored session that has force curves
- **THEN** it receives, computed entirely locally, per-stroke shape features and their aggregates

#### Scenario: No curve data
- **WHEN** the session has no force-curve data
- **THEN** the endpoint reports that curve analysis is unavailable, without error

#### Scenario: Unknown session
- **WHEN** the session id does not exist
- **THEN** the endpoint returns not-found

### Requirement: Biomechanically grounded scorecard
The analysis SHALL score the session's aggregate curve shape against published rowing-biomechanics target
windows (the Kleshnev framework) — at least catch gradient, peak-force position, and finish plateau —
reporting each metric's value and whether it falls inside its target (and by how much it deviates). The
target windows SHALL be documented with their source.

#### Scenario: Scored against targets
- **WHEN** the analysis is computed
- **THEN** each scored metric carries its measured value, its target window, and a pass/deviation result

#### Scenario: A fault is quantified, not guessed
- **WHEN** a metric falls outside its target (e.g. a late peak-force position)
- **THEN** the result states the value, the target it missed, and the deviation — traceable to the
  published framework, not a subjective judgement

### Requirement: Legible aggregates for many strokes
Because a long piece has hundreds or thousands of strokes, the analysis SHALL provide aggregate
representations that stay readable at that scale: a mean drive-force curve with a spread band, feature
trends across the piece (how the shape drifts as the piece progresses), and a whole-session heatmap
(every stroke represented) — so insight does not require inspecting individual curves.

#### Scenario: Thousand-stroke piece stays readable
- **WHEN** a session with many hundreds of strokes is analysed
- **THEN** the analysis yields a mean±spread curve, per-feature drift trends, and a heatmap that
  summarise all strokes without requiring them to be viewed one by one

#### Scenario: Drift across the piece is visible
- **WHEN** a feature changes over the course of the piece (e.g. peak position moving later with fatigue)
- **THEN** the analysis exposes that as a trend across the strokes

### Requirement: Deterministic fault flags
The analysis SHALL flag common technique faults using deterministic, grounded rules — including at least
a disconnected drive (double-hump), a late peak, a soft catch, a collapsing finish, inconsistency, and
fatigue drift. Each flag SHALL carry a code, a severity, how many strokes it affects (where applicable),
and a plain-language description, with no model involved.

#### Scenario: Disconnection flagged
- **WHEN** many strokes show more than one prominent force peak
- **THEN** the analysis flags a disconnected drive with the count of affected strokes

### Requirement: Session analysis view
The app SHALL provide a dedicated analysis view, reached by opening a stored session, that presents the
scorecard, the mean±band curve, the feature trends, the heatmap, and the fault flags. The view SHALL
render fully **without any LLM configured**.

#### Scenario: Open a session's analysis
- **WHEN** the user opens a stored session's analysis
- **THEN** they see the scorecard, mean±band curve, feature-drift trends, heatmap, and fault flags

#### Scenario: Works with no model configured
- **WHEN** no LLM is configured
- **THEN** the full deterministic analysis still renders (only AI narration, added by a later change, is absent)
