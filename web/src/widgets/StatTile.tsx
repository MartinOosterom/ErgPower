import { useLiveStore } from '../store/liveStore'
import { METRICS, metricByKey } from '../metrics'
import type { WidgetDef } from './types'

interface StatConfig {
  metric: string
}

function StatTile({ config }: { config: StatConfig }) {
  const metrics = useLiveStore((s) => s.metrics)
  const desc = metricByKey(config.metric)
  return (
    <div className="stat-tile">
      <div className="stat-value">
        {desc.fmt(desc.get(metrics))}
        {desc.unit && <span className="stat-unit">{desc.unit}</span>}
      </div>
      <div className="stat-label">{desc.label}</div>
    </div>
  )
}

export const statTileDef: WidgetDef<StatConfig> = {
  type: 'stat',
  name: 'Stat tile',
  category: 'metric',
  requires: ['metrics'],
  defaultConfig: { metric: 'power' },
  configFields: [
    {
      key: 'metric',
      label: 'Metric',
      type: 'select',
      options: METRICS.map((m) => ({ value: m.key, label: m.label })),
    },
  ],
  defaultLayout: { w: 3, h: 2 },
  render: StatTile,
}
