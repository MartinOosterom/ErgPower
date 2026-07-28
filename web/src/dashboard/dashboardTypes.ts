/** One placed widget: its type, per-instance config, and grid position/size. */
export interface WidgetInstance {
  id: string
  type: string
  config: Record<string, unknown>
  layout: { x: number; y: number; w: number; h: number }
}

export interface DashboardConfig {
  name: string
  widgets: WidgetInstance[]
}
