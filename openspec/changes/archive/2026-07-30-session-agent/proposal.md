## Why

The coach narrates; it can't converse. A rower will want to *ask* — "how did my catch hold up in the
second half?", "which stroke had the worst force disconnection?", "how does this compare to last week's
2k?" — and get an answer grounded in the actual data, across follow-ups. That's an agent: a multi-turn
chat with **tools** over the stored sessions, so the model pulls exactly the data a question needs (a
time window, a single stroke's curve, another session) instead of being handed a fixed summary.

Built on the Spring AI foundation, tools make multi-session nearly free: the model roams sessions by
calling tools with different ids. It defaults to the session you're viewing and reaches others when a
question calls for it.

## What Changes

- **An interactive chat agent** on the analysis dashboard: multi-turn Q&A about a session, powered by a
  Spring AI `ChatClient` with tool calling. Optional — present only when a tool-capable provider is
  configured; the deterministic analysis and the coach are unaffected without it.
- **Tools over the stored data**, reusing existing readers and the cross-session index:
  `overview(id)`, `analysis(id)`, `metrics(id, from, to)`, `strokes(id, from, to)`,
  `forceCurve(id, stroke|time)`, `listSessions(filter)`, `compareSessions(ids, metric)` — so it answers
  by time, interval, stroke, or across sessions. Because each pull is targeted, tools may return raw
  slices (a single stroke's curve) that the one-shot coach could never send.
- **Single- and multi-session.** Anchored to the open session by default; roams other sessions via the
  listing/compare tools when a question is comparative or historical.
- **Grounded, streaming, stateless.** Answers stream token-by-token with visible tool steps ("looking at
  your splits…"); the agent answers from the session tools and says when it's inferring; the transcript
  is client-held so no server-side conversation state is persisted (`live-api` read-only stays intact).
- **Optional web tool** (may be deferred): a `webSearch(query)` tool for general/background knowledge,
  enabled only when a search provider is configured; session tools remain the source of truth.

Out of scope: persisting/sharing conversations; auth/multi-user; training-plan generation; letting the
agent write or mutate anything.

## Capabilities

### New Capabilities
- `session-agent`: an optional, Spring AI powered chat agent that answers questions about a rowing
  session — and, via tools, across sessions — grounded in the stored data, streaming, and read-only.

## Impact

- Backend: a Spring AI `ChatClient` configured with `@Tool`s that wrap `CoachContext`, `TechniqueAnalyzer`
  /the cached analysis, the NDJSON slice readers, and the `cross-session-analysis` index; a chat endpoint
  (`POST /sessions/{id}/chat`) streaming responses (SSE) with the client-supplied transcript.
- Frontend: a chat panel on the analysis dashboard, below the coach and above the graphs; streams tokens
  and shows tool steps; holds the transcript.
- Security/trust: tools are read-only and confined to the session store (no path traversal); a
  multi-turn chat may stream more session data to a cloud provider than the coach — noted in the trust
  model; Ollama keeps it local; the web tool (if enabled) sends only its query out.
