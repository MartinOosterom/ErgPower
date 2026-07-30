## 1. Profile

- [x] 1.1 An `ergpower.athlete.*` config (weightKg, age, optional sex, hrMax, hrRest, goal), bound from
      the git-ignored local config; all fields optional
- [x] 1.2 Derivations: watts/kg from a session's average power ÷ weight; HR zones (Z1–Z5) from HR max
      (configured, else `220 − age`); each available only when its inputs are set

## 2. Feed the AI

- [x] 2.1 `CoachContext` (and the agent's overview) gain: watts/kg, the HR average/peak as a zone (when a
      belt was worn), and the goal — each omitted when its inputs are missing
- [x] 2.2 The coach/agent prompts frame advice to the goal without changing the grounded numbers or the
      technique targets

## 3. Trends (optional)

- [ ] 3.1 A `wattsPerKg` trend metric (avg power ÷ current weight over time), scoped within a workout type

## 4. Verify

- [x] 4.1 With a profile set, the coach/agent cite watts/kg, an HR zone (when HR was recorded), and the
      goal; with none set, output is unchanged
- [x] 4.2 README/config note: the `ergpower.athlete.*` settings and what they unlock
