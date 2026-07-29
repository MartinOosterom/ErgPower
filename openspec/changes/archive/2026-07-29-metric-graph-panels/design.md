## Context

`StatTile` is already parameterized over a metric registry (`metrics.ts` → `METRICS`), but the graph
widget (`Trend`) reads a separate, hardcoded `TREND_METRICS` of just power/pace/hr — because the live
store only keeps `history{power,pace,hr}`. So "any value as a graph" is blocked by buffering, not by
meaning. This change unifies value and graph panels behind one registry.

## Goals / Non-Goals

**Goals:** any measured value showable as a value tile and (where meaningful) a value-vs-time graph;
one registry as the single source of truth; the palette availability- and mode-aware; open up
measurements the API already serves.

**Non-Goals:** value-vs-distance graphs (time-only for now); any API/backend/contract change; touching
the force-curve widget (already a graph); named profiles (that's the separate `dashboard-profiles`).

## Decisions

### D1: Hybrid — two render types, one registry (chosen)
Keep **two widget types** — a value tile and a value-vs-time graph — because their renders and natural
sizes differ (a 3×2 tile vs a 6×4 chart). But drive **both** from one registry so there's no per-metric
hardcoding, and make the palette a metric→mode picker so it doesn't explode into a flat list.
- Rejected: one "Metric" widget with a value⇄graph toggle (elegant, but one default size can't serve
  both well). Rejected: two fully independent widget types with a flat palette (grows unwieldy).

### D2: Registry entry declares display modes
Extend the metric descriptor with the modes it supports, e.g. `modes: { value: true, graph: true }`
(or an equivalent capability set). `value` marks it tile-able; `graph` marks it graph-able. Remove
`TREND_METRICS`; a graph metric is any registry entry with `graph`.

### D3: Store buffers history for every graphable metric
Generalize the store's history from the fixed `{power, pace, hr}` to a keyed map built from the
graphable metrics, bounded exactly as today (rolling cap). Cost is a few extra numeric arrays.

### D4: Palette = metric → display-mode picker
Two gates compose: **data availability** (the existing `requires[]` — is the API serving this?) and
**mode availability** (does the registry mark this metric graphable?). The palette offers each
measurement's available modes; it never offers `graph` for a value-only metric, nor anything whose data
isn't served.

### D5: x-axis is time (for now)
Graphs are value-vs-**time** (PM5 elapsed seconds). Value-vs-distance is a plausible future knob (nice
for pacing) but out of scope; the registry/graph should not hardcode assumptions that block adding it.

### D6: Curate non-meaningful graphs
Some values are `value`-only by curation: **elapsed time** (time-vs-time is a straight line),
**drag factor** (nearly constant). Expose value for all sensible measurements; expose graph only where
it teaches something. This is a registry authoring choice, revisitable per metric.

## Risks / Trade-offs

- **Palette clarity** as the metric list grows → the metric→mode picker (D4) and grouping keep it
  legible; don't regress to a flat list.
- **Re-render cost** of more live graphs → unchanged per-widget approach (selector subscriptions,
  ECharts `setOption`); graphs still only re-render on their metric's tick.
- **Registry drift** when the API grows (splits/summary) → new entries slot in with modes; no new
  component (D2).

## Migration Plan

Additive and frontend-only. Existing saved dashboards keep working: current `stat`/`trend` widgets map
onto the same metric keys. `TREND_METRICS` removal is internal. No data migration.

## Open Questions

- Exact `modes` shape: a `{value,graph}` object vs a `modes: DisplayMode[]` array — cosmetic; pick when
  implementing.
- Which of the newly-exposed measurements (drive force avg/peak, drive time, stroke distance, split
  pace/power, projected/remaining) are graph-worthy vs value-only — a per-metric curation pass.
- Whether the value and graph widgets keep their current type ids (`stat`, `trend`) or get clearer names
  (e.g. `value`, `graph`) — affects saved-dashboard compatibility.
