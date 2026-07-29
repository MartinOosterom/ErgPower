# enrich-fit-export

Fill the exported .FIT with as much of the session as maps cleanly to FIT: per-lap
power/HR/cadence/speed/calories/strokes, session totals (calories, strokes), record cycle length
(stroke distance), device/firmware provenance, and — via FIT **developer fields** — drag factor and
per-stroke drive force/time/length/recovery + stroke count. (Force curves are intentionally left out —
they don't map to FIT and consumers ignore them.)
