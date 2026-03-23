'use client'

import { useSearchParams } from 'next/navigation'
import { useEffect, useState } from 'react'
import ProteinGraph from '@/components/ProteinGraph'
import EigenvalueChart from '@/components/EigenvalueChart'

interface NodeDto {
  id: string
  label: string
  clusterId: number
  centrality: number
}

interface EdgeDto {
  source: string
  target: string
  score: number
}

interface GraphResponse {
  nodes: NodeDto[]
  edges: EdgeDto[]
}

interface SpectralResponse {
  eigenvalues: number[]
  kStar: number
  maxGap: number
  spectralGapIndex: number
  convergedIn: number
}

export default function GraphDashboard() {
  const searchParams = useSearchParams()
  const jobId = searchParams.get('jobId') ?? ''

  const [graph, setGraph] = useState<GraphResponse | null>(null)
  const [spectral, setSpectral] = useState<SpectralResponse | null>(null)
  const [graphError, setGraphError] = useState<string | null>(null)
  const [spectralError, setSpectralError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!jobId) return

    setLoading(true)
    setGraphError(null)
    setSpectralError(null)

    const fetchGraph = fetch(`/api/graph/${jobId}`)
      .then(r => {
        if (!r.ok) throw new Error(`Graph fetch failed: HTTP ${r.status}`)
        return r.json() as Promise<GraphResponse>
      })
      .then(setGraph)
      .catch(e => setGraphError(e.message))

    const fetchSpectral = fetch(`/api/spectral/${jobId}`)
      .then(r => {
        if (!r.ok) throw new Error(`Spectral fetch failed: HTTP ${r.status}`)
        return r.json() as Promise<SpectralResponse>
      })
      .then(setSpectral)
      .catch(e => setSpectralError(e.message))

    Promise.all([fetchGraph, fetchSpectral]).finally(() => setLoading(false))
  }, [jobId])

  if (!jobId) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-center space-y-2">
          <p className="text-gray-400">No job selected.</p>
          <a href="/" className="text-indigo-400 hover:underline text-sm">
            Submit an ingestion job first →
          </a>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-10">
      <div className="flex items-baseline justify-between border-b border-gray-800 pb-4">
        <h1 className="text-xl font-semibold tracking-tight">Network Analysis</h1>
        <span className="text-xs font-mono text-gray-600">{jobId}</span>
      </div>

      {loading && (
        <div className="text-gray-600 text-sm">Loading…</div>
      )}

      {/* Protein interaction graph */}
      <section>
        <div className="flex items-baseline justify-between mb-4">
          <h2 className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Protein Interaction Network</h2>
          {graph && (
            <div className="flex gap-5 text-xs text-gray-500 tabular-nums">
              <span><span className="text-white font-medium">{graph.nodes.length}</span> proteins</span>
              <span><span className="text-white font-medium">{graph.edges.length}</span> interactions</span>
              {spectral && (
                <span><span className="text-white font-medium">{spectral.kStar}</span> cluster{spectral.kStar !== 1 ? 's' : ''}</span>
              )}
            </div>
          )}
        </div>
        {graphError ? (
          <p className="text-red-400 text-sm">{graphError}</p>
        ) : graph ? (
          <ProteinGraph nodes={graph.nodes} edges={graph.edges} />
        ) : !loading ? (
          <p className="text-gray-600 text-sm">No graph data for this job.</p>
        ) : null}
      </section>

      {/* Eigenvalue spectrum */}
      <section>
        <div className="flex items-baseline justify-between mb-4">
          <h2 className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Eigenvalue Spectrum</h2>
          {spectral && (
            <div className="flex gap-5 text-xs text-gray-500 tabular-nums">
              <span>k* = <span className="text-white font-medium">{spectral.kStar}</span></span>
              <span>max gap = <span className="text-white font-medium font-mono">{spectral.maxGap.toFixed(4)}</span></span>
              <span>converged in <span className="text-white font-medium">{spectral.convergedIn}</span> iters</span>
            </div>
          )}
        </div>
        <p className="text-xs text-gray-600 mb-4">
          Sorted eigenvalues of the normalized Laplacian L<sub>sym</sub>. Vertical marker indicates the spectral gap.
        </p>
        {spectralError ? (
          <p className="text-red-400 text-sm">{spectralError}</p>
        ) : spectral ? (
          <EigenvalueChart
            eigenvalues={spectral.eigenvalues}
            spectralGapIndex={spectral.spectralGapIndex}
          />
        ) : !loading ? (
          <p className="text-gray-600 text-sm">No spectral data for this job.</p>
        ) : null}
      </section>
    </div>
  )
}
