import { useMemo } from 'react'
import type { EChartsOption } from 'echarts'

import { EChart } from '../components/EChart'
import { useLiveStore } from '../store/liveStore'
import { GRAPH_METRICS, metricByKey } from '../metrics'
import type { WidgetDef } from './types'

interface TrendConfig {
  metric: string
}

function Trend({ config }: { config: TrendConfig }) {
  const buffer = useLiveStore((s) => s.history[config.metric])
  const desc = metricByKey(config.metric)

  const option = useMemo<EChartsOption>(
    () => ({
      animation: false,
      grid: { left: 44, right: 12, top: 24, bottom: 24 },
      title: { text: desc.label, textStyle: { fontSize: 12, fontWeight: 'normal' } },
      // No gridlines (splitLine) — they obscure the data.
      xAxis: { type: 'value', name: 's', min: 'dataMin', splitLine: { show: false }, axisLabel: { formatter: (v: number) => String(Math.round(v)) } },
      // Pace-like metrics read better inverted (faster = up); others normal.
      yAxis: { type: 'value', scale: true, inverse: !!desc.invertGraph, splitLine: { show: false } },
      series: [
        {
          type: 'line',
          showSymbol: false,
          smooth: false,
          lineStyle: { width: 2 },
          data: (buffer ?? []).map((p) => [p.t, p.v]),
        },
      ],
    }),
    [buffer, desc.label, desc.invertGraph],
  )

  return <EChart option={option} />
}

export const trendDef: WidgetDef<TrendConfig> = {
  type: 'trend',
  name: 'Graph',
  category: 'chart',
  requires: ['history'],
  defaultConfig: { metric: 'power' },
  configFields: [
    {
      key: 'metric',
      label: 'Metric',
      type: 'select',
      options: GRAPH_METRICS.map((m) => ({ value: m.key, label: m.label })),
    },
  ],
  defaultLayout: { w: 6, h: 4 },
  render: Trend,
}
