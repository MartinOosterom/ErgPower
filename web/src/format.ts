// Display formatting for SI PM5 values. Pace is seconds per 500 m; clocks are elapsed seconds.

export function round(v: number | null): string {
  return v == null ? '—' : String(Math.round(v))
}

/** Seconds/500 m → m:ss (e.g. 112 → "1:52"). */
export function fmtPace(v: number | null): string {
  if (v == null || !isFinite(v) || v <= 0) return '—'
  const s = Math.round(v)
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

/** Elapsed seconds → m:ss, or h:mm:ss past an hour. */
export function fmtClock(v: number | null): string {
  if (v == null || !isFinite(v) || v < 0) return '—'
  const s = Math.round(v)
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  return h > 0
    ? `${h}:${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
    : `${m}:${String(sec).padStart(2, '0')}`
}

export function fmtMeters(v: number | null): string {
  return v == null ? '—' : v.toFixed(0)
}

export function fmt1(v: number | null): string {
  return v == null ? '—' : v.toFixed(1)
}

export function fmt2(v: number | null): string {
  return v == null ? '—' : v.toFixed(2)
}
