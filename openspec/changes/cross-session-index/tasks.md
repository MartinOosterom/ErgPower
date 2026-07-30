## 1. Per-session analysis cache

- [x] 1.1 Persist the `TechniqueAnalyzer` output as a compact `analysis.json` in the session folder,
      stamped with an analyzer version; write it lazily on first access and at capture close
- [x] 1.2 On read, re-compute when the stamp is missing/stale; expose a "get cached analysis for id"
      accessor the coach/agent can reuse

## 2. Aggregate index

- [ ] 2.1 Build a per-session index row (startedAt, workout type/target, distance, duration, avg/peak
      power, key technique scores) over `SessionCatalog` + the cached analysis; persist as a rebuildable
      rollup and keep it current as sessions are added
- [ ] 2.2 A one-shot rebuild path (re-derive the whole index from session folders) with progress logging;
      never block startup

## 3. Query API

- [ ] 3.1 Filtered listing with scores: by workout type, target, distance band, and date range
- [ ] 3.2 Type-aware trends: a metric-over-time query — technique metrics across all sessions,
      performance metrics scoped within a workout type/target

## 4. Verify

- [ ] 4.1 Index a folder of sessions; confirm listing/filtering/trends are correct and that a rebuild
      reproduces the same index (re-derivable); stale-version re-compute works
- [ ] 4.2 Scale check: listing/trends stay fast with a large (hundreds) synthetic index; document the
      approach and any caps
- [ ] 4.3 README: the cross-session index, how it's cached/rebuilt, and the two comparison lenses
