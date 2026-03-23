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
      <h1 className="text-2xl font-bold mb-1">Ingest Protein Network</h1>
      <p className="text-gray-400 text-sm mb-6">
        Submit protein identifiers to fetch interaction data from STRING-DB and run spectral analysis.
      </p>

      <form onSubmit={handleSubmit} className="space-y-6 bg-gray-900 rounded-xl border border-gray-800 p-6">
        <div>
          <label className="block text-sm font-medium text-gray-200 mb-2">
            Protein Identifiers
          </label>
          <textarea
            value={identifierText}
            onChange={e => setIdentifierText(e.target.value)}
            placeholder={'TP53\nMDM2\nBRCA1\nEGFR'}
            rows={6}
            className="w-full bg-gray-800 border border-gray-700 rounded-lg px-3 py-2 text-sm font-mono text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-y"
          />
          <p className="mt-1 text-xs text-gray-500">
            One per line, or comma/space-separated. Accepts gene names, UniProt IDs, or ENSP IDs.
          </p>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-200 mb-2">
            Confidence Threshold —{' '}
            <span className="text-indigo-400 font-semibold">{requiredScore}</span>
            <span className="ml-2 text-gray-400 font-normal text-xs">{confidenceLabel(requiredScore)}</span>
          </label>
          <input
            type="range"
            min={0}
            max={1000}
            step={50}
            value={requiredScore}
            onChange={e => setRequiredScore(Number(e.target.value))}
            className="w-full accent-indigo-500"
          />
          <div className="flex justify-between text-xs text-gray-500 mt-1">
            <span>0 (none)</span>
            <span>400 (medium)</span>
            <span>700 (high)</span>
            <span>1000 (max)</span>
          </div>
        </div>

        <button
          type="submit"
          disabled={loading}
          className="w-full py-2.5 px-4 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed text-sm font-semibold transition-colors"
        >
          {loading ? 'Submitting…' : 'Run Ingestion'}
        </button>
      </form>

      {error && (
        <div className="mt-4 rounded-lg bg-red-950 border border-red-800 px-4 py-3 text-sm text-red-300">
          {error}
        </div>
      )}

      {result && (
        <div className="mt-4 rounded-lg bg-gray-900 border border-gray-700 px-4 py-4 space-y-3">
          <div className="flex items-center gap-2">
            <span
              className={`inline-block h-2 w-2 rounded-full ${
                result.status === 'SUCCESS' ? 'bg-emerald-400' : 'bg-yellow-400'
              }`}
            />
            <span className="text-sm font-semibold">{result.status}</span>
          </div>
          <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
            <dt className="text-gray-400">Job ID</dt>
            <dd className="font-mono text-gray-200 truncate">{result.jobId}</dd>
            <dt className="text-gray-400">Proteins ingested</dt>
            <dd className="text-gray-200">{result.proteinsIngested}</dd>
            <dt className="text-gray-400">Interactions ingested</dt>
            <dd className="text-gray-200">{result.interactionsIngested}</dd>
          </dl>
          {result.errorMessage && (
            <p className="text-xs text-yellow-400">{result.errorMessage}</p>
          )}
          <button
            onClick={() => router.push(`/graph/?jobId=${result.jobId}`)}
            className="mt-2 w-full py-2 px-4 rounded-lg bg-gray-700 hover:bg-gray-600 text-sm font-medium transition-colors"
          >
            View Graph &amp; Spectrum →
          </button>
        </div>
      )}
    </div>
  )
}
