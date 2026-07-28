import { useLiveStore } from '../store/liveStore'
import { fmtClock, fmtMeters } from '../format'
import type { WidgetDef } from './types'

/**
 * Progress toward a fixed-time or fixed-distance target, with the PM5's projected finish. For
 * just-row (OTHER) pieces there is no target, so it shows elapsed/distance without a bar.
 */
function GoalProgress() {
  const workout = useLiveStore((s) => s.workout)
  const metrics = useLiveStore((s) => s.metrics)

  const type = workout?.durationType ?? 'OTHER'
  let done = 0
  let total = 0
  let leftLabel = '—'
  let projectedLabel = '—'

  if (type === 'TIME' && workout?.targetTimeS) {
    total = workout.targetTimeS
    done = metrics?.elapsedTimeS ?? 0
    leftLabel = fmtClock(metrics?.timeLeftS ?? total - done)
    projectedLabel = fmtClock(metrics?.projectedTimeS ?? null)
  } else if (type === 'DISTANCE' && workout?.targetDistanceM) {
    total = workout.targetDistanceM
    done = metrics?.distanceM ?? 0
    leftLabel = `${fmtMeters(metrics?.distanceLeftM ?? total - done)} m`
    projectedLabel = metrics?.projectedDistanceM != null ? `${metrics.projectedDistanceM} m` : '—'
  }

  const pct = total > 0 ? Math.max(0, Math.min(100, (done / total) * 100)) : 0
  const hasTarget = total > 0

  return (
    <div className="goal">
      <div className="goal-head">
        <span>{hasTarget ? (type === 'TIME' ? 'Time piece' : 'Distance piece') : 'Just row'}</span>
        {hasTarget && <span>{pct.toFixed(0)}%</span>}
      </div>
      {hasTarget ? (
        <div className="goal-bar">
          <div className="goal-fill" style={{ width: `${pct}%` }} />
        </div>
      ) : (
        <div className="goal-sub">
          {fmtClock(metrics?.elapsedTimeS ?? null)} · {fmtMeters(metrics?.distanceM ?? null)} m
        </div>
      )}
      {hasTarget && (
        <div className="goal-foot">
          <span>left {leftLabel}</span>
          <span>proj {projectedLabel}</span>
        </div>
      )}
    </div>
  )
}

export const goalProgressDef: WidgetDef = {
  type: 'goal',
  name: 'Goal progress',
  category: 'goal',
  requires: ['workout', 'metrics'],
  defaultConfig: {},
  defaultLayout: { w: 4, h: 2 },
  render: GoalProgress,
}
