import { METRICS } from '../metrics'
import { WIDGETS, isAvailable, widgetByType } from '../widgets/registry'

// 'stat' (value) and 'trend' (graph) are presented via the metric → mode picker below, not as flat
// generic entries.
const METRIC_TYPES = new Set(['stat', 'trend'])

/**
 * Availability-aware palette. Non-metric widgets are gated by their required data. Metric panels are a
 * metric → display-mode picker: each measurement offers "value" and (only if graphable) "graph".
 */
export function WidgetPalette({
  onAdd,
}: {
  onAdd: (type: string, config?: Record<string, unknown>) => void
}) {
  const statDef = widgetByType('stat')
  const graphDef = widgetByType('trend')
  const canValue = statDef ? isAvailable(statDef) : false
  const canGraph = graphDef ? isAvailable(graphDef) : false

  return (
    <div className="palette">
      {WIDGETS.filter((d) => !METRIC_TYPES.has(d.type)).map((def) => {
        const available = isAvailable(def)
        return (
          <button
            key={def.type}
            className="palette-item"
            disabled={!available}
            title={available ? `Add ${def.name}` : `Unavailable — needs: ${def.requires.join(', ')}`}
            onClick={() => onAdd(def.type)}
          >
            + {def.name}
            {!available && <span className="soon">unavailable</span>}
          </button>
        )
      })}

      <div className="metric-picker">
        {METRICS.map((m) => (
          <div key={m.key} className="metric-row">
            <span className="metric-name">{m.label}</span>
            {m.modes.includes('value') && (
              <button className="mode-btn" disabled={!canValue} onClick={() => onAdd('stat', { metric: m.key })}>
                value
              </button>
            )}
            {m.modes.includes('graph') && (
              <button className="mode-btn" disabled={!canGraph} onClick={() => onAdd('trend', { metric: m.key })}>
                graph
              </button>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
