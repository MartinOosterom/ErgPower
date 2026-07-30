## Why

Everything the app produces is about the *session*, never the *athlete*. Two numbers rowers actually
live by are missing because we don't know who's rowing: **watts/kg** (the universal comparison metric)
and **heart-rate zones** (real intensity, not just drift). A small profile — weight, age, HR max, a goal
— unlocks both, plus goal-aware framing, and it drops straight into the coach, agent, and trends.

## What Changes

- **A single-athlete profile** from config (`ergpower.athlete.*`: weight, age, optional sex, HR max /
  resting, and a free-text goal). Single-user app → one profile; unset simply means those derived numbers
  are omitted.
- **Derived, everywhere it helps:**
  - **watts/kg** = a session's average power ÷ body weight — added to the coach/agent session context.
  - **HR zones** — from HR max (configured, else `220 − age`), so recorded heart rate is read as a zone
    (Z1–Z5), not just a drift.
  - **Goal-aware framing** — the coach and agent know the goal ("2k in 6 weeks") and weigh advice
    accordingly, without changing the grounded numbers.
- Technique **targets are unchanged** — Kleshnev shape metrics are % of the drive and body-independent;
  the profile adds *physiology and comparison*, not new technique targets.

Out of scope: multiple athletes / identity (single-user for now); a UI profile editor (config for v1);
altering the force-curve targets; medical inference from HR.

## Capabilities

### New Capabilities
- `rower-profile`: an optional single-athlete profile (weight/age/HR-max/goal) and the values derived
  from it (watts/kg, HR zones, goal context), consumed by the coach, agent, and trends.

## Impact

- Config: `ergpower.athlete.*` (from the git-ignored local config, like the rest).
- Backend: a profile bean + derivations; `CoachContext` gains watts/kg + HR-zone lines + the goal; the
  agent's overview tool surfaces the same; optionally a `wattsPerKg` trend metric.
- Privacy: the profile is local config; nothing new leaves the machine beyond what the coach/agent
  already send.
