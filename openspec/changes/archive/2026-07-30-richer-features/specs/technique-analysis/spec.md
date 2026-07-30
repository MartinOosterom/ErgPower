## ADDED Requirements

### Requirement: Extended technique metrics
The deterministic analysis SHALL additionally compute, from the already-stored session data: (a) the
**per-quartile progression** of the scored shape metrics (each metric's mean over Q1–Q4 of the analysed
strokes), (b) a **smoothness** score of the mean drive curve (lower curvature is smoother), and (c) a
**drive-to-recovery ratio** (rhythm) from the per-stroke drive/recovery timings. These SHALL be added to
the scorecard/features so they propagate to the cross-session index, trends, coach, and agent. The
analyzer version SHALL be bumped so cached analyses recompute.

#### Scenario: Per-quartile locates drift
- **WHEN** a session with enough strokes is analysed
- **THEN** each scored shape metric has a Q1–Q4 progression showing where in the piece it changed

#### Scenario: Smoothness and rhythm are scored
- **WHEN** a session's curves and stroke timings are analysed
- **THEN** a smoothness score and a drive-to-recovery ratio are produced, scored against their targets and
  flagged when poor

#### Scenario: New metrics propagate and invalidate
- **WHEN** the analyzer version is bumped and a previously-cached session is read
- **THEN** its analysis is recomputed and the new metrics appear in the index, trends, coach, and agent
