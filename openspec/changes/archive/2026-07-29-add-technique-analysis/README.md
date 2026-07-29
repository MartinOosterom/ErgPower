# add-technique-analysis

Turn a stored session's force curves into a **deterministic** technique analysis (no LLM): per-stroke
shape features scored against published rowing-biomechanics targets (Kleshnev), a mean±band average
curve, feature-drift trends across the piece, a whole-session heatmap, and grounded fault flags —
computed in Java (`GET /sessions/{id}/analysis`) and shown on a dedicated session analysis view. A
pluggable LLM "coach" that narrates this is a separate later change.
