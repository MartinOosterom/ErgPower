## Context

A stored session keeps every stroke's force curve (`force-curve.ndjson`: per stroke a `forcesN[]`
array) plus per-stroke drive metrics and splits. This change extracts technique features from those
curves and serves them for a dedicated analysis view. The interpretation is grounded in Valery
Kleshnev / BioRow's rowing biomechanics, which defines shape parameters with normative target windows —
so scoring is deterministic and citable, not subjective.

## Goals / Non-Goals

**Goals:** a self-contained (no LLM, no network) technique analysis of a stored session — grounded
per-stroke features + scorecard, aggregate shape + consistency, drift across the piece, a whole-session
heatmap, and fault flags; a read-only endpoint; a dedicated view; JSON structured so a later LLM layer
consumes it unchanged.

**Non-Goals:** the LLM coach (separate `add-llm-coach`); functional clustering; individualized
best-stroke targets; cross-session progress; live/real-time feedback.

## Decisions

### D1: Feature extraction in the backend (Java)
Compute features server-side so they feed the UI now and the LLM coach later (and could enrich exports).
Expose `GET /api/v1/sessions/{id}/analysis` → the analysis JSON. Reads `force-curve.ndjson` + `split*`
(reusing the FIT/exporter recombination). A read → no `live-api` change.

### D2: Grounded in Kleshnev normative windows (the source of "meaning")
The scorecard scores aggregate features against published targets, e.g. **catch gradient** (drive % to
reach 70% Fmax) ≤17%; **peak position** ≤40% of drive (late >55% = fault); **finish plateau** (width
above 70% Fmax) 28–40%; plus **mean/max ratio** (work efficiency). Targets are constants in code with a
source citation. (Refs: Kleshnev, *Biomechanics of Rowing* Table 9.2; BioRow; row2k; British Rowing.)

### D3: The per-stroke feature set + algorithms
From each `forcesN[]` curve (resampled to a common normalized drive position 0..1):
- **peak force** = max; **peak position** = argmax / (n−1)
- **catch gradient** = position where force first crosses 70% of peak, as a fraction of the drive
- **finish plateau** = fraction of the drive with force ≥70% of peak after the peak
- **impulse/work** = area under the curve; **mean/max ratio** = mean/peak
- **hump index** = count of prominent local maxima (prominence threshold to ignore noise)
- **drive length** = sample count (∝ drive time)

### D4: Keep many strokes legible (the 1000-curve problem)
- **Mean ± band**: resample every curve to a fixed grid (e.g. 50 points), average + standard deviation.
- **Feature-drift trends**: each feature as a per-stroke (optionally smoothed) series over the piece.
- **Heatmap**: a stroke × drive-position grid of force (each stroke a column) — the whole row in one image.
- **Consistency**: coefficient of variation per feature → a single consistency score.

### D5: Deterministic fault flags (grounded rules)
E.g. share of strokes with `humpIndex>1` above a threshold → *disconnection*; aggregate peak position
>55% → *late peak*; catch gradient >17% → *soft catch*; narrow finish plateau → *collapsing finish*;
high feature CV → *inconsistency*; significant drift slope in peak position/catch → *fatigue drift*.
Each flag carries a code, severity, count, and a plain message (no model).

### D6: A dedicated analysis view (not the widget dashboard)
The analysis is opinionated and specialized, so it's its own page reached by **opening a session** (from
the session list), not the configurable live dashboard. Reuses the app shell + ECharts. Panels:
scorecard, mean±band curve, feature trends, heatmap, flags. Renders fully without any LLM.

### D7: Structured for the future LLM layer
The analysis JSON is the exact structured input the later `add-llm-coach` will feed to a (pluggable)
model — so that change adds narration over these numbers, not a re-computation.

## Risks / Trade-offs

- **Curve variability / normalization** — lengths and scaling differ; resample to a common grid; work in
  % of peak so absolute force differences don't distort shape metrics.
- **Hump/peak detection sensitivity** — prominence thresholds to avoid false faults; make them tunable.
- **Sessions without force curves** (force-curve capture disabled) — the endpoint returns what it can
  (metrics-only) or a clear "no curve data" state; the view degrades gracefully.
- **Erg vs on-water calibration** — Kleshnev targets are drive-angle based; on an erg we approximate the
  drive by sample position. Note the caveat; treat targets as guidance, show the raw numbers too.

## Open Questions

- Exact target windows + which Kleshnev metrics ship in v1 (start with catch/peak/finish/mean-max).
- Heatmap: server-computed grid vs client-rendered from per-stroke curves in the payload.
- Scorecard granularity: whole-session only, or also per split.
- Very short pieces / few strokes — minimum stroke count before trends/flags are meaningful.
