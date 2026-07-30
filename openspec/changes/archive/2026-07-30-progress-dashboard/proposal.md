## Why

The per-session analysis view now carries three things: single-session coaching, *progress* coaching, and
a chat agent. That mixes two different mental modes — **"analyze this piece"** and **"how is my rowing
going over time?"** — on one screen. They deserve separate homes: the analysis view should stay focused on
one session, and a dedicated **Progress dashboard** should own the cross-session story — with the user
**choosing which sessions** to look at.

Happily the backend already exists: the cross-session index (`/sessions/index`, `/trends`), progress
coaching, and the agent's cross-session tools all shipped. This change is mostly a **frontend
restructuring** plus letting the coach and agent take an **explicit set of session ids**.

## What Changes

- **Trim the Analysis view to a single session.** Remove the coach's *This session / Progress* toggle here
  (coach = this session only), and scope the agent to **this session only** (drop its cross-session tools on
  this screen), so the per-session view behaves exactly as expected.
- **A new Progress dashboard** reached from a top-level **Sessions | Progress** switch. It presents a
  **filterable, multi-select session list** (pick "my recent 2ks", or tick specific sessions), cross-session
  **trend charts** (reusing `/sessions/index` + `/trends`), **progress coaching over the selected set**, and
  a **set-scoped chat agent** ("which of these had my best finish?", "am I improving?").
- **Coach and agent accept an explicit selection.** Progress coaching grounds the *chosen* sessions (not
  only the auto "recent same-type"); the agent on the Progress dashboard is scoped to the selected set.

Out of scope: changing the deterministic analysis, index, or trend math (reused as-is); persisting the
selection server-side (it's a client-side choice for now); the web-search tool (still deferred).

## Capabilities

### Modified Capabilities
- `web-viewer`: adds a **Progress dashboard** with session selection and cross-session trends, and keeps the
  **Analysis view single-session**.
- `llm-coach`: progress coaching MAY be grounded in an **explicitly selected set** of sessions, in addition
  to the automatic recent-same-type history.
- `session-agent`: the agent's reach is **scoped by context** — session-only on the analysis view, and
  scoped to the **selected set** on the progress dashboard.

## Impact

- Frontend: a top-level Sessions/Progress switch; a Progress screen (session picker + trend charts + a
  progress-coach panel + a set-scoped chat); the Analysis view trimmed to single session.
- Backend: let progress coaching and the chat agent take an explicit set of session ids (a small extension
  to the coach and chat endpoints); the session-view agent uses only the session tools.
- Reuse: the cross-session index, `/trends`, progress coaching, and the agent tools all already exist — no
  new analysis or storage.
