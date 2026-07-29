## Why

The dashboard already parameterizes a **StatTile** over a metric registry (`METRICS`, 9 values), but
**graphs are artificially limited to 3** — power, pace, heart rate — for a purely mechanical reason:

```
   metrics.ts today
   METRICS[9]        → any of these can be a value tile
   TREND_METRICS[3]  → power, pace, hr ONLY
                       "// only those the store keeps a rolling history buffer for"
```

A graph of pace, stroke rate, distance, drive force, etc. over time is just as meaningful as a tile of
it — the limit is history buffering, not sense. This change makes the intent general: **for every
measured value there can be a value tile, and (where meaningful) a value-vs-time graph**, both driven by
**one registry** that declares each measurement's display modes. It also opens up measurements the API
already provides but the dashboard doesn't yet offer (drive peak/avg force, drive time, stroke distance,
split pace/power, projected/remaining).

## What Changes

- **One metric registry as the single source of truth.** Each entry declares label, unit, how to read
  the current value, and which **display modes** are meaningful: `value` (a tile) and/or `graph` (a
  value-vs-time chart). `TREND_METRICS` is removed — a graph metric is simply any metric marked
  graphable.
- **The shared live store buffers rolling history for every graphable metric** (today it buffers 3),
  bounded as now, so a graph can be added for any of them.
- **The graph widget (Trend) becomes generic** over the registry — any graphable metric, not a fixed
  list. StatTile already is generic; it reads the same registry.
- **The palette becomes a metric → display-mode picker** (per the "hybrid" decision): two render types
  (value tile, graph) both fed by the registry, but the palette offers each measurement's available
  modes rather than a flat, ever-growing list. It offers `graph` only for graphable metrics, and still
  disables anything whose *data* the API doesn't provide.
- **Curation:** non-meaningful graphs are not offered (e.g. elapsed time vs time; drag factor is nearly
  constant) — those metrics are `value`-only in the registry.

Out of scope: value-vs-**distance** graphs (time is the only x-axis for now — a noted future knob); any
API/backend change (all data is already served); the force-curve widget (already its own graph).

## Capabilities

### Modified Capabilities
- `web-viewer`: adds a metric registry with per-metric display modes; generalizes value/graph panels to
  any graphable measurement; the palette becomes availability- **and** mode-aware (a metric→mode picker).

## Impact

- **Frontend only** (`web/`). No API, no backend, no contract change.
- `metrics.ts` (registry gains modes + more measurements), `liveStore.ts` (history generalized from
  `{power,pace,hr}` to a keyed map), `Trend.tsx` (generic over the registry), and the palette. Small,
  contained.
- Memory: a few extra bounded history arrays — negligible.
- Unlocks graphs for ~all metrics and surfaces measurements the API already sends but the UI didn't
  offer.
