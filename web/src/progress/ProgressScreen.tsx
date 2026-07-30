import { useEffect, useMemo, useState } from 'react'
import type { EChartsOption } from 'echarts'

import { api } from '../api/client'
import { EChart } from '../components/EChart'
import { Markdown } from '../components/Markdown'
import type { CoachResult, LlmStatus, SessionIndexEntry } from '../api/types'

const ACCENT = '#38bdf8'
const MAX_SELECT = 12 // cap the comparison set (tokens/latency)
const TECH = [
  { key: 'catchGradient', label: 'catch gradient', color: ACCENT },
  { key: 'peakPosition', label: 'peak position', color: '#a78bfa' },
  { key: 'finishPlateau', label: 'finish plateau', color: '#f472b6' },
] as const

/**
 * The cross-session Progress dashboard: pick a set of sessions from the index, see technique trends over
 * the set, and get progress coaching and a set-scoped agent chat. Distinct from the single-session
 * Analysis view (change progress-dashboard).
 */
export function ProgressScreen({ onBack }: { onBack: () => void }) {
  const [entries, setEntries] = useState<SessionIndexEntry[]>([])
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [targetType, setTargetType] = useState<'all' | 'time' | 'distance'>('all')
  const [llm, setLlm] = useState<LlmStatus | null>(null)

  useEffect(() => {
    void (async () => {
      const { data } = await api.GET('/sessions/index')
      if (data) setEntries(data)
    })()
    void (async () => {
      const { data } = await api.GET('/integrations/llm')
      if (data) setLlm(data)
    })()
  }, [])

  const shown = useMemo(
    () => entries.filter((e) => targetType === 'all' || e.targetType === targetType),
    [entries, targetType],
  )
  const selectedEntries = useMemo(
    () =>
      entries
        .filter((e) => selected.has(e.id))
        .slice()
        .sort((a, b) => (a.startedAt ?? '').localeCompare(b.startedAt ?? '')), // oldest → newest
    [entries, selected],
  )
  const selectedIds = selectedEntries.map((e) => e.id)

  const toggle = (id: string) => {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else if (next.size < MAX_SELECT) next.add(id)
      return next
    })
  }

  const trendOption = useMemo<EChartsOption | null>(() => {
    if (selectedEntries.length < 2) return null
    const x = selectedEntries.map((e) => (e.startedAt ?? e.id).slice(0, 16).replace('T', ' '))
    return {
      animation: false,
      grid: { left: 40, right: 12, top: 30, bottom: 40 },
      title: { text: 'Technique over the selected sessions (%)', textStyle: { fontSize: 12, fontWeight: 'normal' } },
      legend: { bottom: 0, textStyle: { fontSize: 10 } },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: x, axisLabel: { fontSize: 9, rotate: 20 }, splitLine: { show: false } },
      yAxis: { type: 'value', scale: true, splitLine: { show: false } },
      series: TECH.map((m) => ({
        name: m.label,
        type: 'line',
        showSymbol: true,
        lineStyle: { width: 2, color: m.color },
        itemStyle: { color: m.color },
        data: selectedEntries.map((e) => (e.scores ? (e.scores[m.key] ?? null) : null)),
      })),
    }
  }, [selectedEntries])

  return (
    <div className="progress-screen">
      <header className="toolbar">
        <button onClick={onBack}>← Sessions</button>
        <div className="brand">Progress</div>
        <div className="conn-sub">{selected.size} selected{selected.size >= MAX_SELECT ? ` (max ${MAX_SELECT})` : ''}</div>
      </header>

      <section className="progress-body">
        <aside className="progress-picker">
          <div className="picker-filter">
            <label>Type
              <select value={targetType} onChange={(e) => setTargetType(e.target.value as 'all' | 'time' | 'distance')}>
                <option value="all">all</option>
                <option value="distance">distance</option>
                <option value="time">time</option>
              </select>
            </label>
          </div>
          <ul className="picker-list">
            {shown.map((e) => (
              <li key={e.id} className={selected.has(e.id) ? 'picked' : ''}>
                <label>
                  <input
                    type="checkbox"
                    checked={selected.has(e.id)}
                    disabled={!selected.has(e.id) && selected.size >= MAX_SELECT}
                    onChange={() => toggle(e.id)}
                  />
                  <span className="picker-id">{e.id}</span>
                  <span className="picker-sub">
                    {e.targetType} · {e.distanceM != null ? `${Math.round(e.distanceM)} m` : '—'}
                    {e.scores?.catchGradient != null ? ` · catch ${e.scores.catchGradient}%` : ' · no curves'}
                  </span>
                </label>
              </li>
            ))}
            {shown.length === 0 && <li className="empty">No sessions.</li>}
          </ul>
        </aside>

        <div className="progress-main">
          {selectedEntries.length < 2 ? (
            <div className="empty">Select at least two sessions to compare.</div>
          ) : (
            <>
              {trendOption && <div className="chart-box"><EChart option={trendOption} /></div>}
              {llm?.configured && <ProgressCoach ids={selectedIds} llm={llm} />}
              {llm?.configured && <SetChat ids={selectedIds} />}
            </>
          )}
        </div>
      </section>
    </div>
  )
}

