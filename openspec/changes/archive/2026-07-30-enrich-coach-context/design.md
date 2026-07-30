## Context

`add-llm-coach` grounds coaching in `TechniqueAnalyzer`'s force-curve analysis and deliberately sends
*features, not curves*. That keeps it grounded but context-blind: it can't tell a 2k test from a 10k
UT2, or relate a force fade to pacing/fatigue. The session already holds that context; this change
distils it into the prompt without loosening the grounding or shifting the coach off technique.

## Goals / Non-Goals

**Goals:** feed a compact, interpretable session context (workout, splits, pace/power/rate, drag, HR
when present) into the coach so its technique feedback is individualised and causal; keep technique the
primary subject; degrade gracefully when data is absent; preserve grounding.

**Non-Goals:** raw per-sample series or raw curves; a rower profile / age / HR-zones; turning the coach
into a pacing/training/physiology advisor; any change to capture, storage, the deterministic analysis,
or the endpoint/UI.

## Decisions

### D1: Distil, don't dump
Send a handful of interpretable numbers, not time series. Per-split rows (pace/power/rate/HR) plus
whole-piece aggregates; if a piece has many splits/intervals, cap the rows and summarise the rest (or
fall back to per-quartile) with a note. LLMs reason well over ~5–15 labelled numbers and badly over
long arrays — so this is about *meaning*, not volume.

### D2: Technique stays primary; context is interpretive
The system prompt keeps the force-curve technique as the subject and frames the new data as *why*
context: relate curve/feature changes to pacing, fatigue, and drag. The coach should not become a
pacing plan or training-load advisor.

### D3: Graceful degradation
HR (needs a belt), split detail, and workout targets are all optional. Missing data is simply omitted;
a single-split steady piece still coaches. No section is fabricated when its data is absent.

### D4: Grounding unchanged
Still no raw curves; still "comment only on the provided numbers." HR is interpreted only as
effort/drift (no medical inference). **Age is excluded** — it isn't in the session and there's no
profile store; without max-HR/zones it adds little to force-curve technique. Revisit with a profile
change if we want HR-zone context.

### D5: Reuse stored data, add nothing to capture
Build the context from what's already written: `summary.json` (distance, duration, avg/peak power,
strokes), `split.ndjson` (per-split time/distance/type), `split-additional.ndjson` (per-split
pace/power/spm/hr/speed), and drag from `status-general`. A small builder assembles it; `CoachService`
appends it to the prompt. No new events, files, endpoints, or config.

## Risks / Trade-offs

- **Scope creep toward pacing coaching** → D2 rubric framing keeps technique primary; context is support.
- **Over-reading HR without zones** → interpret only as relative effort/drift; never diagnose.
- **Token growth / dilution** → D1 caps rows; the context is still an order of magnitude smaller than a
  raw-curve dump.
- **Provider sees more numbers** → still only distilled metrics (no raw session/curve data); Ollama keeps
  everything local; documented in the existing trust note.

## Open Questions

- Split cap vs. per-quartile fallback for very long interval sessions — pick a simple cap first.
- Whether to include calories / projected finish — likely low value for technique; omit in v1.
