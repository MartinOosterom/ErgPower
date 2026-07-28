import { AVAILABLE_DATA } from '../store/liveStore'
import type { AnyWidgetDef } from './types'

import { statTileDef } from './StatTile'
import { connectionStatusDef } from './ConnectionStatusWidget'
import { workoutPhaseDef } from './WorkoutPhaseWidget'
import { trendDef } from './Trend'
import { goalProgressDef } from './GoalProgress'
import { forceCurveDef } from './ForceCurve'
import { splitsDef, summaryDef } from './futureWidgets'

/** All known widgets. Order = palette order. Future (unavailable) widgets are registered too. */
export const WIDGETS: AnyWidgetDef[] = [
  statTileDef,
  trendDef,
  forceCurveDef,
  goalProgressDef,
  connectionStatusDef,
  workoutPhaseDef,
  splitsDef,
  summaryDef,
]

export function widgetByType(type: string): AnyWidgetDef | undefined {
  return WIDGETS.find((w) => w.type === type)
}

/** A widget is available when every data key it requires is served by the live store today. */
export function isAvailable(def: AnyWidgetDef): boolean {
  return def.requires.every((k) => AVAILABLE_DATA.has(k))
}
