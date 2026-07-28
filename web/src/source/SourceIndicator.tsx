import { useLiveStore } from '../store/liveStore'
import type { SourceStatus } from '../api/types'

/** Which source is feeding the dashboard (live PM5 vs replaying a session), plus stream liveness. */
export function SourceIndicator({ source }: { source: SourceStatus | null }) {
  const streamOpen = useLiveStore((s) => s.streamOpen)
  const type = source?.sourceType ?? 'NONE'
  const label =
    type === 'BLE' ? 'Live PM5' : type === 'REPLAY' ? `Replaying ${source?.sessionId ?? ''}` : 'No source'
  return (
    <div className={`source-badge source-${type.toLowerCase()}`}>
      <span className={`live-dot${streamOpen ? ' on' : ''}`} />
      {label}
    </div>
  )
}
