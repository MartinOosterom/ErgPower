## Context

`technique-analysis` produces a structured, deterministic analysis (features, Kleshnev scorecard, drift
trends, fault flags) via `TechniqueAnalyzer` and `GET /sessions/{id}/analysis`. This change adds an
optional LLM that *narrates* that structured output — it is a pure add-on: the core never depends on it.

## Goals / Non-Goals

**Goals:** optional, pluggable LLM coaching grounded in the deterministic analysis; Ollama-first
(local/private) with swappable providers; disabled unless configured; the deterministic experience
unchanged without it.

**Non-Goals:** fine-tuning; RAG corpus (a curated rubric suffices); caching/persisting coaching;
multimodal image input; real-time coaching; any change to the deterministic analysis.

## Decisions

### D1: Optional and pluggable — provider behind an interface
`LlmCoach { coach(analysis, rubric) → text }` with implementations: `OllamaCoach` (local HTTP, e.g.
`/api/generate`), `OpenAiCoach`, `AnthropicCoach` (and any OpenAI-compatible base-url). Config selects
one: `ergpower.llm.provider = none|ollama|openai|anthropic`, plus `model`, `base-url`, `api-key`.
`none` (default) → the coach is disabled everywhere. Secrets come from the git-ignored local config.

### D2: Grounded — the LLM sees the analysis, never raw curves
The prompt is built from the session's deterministic analysis (scorecard values + targets + pass, fault
flags, feature averages/consistency, drift trends) plus a Kleshnev rubric, with an instruction to
**comment only on the provided numbers** (priority, narrative, drills). This keeps it grounded and
cheap, and means the same input could drive any provider identically.

### D3: A read-only coaching endpoint
`GET /api/v1/sessions/{id}/coach` → `{ model, text }`. Reuses `TechniqueAnalyzer` to get the analysis,
then calls the configured provider. Returns a clear "not configured" (409) when no provider is set, and
404 for an unknown session. No persistence in v1 → `live-api` read-only is untouched (caching is a
future option that would add a marker + broaden the rule).

### D4: Ollama-first for privacy
Ollama runs locally, so with the default nothing leaves the machine — matching the self-contained ethos.
Cloud providers receive only the *numeric* analysis (not raw session/curve data) and only when the user
opts in by configuring one; documented in the trust model.

### D5: Optional AI panel
The analysis view gains an "AI Coach" panel that appears **only when a provider is configured** (via a
small status signal, e.g. `GET /integrations/llm` or a field on the analysis), with generate-on-click.
The deterministic view is otherwise unchanged and complete.

### D6: Provider-agnostic prompt/rubric
The coaching prompt + Kleshnev rubric are shared; each provider adapter only does the completion
(request/response shape). Swapping providers changes no coaching logic.

## Risks / Trade-offs

- **Hallucinated coaching** → grounded prompt + "only comment on the provided numbers"; the deterministic
  scorecard remains the source of truth the user can check against.
- **Over-prescribing one ideal** → the biomechanics research stresses individualization; the rubric
  should coach relative to the rower's own numbers, not a rigid template.
- **Latency/cost** → per-session, generate-on-demand (not per-stroke); local Ollama has no API cost.
- **Provider variance** → keep expectations to text coaching; no reliance on tool-use/JSON-mode in v1.

## Migration Plan

Additive and optional. No change to the deterministic analysis, storage, or existing endpoints. With
`provider=none` (default) the app behaves exactly as before this change.

## Open Questions

- Endpoint verb: `GET` (compute-on-demand) vs `POST` (if we later cache) — start GET.
- Where the "configured?" signal lives: a dedicated `GET /integrations/llm` vs a boolean on the analysis.
- OpenAI-compatible base-url support to cover local runners (LM Studio, etc.) with one adapter.
- Prompt/rubric wording — the biggest quality lever; iterate against real sessions + Ollama.
