## Context

The app knows sessions, not the athlete, so watts/kg and HR zones are impossible today. A tiny config
profile fills that gap and feeds the existing coach/agent context and trends. Single-user, so identity
is trivial.

## Goals / Non-Goals

**Goals:** optional profile (weight/age/HR-max/goal); derived watts/kg + HR zones + goal context; feed
the coach, agent, and (optionally) trends; graceful when unset.

**Non-Goals:** multiple athletes/identity; a UI editor; changing technique targets; medical inference.

## Decisions

### D1: Profile from config, single athlete
`ergpower.athlete.*` (weightKg, age, sex?, hrMax?, hrRest?, goal), bound from the git-ignored local
config like `ergpower.ai.*`. Unset fields simply drop the derived values. A UI editor can come later
(it would need a write endpoint; config keeps v1 read-only).

### D2: watts/kg is derived, not stored
watts/kg = a session's `avgPowerW / weightKg`, computed where needed (coach context, a trend metric) —
not baked into the per-session index, so a weight change doesn't leave stale numbers. If a `wattsPerKg`
trend is added, it's computed from the indexed avg power + the current weight.

### D3: HR zones from HR max
Zones Z1–Z5 as % of HR max (configured, else `220 − age`). Recorded average/peak HR (when a belt was
worn) is reported as a zone in the coach/agent context; the coach still reads HR as effort, now with a
zone label rather than a bare number. No zone data when neither HR nor HR-max is available.

### D4: Goal is free-text framing
The goal string is injected into the coach/agent prompts as context ("the athlete is training for: …")
so advice is framed to it. It does not change the grounded numbers or the technique targets.

### D5: Targets stay body-independent
Kleshnev shape metrics are normalized (% of drive), so a light/heavy rower has the same technique
targets. The profile adds physiology (watts/kg, zones) and intent (goal), not new technique scoring.

## Risks / Trade-offs

- **Stale weight** → watts/kg computed on read from the current profile, never cached per session.
- **220−age is rough** → prefer a configured HR max; fall back to the estimate only when age is set.
- **Over-personalising the coach** → the goal only frames; grounding and targets are unchanged.

## Open Questions

- Add a `wattsPerKg` trend metric now, or just the coach/agent context first? (Lean: context first,
  trend as a fast follow.)
- Should the profile eventually be UI-editable (needs a write endpoint + storage like dashboards)?
