## Why

Two small presentation wishes for the AI features:

1. **Language.** The coach and agent always answer in English. A rower who thinks in another language
   should be able to set one — in config — and get coaching/answers in it.
2. **Markdown.** The agent already *emits* Markdown (headings, tables, **bold**), but the chat panels
   render it as raw text (`white-space: pre-wrap`), so you see literal `##` and `|`. Rendering it makes
   the agent's structured answers readable. The coach stays clean prose.

Both are low-risk, mostly-presentation changes.

## What Changes

- **A configured response language** (`ergpower.ai.language`, a natural-language name like `Dutch`;
  default unset = English), injected into the coach and agent system prompts as "respond in that
  language". Only the **prose** is translated — metric names and numbers stay as given (grounded and
  citable).
- **Markdown rendering for the agent.** The agent is instructed to format answers as Markdown, and the
  chat panels (single-session and set-scoped) render it with a safe Markdown renderer. The **coach stays
  prose** — unchanged prompt and plain rendering.

Out of scope: per-request language switching in the UI (config-file only for now); localizing metric
names/numbers; making the coach use Markdown.

## Capabilities

### Modified Capabilities
- `llm-coach`: coaching is produced in the **configured language** (prose translated, numbers/labels
  kept); the coach's prose format is unchanged.
- `session-agent`: the agent answers in the **configured language** and formats answers as **Markdown**.
- `web-viewer`: the chat panels **render the agent's Markdown**; the coach panel stays prose.

## Impact

- Backend: a small `ergpower.ai.*` config (language) injected into the coach and agent system prompts;
  the agent prompt also asks for Markdown output. No change to tools, grounding, endpoints, or the
  deterministic analysis.
- Frontend: a Markdown renderer (`react-markdown` + `remark-gfm`, XSS-safe) in the chat panels; the
  coach panel unchanged.
