## 1. Spring AI dependencies (no Boot change)

- [x] 1.1 Add the `spring-ai-bom` (2.0.0) to dependencyManagement and the Ollama (+ OpenAI / Anthropic)
      model starters, keeping Spring Boot 4.1 / Java 21
- [x] 1.2 Rebuild and get the full existing suite green — this proves Spring AI 2.0.0 resolves and runs
      on Boot 4.1 (the foundation check); no dependency conflicts

## 2. Spring AI foundation

- [x] 2.1 Add the Spring AI starter for Ollama (+ OpenAI / Anthropic) and wire a `ChatClient`; prove one
      completion and one tool call succeed against the Ollama cloud model (the go signal for the roadmap)
- [x] 2.2 Map the `ergpower.llm.*` config onto Spring AI (`spring.ai.<provider>.*`) from the git-ignored
      local file; keep `provider=none` meaning "disabled"

## 3. Migrate the coach

- [x] 3.1 `CoachService` builds the same grounded, session-context prompt but calls the `ChatClient`
      (system + user) instead of `LlmCoach.complete`
- [x] 3.2 Delete `LlmCoach`, `OllamaCoach`, `OpenAiCoach`, `AnthropicCoach`, `LlmCoachFactory`; replace the
      `configured()` gate; keep `GET /integrations/llm` reporting configured/provider/model
- [x] 3.3 Update the coach tests to the Spring AI seam (mock the `ChatClient`/model); keep the
      not-configured, unknown-session, and grounding/context assertions

## 4. Verify

- [x] 4.1 Coach the reference replay session before/after — output stays technique-first, session-context
      aware, and grounded (no raw curves); the not-configured path still returns 409
- [x] 4.2 README/config note: the coach now runs on Spring AI (2.0.0, on Boot 4.1); provider config via
      `spring.ai.*` (still from the git-ignored local file)
