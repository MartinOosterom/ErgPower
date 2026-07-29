// Dashboard profiles are persisted server-side via the /dashboards API (see profiles.ts); this module
// keeps only the widget-id helper.

let idSeq = 0
export function newWidgetId(type: string): string {
  idSeq += 1
  return `${type}-${Date.now().toString(36)}-${idSeq}`
}
