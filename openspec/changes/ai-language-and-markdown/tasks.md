## 1. Language config

- [ ] 1.1 An `ergpower.ai.language` config property (a natural-language name; default unset = English),
      bound from the git-ignored local config like the rest
- [ ] 1.2 Inject a "respond in <language>" line into the coach system prompts (single + progress) and the
      agent system prompts (session + set); translate prose only, keep metric names/values as given

## 2. Markdown for the agent

- [ ] 2.1 Instruct the agent to format answers as Markdown (both session and set prompts); the coach
      prompt is unchanged (stays prose)
- [ ] 2.2 Render the agent chat messages as Markdown in both chat panels (single-session and set-scoped)
      with a safe renderer (`react-markdown` + `remark-gfm`); the coach panel stays plain prose

## 3. Verify

- [ ] 3.1 With a language configured, the coach and agent answer in that language while metric names and
      numbers stay as given; with none set, output is English (unchanged)
- [ ] 3.2 The agent's answers render as Markdown (headings/tables/bold) in the chat; the coach stays prose;
      streaming still works
- [ ] 3.3 README/config note: the `ergpower.ai.language` setting and that the agent renders Markdown
