## 1. New metrics in the analyzer

- [x] 1.1 Per-quartile progression: each scored shape metric's mean over Q1–Q4 of the analysed strokes
      (fall back to fewer buckets / omit for very short pieces)
- [x] 1.2 A smoothness score from the mean drive curve (normalised curvature; lower = smoother), scored
      against a defensible target and flagged when poor
- [x] 1.3 A drive-to-recovery ratio (rhythm) from per-stroke drive/recovery timings (~1:2 target), scored
      and flagged when rushed

## 2. Propagate

- [x] 2.1 Bump `ANALYZER_VERSION` so cached analyses and the index recompute; confirm the new scores
      appear in `/sessions/index`, `/trends`, the coach context, and the agent's analysis tool
- [x] 2.2 The new scorecard entries render in the existing scorecard grid; per-quartile summarised into
      the coach prompt (a dedicated chart is optional)

## 3. Verify

- [x] 3.1 On the reference session, the new metrics compute to plausible values; per-quartile locates
      drift; smoothness and rhythm are scored and flagged sensibly
- [x] 3.2 The new scores flow into the index and a trend; the coach and agent can cite them; a version
      bump recomputes cached analyses
- [x] 3.3 README: the new metrics and how they read
