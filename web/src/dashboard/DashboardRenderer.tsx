import GridLayout, { WidthProvider, type Layout } from 'react-grid-layout'
import 'react-grid-layout/css/styles.css'
import 'react-resizable/css/styles.css'

import { WidgetHost } from './WidgetHost'
import type { DashboardConfig } from './dashboardTypes'

const Grid = WidthProvider(GridLayout)

/**
 * Renders a dashboard config as a grid of widgets fed by the shared live store. Drag/resize is enabled
 * only in edit mode; layout changes flow back to the parent to persist.
 */
export function DashboardRenderer({
  config,
  editable,
  selectedId,
  onLayoutChange,
  onSelect,
  onRemove,
}: {
  config: DashboardConfig
  editable: boolean
  selectedId: string | null
  onLayoutChange: (layout: Layout[]) => void
  onSelect: (id: string) => void
  onRemove: (id: string) => void
}) {
  const layout: Layout[] = config.widgets.map((w) => ({ i: w.id, ...w.layout }))
  return (
    <Grid
      className="grid"
      layout={layout}
      cols={12}
      rowHeight={64}
      margin={[12, 12]}
      isDraggable={editable}
      isResizable={editable}
      draggableHandle=".widget-drag"
      compactType="vertical"
      onLayoutChange={onLayoutChange}
    >
      {config.widgets.map((w) => (
        <div key={w.id}>
          <WidgetHost
            instance={w}
            editable={editable}
            selected={selectedId === w.id}
            onSelect={onSelect}
            onRemove={onRemove}
          />
        </div>
      ))}
    </Grid>
  )
}
