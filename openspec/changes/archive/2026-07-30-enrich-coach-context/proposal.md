## Why

The LLM coach today sees only the force-curve *technique* analysis (scorecard, feature stats, drift
trends, flags). That's enough to say **what** changed but not **why**: "peak force fell over the piece"
is a shrug on its own. The session already carries the context that turns that into a diagnosis —
"peak force fell 12% while your split held and rate crept up 3 spp and HR drifted into the 180s → you
fatigued and papered over it with rate." Correlating technique against pacing/effort is exactly what an
LLM is good at, and it's where individualised coaching comes from.

We already store this data (`summary.json`, `split-additional.ndjson`, drag in `status-general`); it's
just not handed to the coach. This change feeds a compact **session-context** block into the coach
prompt so its **technique** feedback is grounded in what actually happened during the piece.

**Technique stays the job.** The extra data is *interpretive context for the force curves*, not a pivot
to a pacing/physiology coach.

## What Changes

- **A "session context" block in the coach prompt**: workout type/target, total distance & time,
  average/peak power, average pace & stroke rate, **drag factor**, a compact per-split summary (pace,
  power, rate, and HR per split), and — when a belt was worn — average HR plus its drift across the
  piece. Distilled to a handful of interpretable rows, never raw per-sample series.
- **A rubric/system-prompt update** so the coach *uses* that context to interpret technique — relate
  force-curve changes to pacing, fatigue, and drag; read HR only as effort/drift; and weigh the workout
  type (a fade is fine in a 2k test, a flag in steady state) — while keeping the force-curve technique
  as the primary subject.
- **Graceful degradation**: HR, splits, and targets are optional; anything absent is simply omitted, and
  a single-split steady piece still coaches fine.

Out of scope: raw per-sample time series or raw force curves (LLMs reason badly over long arrays — kept
excluded); **age / rower profile** (not in the session and no profile store yet — a separate change if
we want HR-zone context); any change to capture, storage, or the deterministic analysis.

## Capabilities

### Modified Capabilities
- `llm-coach`: the grounded coaching now also consumes a distilled **session context** (workout,
  splits/pace/power/rate, drag, HR-when-present) alongside the technique analysis, so feedback explains
  *why* the curve behaved as it did — still grounded, still technique-first, still no raw curves.

## Impact

- Backend: assemble a compact session-context summary from already-stored session data
  (`summary.json` + `split-additional.ndjson` + `split.ndjson` + drag) and add it to the coach prompt;
  extend the system prompt/rubric to interpret it. No new capture/storage, no new endpoint.
- Privacy/grounding: unchanged — still no raw curves, still "comment only on the provided numbers";
  a cloud provider now also receives these numeric session metrics (Ollama keeps it local).
- Frontend: none (same `GET /sessions/{id}/coach`, richer text).
