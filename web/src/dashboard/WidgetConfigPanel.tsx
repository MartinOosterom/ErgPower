import type { AnyWidgetDef, ConfigField } from '../widgets/types'

/** Renders a widget's `configFields` as a small form; changes flow back via onChange. */
export function WidgetConfigPanel({
  def,
  config,
  onChange,
}: {
  def: AnyWidgetDef
  config: Record<string, unknown>
  onChange: (next: Record<string, unknown>) => void
}) {
  const fields = def.configFields ?? []
  if (fields.length === 0) return <div className="cfg-empty">This widget has no options.</div>

  const set = (key: string, value: unknown) => onChange({ ...config, [key]: value })

  return (
    <div className="cfg">
      {fields.map((f) => (
        <label key={f.key} className="cfg-field">
          <span>{f.label}</span>
          {renderInput(f, config[f.key], (v) => set(f.key, v))}
        </label>
      ))}
    </div>
  )
}

function renderInput(field: ConfigField, value: unknown, onChange: (v: unknown) => void) {
  switch (field.type) {
    case 'select':
      return (
        <select value={String(value ?? '')} onChange={(e) => onChange(e.target.value)}>
          {field.options.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      )
    case 'number':
      return (
        <input
          type="number"
          value={Number(value ?? 0)}
          min={field.min}
          max={field.max}
          step={field.step}
          onChange={(e) => onChange(Number(e.target.value))}
        />
      )
    case 'boolean':
      return <input type="checkbox" checked={Boolean(value)} onChange={(e) => onChange(e.target.checked)} />
  }
}
