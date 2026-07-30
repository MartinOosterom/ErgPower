import { useEffect, useMemo, useState } from 'react'
import type { EChartsOption } from 'echarts'

import { api } from '../api/client'
import { EChart } from '../components/EChart'
import type { CoachResult, LlmStatus, ScoreMetric, SessionAnalysis } from '../api/types'

const ACCENT = '#38bdf8'

/**
 * The deterministic technique-analysis view for one stored session: a Kleshnev-grounded scorecard, the
 * mean±spread drive-force curve, per-feature drift trends, a whole-session force heatmap, and fault
 * flags. Renders fully with no LLM; when a provider is configured (GET /integrations/llm), an optional
 * "AI Coach" panel narrates the analysis on demand (change add-llm-coach).
 */
export function AnalysisScreen({ id, onBack }: { id: string; onBack: () => void }) {
  const [analysis, setAnalysis] = useState<SessionAnalysis | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [llm, setLlm] = useState<LlmStatus | null>(null)

  useEffect(() => {
    void (async () => {
      const { data, error: err } = await api.GET('/sessions/{id}/analysis', { params: { path: { id } } })
      if (err || !data) setError('Could not analyse this session.')
      else setAnalysis(data)
    })()
  }, [id])

  // Is an LLM coach configured? Only then do we show the panel (deterministic view is complete without it).
  useEffect(() => {
    void (async () => {
      const { data } = await api.GET('/integrations/llm')
      if (data) setLlm(data)
    })()
  }, [])

  const meanOption = useMemo<EChartsOption | null>(() => {
    const band = analysis?.meanCurve ?? []
    if (band.length === 0) return null
    const x = (p: { x?: number }) => (p.x ?? 0) * 100
    return {
      animation: false,
      grid: { left: 46, right: 12, top: 28, bottom: 30 },
      title: { text: 'Average drive force (± spread)', textStyle: { fontSize: 12, fontWeight: 'normal' } },
      xAxis: { type: 'value', min: 0, max: 100, name: 'drive %', nameLocation: 'middle', nameGap: 20, splitLine: { show: false } },
      yAxis: { type: 'value', name: 'N', splitLine: { show: false } },
      series: [
        { type: 'line', data: band.map((p) => [x(p), p.lower]), lineStyle: { opacity: 0 }, stack: 'band', symbol: 'none', silent: true },
        { type: 'line', data: band.map((p) => [x(p), (p.upper ?? 0) - (p.lower ?? 0)]), lineStyle: { opacity: 0 }, areaStyle: { color: ACCENT, opacity: 0.15 }, stack: 'band', symbol: 'none', silent: true },
        { type: 'line', data: band.map((p) => [x(p), p.mean]), lineStyle: { width: 2, color: ACCENT }, symbol: 'none' },
      ],
    }
  }, [analysis])

  const heatmapOption = useMemo<EChartsOption | null>(() => {
    const curves = analysis?.curves ?? []
    if (curves.length === 0) return null
    const bins = curves[0]?.length ?? 0
    const data: [number, number, number][] = []
    let max = 1
    curves.forEach((row, s) =>
      row.forEach((v, b) => {
        data.push([s, b, v])
        if (v > max) max = v
      }),
    )
    return {
      animation: false,
      grid: { left: 46, right: 12, top: 28, bottom: 30 },
      title: { text: 'Every stroke (force across the drive)', textStyle: { fontSize: 12, fontWeight: 'normal' } },
      tooltip: { position: 'top' },
      xAxis: { type: 'category', name: 'stroke', data: curves.map((_, s) => String(s + 1)), axisLabel: { show: false }, splitArea: { show: false } },
      yAxis: { type: 'category', name: 'drive %', data: Array.from({ length: bins }, (_, b) => String(Math.round((b / (bins - 1)) * 100))), axisLabel: { show: false } },
      visualMap: { min: 0, max, calculable: true, orient: 'horizontal', left: 'center', bottom: 0, itemHeight: 60, inRange: { color: ['#0e1116', '#1e40af', ACCENT, '#fef08a'] } },
      series: [{ type: 'heatmap', data, progressive: 4000 }],
    }
  }, [analysis])

  if (error) {
    return (
      <div className="analysis-screen">
        <Header id={id} onBack={onBack} />
        <div className="empty">{error}</div>
      </div>
    )
  }
  if (!analysis) {
    return (
      <div className="analysis-screen">
        <Header id={id} onBack={onBack} />
        <div className="loading">Analysing…</div>
      </div>
    )
  }
  if (!analysis.hasCurves) {
    return (
      <div className="analysis-screen">
        <Header id={id} onBack={onBack} />
        <div className="empty">This session has no force-curve data to analyse.</div>
      </div>
    )
  }

  const trends = analysis.trends ?? []

  return (
    <div className="analysis-screen">
      <Header id={id} onBack={onBack} strokes={analysis.strokes ?? undefined} />

      <section className="scorecard-grid">
        {(analysis.scorecard ?? []).map((m) => (
          <ScoreCard key={m.key} metric={m} />
        ))}
      </section>

      {(analysis.flags ?? []).length > 0 && (
        <section className="flags">
          {analysis.flags!.map((f, i) => (
            <div key={i} className={`flag flag-${f.severity}`}>
              <span className="flag-badge">{f.severity === 'warn' ? '!' : 'i'}</span>
              {f.message}
            </div>
          ))}
        </section>
      )}

      {llm?.configured && <CoachPanel id={id} llm={llm} />}
      {llm?.configured && <ChatPanel id={id} />}

      <section className="analysis-charts">
        {meanOption && <div className="chart-box"><EChart option={meanOption} /></div>}
        {heatmapOption && <div className="chart-box"><EChart option={heatmapOption} /></div>}
        {trends.map((t) => (
          <div key={t.key} className="chart-box">
            <EChart option={trendOption(t)} />
          </div>
        ))}
      </section>
    </div>
  )
}

