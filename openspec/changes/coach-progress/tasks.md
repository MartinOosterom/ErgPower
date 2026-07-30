## 1. History block

- [ ] 1.1 Given an anchor session, query the `cross-session-analysis` index for recent **same-type**
      sessions and distil a compact history block (key technique scores + trend), applying the two-lens
      rule (technique spans; performance within type)
- [ ] 1.2 Enforce a minimum comparable-session count before asserting a trend; otherwise omit the trend
      and fall back to single-session

## 2. Progress coaching

- [ ] 2.1 Add a coaching **mode** (single | progress); single is the default and unchanged; progress
      appends the history block to the prompt
- [ ] 2.2 Extend the rubric so progress coaching narrates improved / plateaued / regressed vs. the
      athlete's own baseline, staying technique-first and commenting only on the provided numbers

## 3. Frontend

- [ ] 3.1 Add a **This session / Progress** toggle to the AI-coach panel; show Progress only when
      comparable history exists; regenerate on toggle

## 4. Verify

- [ ] 4.1 With a history of same-type sessions, confirm the coach narrates a real trend grounded in the
      index; with a first/only session it degrades cleanly to single-session
- [ ] 4.2 Confirm performance context stays within type and technique trends span the log; grounding and
      the not-configured path are unaffected
- [ ] 4.3 README: the coach's progress mode and how history is selected
