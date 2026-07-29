## Why

ErgPower faithfully stores something no other tool keeps: the **force curve of every stroke** plus
per-stroke drive metrics. Today that data is live-only (a curve flashes past and is gone) or buried in
FIT developer fields nothing reads. Yet it's exactly the data a coach uses to improve technique — and,
crucially, its interpretation is **published sports science**, not guesswork: Valery Kleshnev / BioRow
define measurable shape parameters with normative target windows.

This change turns a stored session's curves into a **deterministic, self-contained technique analysis**
— per-stroke features scored against those targets, aggregate shape + consistency, drift across the
piece, and fault flags — with **no LLM and no network**. It's fully useful on its own; a pluggable LLM
"coach" that *narrates* this analysis is a deliberately separate later change (`add-llm-coach`), so the
core never depends on a model.

## What Changes

- **A feature-extraction engine (Java)** turns each stored force curve into shape features — peak force,
  **peak position** (front-load), **catch gradient** (leg connection), **finish plateau**, impulse/work,
  mean/max ratio, **hump index** (sequence breaks), drive length — and aggregates them across the session
  (mean, variance/consistency) and per split.
- **A grounded scorecard**: aggregate features scored against Kleshnev target windows (catch ≤17% of
  drive, peak ≤40%, finish plateau 28–40%, …) → pass / how-far-off per metric.
- **Many-strokes-legible views**: a **mean ± spread band** average curve, **feature-drift trends** over
  the piece, and a **whole-session heatmap** (every stroke as a column) — so a 10k's ~1000 curves stay
  readable without scrolling them.
- **Deterministic fault flags**: double-hump/disconnection, late peak, soft catch, collapsing finish,
  inconsistency, fatigue drift — each from a grounded rule.
- **A read-only endpoint** `GET /api/v1/sessions/{id}/analysis` returning the analysis as JSON
  (structured so a future LLM layer can consume it unchanged).
- **A dedicated session analysis view** (its own page, reached by opening a stored session) rendering the
  scorecard, mean±band curve, feature trends, heatmap, and flags — no LLM required.

Out of scope (later/other changes): the **pluggable LLM coach** (`add-llm-coach`); functional clustering
into stroke "types"; individualized best-stroke target curves; cross-session progress tracking;
real-time/live technique feedback.

## Capabilities

### New Capabilities
- `technique-analysis`: deterministic force-curve/session technique analysis (feature extraction,
  Kleshnev-grounded scorecard, aggregate/consistency, drift trends, heatmap, fault flags) exposed via a
  read-only endpoint and a dedicated analysis view.

## Impact

- Backend: a feature-extraction module + `GET /sessions/{id}/analysis` (reads the stored
  `force-curve.ndjson` + splits, reusing the recombination the FIT exporter does). A read, so
  `live-api`'s read-only stance is untouched.
- Contract: new `/sessions/{id}/analysis` path + analysis schemas in `api/openapi.yaml`.
- Frontend: a new analysis view (ECharts, reusing the app shell + session picker).
- The analysis JSON is deliberately structured features → it doubles as the input to the later LLM
  coach, so that change adds a voice, not a rewrite.
- Grounding is cited, quantitative, and offline — every claim traces to a published target.
