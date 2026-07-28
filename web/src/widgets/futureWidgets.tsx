import type { WidgetDef } from './types'

// Widgets registered ahead of API support. Their `requires` keys aren't in the live store yet, so the
// palette shows them disabled — they light up unchanged once the API serves splits/summary data.
function AwaitingApi({ what }: { what: string }) {
  return <div className="awaiting">Awaiting API support: {what}</div>
}

export const splitsDef: WidgetDef = {
  type: 'splits',
  name: 'Splits (soon)',
  category: 'metric',
  requires: ['splits'],
  defaultConfig: {},
  defaultLayout: { w: 4, h: 3 },
  render: () => <AwaitingApi what="per-split table" />,
}

export const summaryDef: WidgetDef = {
  type: 'summary',
  name: 'Session summary (soon)',
  category: 'metric',
  requires: ['summary'],
  defaultConfig: {},
  defaultLayout: { w: 4, h: 3 },
  render: () => <AwaitingApi what="end-of-piece summary" />,
}
