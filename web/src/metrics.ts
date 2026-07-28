// Metric descriptors: the single source of what a StatTile / Trend can show, so those widgets are
// parameterized (one component, many metrics) rather than hardcoded per value.
import type { LiveMetrics } from './api/types'
import { fmtClock, fmtMeters, fmtPace, round } from './format'

export interface MetricDesc {
  key: string
  label: string
  unit: string
  get: (m: LiveMetrics | null) => number | null
  fmt: (v: number | null) => string
}

export const METRICS: MetricDesc[] = [
  { key: 'power', label: 'Power', unit: 'W', get: (m) => m?.powerW ?? null, fmt: round },
  { key: 'pace', label: 'Pace', unit: '/500m', get: (m) => m?.paceSecondsPer500 ?? null, fmt: fmtPace },
  { key: 'avgPace', label: 'Avg pace', unit: '/500m', get: (m) => m?.avgPaceSecondsPer500 ?? null, fmt: fmtPace },
  { key: 'spm', label: 'Rate', unit: 'spm', get: (m) => m?.strokeRate ?? null, fmt: round },
  { key: 'hr', label: 'Heart rate', unit: 'bpm', get: (m) => m?.heartRateBpm ?? null, fmt: round },
  { key: 'distance', label: 'Distance', unit: 'm', get: (m) => m?.distanceM ?? null, fmt: fmtMeters },
  { key: 'elapsed', label: 'Time', unit: '', get: (m) => m?.elapsedTimeS ?? null, fmt: fmtClock },
  { key: 'calories', label: 'Calories', unit: 'cal', get: (m) => m?.totalCalories ?? null, fmt: round },
  { key: 'drag', label: 'Drag factor', unit: '', get: (m) => m?.dragFactor ?? null, fmt: round },
]

export function metricByKey(key: string): MetricDesc {
  return METRICS.find((d) => d.key === key) ?? METRICS[0]
}

/** Trend-able metrics — only those the store keeps a rolling history buffer for. */
export const TREND_METRICS: { key: 'power' | 'pace' | 'hr'; label: string; unit: string; fmt: (v: number | null) => string }[] = [
  { key: 'power', label: 'Power', unit: 'W', fmt: round },
  { key: 'pace', label: 'Pace', unit: '/500m', fmt: fmtPace },
  { key: 'hr', label: 'Heart rate', unit: 'bpm', fmt: round },
]
