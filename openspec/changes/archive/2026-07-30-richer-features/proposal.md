## Why

The analysis scores four shape metrics (catch gradient, peak position, finish plateau, mean/max) and
flags a few faults. Coaches read more than that. Three additions are high-value, grounded, and fully
computable from data we already store — and because the scorecard flows into the index, they light up
across trends, the coach, and the agent for free.

## What Changes

- **Per-quartile progression.** For the scored shape metrics, report the value in each quarter of the
  piece (Q1–Q4), so degradation is located ("the finish held for three quarters, then blew up") rather
  than reduced to a single first→last drift.
- **A smoothness score.** How jagged the drive is — a clean, continuous force application vs a bumpy one
  — beyond the single hump index. Scored (smoother is better) and a fault flag when it's poor.
- **Drive-to-recovery ratio (rhythm).** From the per-stroke drive/recovery timings, the rhythm of the
  stroke (a well-known ~1:2 target), which speaks to recovery discipline and rating efficiency.

All three become scorecard/feature entries, so they appear automatically in the cross-session index,
`/trends`, the coach, and the agent. The analyzer version bumps, so cached analyses recompute.

Out of scope: left/right asymmetry (a PM5 reports one combined curve — not measurable); an "ideal curve"
template (subjective — kept as a possible UI overlay, not a score); anything needing new capture.

## Capabilities

### Modified Capabilities
- `technique-analysis`: adds per-quartile progression of the shape metrics, a smoothness score (+ flag),
  and a drive-to-recovery rhythm metric to the deterministic analysis.

## Impact

- Backend: new features/scores in `TechniqueAnalyzer` (per-quartile, smoothness, drive:recovery), a
  bumped `ANALYZER_VERSION` (cache invalidation is automatic), and the new scores surface in the index,
  trends, coach, and agent with no changes there.
- Frontend: the new scorecard entries render in the existing scorecard grid; a per-quartile view is a
  natural (optional) addition to the analysis charts.
