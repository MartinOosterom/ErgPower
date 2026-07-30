## Why

Every insight the app produces today is about **one session in isolation**. The obvious next value —
"is my catch improving?", "how does this row compare to my recent 2ks?", progress over time — needs to
reason across the whole log. `SessionCatalog` already lists per-session *summaries* (date, distance,
power) cheaply, but not the *technique* scores (catch gradient, peak position, finish plateau, flags),
which today only exist by running `TechniqueAnalyzer` on demand. At **hundreds of sessions**, re-running
analysis per question doesn't scale.

This change builds the missing backbone: a cached, queryable **cross-session index** of per-session
technique scores plus type-aware trends. It's deterministic and useful on its own (a progress view), and
it's the substrate both the multi-session coach and the chat agent will build on.

## What Changes

- **Cache per-session analysis.** Compute `TechniqueAnalyzer` once and persist a compact `analysis.json`
  in the session folder (lazily on first access, and/or at capture close), stamped with an analyzer
  version so it re-computes when the analysis logic changes.
- **A lightweight session index** mapping `id → {startedAt, workout type/target, distance, duration,
  avg/peak power, key technique scores}`, rebuildable from the session folders, for fast filtered
  listing at scale (no re-analysis to list).
- **Type-aware querying.** Filter/list by workout type, target, distance band, and date range; and a
  **trends** query returning a metric over time — **technique** metrics span all sessions (normalized
  shape), **performance** metrics (power/pace) grouped **within a workout type**.

Out of scope: any LLM use (that's the coach/agent changes); a rower profile (change B); new force-curve
features (change D) — both simply become new columns in this index later; a full progress-dashboard UI
(a minimal one may follow; the index is what makes it possible).

## Capabilities

### New Capabilities
- `cross-session-analysis`: a cached, scalable index of per-session technique scores and summaries, with
  type-aware filtering and trends-over-time, derived from stored sessions and rebuildable on demand.

## Impact

- Backend: a per-session `analysis.json` cache (versioned), an index builder over `SessionCatalog` +
  cached scores, and query endpoints (filtered listing with scores; metric-over-time trends).
- Storage: additive — a derived `analysis.json` per session and/or a rollup index file; no change to
  captured data, and everything is re-derivable from the raw session folders.
- Consumers: `llm-coach` (progress mode) and the future agent read this index instead of re-analysing.
- Performance: cross-session queries become O(index) instead of O(sessions × analysis).
