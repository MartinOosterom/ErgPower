## Context

The hand-rolled `LlmCoach` layer can't reach tool-calling/memory/streaming without re-implementing each
provider's function-calling format. Spring AI gives that for free. Spring AI **2.0.0 GA targets Spring
Boot 4.1.0** (verified via its starter POMs) — the version already in use — so adoption needs no
platform change at all.

## Goals / Non-Goals

**Goals:** one AI abstraction (Spring AI) for everything that follows; a supported, GA foundation;
zero change to coaching behavior or the deterministic analysis; a clean removal of the hand-rolled code.

**Non-Goals:** new coaching features (multi-session, agent, tools, streaming); staying on Boot 4;
changing the coach prompt, endpoints, or grounding.

## Decisions

### D1: Adopt Spring AI 2.0.0 on the existing Boot 4.1 — no platform change
Spring AI 2.0.0 GA is built against Spring Boot 4.1.0 (its starters depend on `spring-boot-starter-*`
`4.1.0`), which is exactly what the project runs. So we add the `spring-ai-bom` + model starters and keep
Boot 4.1 / Java 21. Exit criterion: the full existing test suite stays green with the new dependencies.

### D2: Spring AI ChatClient replaces the hand-rolled providers
`CoachService` builds its prompt exactly as today and calls a Spring AI `ChatClient` (system + user
message) instead of `LlmCoach.complete`. `LlmCoach`, the three provider classes, and `LlmCoachFactory`
are deleted. Spring AI's provider auto-configuration (Ollama / OpenAI / Anthropic) does the transport.

### D3: Preserve the config + status surface
Keep "provider `none` disables everything" semantics. Map the existing `ergpower.llm.*` intent onto
Spring AI's config (`spring.ai.<provider>.*`), driven from the same git-ignored local file. Keep
`GET /integrations/llm` reporting configured/provider/model — now derived from the active Spring AI
model (or "not configured" when none). A thin gate replaces `LlmCoachFactory.configured()`.

### D4: No behavior change is the acceptance test
Coach the reference replay session before and after; the shape/grounding of the output must match
(still technique-first, still session-context aware, still no raw curves). The migration is invisible
to the API and UI.

## Risks / Trade-offs

- **New transitive dependencies** (Spring AI + its HTTP/webclient bits) → all pinned via the Spring AI
  BOM against Boot 4.1; the test suite is the safety net.
- **Spring AI 2.0 API surface** → confirm the `ChatClient`/config API from the resolved 2.0.0 jars before
  writing the migration, rather than assuming 1.x shapes.
- **Multiple provider starters on the classpath** → auto-config could create several `ChatModel` beans;
  select the active one by config (see D3) to avoid ambiguity.

## Migration Plan

Add Spring AI BOM + starters → build/test green (proves 2.0.0 on Boot 4.1) → swap `CoachService` to
`ChatClient` → delete hand-rolled providers → re-verify coach output. Each step keeps the app buildable.

## Open Questions

- Whether to keep the `ergpower.llm.*` keys as an alias over `spring.ai.*` for a stable local-config
  surface, or move fully to `spring.ai.*` — lean: thin alias so the user's local file keeps working.
- How to select a single active provider when multiple starters are present (config-driven bean pick).
