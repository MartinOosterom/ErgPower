import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'

/**
 * Thin ECharts wrapper: init on mount, re-`setOption` when `option` changes, resize with the box,
 * dispose on unmount. Real-time widgets (trends, force curve) reuse it so their re-renders stay cheap.
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

  return <div ref={ref} className={className} style={{ width: '100%', height: '100%' }} />
}
