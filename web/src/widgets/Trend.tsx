import { useMemo } from 'react'
import type { EChartsOption } from 'echarts'

import { EChart } from '../components/EChart'
import { useLiveStore } from '../store/liveStore'
import { TREND_METRICS } from '../metrics'
import type { WidgetDef } from './types'

interface TrendConfig {
  metric: 'power' | 'pace' | 'hr'
}

function Trend({ config }: { config: TrendConfig }) {
  const buffer = useLiveStore((s) => s.history[config.metric])
  const desc = TREND_METRICS.find((m) => m.key === config.metric) ?? TREND_METRICS[0]

  const option = useMemo<EChartsOption>(
    () => ({
      animation: false,
      grid: { left: 44, right: 12, top: 24, bottom: 24 },
      title: { text: desc.label, textStyle: { fontSize: 12, fontWeight: 'normal' } },
      // No gridlines (splitLine) — they obscure the data.
      xAxis: {
        type: 'value',
        name: 's',
        min: 'dataMin',
        axisLabel: { formatter: (v: number) => String(Math.round(v)) },
        splitLine: { show: false },
      },
      // Pace reads better inverted (faster = up); power/hr normal.
      yAxis: { type: 'value', scale: true, inverse: config.metric === 'pace', splitLine: { show: false } },
      series: [
        {
          type: 'line',
          showSymbol: false,
          smooth: false,
          lineStyle: { width: 2 },
          data: buffer.map((p) => [p.t, p.v]),
        },
      ],
    }),
    [buffer, desc.label, config.metric],
  )

  return <EChart option={option} />
}

export const trendDef: WidgetDef<TrendConfig> = {
  type: 'trend',
  name: 'Trend',
  category: 'chart',
  requires: ['history'],
  defaultConfig: { metric: 'power' },
  configFields: [
    {
      key: 'metric',
      label: 'Metric',
      type: 'select',
      options: TREND_METRICS.map((m) => ({ value: m.key, label: m.label })),
    },
  ],
  defaultLayout: { w: 6, h: 4 },
  render: Trend,
}
