## Context

The coach is one-shot and can't fetch data itself, so multi-session coaching means CODE selects and
distills the right history into the prompt. The `cross-session-analysis` index makes that cheap; the
normalized technique scores make the comparison honest across varied distances/times.

## Goals / Non-Goals

**Goals:** optional progress coaching grounded in the athlete's own same-type history; like-for-like
comparison; graceful fallback to single-session; still grounded and technique-first.

**Non-Goals:** the interactive agent; building the index; a trends UI; comparing across incomparable
workout types.

## Decisions

### D1: History is selected by code, not the model
Because the coach is one-shot, it can't "go look." Given the anchor session, code queries the index for
recent **same-type** sessions and distils their key technique scores + the trend into a compact history
block. The model narrates; it never fetches.

### D2: Same-type default, two-lens honesty
Default history = the most recent N sessions of the same workout type/target as the anchor. Technique
trends may span the log; performance context stays within type (reuses the index's two-lens rule). This
avoids "your 2k catch vs your 10k catch" noise.

### D3: A mode, defaulting to single-session
The endpoint gains a mode (single | progress); single is the default and byte-for-byte the current
behavior. Progress adds the history block and a progress-aware rubric line (improved / plateaued /
regressed vs. the athlete's own baseline).

### D4: Graceful degradation
First session, or no comparable history → no history block, and the coach silently falls back to
single-session coaching. The Progress toggle is offered only when comparable history exists.

## Risks / Trade-offs

- **Over-claiming progress from noise** → require a minimum number of comparable sessions before
  asserting a trend; otherwise coach the session and note "not enough history yet."
- **Prompt growth** → the history is distilled scores/trend, not full analyses (a handful of rows).
- **Grounding** → same rule as the coach: comment only on the provided (now cross-session) numbers.

## Open Questions

- N (how many history sessions) and the minimum for a trend claim — pick sane defaults, expose later.
- Whether "Progress" also compares against a personal best, not just recent trend — likely yes, cheap
  from the index; keep for a follow-up if it bloats v1.
