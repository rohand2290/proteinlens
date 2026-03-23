'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'

interface IngestResponse {
  jobId: string
  status: 'SUCCESS' | 'PARTIAL_FAILURE' | 'FAILURE'
  proteinsIngested: number
  interactionsIngested: number
  errorMessage?: string
}

export default function IngestPage() {
  const router = useRouter()
  const [identifierText, setIdentifierText] = useState('')
  const [requiredScore, setRequiredScore] = useState(400)
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<IngestResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setResult(null)

    const identifiers = identifierText
      .split(/[\n,\s]+/)
      .map(s => s.trim())
      .filter(Boolean)

    if (identifiers.length === 0) {
      setError('Enter at least one protein identifier.')
      return
    }

    setLoading(true)
    try {
      const res = await fetch('/api/ingest', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ identifiers, requiredScore }),
      })

      if (!res.ok) {
        const text = await res.text()
        throw new Error(`HTTP ${res.status}: ${text}`)
      }

      const data: IngestResponse = await res.json()
      setResult(data)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Unknown error')
    } finally {
      setLoading(false)
    }
  }

  const confidenceLabel = (score: number) => {
    if (score >= 900) return 'Highest (≥900)'
    if (score >= 700) return 'High (≥700)'
    if (score >= 400) return 'Medium (≥400)'
    return 'Low (<400)'
  }

  return (
    <div className="max-w-2xl mx-auto">
      <h1 className="text-xl font-semibold mb-1 tracking-tight">Ingest Protein Network</h1>
      <p className="text-gray-500 text-sm mb-8">
        Fetch interaction data from STRING-DB and run spectral cluster analysis.
      </p>

      <form onSubmit={handleSubmit} className="space-y-7">
        <div>
          <label className="block text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
            Protein Identifiers
          </label>
          <textarea
            value={identifierText}
            onChange={e => setIdentifierText(e.target.value)}
            placeholder={'TP53\nMDM2\nBRCA1\nEGFR'}
            rows={8}
            className="w-full bg-gray-900 border border-gray-700 rounded px-3 py-2.5 text-sm font-mono text-gray-100 placeholder-gray-600 focus:outline-none focus:border-gray-500 resize-y"
          />
          <p className="mt-1.5 text-xs text-gray-600">
            One per line, or comma/space-separated. Accepts gene names, UniProt IDs, or ENSP IDs.
          </p>
        </div>

        <div>
          <div className="flex items-baseline justify-between mb-2">
            <label className="text-xs font-semibold text-gray-400 uppercase tracking-wider">
              Confidence Threshold
            </label>
            <span className="text-sm font-mono text-white">
              {requiredScore}
              <span className="ml-2 text-xs text-gray-500 font-sans">{confidenceLabel(requiredScore)}</span>
            </span>
          </div>
          <input
            type="range"
            min={0}
            max={1000}
            step={50}
            value={requiredScore}
            onChange={e => setRequiredScore(Number(e.target.value))}
            className="w-full accent-white"
          />
          <div className="flex justify-between text-xs text-gray-600 mt-1.5">
            <span>0</span>
            <span>400</span>
            <span>700</span>
            <span>1000</span>
          </div>
        </div>

        <button
          type="submit"
          disabled={loading}
          className="w-full py-2.5 px-4 rounded bg-white text-gray-950 disabled:opacity-40 disabled:cursor-not-allowed text-sm font-semibold transition-opacity hover:opacity-90"
        >
          {loading ? 'Submitting…' : 'Run Ingestion'}
        </button>
      </form>

      {error && (
        <div className="mt-6 border border-red-800 bg-red-950/40 px-4 py-3 rounded text-sm text-red-300">
          {error}
        </div>
      )}

      {result && (
        <div className="mt-6 border border-gray-800 rounded">
          <div className="px-4 py-3 border-b border-gray-800 flex items-center justify-between">
            <span className={`text-xs font-semibold uppercase tracking-wider ${
              result.status === 'SUCCESS' ? 'text-emerald-400' : 'text-yellow-400'
            }`}>
              {result.status}
            </span>
            <span className="text-xs font-mono text-gray-600 truncate ml-4">{result.jobId}</span>
          </div>
          <div className="grid grid-cols-2 divide-x divide-gray-800">
            <div className="px-4 py-4">
              <p className="text-2xl font-semibold tabular-nums">{result.proteinsIngested}</p>
              <p className="text-xs text-gray-500 mt-0.5">proteins</p>
            </div>
            <div className="px-4 py-4">
              <p className="text-2xl font-semibold tabular-nums">{result.interactionsIngested}</p>
              <p className="text-xs text-gray-500 mt-0.5">interactions</p>
            </div>
          </div>
          {result.errorMessage && (
            <div className="px-4 py-2 border-t border-gray-800 text-xs text-yellow-400">{result.errorMessage}</div>
          )}
          <div className="px-4 py-3 border-t border-gray-800">
            <button
              onClick={() => router.push(`/graph/?jobId=${result.jobId}`)}
              className="text-sm text-gray-300 hover:text-white transition-colors"
            >
              View graph &amp; spectrum →
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
