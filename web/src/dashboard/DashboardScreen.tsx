import { useEffect, useMemo, useState } from 'react'
import type { Layout } from 'react-grid-layout'

import { SourceIndicator } from '../source/SourceIndicator'
import { widgetByType } from '../widgets/registry'
import type { SourceStatus } from '../api/types'
import { DashboardRenderer } from './DashboardRenderer'
import { WidgetConfigPanel } from './WidgetConfigPanel'
import { WidgetPalette } from './WidgetPalette'
import type { DashboardConfig } from './dashboardTypes'
import { loadDashboard, newWidgetId, saveDashboard } from './persistence'
import { PRESETS } from './presets'

/** The dashboard screen: toolbar + (edit) palette/config + the widget grid. Config persists locally. */
export function DashboardScreen({
  source,
  onChangeSource,
}: {
  source: SourceStatus | null
  onChangeSource: () => void
}) {
  const [config, setConfig] = useState<DashboardConfig>(() => loadDashboard())
  const [editable, setEditable] = useState(false)
  const [selectedId, setSelectedId] = useState<string | null>(null)

  useEffect(() => saveDashboard(config), [config])

  const selected = useMemo(
    () => config.widgets.find((w) => w.id === selectedId) ?? null,
    [config.widgets, selectedId],
  )
  const selectedDef = selected ? widgetByType(selected.type) : undefined

  function addWidget(type: string) {
    const def = widgetByType(type)
    if (!def) return
    const bottom = config.widgets.reduce((max, w) => Math.max(max, w.layout.y + w.layout.h), 0)
    const widget = {
      id: newWidgetId(type),
      type,
      config: { ...def.defaultConfig },
      layout: { x: 0, y: bottom, w: def.defaultLayout.w, h: def.defaultLayout.h },
    }
    setConfig((c) => ({ ...c, widgets: [...c.widgets, widget] }))
    setSelectedId(widget.id)
  }

  function removeWidget(id: string) {
    setConfig((c) => ({ ...c, widgets: c.widgets.filter((w) => w.id !== id) }))
    if (selectedId === id) setSelectedId(null)
  }

  function onLayoutChange(layout: Layout[]) {
    setConfig((c) => ({
      ...c,
      widgets: c.widgets.map((w) => {
        const l = layout.find((x) => x.i === w.id)
        return l ? { ...w, layout: { x: l.x, y: l.y, w: l.w, h: l.h } } : w
      }),
    }))
  }

  function updateSelectedConfig(next: Record<string, unknown>) {
    if (!selected) return
    setConfig((c) => ({
      ...c,
      widgets: c.widgets.map((w) => (w.id === selected.id ? { ...w, config: next } : w)),
    }))
  }

  function applyPreset(name: string) {
    setConfig(PRESETS[name]())
    setSelectedId(null)
  }

  return (
    <div className="screen">
      <header className="toolbar">
        <div className="brand">ErgPower</div>
        <SourceIndicator source={source} />
        <div className="spacer" />
        <select
          className="preset-select"
          value=""
          onChange={(e) => {
            if (e.target.value) applyPreset(e.target.value)
          }}
        >
          <option value="">Preset…</option>
          {Object.keys(PRESETS).map((name) => (
            <option key={name} value={name}>
              {name}
            </option>
          ))}
        </select>
        <button onClick={() => setEditable((v) => !v)}>{editable ? 'Done' : 'Edit'}</button>
        <button onClick={onChangeSource}>Change source</button>
      </header>

      {editable && (
        <div className="edit-bar">
          <WidgetPalette onAdd={addWidget} />
        </div>
      )}

      <div className="content">
        <div className="grid-wrap">
          <DashboardRenderer
            config={config}
            editable={editable}
            selectedId={selectedId}
            onLayoutChange={onLayoutChange}
            onSelect={setSelectedId}
            onRemove={removeWidget}
          />
        </div>
        {editable && selected && selectedDef && (
          <aside className="config-aside">
            <div className="config-head">
              <h3>{selectedDef.name}</h3>
              <button onClick={() => setSelectedId(null)}>×</button>
            </div>
            <WidgetConfigPanel def={selectedDef} config={selected.config} onChange={updateSelectedConfig} />
          </aside>
        )}
      </div>
    </div>
  )
}
