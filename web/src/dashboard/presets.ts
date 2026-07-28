import { widgetByType } from '../widgets/registry'
import type { DashboardConfig, WidgetInstance } from './dashboardTypes'

let seq = 0
function inst(type: string, config: Record<string, unknown>, x: number, y: number, w: number, h: number): WidgetInstance {
  return { id: `${type}-${++seq}`, type, config: { ...widgetByType(type)?.defaultConfig, ...config }, layout: { x, y, w, h } }
}

/** Compact glanceable HUD: the four numbers you watch while rowing, plus the force curve. */
function minimalHud(): DashboardConfig {
  return {
    name: 'Minimal HUD',
    widgets: [
      inst('stat', { metric: 'power' }, 0, 0, 3, 2),
      inst('stat', { metric: 'pace' }, 3, 0, 3, 2),
      inst('stat', { metric: 'spm' }, 6, 0, 3, 2),
      inst('stat', { metric: 'distance' }, 9, 0, 3, 2),
      inst('forceCurve', {}, 0, 2, 6, 5),
      inst('phase', {}, 6, 2, 3, 2),
      inst('stat', { metric: 'elapsed' }, 9, 2, 3, 2),
    ],
  }
}

/** Everything: tiles, trends, goal, status, and the force curve. */
function fullPanel(): DashboardConfig {
  return {
    name: 'Full panel',
    widgets: [
      inst('stat', { metric: 'power' }, 0, 0, 3, 2),
      inst('stat', { metric: 'pace' }, 3, 0, 3, 2),
      inst('stat', { metric: 'spm' }, 6, 0, 2, 2),
      inst('stat', { metric: 'hr' }, 8, 0, 2, 2),
      inst('stat', { metric: 'distance' }, 10, 0, 2, 2),
      inst('forceCurve', {}, 0, 2, 6, 5),
      inst('trend', { metric: 'power' }, 6, 2, 6, 4),
      inst('goal', {}, 6, 6, 4, 2),
      inst('connection', {}, 10, 6, 2, 2),
      inst('trend', { metric: 'pace' }, 0, 7, 6, 4),
    ],
  }
}

export const PRESETS: Record<string, () => DashboardConfig> = {
  'Minimal HUD': minimalHud,
  'Full panel': fullPanel,
}

export const DEFAULT_PRESET = 'Full panel'
