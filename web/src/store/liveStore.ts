import { create } from 'zustand'

import { api, LIVE_STREAM_URL } from '../api/client'
import type {
  ConnectionStatus,
  ForceCurve,
  LiveMetrics,
  StrokeSummary,
  WorkoutState,
} from '../api/types'

/** One time-stamped numeric sample (x = PM5 elapsed seconds) for trend widgets. */
export interface HistoryPoint {
  t: number
  v: number
}

export interface HistoryBuffers {
  power: HistoryPoint[]
  pace: HistoryPoint[]
  hr: HistoryPoint[]
}

/** The data keys a widget can require. Some (splits/summary) are not yet served by the API. */
export type DataKey =
  | 'connection'
  | 'workout'
  | 'metrics'
  | 'stroke'
  | 'forceCurve'
  | 'history'
  | 'splits'
  | 'summary'

/** Keys the live store actually provides today — the palette gates widgets on this set. */
export const AVAILABLE_DATA: ReadonlySet<DataKey> = new Set<DataKey>([
  'connection',
  'workout',
  'metrics',
  'stroke',
  'forceCurve',
  'history',
])

const MAX_HISTORY = 1800 // ~15 min at the PM5 2 Hz cadence
const MAX_CURVES = 4 // current + a few ghosts

interface LiveStore {
  streamOpen: boolean
  connection: ConnectionStatus | null
  workout: WorkoutState | null
  metrics: LiveMetrics | null
  lastStroke: StrokeSummary | null
  forceCurve: ForceCurve | null
  recentCurves: ForceCurve[] // oldest → newest; last is current
  history: HistoryBuffers
  sessionPeakN: number

  /** Open the single SSE connection (idempotent) and seed from the snapshot. */
  start: () => void
  /** Close the SSE connection. */
  stop: () => void
  /** Clear rolling live data (e.g. on a source switch) and re-seed from the snapshot. */
  reset: () => void
}

const emptyBuffers = (): HistoryBuffers => ({ power: [], pace: [], hr: [] })

const emptyData = () => ({
  connection: null,
  workout: null,
  metrics: null,
  lastStroke: null,
  forceCurve: null,
  recentCurves: [] as ForceCurve[],
  history: emptyBuffers(),
  sessionPeakN: 0,
})

// Module-scoped so the connection is a true singleton regardless of how many components mount.
let es: EventSource | null = null

function pushCapped(buf: HistoryPoint[], point: HistoryPoint): HistoryPoint[] {
  const next = buf.length >= MAX_HISTORY ? buf.slice(buf.length - MAX_HISTORY + 1) : buf.slice()
  next.push(point)
  return next
}

export const useLiveStore = create<LiveStore>((set, get) => {
  const applyMetrics = (m: LiveMetrics) => {
    const t = m.elapsedTimeS ?? 0
    const h = get().history
    const history: HistoryBuffers = {
      power: pushCapped(h.power, { t, v: m.powerW ?? 0 }),
      pace: m.paceSecondsPer500 != null ? pushCapped(h.pace, { t, v: m.paceSecondsPer500 }) : h.pace,
      hr: m.heartRateBpm != null ? pushCapped(h.hr, { t, v: m.heartRateBpm }) : h.hr,
    }
    set({ metrics: m, history })
  }

  const applyForceCurve = (fc: ForceCurve) => {
    const curves = [...get().recentCurves, fc].slice(-MAX_CURVES)
    const peak = Math.max(get().sessionPeakN, fc.peakN ?? 0, ...(fc.forcesN ?? []))
    set({ forceCurve: fc, recentCurves: curves, sessionPeakN: peak })
  }

  const resync = async () => {
    const { data } = await api.GET('/live/snapshot')
    if (!data) return
    set({ connection: data.connection })
    if (data.workout) set({ workout: data.workout })
    if (data.metrics) applyMetrics(data.metrics)
    if (data.lastStroke) set({ lastStroke: data.lastStroke })
    if (data.lastForceCurve) applyForceCurve(data.lastForceCurve)
  }

  const parse = <T,>(e: Event): T => JSON.parse((e as MessageEvent).data) as T

  return {
    streamOpen: false,
    ...emptyData(),

    start: () => {
      if (es) return
      es = new EventSource(LIVE_STREAM_URL)
      // (re)connect → resync from the snapshot so widgets show current state.
      es.addEventListener('open', () => {
        set({ streamOpen: true })
        void resync()
      })
      es.addEventListener('connection', (e) => set({ connection: parse<ConnectionStatus>(e) }))
      es.addEventListener('workout', (e) => set({ workout: parse<WorkoutState>(e) }))
      es.addEventListener('metrics', (e) => applyMetrics(parse<LiveMetrics>(e)))
      es.addEventListener('stroke', (e) => set({ lastStroke: parse<StrokeSummary>(e) }))
      es.addEventListener('forceCurve', (e) => applyForceCurve(parse<ForceCurve>(e)))
      es.onerror = () => set({ streamOpen: false }) // EventSource auto-reconnects; 'open' will resync
    },

    stop: () => {
      es?.close()
      es = null
      set({ streamOpen: false })
    },

    reset: () => {
      set({ ...emptyData() })
      void resync()
    },
  }
})
