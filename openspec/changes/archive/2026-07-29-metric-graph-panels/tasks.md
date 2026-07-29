## 1. Metric registry

- [x] 1.1 Extend the metric descriptor with display modes (`value` / `graph`); make `metrics.ts` the
      single source of truth and remove `TREND_METRICS`
- [x] 1.2 Add the measurements the API already serves but the UI doesn't yet offer (drive peak/avg force,
      drive time, stroke distance, split pace/power, projected/remaining), each with sensible modes;
      curate value-only ones (elapsed, drag)

## 2. Shared store

- [x] 2.1 Generalize the live store's history from fixed `{power,pace,hr}` to a keyed map built from the
      graphable metrics; keep the bounded rolling cap

## 3. Widgets

- [x] 3.1 Make the graph widget (Trend) generic over any graphable registry metric (drop the hardcoded
      3-metric list); keep StatTile reading the same registry
- [x] 3.2 Value-vs-time on the x-axis (PM5 elapsed seconds); don't hardcode assumptions that would block
      a future value-vs-distance option

## 4. Palette

- [x] 4.1 Metric panels become a metric → display-mode picker; gate on data-availability AND mode
      (offer `graph` only for graphable metrics); leave force-curve/goal/status widgets as-is

## 5. Verify

- [x] 5.1 Any graphable metric can be added as a graph and updates live; a value-only metric offers no
      graph; existing saved dashboards still load
- [x] 5.2 `web/README` note; typecheck + build green (`npm run build`)
