## 1. Session context

- [x] 1.1 Assemble a compact **session-context** summary from already-stored session data: workout
      type/target, total distance & time, avg/peak power, avg pace & stroke rate, and **drag factor**
      (from `summary.json` + `status-general`)
- [x] 1.2 Add a distilled **per-split** table — pace, power, stroke rate, HR per split (from
      `split.ndjson` + `split-additional.ndjson`); cap the row count for long interval sessions and note
      any summarisation
- [x] 1.3 Add **HR context when a belt was worn** — average HR and its drift across the piece; omit
      cleanly when HR is absent (also omit split/target sections when their data is missing)

## 2. Prompt + rubric

- [x] 2.1 Append the session-context block to the coach prompt (still no raw curves / no per-sample series)
- [x] 2.2 Update the system prompt/rubric so the coach **uses context to interpret technique** — relate
      force-curve/feature changes to pacing, fatigue, and drag; read HR only as effort/drift; weigh the
      workout type — while keeping the force-curve technique as the primary subject and grounding intact

## 3. Verify

- [x] 3.1 Coach a multi-split replayed session with a provider configured; confirm the feedback ties
      technique to pacing/drag/HR (explains *why*), stays technique-first, and invents nothing beyond the
      provided numbers
- [x] 3.2 Confirm graceful degradation: a single-split piece and a no-HR session still coach; the
      deterministic analysis and the "not configured" path are unaffected
- [x] 3.3 Update the README AI-coach note to mention the session context the coach now uses
