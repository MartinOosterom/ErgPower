import { useEffect, useState } from 'react'

import { api } from '../api/client'
import { useLiveStore } from '../store/liveStore'
import type { DiscoveredDevice, SessionSummary, SourceRequest } from '../api/types'
import { fmtClock, fmtMeters } from '../format'

/**
 * Entry screen (spec: "Source-selection entry"): connect to an erg or replay a stored session, both
 * feeding the same /live/stream. Consumes the add-source-control API (/sessions, /devices, /source).
 */
export function SourceSelect({ onStarted }: { onStarted: () => void }) {
  const [tab, setTab] = useState<'replay' | 'connect'>('replay')
  const [sessions, setSessions] = useState<SessionSummary[]>([])
  const [devices, setDevices] = useState<DiscoveredDevice[]>([])
  const [speed, setSpeed] = useState(2)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    void refreshSessions()
  }, [])

  async function refreshSessions() {
    const { data } = await api.GET('/sessions')
    setSessions(data ?? [])
  }

  async function scan() {
    setBusy(true)
    setError(null)
    const { data } = await api.GET('/devices')
    setDevices(data ?? [])
    setBusy(false)
  }

  async function start(body: SourceRequest) {
    setBusy(true)
    setError(null)
    const { error: err } = await api.POST('/source', { body })
    setBusy(false)
    if (err) {
      const problem = err as { title?: string; detail?: string }
      setError(problem.detail ?? problem.title ?? 'Failed to start source')
      return
    }
    useLiveStore.getState().reset() // clear any previous source's data before the new stream
    onStarted()
  }

  return (
    <div className="select-screen">
      <div className="select-card">
        <h1>ErgPower</h1>
        <p className="select-sub">Pick a source — both feed the same live dashboard.</p>

        <div className="tabs">
          <button className={tab === 'replay' ? 'active' : ''} onClick={() => setTab('replay')}>
            Replay a session
          </button>
          <button className={tab === 'connect' ? 'active' : ''} onClick={() => setTab('connect')}>
            Connect to erg
          </button>
        </div>

        {error && <div className="error">{error}</div>}

        {tab === 'replay' ? (
          <div className="tab-body">
            <div className="row">
              <label>
                Speed ×
                <input
                  type="number"
                  min={0.25}
                  max={50}
                  step={0.25}
                  value={speed}
                  onChange={(e) => setSpeed(Number(e.target.value))}
                />
              </label>
              <button onClick={() => void refreshSessions()} disabled={busy}>
                Refresh
              </button>
            </div>
            {sessions.length === 0 && <div className="empty">No stored sessions yet.</div>}
            <ul className="list">
              {sessions.map((s) => (
                <li key={s.id}>
                  <div className="list-main">
                    <div className="list-title">{s.id}</div>
                    <div className="list-sub">
                      {fmtMeters(s.distanceM ?? null)} m · {s.strokes ?? '—'} strokes ·{' '}
                      {fmtClock(s.durationS ?? null)}
                      {s.avgPowerW != null ? ` · ${s.avgPowerW} W avg` : ''}
                    </div>
                  </div>
                  <button
                    disabled={busy || !s.replayable}
                    title={s.replayable ? '' : 'no raw frames — not replayable'}
                    onClick={() => void start({ type: 'replay', sessionId: s.id, speed })}
                  >
                    {s.replayable ? 'Replay' : 'N/A'}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        ) : (
          <div className="tab-body">
            <div className="row">
              <button onClick={() => void scan()} disabled={busy}>
                {busy ? 'Scanning…' : 'Scan for PM5s'}
              </button>
              <button onClick={() => void start({ type: 'ble' })} disabled={busy}>
                Connect to first PM5
              </button>
            </div>
            <ul className="list">
              {devices.map((d) => (
                <li key={d.address}>
                  <div className="list-main">
                    <div className="list-title">{d.name}</div>
                    <div className="list-sub">
                      {d.address}
                      {d.rssi != null ? ` · ${d.rssi} dBm` : ''}
                    </div>
                  </div>
                  <button disabled={busy} onClick={() => void start({ type: 'ble', device: d.name })}>
                    Connect
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </div>
  )
}
