## Context

`TechniqueAnalyzer` extracts per-stroke shape features, aggregates them, scores against Kleshnev targets,
and flags faults. The cross-session index stores whatever is in the scorecard, so new scores propagate to
trends/coach/agent automatically. This change adds three grounded metrics from the data we already have.

## Goals / Non-Goals

**Goals:** locate *where* technique drifts (per-quartile); score drive **smoothness**; add stroke
**rhythm** (drive:recovery). Grounded, deterministic, and free-flowing into everything downstream.

**Non-Goals:** asymmetry (not measurable from one PM5 curve); an "ideal-curve" template score (subjective);
any new capture or storage.

## Decisions

### D1: Per-quartile progression
Split the analysed strokes into four quarters and report each scored shape metric's mean per quarter
(Q1–Q4). This locates degradation ("finish fine through Q3, collapsed in Q4") — more actionable than the
existing first→last trend, which stays. Rendered as a small per-metric progression; also summarised into
the prompt so the coach can cite it.

### D2: Smoothness score
Beyond the single hump index, score how jagged the mean drive curve is — e.g. normalised total curvature
(sum of |second difference|) relative to the peak. Lower is smoother. Scored against a sensible target
and flagged when poor. Grounded: a continuous, connected force application is a standard coaching aim.

### D3: Drive-to-recovery ratio (rhythm)
From the per-stroke `driveTimeS` / `recoveryTimeS`, compute the average drive:recovery ratio (a
well-known ~1:2 rowing target). A recovery rushed toward 1:1 is a fault; scored + flagged. Uses stroke
data already stored (no curves needed), so it works even when a session's ratio matters more than shape.

### D4: Version bump = automatic invalidation
Increment `ANALYZER_VERSION`; the per-session `analysis.json` cache and the aggregate index recompute on
next read (already built). No migration.

### D5: Free propagation
New entries go in the scorecard/features, which the index already copies into its rows, so `/trends`,
the coach's context, and the agent's `analysis` tool pick them up with no changes there.

## Risks / Trade-offs

- **Targets for new metrics** → smoothness/rhythm targets are less canonical than catch/finish; pick
  defensible ranges and label them clearly (flag as guidance, not gospel).
- **Recompute cost on version bump** → the index rebuilds lazily from the (fast) per-session cache; large
  logs recompute once, with progress logging (already present).
- **Per-quartile needs enough strokes** → for very short pieces, fall back to fewer buckets or omit.

## Open Questions

- Smoothness definition — total curvature vs a spectral measure; start with normalised curvature.
- Whether per-quartile becomes its own chart in the analysis view now, or just feeds the prompt first.
