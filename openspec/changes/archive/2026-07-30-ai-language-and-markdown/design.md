## Context

The coach and agent build system prompts in `CoachService` and `SessionAgent`. Adding a language line is
a one-place edit each. The agent already produces Markdown; the chat panels just render raw text. The
coach is deliberately prose.

## Goals / Non-Goals

**Goals:** a single config knob for the AI response language (coach + agent); render the agent's Markdown;
keep the coach as prose; keep grounding (numbers/labels) intact.

**Non-Goals:** localizing metric names/numbers; per-request/in-UI language switching; Markdown for the
coach; a translation service (the LLM translates).

## Decisions

### D1: One config knob, a natural-language name
`ergpower.ai.language` holds a natural-language name (e.g. `Dutch`, `Nederlands`) — most reliable for an
LLM. Default unset → English. A tiny `@ConfigurationProperties(ergpower.ai)` bean; the value flows from
the git-ignored local config like everything else.

### D2: Inject as a prompt line; translate prose only
When set, each system prompt (coach single/progress, agent session/set) gains "Respond in <language>."
The prompt keeps the instruction to comment only on the provided numbers, so **metric names and values
stay as given** (English, citable) while the narration is translated. This keeps answers grounded.

### D3: Markdown for the agent only
The agent's system prompt asks it to format answers in Markdown. The chat panels (single-session
`ChatPanel` and set-scoped `SetChat`) render message content through a Markdown component. The **coach
stays prose**: its prompt is unchanged and its panel keeps plain rendering.

### D4: react-markdown + remark-gfm (safe by default)
Use `react-markdown` (renders to React nodes, no `dangerouslySetInnerHTML`, sanitises) plus `remark-gfm`
for GitHub tables/strikethrough/task-lists. Avoid `marked`/`markdown-it` + `innerHTML`, which need a
separate sanitiser. ~40 KB gz, built by the existing frontend toolchain.

### D5: Streaming renders progressively
The chat streams tokens; the Markdown renderer re-renders on each token, so a half-formed table reflows
as it arrives and settles when complete — a cosmetic flicker, acceptable.

## Risks / Trade-offs

- **Model language quality** → depends on the provider/model; the config just asks. Numbers stay English
  so nothing critical is lost in translation.
- **New frontend dependency** → small, well-maintained, XSS-safe; built by the bundled Node toolchain.
- **Partial-Markdown flicker while streaming** → cosmetic; settles on completion.

## Open Questions

- Whether to also expose the language as a query param later for ad-hoc switching — deferred; config-file
  is enough now.
