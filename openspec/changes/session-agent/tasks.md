## 1. Tools over the data

- [ ] 1.1 Session tools reusing existing readers: `overview(id)`, `analysis(id)`, `metrics(id,from,to)`,
      `strokes(id,from,to)`, `forceCurve(id, stroke|time)` — read-only, confined to the session store
      (validate id/paths), with size caps/windowing on large ranges
- [ ] 1.2 Cross-session tools over the index: `listSessions(filter)` and `compareSessions(ids, metric)`
      (type-aware), so the agent can roam and compare

## 2. Agent

- [ ] 2.1 A Spring AI `ChatClient` with the tools + a grounded system prompt (answer from the session
      tools; web is background only; state when inferring); cap tool iterations per turn
- [ ] 2.2 `POST /sessions/{id}/chat` taking the client-held transcript and streaming the answer over SSE,
      including lightweight tool-step status events; anchored to `{id}`, roams via tools
- [ ] 2.3 Gate the agent on a tool-capable provider being configured (reuse the LLM status signal)

## 3. Frontend

- [ ] 3.1 A chat panel on the analysis dashboard, below the coach and above the graphs: transcript,
      input, streamed tokens, and visible tool steps; holds the conversation client-side
- [ ] 3.2 Show the panel only when the agent is available; the analysis view is complete without it

## 4. Optional web tool (may be deferred)

- [ ] 4.1 A `webSearch(query)` tool for general/background knowledge, enabled only when a search provider
      is configured; session tools remain the source of truth

## 5. Verify

- [ ] 5.1 Multi-turn Q&A on a session: time-window, single-stroke, and analysis questions answered from
      tools; confirm groundedness (no invented data) and streaming + tool steps
- [ ] 5.2 Cross-session: a comparative question ("vs my last 2k") drives `listSessions`/`compareSessions`
      and answers correctly; single-session chat is unaffected
- [ ] 5.3 Read-only/safety: tools cannot escape the session store; nothing is persisted; the coach and
      deterministic analysis are unaffected when the agent is disabled
- [ ] 5.4 README: the agent, its tools, the privacy/trust note, and that it's optional
