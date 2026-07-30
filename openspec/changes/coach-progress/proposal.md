## Why

The coach describes a single session well, but the most motivating coaching is longitudinal: "your
catch improved from 12% to 7.8% over your last five 2ks; the finish is still hanging long." The coach
can't say that today because it only sees one session. With the cross-session index in place, it can —
by grounding a compact **history** in the same normalized technique scores, compared like-for-like.

## What Changes

- **An optional progress/history mode for the coach.** In addition to the single-session coaching, the
  coach can consume a distilled history from the `cross-session-analysis` index — recent **same-type**
  sessions' technique scores plus the trend — and narrate progress: what improved, what plateaued, what
  regressed, relative to the athlete's own past.
- **Type-aware, like-for-like.** History is drawn using the two-lens rule: technique-shape trends span
  the log; performance context stays within the workout type/target. The coach compares comparable work.
- **Single-session stays the default.** Progress is a mode, selected by the caller/UI; with no history
  (a first session, or nothing comparable) the coach degrades to single-session coaching.

Out of scope: the interactive agent (separate change); the index itself (its own change); a dedicated
progress dashboard (the coach's narrative is the first surface; a trends screen can follow).

## Capabilities

### Modified Capabilities
- `llm-coach`: coaching can operate over **multiple sessions** — an optional progress mode that grounds
  a same-type history from the cross-session index to narrate improvement over time, still grounded and
  technique-first, still degrading to single-session when no history exists.

## Impact

- Backend: the coach optionally assembles a history block from the `cross-session-analysis` index
  (recent same-type scores + trend) and adds it to the prompt; the rubric learns to narrate progress
  (improved/plateaued/regressed vs. the athlete's own baseline) without inventing beyond the numbers.
- API: the coaching endpoint gains a way to request progress vs. single-session (e.g. a mode/param);
  single-session remains the default and unchanged.
- Frontend: the AI-coach panel gains a **This session / Progress** toggle; Progress appears only when
  comparable history exists.
