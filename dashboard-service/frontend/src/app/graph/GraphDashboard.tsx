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
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold mb-1">Network Analysis</h1>
        <p className="text-gray-400 text-sm font-mono">Job: {jobId}</p>
      </div>

      {loading && (
        <div className="text-gray-400 text-sm animate-pulse">Loading graph data…</div>
      )}

      {/* Protein interaction graph */}
      <section className="bg-gray-900 rounded-xl border border-gray-800 p-4">
        <h2 className="text-base font-semibold mb-3">Protein Interaction Network</h2>
        {graphError ? (
          <p className="text-red-400 text-sm">{graphError}</p>
        ) : graph ? (
          <>
            <div className="flex gap-4 mb-3 text-xs text-gray-400">
              <span>{graph.nodes.length} proteins</span>
              <span>{graph.edges.length} interactions</span>
              {spectral && (
                <span className="text-indigo-300">
                  {spectral.kStar} natural cluster{spectral.kStar !== 1 ? 's' : ''}
                </span>
              )}
            </div>
            <ProteinGraph nodes={graph.nodes} edges={graph.edges} />
          </>
        ) : !loading ? (
          <p className="text-gray-500 text-sm">No graph data for this job.</p>
        ) : null}
      </section>

      {/* Eigenvalue spectrum */}
      <section className="bg-gray-900 rounded-xl border border-gray-800 p-4">
        <h2 className="text-base font-semibold mb-1">Eigenvalue Spectrum</h2>
        <p className="text-xs text-gray-500 mb-4">
          Sorted eigenvalues of the normalized Laplacian L<sub>sym</sub>. The vertical marker
          indicates the spectral gap (k* = {spectral?.kStar ?? '—'}).
        </p>
        {spectralError ? (
          <p className="text-red-400 text-sm">{spectralError}</p>
        ) : spectral ? (
          <>
            <div className="flex gap-6 mb-4 text-xs text-gray-400">
              <span>k* = <span className="text-indigo-300 font-semibold">{spectral.kStar}</span></span>
              <span>max gap = <span className="text-indigo-300 font-semibold">{spectral.maxGap.toFixed(4)}</span></span>
              <span>converged in <span className="text-indigo-300 font-semibold">{spectral.convergedIn}</span> iterations</span>
            </div>
            <EigenvalueChart
              eigenvalues={spectral.eigenvalues}
              spectralGapIndex={spectral.spectralGapIndex}
            />
          </>
        ) : !loading ? (
          <p className="text-gray-500 text-sm">No spectral data for this job.</p>
        ) : null}
      </section>
    </div>
  )
}
