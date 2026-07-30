## Context

`SessionCatalog.list()` gives cheap per-session summaries but no technique scores; those need
`TechniqueAnalyzer`, which is too expensive to run per-question across hundreds of sessions. We need the
scores cached and a fast, filterable index — the backbone for multi-session coaching and the agent.

## Goals / Non-Goals

**Goals:** cache per-session technique scores; a fast, filterable cross-session index at hundreds-of-
sessions scale; type-aware trends; everything re-derivable from stored data.

**Non-Goals:** any LLM use; profile/HR-zone or new features (future columns); a database (files suffice);
a full progress UI.

## Decisions

### D1: Cache per session, index in aggregate
Two tiers: (1) a per-session `analysis.json` (the `TechniqueAnalyzer` output) written lazily on first
access and at capture close; (2) a small aggregate index row per session (summary + key scores) for
listing/filtering without opening every analysis. The aggregate is rebuildable from the per-session
files, so it's a cache, not a source of truth.

### D2: Version-stamp for invalidation
`analysis.json` carries an analyzer version. On read, a stale/missing version triggers re-compute. This
keeps cached scores correct as the analysis evolves (ties to change D's richer features).

### D3: Two comparison lenses, because the log is heterogeneous
The user rows varied distances and fixed times. **Technique** metrics are normalized shape (% of the
drive) and compare honestly across any pieces → trends span the whole log. **Performance** metrics
(power, pace) do not → trends and comparisons are scoped **within a workout type/target**. The index
stores type + target so both lenses are queryable.

### D4: Files, not a database
Session data is already file-per-folder; the index is a derived rollup file (rebuild on demand / on
change). No new infrastructure, consistent with the app's self-contained ethos. Revisit only if scale
demands it.

### D5: Additive and re-derivable
No captured data changes. Deleting the cache/index and rebuilding from session folders yields the same
result — safe to regenerate anytime.

## Risks / Trade-offs

- **Cache staleness** → version stamp + rebuild-from-source; the aggregate index is always regenerable.
- **First-run cost** (analysing a large existing backlog) → analyse lazily and/or offer a one-shot
  rebuild; log progress; don't block startup.
- **Comparing apples/oranges** → D3's type-aware scoping is a first-class part of the query API, not an
  afterthought.
- **Index write concurrency** → single-user, local; keep writes simple/atomic (temp-then-rename).

## Open Questions

- Aggregate index as one rollup file vs. computed-on-read from per-session `analysis.json` (fast enough
  at hundreds?) — start with a rollup file, keep it rebuildable.
- Which technique scores are "key" enough for the aggregate row vs. living only in `analysis.json`.
