## Context

Everything cross-session already exists (index, `/trends`, progress coaching, agent cross-session tools).
The gap is UX: two mental modes share one screen, and the comparison set is auto-chosen, not user-chosen.
This change separates the two modes into two dashboards and puts the athlete in control of the set.

## Goals / Non-Goals

**Goals:** a focused single-session Analysis view; a dedicated Progress dashboard with user-selected
sessions, trends, progress coaching, and a set-scoped agent; reuse the existing cross-session backend.

**Non-Goals:** new analysis/index/trend math; server-side persistence of the selection; the web-search
tool; multi-user/auth.

## Decisions

### D1: Two dashboards behind a top-level switch
A **Sessions | Progress** switch at the app root. *Sessions* is today's flow (source select → live/analysis
for one session). *Progress* is the new cross-session home. Simple, discoverable, and keeps each screen's
purpose obvious.

### D2: Filter-to-narrow, then multi-select
The Progress screen lists sessions from `/sessions/index` with quick filters (workout type, distance band,
date range) to narrow, and checkboxes to pick the exact set. Shortcuts like "recent same-type" pre-tick a
sensible default; the user adjusts. The selection is client-side state.

### D3: Progress coaching over the selected set
Progress coaching takes the chosen ids: the most recent is the "current" piece and the rest are the
history/baseline; the two-lens rule still applies (technique spans; performance within type). This extends
the coach beyond the automatic recent-same-type selection — same rubric, explicit set.

### D4: Agent scope follows the screen
- **Analysis view:** the agent gets only the *session tools* (overview/analysis/metrics/strokes/forceCurve)
  for the one session — no cross-session tools, so it can't wander.
- **Progress dashboard:** the agent gets the cross-session tools too, but scoped to the **selected set**
  (its system prompt names the chosen sessions; listing/compare focus on them).
This makes each screen's agent behave exactly as the user expects.

### D5: Reuse, don't rebuild
Trend charts consume `/sessions/index` + `/trends`; the session picker consumes `/sessions/index`; the
coach and agent reuse their existing logic with an explicit-set input. The only backend work is accepting
that set.

### D6: Trim the Analysis coach + agent
Remove the *Progress* toggle from the analysis coach (single-session there); scope its agent down. Progress
lives solely on the new dashboard.

## Risks / Trade-offs

- **Two ways to reach cross-session** (old auto-history vs new explicit set) → keep the auto default as the
  pre-tick; the explicit set is an override, so behavior stays predictable.
- **Selection UX at hundreds of sessions** → filters first, sensible default pre-selection, and a cap on how
  many can be compared at once.
- **Endpoint shape for a set** → likely a set-taking coach/chat request (ids in the body/params); decided in
  tasks. Keep the single-session endpoints unchanged.

## Open Questions

- Endpoint for set-scoped coaching/chat: extend the existing `/sessions/{id}/…` with a `sessions=` param, or
  add set-level endpoints (`POST /coach/progress`, `POST /chat`)? Lean: set-level endpoints, cleaner.
- Reaching Progress: only the top-level switch, or also a "compare this session" shortcut from Analysis?
- A cap on the comparison set size (tokens/latency) — pick a sensible default.
