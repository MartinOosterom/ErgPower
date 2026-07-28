import type { MouseEvent } from 'react'

import { isAvailable, widgetByType } from '../widgets/registry'
import type { WidgetInstance } from './dashboardTypes'

// Keep clicks on the header buttons from starting a grid drag.
const stopDrag = (e: MouseEvent) => e.stopPropagation()

/** Chrome around a widget: a drag handle + title, and (in edit mode) configure/remove actions. */
export function WidgetHost({
  instance,
  editable,
  selected,
  onSelect,
  onRemove,
}: {
  instance: WidgetInstance
  editable: boolean
  selected: boolean
  onSelect: (id: string) => void
  onRemove: (id: string) => void
}) {
  const def = widgetByType(instance.type)
  if (!def) {
    return (
      <div className="widget">
        <div className="widget-body awaiting">Unknown widget: {instance.type}</div>
      </div>
    )
  }
  return (
    <div className={`widget${selected ? ' selected' : ''}`}>
      <div className={`widget-head${editable ? ' widget-drag' : ''}`}>
        <span className="widget-title">{def.name}</span>
        {editable && (
          <span className="widget-actions">
            <button onMouseDown={stopDrag} onClick={() => onSelect(instance.id)} title="Configure">
              ⚙
            </button>
            <button onMouseDown={stopDrag} onClick={() => onRemove(instance.id)} title="Remove">
              ✕
            </button>
          </span>
        )}
      </div>
      <div className="widget-body">
        {isAvailable(def) ? def.render({ config: instance.config }) : <div className="awaiting">Unavailable</div>}
      </div>
    </div>
  )
}
