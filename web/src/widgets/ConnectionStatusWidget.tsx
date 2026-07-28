import { useLiveStore } from '../store/liveStore'
import type { WidgetDef } from './types'

function ConnectionStatusWidget() {
  const connection = useLiveStore((s) => s.connection)
  const streamOpen = useLiveStore((s) => s.streamOpen)
  const state = connection?.state ?? 'DISCONNECTED'
  return (
    <div className="conn">
      <div className={`conn-dot conn-${state.toLowerCase()}`} />
      <div>
        <div className="conn-state">{state}</div>
        {connection?.device?.name && <div className="conn-sub">{connection.device.name}</div>}
        {connection?.firmware && <div className="conn-sub">fw {connection.firmware}</div>}
        {!streamOpen && <div className="conn-sub warn">stream offline</div>}
      </div>
    </div>
  )
}

export const connectionStatusDef: WidgetDef = {
  type: 'connection',
  name: 'Connection status',
  category: 'status',
  requires: ['connection'],
  defaultConfig: {},
  defaultLayout: { w: 3, h: 2 },
  render: ConnectionStatusWidget,
}
