## 1. Pluggable provider + config

- [x] 1.1 `ergpower.llm.*` config (provider `none|ollama|openai|anthropic`, model, base-url, api-key;
      secrets from the git-ignored local config; provider `none` by default)
- [x] 1.2 `LlmCoach` interface + provider implementations — **Ollama first** (local HTTP), then an
      OpenAI-compatible adapter (covers OpenAI / LM Studio / etc.) and Anthropic; a factory that returns
      the configured one (or "disabled")

## 2. Coaching endpoint

- [x] 2.1 `CoachService`: build a grounded prompt from the session's deterministic analysis (reuse
      `TechniqueAnalyzer`) + a Kleshnev rubric; instruct "comment only on the provided numbers"; call the
      configured provider
- [x] 2.2 `GET /sessions/{id}/coach` → `{model, text}`; 409/"not configured" when no provider; 404
      unknown session; off the event loop
- [x] 2.3 A "configured?" signal for the UI (`GET /integrations/llm` or a field), so the panel knows
      whether to show

## 3. Frontend

- [x] 3.1 An "AI Coach" panel on the analysis view, shown only when a provider is configured;
      generate-on-click; render the coaching text (and the model)

## 4. Verify

- [x] 4.1 End-to-end with a local **Ollama** running: coach a replayed session; confirm grounded output;
      the "not configured" and unknown-session paths behave; deterministic view unchanged when disabled
- [x] 4.2 `web`/main README: configuring a provider (Ollama-first), the privacy/trust note, and that the
      coach is optional
