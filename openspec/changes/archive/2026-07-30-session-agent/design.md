## Context

The coach is a one-shot narration; an agent is a multi-turn, tool-calling conversation. On the Spring AI
foundation (tool calling, streaming, memory) and the cross-session index, the agent mostly wires
existing readers as tools and runs the loop. It anchors to the open session and roams via tools.

## Goals / Non-Goals

**Goals:** grounded, multi-turn Q&A about a session and across sessions; the model pulls exactly the
data it needs via tools; streaming with visible tool steps; optional and read-only; the deterministic
analysis and coach unaffected when it's absent.

**Non-Goals:** persisting/sharing chats; auth/multi-user; any write/mutation; training-plan generation;
replacing the coach (they coexist).

## Decisions

### D1: Spring AI ChatClient + tools; no hand-rolled loop
The agent loop (think → call tool → observe → answer) is Spring AI's; we supply `@Tool`s and a system
prompt. This depends on `align-platform-spring-ai` (the shared AI layer + tool-calling).

### D2: Tools reuse existing readers; targeted raw is fine
`overview(id)`←CoachContext, `analysis(id)`←cached analysis, `metrics(id,from,to)` / `strokes(id,from,to)`
/ `forceCurve(id,stroke|time)`←NDJSON readers, `listSessions(filter)` / `compareSessions(ids,metric)`←
cross-session index. Because each call is targeted, a tool may return a raw slice (one stroke's curve) —
the opposite of the coach's pre-distilled prompt, and exactly why tools are the right shape.

### D3: Anchor + roam
The chat is anchored to the open session (tools default that `id`); comparative/historical questions let
the model call `listSessions`/`compareSessions`/`analysis(otherId)`. Single- and multi-session are the
same chat — the model decides — so the UI stays "you're looking at one session."

### D4: Stateless, client-held transcript
No server-side conversation store; the client sends the transcript each turn. Keeps `live-api` read-only,
needs no auth, and avoids persistence. (Spring AI ChatMemory MAY back an ephemeral in-request window, but
nothing is persisted.)

### D5: Streaming with visible tool steps
Responses stream over SSE (the app is already SSE-heavy). Surface tool use as lightweight status
("looking at your splits…") for trust and perceived speed. Cap tool iterations per question.

### D6: Grounded + safe + optional
System prompt: answer from the session tools; use the web only for general background; state when
inferring. Tools are read-only and confined to the session store (no path traversal). The agent appears
only when a tool-capable provider is configured; the web tool only when a search provider is.

## Risks / Trade-offs

- **Cost/latency** (N model calls per question) → interactive is fine; cap iterations; stream for
  perceived speed.
- **Privacy** → a multi-turn chat can stream more session data to a cloud provider than the coach; Ollama
  keeps it local; documented. The web tool sends only its query out.
- **Hallucination / wrong web facts** → grounding prompt + "answer from tools; web is background only";
  prefer citing which tool/data an answer used.
- **Tool safety** → validate the `id`/paths; read-only; never escape the session store.

## Open Questions

- Web tool in v1 vs. fast-follow (search-provider dependency + key) — lean fast-follow; ship session
  tools first.
- Whether the coach becomes the chat's implicit first turn, or stays a separate panel — keep separate.
- Tool-result size caps (e.g. a full-session `metrics` dump) — window/summarise large ranges.
