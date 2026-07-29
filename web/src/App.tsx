import { useEffect, useState } from 'react'

import { api } from './api/client'
import { useLiveStore } from './store/liveStore'
import type { SourceStatus } from './api/types'
import { AnalysisScreen } from './analysis/AnalysisScreen'
import { DashboardScreen } from './dashboard/DashboardScreen'
import { SourceSelect } from './source/SourceSelect'

/**
 * Top level: open the single live stream, learn the active source, and route between the
 * source-selection screen (no source, or "change source") and the dashboard.
 */
export default function App() {
  const [source, setSource] = useState<SourceStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const [forceSelect, setForceSelect] = useState(false)
  const [analyze, setAnalyze] = useState<string | null>(null)

  useEffect(() => {
    useLiveStore.getState().start() // one SSE connection for the app's lifetime
    void refresh()
  }, [])

  async function refresh() {
    const { data } = await api.GET('/source')
    setSource(data ?? null)
    setLoading(false)
  }

  if (loading) return <div className="loading">Connecting…</div>

  if (analyze) {
    return <AnalysisScreen id={analyze} onBack={() => setAnalyze(null)} />
  }

  const showSelect = forceSelect || !source || source.sourceType === 'NONE'
  if (showSelect) {
    return (
      <SourceSelect
        onStarted={async () => {
          await refresh()
          setForceSelect(false)
        }}
        onAnalyze={setAnalyze}
      />
    )
  }
  return <DashboardScreen source={source} onChangeSource={() => setForceSelect(true)} />
}
