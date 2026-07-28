import type { ReactNode } from 'react'
import type { DataKey } from '../store/liveStore'

export type WidgetCategory = 'metric' | 'chart' | 'status' | 'goal'

/** A configurable option for a widget instance, rendered by the config panel. */
export type ConfigField =
  | { key: string; label: string; type: 'select'; options: { value: string; label: string }[] }
  | { key: string; label: string; type: 'number'; min?: number; max?: number; step?: number }
  | { key: string; label: string; type: 'boolean' }

export interface WidgetProps<C> {
  config: C
}

/**
 * A self-describing widget: its type/name/category, the store data it `requires` (used to gate the
 * palette by availability), its default config + config fields, a default grid size, and how to render.
 */
export interface WidgetDef<C = Record<string, unknown>> {
  type: string
  name: string
  category: WidgetCategory
  requires: DataKey[]
  defaultConfig: C
  configFields?: ConfigField[]
  defaultLayout: { w: number; h: number }
  render: (props: WidgetProps<C>) => ReactNode
}

// The registry is heterogeneous in config type; instances erase C at the boundary.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type AnyWidgetDef = WidgetDef<any>
