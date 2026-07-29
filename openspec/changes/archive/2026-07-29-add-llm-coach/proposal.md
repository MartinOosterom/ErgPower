## Why

The `technique-analysis` change ships a complete deterministic analysis — Kleshnev-scored features,
mean±band, drift trends, heatmap, fault flags — with **no model**. What formulas can't do is *translate*
that into coaching: prioritize the one thing to fix, narrate how the piece drifted, suggest a drill,
compare to your own history. That's the natural (and only) job for an LLM here.

This change adds that as an **optional, pluggable** layer that mirrors the two-tier architecture: the
deterministic core stays untouched and fully usable; when — and only when — an LLM provider is
configured, an "AI coach" narrates the analysis. It consumes the deterministic **analysis JSON** (the
numbers, scores, and flags), never raw curves, so it stays **grounded** and can't invent biomechanics.

## What Changes

- **A pluggable LLM abstraction** (`LlmCoach`) with providers — **Ollama** (local, the default; nothing
  leaves the machine), plus OpenAI / Anthropic / any OpenAI-compatible endpoint — selected by config
  (`ergpower.llm.provider|model|base-url|api-key`). Unset → the coach is **disabled** and nothing about
  the deterministic analysis changes.
- **A grounded coaching endpoint** `GET /api/v1/sessions/{id}/coach`: build a prompt from the session's
  deterministic analysis (features + scorecard + flags + trends) plus a Kleshnev rubric, call the
  configured provider, and return prioritized plain-language coaching (+ the model used). A read.
- **An optional "AI Coach" panel** on the analysis view, shown only when a provider is configured, with
  a generate-on-demand action; the deterministic view is unchanged and complete without it.
- The coach is instructed to **comment only on the provided numbers** — priority, narrative, and drills,
  not invented observations.

Out of scope: fine-tuning; RAG over a biomechanics corpus (a curated rubric in the prompt is enough for
v1); persisting/caching coaching; multimodal (feeding the curve *image*) — features-first is the
reliable anchor; real-time/live coaching.

## Capabilities

### New Capabilities
- `llm-coach`: an optional, pluggable LLM that turns the deterministic technique analysis into grounded
  natural-language coaching, disabled unless a provider is configured.

## Impact

- Backend: an `LlmCoach` interface + provider implementations (Ollama first), a `CoachService` that
  reuses `TechniqueAnalyzer`'s output + a rubric, and `GET /sessions/{id}/coach`. A read → `live-api`
  read-only is untouched (no persistence in v1).
- Config/secrets: `ergpower.llm.*` from the git-ignored local config (api keys never committed);
  provider `none` by default.
- Frontend: an optional AI panel on the analysis view + a "is a provider configured?" signal.
- Privacy: **Ollama keeps everything local**; cloud providers receive only the *numeric* analysis (not
  raw session data), and only when the user opts in by configuring one.