/**
 * Optional AI coach: grounded natural-language coaching generated on demand from this session's
 * deterministic analysis. Shown only when a provider is configured; the model narrates the numbers.
 */
function CoachPanel({ id, llm }: { id: string; llm: LlmStatus }) {
  const [coaching, setCoaching] = useState<CoachResult | null>(null)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  // This view is single-session; progress/cross-session coaching lives on the Progress dashboard.
  const generate = async () => {
    setBusy(true)
    setErr(null)
    const { data, error } = await api.GET('/sessions/{id}/coach', { params: { path: { id } } })
    if (error || !data) setErr('Coaching is unavailable right now.')
    else setCoaching(data)
    setBusy(false)
  }

  return (
    <section className="coach-panel">
      <div className="coach-head">
        <div className="coach-title">AI Coach <span className="coach-provider">{llm.provider}{llm.model ? ` · ${llm.model}` : ''}</span></div>
        <button className="coach-btn" onClick={() => void generate()} disabled={busy}>
          {busy ? 'Coaching…' : coaching ? 'Regenerate' : 'Generate coaching'}
        </button>
      </div>
      {err && <div className="coach-error">{err}</div>}
      {coaching && <p className="coach-text">{coaching.text}</p>}
      {!coaching && !err && <p className="coach-hint">Grounded in the metrics above — no raw curves leave your machine unless you configure a cloud provider.</p>}
    </section>
  )
}

type ChatTurn = { role: 'user' | 'assistant'; content: string }

/**
 * Optional agent chat: multi-turn Q&A about the session (and, via the agent's tools, across sessions),
 * grounded in the stored data and streamed token-by-token over SSE. Shown only when a provider is
 * configured; the transcript is held here (client-side) and sent each turn — nothing is persisted.
 */
function ChatPanel({ id }: { id: string }) {
  const [turns, setTurns] = useState<ChatTurn[]>([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)

  const send = async () => {
    const q = input.trim()
    if (!q || busy) return
    const history: ChatTurn[] = [...turns, { role: 'user', content: q }]
    setTurns([...history, { role: 'assistant', content: '' }])
    setInput('')
    setBusy(true)
    try {
      const res = await fetch(`/api/v1/sessions/${encodeURIComponent(id)}/chat`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ messages: history }),
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
          let data = ''
          for (const line of block.split('\n')) {
            if (line.startsWith('event:')) ev = line.slice(6).trim()
            else if (line.startsWith('data:')) data += line.slice(5) // no space-strip: preserve token spacing
          }
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
      <div className="chat-title">Ask about this session</div>
      <div className="chat-log">
        {turns.length === 0 && (
          <p className="chat-hint">e.g. “how did my catch hold up in the second half?” or “compare this piece to my last 2k”.</p>
        )}
        {turns.map((t, i) => (
          <div key={i} className={`chat-msg chat-${t.role}`}>
            {t.content || (busy && i === turns.length - 1 ? 'Thinking…' : '')}
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

function Header({ id, onBack, strokes }: { id: string; onBack: () => void; strokes?: number }) {
  return (
    <header className="toolbar">
      <button onClick={onBack}>← Sessions</button>
      <div className="brand">Technique analysis</div>
      <div className="conn-sub">{id}{strokes ? ` · ${strokes} strokes` : ''}</div>
    </header>
  )
}

function ScoreCard({ metric }: { metric: ScoreMetric }) {
  const cls = metric.pass == null ? 'neutral' : metric.pass ? 'good' : 'bad'
  return (
    <div className={`score-card score-${cls}`} title={metric.note ?? ''}>
      <div className="score-label">{metric.label}</div>
      <div className="score-value">
        {metric.value != null ? metric.value : '—'}
        <span className="score-unit">{metric.unit}</span>
      </div>
      <div className="score-target">{targetText(metric)}</div>
    </div>
  )
}

function targetText(m: ScoreMetric): string {
  const u = m.unit ?? ''
  if (m.targetMin != null && m.targetMax != null) return `target ${m.targetMin}–${m.targetMax}${u}`
  if (m.targetMax != null) return `target ≤ ${m.targetMax}${u}`
  if (m.targetMin != null) return `target ≥ ${m.targetMin}${u}`
  return 'no target'
}

function trendOption(t: NonNullable<SessionAnalysis['trends']>[number]): EChartsOption {
  return {
    animation: false,
    grid: { left: 46, right: 12, top: 24, bottom: 24 },
    title: { text: `${t.label} across the piece`, textStyle: { fontSize: 12, fontWeight: 'normal' } },
    xAxis: { type: 'value', name: 'stroke', min: 'dataMin', splitLine: { show: false } },
    yAxis: { type: 'value', scale: true, name: t.unit, splitLine: { show: false } },
    series: [{ type: 'line', showSymbol: false, lineStyle: { width: 2, color: ACCENT }, data: (t.points ?? []).map((p) => [p.stroke, p.value]) }],
  }
}
