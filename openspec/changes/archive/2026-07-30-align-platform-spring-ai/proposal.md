## Why

The coach's LLM layer is hand-rolled — a `LlmCoach` interface with one HTTP client per provider
(`OllamaCoach`, `OpenAiCoach`, `AnthropicCoach`) over the JDK `HttpClient`. That was fine for one-shot
completion, but the roadmap (multi-session coaching, an interactive tool-calling agent) needs
tool/function calling, conversation memory, and streaming across providers. Re-implementing those by
hand across three provider wire formats is a tarpit. **Spring AI** provides exactly this.

Happily, **Spring AI 2.0.0 GA targets Spring Boot 4.1.0** — the exact version this project already runs
(verified: the 2.0.0 starters depend on `spring-boot-starter-*` `4.1.0`). So there is **no platform
change**: we stay on Boot 4.1 (newest) and simply adopt Spring AI 2.0.0. What began as a "downgrade to
reach Spring AI" is, pleasantly, just "add Spring AI."

## What Changes

- **Add Spring AI 2.0.0 on the existing Spring Boot 4.1** (no Boot/Java change): the `spring-ai-bom`
  plus the Ollama / OpenAI / Anthropic model starters; rebuild and confirm all existing tests pass.
- **Adopt Spring AI as the sole AI abstraction.** Replace `LlmCoach` + the three hand-rolled providers
  + `LlmCoachFactory` with a Spring AI `ChatClient`. Providers (Ollama / OpenAI / Anthropic) are
  selected by Spring AI configuration; the coach calls `ChatClient` instead of hand-rolled HTTP.
- **Keep the coach's behavior and surface identical.** Same grounded, session-context prompt; same
  `GET /sessions/{id}/coach` and `GET /integrations/llm`; still disabled (and analysis untouched) when
  no provider is configured; secrets still from the git-ignored local config.

Out of scope: any new coaching capability (multi-session, agent, tools, streaming) — those land in the
changes that build on this foundation; any Spring Boot version change (none needed).

## Capabilities

### Modified Capabilities
- `llm-coach`: the optional, pluggable provider is now realized through **Spring AI** (provider selected
  by Spring AI config), replacing the hand-rolled HTTP providers — same optionality and behavior.

## Impact

- Build/platform: add the Spring AI `spring-ai-bom` (2.0.0) + model starters on the existing Boot 4.1;
  no Boot/Java/reactor/Jackson change.
- Code: delete `LlmCoach`, `OllamaCoach`, `OpenAiCoach`, `AnthropicCoach`, `LlmCoachFactory`; `CoachService`
  and `CoachController` use a Spring AI `ChatClient`; `LlmProperties`/config mapped onto `spring.ai.*`.
- No change to the deterministic analysis, storage, endpoints' contracts, or the web UI.
