import type { DashboardConfig } from './dashboardTypes'
import { DEFAULT_PRESET, PRESETS } from './presets'

const KEY = 'ergpower.dashboard'

export function loadDashboard(): DashboardConfig {
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) return JSON.parse(raw) as DashboardConfig
  } catch {
    // corrupt/unavailable storage → fall back to a preset
  }
  return PRESETS[DEFAULT_PRESET]()
}

export function saveDashboard(config: DashboardConfig): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(config))
  } catch {
    // storage full/disabled — non-fatal
  }
}

let idSeq = 0
export function newWidgetId(type: string): string {
  idSeq += 1
  return `${type}-${Date.now().toString(36)}-${idSeq}`
}
