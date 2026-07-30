## 1. Progress dashboard + session selection

- [x] 1.1 A top-level **Sessions | Progress** switch; a new Progress screen shell
- [x] 1.2 A filterable, multi-select **session picker** over `/sessions/index` (filter by workout type,
      distance band, date range; checkboxes; a "recent same-type" pre-tick; a cap on how many are compared)
- [x] 1.3 Cross-session **trend charts** for the selected set, reusing `/sessions/index` + `/trends`
      (technique metrics across the set; performance metrics within a type)

## 2. Coach over a selected set

- [x] 2.1 Let progress coaching take an explicit set of session ids (most recent = current, rest = baseline),
      reusing the existing history/rubric and the two-lens rule
- [x] 2.2 A progress-coach panel on the Progress dashboard driven by the current selection

## 3. Agent scope by screen

- [x] 3.1 On the **Analysis** view, scope the agent to the single session (session tools only, no
      cross-session tools) so it cannot roam
- [x] 3.2 On the **Progress** dashboard, a set-scoped chat: the agent gets the cross-session tools but is
      focused on the selected sessions (named in its context)

## 4. Trim the Analysis view

- [x] 4.1 Remove the coach's **This session / Progress** toggle from the analysis view (single-session only);
      Progress now lives on the new dashboard

## 5. Verify

- [x] 5.1 Pick a set on the Progress dashboard → trends render, progress coaching narrates over the set, and
      the set-scoped agent answers comparative questions about the chosen sessions
- [x] 5.2 The Analysis view is single-session: the coach is this-session-only and its agent cannot reach
      other sessions; the deterministic analysis is unchanged
- [x] 5.3 README: the two dashboards, session selection, and set-scoped coaching/agent