/** Progress coaching over the selected set (POST /coach/progress). */
function ProgressCoach({ ids, llm }: { ids: string[]; llm: LlmStatus }) {
  const [coaching, setCoaching] = useState<CoachResult | null>(null)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  const generate = async () => {
    setBusy(true)
    setErr(null)
    const { data, error } = await api.POST('/coach/progress', { body: { sessions: ids } })
    if (error || !data) setErr('Coaching is unavailable right now.')
    else setCoaching(data)
    setBusy(false)
  }

  return (
    <section className="coach-panel">
      <div className="coach-head">
        <div className="coach-title">Progress coach <span className="coach-provider">{llm.provider}{llm.model ? ` · ${llm.model}` : ''}</span></div>
        <button className="coach-btn" onClick={() => void generate()} disabled={busy}>
          {busy ? 'Coaching…' : coaching ? 'Regenerate' : 'Coach my progress'}
        </button>
      </div>
      {err && <div className="coach-error">{err}</div>}
      {coaching && <p className="coach-text">{coaching.text}</p>}
      {!coaching && !err && <p className="coach-hint">Narrates how your technique changed across the selected sessions.</p>}
    </section>
  )
}

/** Set-scoped agent chat (POST /chat with the selected session ids). */
function SetChat({ ids }: { ids: string[] }) {
  const [turns, setTurns] = useState<{ role: 'user' | 'assistant'; content: string }[]>([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)

  const send = async () => {
    const q = input.trim()
    if (!q || busy) return
    const history = [...turns, { role: 'user' as const, content: q }]
    setTurns([...history, { role: 'assistant', content: '' }])
    setInput('')
    setBusy(true)
    try {
      const res = await fetch('/api/v1/chat', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ sessions: ids, messages: history }),
      })
      if (!res.ok || !res.body) throw new Error('chat failed')
      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buf = ''
      for (;;) {
        const { value, done } = await reader.read()
        if (done) break
        buf += decoder.decode(value, { stream: true })
        const blocks = buf.split('\n\n')
        buf = blocks.pop() ?? ''
        for (const block of blocks) {
          let ev = ''
          const dataLines: string[] = []
          for (const line of block.split('\n')) {
            if (line.startsWith('event:')) ev = line.slice(6).trim()
            else if (line.startsWith('data:')) dataLines.push(line.slice(5))
          }
          const data = dataLines.join('\n') // SSE: multiple data: lines rejoin with newlines (Markdown needs them)
          if (ev === 'token' && data) {
            setTurns((t) => {
              const copy = [...t]
              copy[copy.length - 1] = { role: 'assistant', content: copy[copy.length - 1].content + data }
              return copy
            })
          }
        }
      }
    } catch {
      setTurns((t) => {
        const copy = [...t]
        copy[copy.length - 1] = { role: 'assistant', content: '(the agent is unavailable right now)' }
        return copy
      })
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="chat-panel">
      <div className="chat-title">Ask across the selected sessions</div>
      <div className="chat-log">
        {turns.length === 0 && <p className="chat-hint">e.g. “which of these had my best finish?” or “am I improving my catch?”.</p>}
        {turns.map((t, i) => (
          <div key={i} className={`chat-msg chat-${t.role}`}>
            {t.role === 'assistant'
              ? t.content
                ? <Markdown>{t.content}</Markdown>
                : busy && i === turns.length - 1 ? 'Thinking…' : ''
              : t.content}
          </div>
        ))}
      </div>
      <div className="chat-input">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') void send() }}
          placeholder="Ask a question…"
          disabled={busy}
        />
        <button className="coach-btn" onClick={() => void send()} disabled={busy || !input.trim()}>Send</button>
      </div>
    </section>
  )
}
