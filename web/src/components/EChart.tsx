import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'

/**
 * Thin ECharts wrapper: init on mount, re-`setOption` when `option` changes, resize with the box,
 * dispose on unmount. Real-time widgets (trends, force curve) reuse it so their re-renders stay cheap.
 *
 * The chart lives in an absolutely-positioned inner div inside a relative, overflow-clipped box. This
 * takes the (fixed-pixel-width) ECharts canvas out of layout flow so it can never widen its own
 * container — otherwise a `width:100%` canvas feeds its measured width back to a flex/grid parent that
 * has nothing constraining it, and the chart grows past the viewport.
 */
export function EChart({ option, className }: { option: echarts.EChartsOption; className?: string }) {
  const ref = useRef<HTMLDivElement>(null)
  const chart = useRef<echarts.ECharts | null>(null)

  useEffect(() => {
    if (!ref.current) return
    chart.current = echarts.init(ref.current)
    const ro = new ResizeObserver(() => chart.current?.resize())
    ro.observe(ref.current)
    return () => {
      ro.disconnect()
      chart.current?.dispose()
      chart.current = null
    }
  }, [])

  useEffect(() => {
    chart.current?.setOption(option, { notMerge: true })
  }, [option])

  return (
    <div className={className} style={{ position: 'relative', width: '100%', height: '100%', overflow: 'hidden' }}>
      <div ref={ref} style={{ position: 'absolute', inset: 0 }} />
    </div>
  )
}
