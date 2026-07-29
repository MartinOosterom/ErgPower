// The single registry of displayable measurements. Each entry declares its label/unit, how to read the
// current value (from the live metrics and/or the last stroke), and which display MODES are meaningful:
// a value tile and/or a value-vs-time graph. The value and graph widgets are both driven by this — no
// per-metric hardcoding — and the store buffers history for every graphable metric.
import type { LiveMetrics, StrokeSummary } from './api/types'
import { fmt1, fmt2, fmtClock, fmtMeters, fmtPace, round } from './format'

export type DisplayMode = 'value' | 'graph'

/** The live sources a metric can read from. */
export interface MetricSources {
  metrics: LiveMetrics | null
  stroke: StrokeSummary | null
}

export interface MetricDesc {
  key: string
  label: string
  unit: string
  modes: DisplayMode[]
  /** When graphed, plot the y-axis inverted so "better" (e.g. faster pace) is up. */
  invertGraph?: boolean
  get: (s: MetricSources) => number | null
  fmt: (v: number | null) => string
}

const BOTH: DisplayMode[] = ['value', 'graph']
const VALUE: DisplayMode[] = ['value']

export const METRICS: MetricDesc[] = [
  { key: 'power', label: 'Power', unit: 'W', modes: BOTH, get: (s) => s.metrics?.powerW ?? null, fmt: round },
  { key: 'pace', label: 'Pace', unit: '/500m', modes: BOTH, invertGraph: true, get: (s) => s.metrics?.paceSecondsPer500 ?? null, fmt: fmtPace },
  { key: 'avgPace', label: 'Avg pace', unit: '/500m', modes: BOTH, invertGraph: true, get: (s) => s.metrics?.avgPaceSecondsPer500 ?? null, fmt: fmtPace },
  { key: 'spm', label: 'Rate', unit: 'spm', modes: BOTH, get: (s) => s.metrics?.strokeRate ?? null, fmt: round },
  { key: 'hr', label: 'Heart rate', unit: 'bpm', modes: BOTH, get: (s) => s.metrics?.heartRateBpm ?? null, fmt: round },
  { key: 'distance', label: 'Distance', unit: 'm', modes: BOTH, get: (s) => s.metrics?.distanceM ?? null, fmt: fmtMeters },
  { key: 'elapsed', label: 'Time', unit: '', modes: VALUE, get: (s) => s.metrics?.elapsedTimeS ?? null, fmt: fmtClock },
  { key: 'calories', label: 'Calories', unit: 'cal', modes: BOTH, get: (s) => s.metrics?.totalCalories ?? null, fmt: round },
  { key: 'drag', label: 'Drag factor', unit: '', modes: VALUE, get: (s) => s.metrics?.dragFactor ?? null, fmt: round },
  { key: 'splitPower', label: 'Split power', unit: 'W', modes: BOTH, get: (s) => s.metrics?.splitAvgPowerW ?? null, fmt: round },
  { key: 'splitPace', label: 'Split pace', unit: '/500m', modes: BOTH, invertGraph: true, get: (s) => s.metrics?.splitAvgPaceSecondsPer500 ?? null, fmt: fmtPace },
  { key: 'timeLeft', label: 'Time left', unit: '', modes: VALUE, get: (s) => s.metrics?.timeLeftS ?? null, fmt: fmtClock },
  { key: 'distanceLeft', label: 'Distance left', unit: 'm', modes: VALUE, get: (s) => s.metrics?.distanceLeftM ?? null, fmt: fmtMeters },
  { key: 'projTime', label: 'Projected time', unit: '', modes: BOTH, invertGraph: true, get: (s) => s.metrics?.projectedTimeS ?? null, fmt: fmtClock },
  { key: 'projDistance', label: 'Projected dist', unit: 'm', modes: BOTH, get: (s) => s.metrics?.projectedDistanceM ?? null, fmt: fmtMeters },
  { key: 'peakForce', label: 'Peak force', unit: 'N', modes: BOTH, get: (s) => s.stroke?.drivePeakForceN ?? null, fmt: round },
  { key: 'avgForce', label: 'Avg drive force', unit: 'N', modes: BOTH, get: (s) => s.stroke?.avgDriveForceN ?? null, fmt: round },
  { key: 'driveTime', label: 'Drive time', unit: 's', modes: BOTH, get: (s) => s.stroke?.driveTimeS ?? null, fmt: fmt2 },
  { key: 'strokeDistance', label: 'Stroke distance', unit: 'm', modes: BOTH, get: (s) => s.stroke?.strokeDistanceM ?? null, fmt: fmt1 },
]

export function metricByKey(key: string): MetricDesc {
  return METRICS.find((d) => d.key === key) ?? METRICS[0]
}

export function graphable(desc: MetricDesc): boolean {
  return desc.modes.includes('graph')
}

/** Metrics the store keeps a rolling history for (those with a graph mode). */
export const GRAPH_METRICS: MetricDesc[] = METRICS.filter(graphable)
