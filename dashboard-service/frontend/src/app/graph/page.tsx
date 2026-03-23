import { Suspense } from 'react'
import GraphDashboard from './GraphDashboard'

export default function GraphPage() {
  return (
    <Suspense fallback={<div className="text-gray-400 text-sm">Loading…</div>}>
      <GraphDashboard />
    </Suspense>
  )
}
