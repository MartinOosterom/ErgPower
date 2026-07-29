import { useEffect, useMemo, useRef, useState } from 'react'
import type { Layout } from 'react-grid-layout'

import { SourceIndicator } from '../source/SourceIndicator'
import { widgetByType } from '../widgets/registry'
import type { SourceStatus } from '../api/types'
import { DashboardRenderer } from './DashboardRenderer'
import { WidgetConfigPanel } from './WidgetConfigPanel'
import { WidgetPalette } from './WidgetPalette'
import type { DashboardConfig } from './dashboardTypes'
import { newWidgetId } from './persistence'
import { PRESETS } from './presets'
import {
  bootstrapProfiles,
  deleteProfile,
  getProfile,
  putProfile,
  setActiveName,
} from './profiles'

/**
 * The dashboard screen: a profile picker + toolbar, an (edit) palette/config panel, and the widget
 * grid. Profiles are stored server-side via the /dashboards API; edits to the active profile autosave
 * (debounced). The active selection is per-device.
 */
export function DashboardScreen({
  source,
  onChangeSource,
}: {
  source: SourceStatus | null
  onChangeSource: () => void
}) {
  const [config, setConfig] = useState<DashboardConfig | null>(null)
  const [names, setNames] = useState<string[]>([])
  const [active, setActive] = useState<string>('')
  const [editable, setEditable] = useState(false)
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const activeRef = useRef('')
  const saveTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  useEffect(() => {
    activeRef.current = active
  }, [active])

  // Load profiles once (migrates a legacy local layout if the server has none).
  useEffect(() => {
    void bootstrapProfiles().then((b) => {
      setNames(b.names)
      setActive(b.active)
      setConfig(b.config)
    })
  }, [])

  // Debounced autosave of the active profile whenever its config changes.
  useEffect(() => {
    if (!config) return
    clearTimeout(saveTimer.current)
    saveTimer.current = setTimeout(() => {
      void putProfile(activeRef.current, config)
    }, 500)
    return () => clearTimeout(saveTimer.current)
  }, [config])

  const selected = useMemo(
    () => config?.widgets.find((w) => w.id === selectedId) ?? null,
    [config, selectedId],
  )
  const selectedDef = selected ? widgetByType(selected.type) : undefined

  function addWidget(type: string, configOverride?: Record<string, unknown>) {
    const def = widgetByType(type)
    if (!def) return
    const id = newWidgetId(type)
    setConfig((c) => {
      if (!c) return c
      const bottom = c.widgets.reduce((max, w) => Math.max(max, w.layout.y + w.layout.h), 0)
      return {
        ...c,
        widgets: [
          ...c.widgets,
          {
            id,
            type,
            config: { ...def.defaultConfig, ...configOverride },
            layout: { x: 0, y: bottom, w: def.defaultLayout.w, h: def.defaultLayout.h },
          },
        ],
      }
    })
    setSelectedId(id)
  }

  function removeWidget(id: string) {
    setConfig((c) => (c ? { ...c, widgets: c.widgets.filter((w) => w.id !== id) } : c))
    if (selectedId === id) setSelectedId(null)
  }

  function onLayoutChange(layout: Layout[]) {
    setConfig((c) =>
      c
        ? {
            ...c,
            widgets: c.widgets.map((w) => {
              const l = layout.find((x) => x.i === w.id)
              return l ? { ...w, layout: { x: l.x, y: l.y, w: l.w, h: l.h } } : w
            }),
          }
        : c,
    )
  }

  function updateSelectedConfig(next: Record<string, unknown>) {
    if (!selected) return
    setConfig((c) =>
      c ? { ...c, widgets: c.widgets.map((w) => (w.id === selected.id ? { ...w, config: next } : w)) } : c,
    )
  }

  // --- profile operations ---
  async function switchTo(name: string) {
    if (!name || name === active) return
    const cfg = await getProfile(name)
    setActive(name)
    setActiveName(name)
    setConfig(cfg ?? { name, widgets: [] })
    setSelectedId(null)
  }

  async function createProfile(name: string, seed: DashboardConfig) {
    const cfg = { ...seed, name }
    await putProfile(name, cfg)
    setNames((n) => Array.from(new Set([...n, name])).sort())
    setActive(name)
    setActiveName(name)
    setConfig(cfg)
    setSelectedId(null)
  }

  function promptNewName(defaultName: string): string | null {
    const name = window.prompt('Profile name:', defaultName)?.trim()
    if (!name) return null
    if (names.includes(name)) {
      window.alert(`A profile named "${name}" already exists.`)
      return null
    }
    return name
  }

  async function saveAsNew() {
    if (!config) return
    const name = promptNewName(`${active} copy`)
    if (name) await createProfile(name, config)
  }

  async function renameActive() {
    if (!config || !active) return
    const name = promptNewName(active)
    if (!name) return
    await putProfile(name, { ...config, name })
    await deleteProfile(active)
    setNames((n) => n.filter((x) => x !== active).concat(name).sort())
    setActive(name)
    setActiveName(name)
    setConfig({ ...config, name })
  }

  async function deleteActive() {
    if (names.length <= 1) {
      window.alert('Keep at least one profile.')
      return
    }
    if (!window.confirm(`Delete profile "${active}"?`)) return
    const target = names.filter((x) => x !== active)[0]
    await deleteProfile(active)
    setNames((n) => n.filter((x) => x !== active))
    await switchTo(target)
  }

  async function newFromPreset(preset: string) {
    const name = promptNewName(preset)
    if (name) await createProfile(name, PRESETS[preset]())
  }

  if (!config) {
    return <div className="loading">Loading dashboard…</div>
  }

  return (
    <div className="screen">
      <header className="toolbar">
        <div className="brand">ErgPower</div>
        <SourceIndicator source={source} />
        <div className="spacer" />
        <select className="preset-select" value={active} onChange={(e) => void switchTo(e.target.value)}>
          {names.map((n) => (
            <option key={n} value={n}>
              {n}
            </option>
          ))}
        </select>
        <button onClick={() => setEditable((v) => !v)}>{editable ? 'Done' : 'Edit'}</button>
        <button onClick={onChangeSource}>Change source</button>
      </header>

      {editable && (
        <div className="edit-bar">
          <div className="profile-actions">
            <button onClick={() => void saveAsNew()}>Save as new</button>
            <button onClick={() => void renameActive()}>Rename</button>
            <button onClick={() => void deleteActive()}>Delete</button>
            <select className="preset-select" value="" onChange={(e) => e.target.value && void newFromPreset(e.target.value)}>
              <option value="">New from preset…</option>
              {Object.keys(PRESETS).map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </select>
          </div>
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
