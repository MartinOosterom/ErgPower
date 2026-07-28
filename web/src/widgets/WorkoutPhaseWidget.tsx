import { useLiveStore } from '../store/liveStore'
import type { WidgetDef } from './types'

function WorkoutPhaseWidget() {
  const workout = useLiveStore((s) => s.workout)
  const phase = workout?.phase ?? 'WAITING'
  return (
    <div className="phase">
      <div className={`phase-badge phase-${phase.toLowerCase()}`}>{phase}</div>
      {workout && (
        <div className="phase-sub">
          {workout.durationType}
          {workout.durationType === 'DISTANCE' && workout.targetDistanceM ? ` · ${workout.targetDistanceM} m` : ''}
          {workout.durationType === 'TIME' && workout.targetTimeS ? ` · ${workout.targetTimeS}s` : ''}
        </div>
      )}
    </div>
  )
}

export const workoutPhaseDef: WidgetDef = {
  type: 'phase',
  name: 'Workout phase',
  category: 'status',
  requires: ['workout'],
  defaultConfig: {},
  defaultLayout: { w: 3, h: 2 },
  render: WorkoutPhaseWidget,
}
