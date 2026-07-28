import { WIDGETS, isAvailable } from '../widgets/registry'

/** Availability-aware palette: widgets whose required data the API doesn't serve are disabled. */
export function WidgetPalette({ onAdd }: { onAdd: (type: string) => void }) {
  return (
    <div className="palette">
      {WIDGETS.map((def) => {
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
    </div>
  )
}
