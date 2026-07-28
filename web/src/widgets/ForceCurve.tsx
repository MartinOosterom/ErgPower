import { useMemo } from 'react'
import type { EChartsOption, SeriesOption } from 'echarts'

import { EChart } from '../components/EChart'
import { useLiveStore } from '../store/liveStore'
import type { ForceCurve as ForceCurveData } from '../api/types'
import type { WidgetDef } from './types'

interface ForceCurveConfig {
  xAxis: 'index' | 'normalized'
  yScale: 'session-peak' | 'fixed'
  fixedMaxN: number
  ghosts: number
  pointSize: number
  connect: boolean
}

const ACCENT = '#38bdf8'

/** Map a curve's samples to [x, force] pairs, x by sample index or 0–100% of the drive. */
function points(curve: ForceCurveData, mode: 'index' | 'normalized'): [number, number][] {
  const f = curve.forcesN ?? []
  const n = f.length
  return f.map((v, i) => [mode === 'normalized' ? (n > 1 ? (i / (n - 1)) * 100 : 0) : i, v])
}

function ForceCurve({ config }: { config: ForceCurveConfig }) {
  const recent = useLiveStore((s) => s.recentCurves)
  const sessionPeak = useLiveStore((s) => s.sessionPeakN)

  const option = useMemo<EChartsOption>(() => {
    const ghostCount = Math.max(0, Math.min(3, Math.round(config.ghosts)))
    const window = recent.slice(-(ghostCount + 1))
    const current = window[window.length - 1]
    const ghostsArr = window.slice(0, -1)

    // Stable axes: Y from the rolling session peak (not per-stroke max) so a weaker pull looks smaller.
    const yMax = config.yScale === 'fixed' ? config.fixedMaxN : Math.ceil((sessionPeak * 1.1) / 50) * 50 || 100
    const xMax =
      config.xAxis === 'normalized' ? 100 : Math.max(1, ...recent.map((c) => (c.forcesN?.length ?? 1) - 1))

    const mk = (curve: ForceCurveData, color: string, opacity: number): SeriesOption =>
      config.connect
        ? { type: 'line', showSymbol: true, symbolSize: config.pointSize, data: points(curve, config.xAxis), lineStyle: { color, opacity }, itemStyle: { color, opacity } }
        : { type: 'scatter', symbolSize: config.pointSize, data: points(curve, config.xAxis), itemStyle: { color, opacity } }

    const series: SeriesOption[] = []
    ghostsArr.forEach((g, i) => {
      // Older ghosts fade more; newest ghost is the most visible gray.
      const opacity = 0.15 + 0.2 * ((i + 1) / (ghostsArr.length || 1))
      series.push(mk(g, '#9ca3af', opacity))
    })
    if (current) series.push(mk(current, ACCENT, 1))

    return {
      animation: false,
      grid: { left: 48, right: 12, top: 28, bottom: 30 },
      title: { text: 'Force curve (N)', textStyle: { fontSize: 12, fontWeight: 'normal' } },
      xAxis: {
        type: 'value',
        min: 0,
        max: xMax,
        name: config.xAxis === 'normalized' ? 'drive %' : 'sample',
        nameLocation: 'middle',
        nameGap: 20,
      },
      yAxis: { type: 'value', min: 0, max: yMax, name: 'N' },
      series,
    }
  }, [recent, sessionPeak, config])

  return <EChart option={option} />
}

export const forceCurveDef: WidgetDef<ForceCurveConfig> = {
  type: 'forceCurve',
  name: 'Force curve',
  category: 'chart',
  requires: ['forceCurve'],
  defaultConfig: { xAxis: 'index', yScale: 'session-peak', fixedMaxN: 800, ghosts: 1, pointSize: 7, connect: false },
  configFields: [
    { key: 'xAxis', label: 'Drive axis', type: 'select', options: [
      { value: 'index', label: 'Sample index' },
      { value: 'normalized', label: 'Normalized 0–100%' },
    ] },
    { key: 'yScale', label: 'Force scale', type: 'select', options: [
      { value: 'session-peak', label: 'Session peak' },
      { value: 'fixed', label: 'Fixed max' },
    ] },
    { key: 'fixedMaxN', label: 'Fixed max (N)', type: 'number', min: 100, max: 2000, step: 50 },
    { key: 'ghosts', label: 'Ghost strokes', type: 'number', min: 0, max: 3, step: 1 },
    { key: 'pointSize', label: 'Point size', type: 'number', min: 2, max: 14, step: 1 },
    { key: 'connect', label: 'Connect points', type: 'boolean' },
  ],
  defaultLayout: { w: 6, h: 5 },
  render: ForceCurve,
}
