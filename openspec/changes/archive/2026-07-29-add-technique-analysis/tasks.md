## 1. Contract

- [x] 1.1 Add `GET /sessions/{id}/analysis` to `api/openapi.yaml` + schemas (`SessionAnalysis`:
      scorecard, mean±band curve, per-feature aggregates, drift trends, heatmap, flags); 404 unknown,
      a "no curve data" state; regenerate interfaces

## 2. Backend feature engine

- [x] 2.1 Per-stroke feature extraction from `force-curve.ndjson` (resample to a normalized 0..1 grid):
      peak force, peak position, catch gradient, finish plateau, impulse/work, mean/max ratio, hump
      index, drive length
- [x] 2.2 Session aggregates: mean±sd curve (fixed grid), per-feature average + consistency (CV),
      per-split; feature-drift trends across strokes; whole-session heatmap grid
- [x] 2.3 Kleshnev-grounded scorecard (target windows as documented constants) + deterministic fault
      flags (disconnection/late-peak/soft-catch/collapsing-finish/inconsistency/fatigue-drift)
- [x] 2.4 `SessionAnalysisController` implementing/serving `GET /sessions/{id}/analysis` off the event
      loop; 404 unknown; graceful "no curve data"

## 3. Frontend analysis view

- [x] 3.1 An analysis route reached by opening a session (from the session list); fetch the analysis
- [x] 3.2 Panels (ECharts): scorecard (value vs target, pass/deviation), mean±band curve, feature-drift
      trends, whole-session heatmap, fault-flag list — rendering fully with no LLM

## 4. Verify

- [x] 4.1 Feature-math test on the reference-fixture session (assert plausible peak position / catch
      gradient / hump index and a populated scorecard/flags); contract test for `/analysis`
- [x] 4.2 Runtime: analyse a replayed session end-to-end; `web`/main README note (deterministic; LLM
      coach is a later change)
