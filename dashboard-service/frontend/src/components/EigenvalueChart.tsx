'use client'

import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceLine,
  ResponsiveContainer,
  TooltipProps,
} from 'recharts'

interface Props {
  eigenvalues: number[]
  spectralGapIndex: number // kStar - 1: gap is between [i] and [i+1]
}

interface DataPoint {
  index: number
  value: number
}

function CustomTooltip({ active, payload }: TooltipProps<number, number>) {
  if (!active || !payload?.length) return null
  const { index, value } = payload[0].payload as DataPoint
  return (
    <div className="rounded-md bg-gray-800 border border-gray-700 px-3 py-2 text-xs shadow-lg">
      <p className="text-gray-400">Index <span className="text-white font-semibold">{index}</span></p>
      <p className="text-indigo-300">λ = {(value as number).toFixed(6)}</p>
    </div>
  )
}

export default function EigenvalueChart({ eigenvalues, spectralGapIndex }: Props) {
  const data: DataPoint[] = eigenvalues.map((value, index) => ({ index, value }))

  return (
    <ResponsiveContainer width="100%" height={280}>
      <LineChart data={data} margin={{ top: 4, right: 24, left: 8, bottom: 4 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
        <XAxis
          dataKey="index"
          tick={{ fill: '#9ca3af', fontSize: 11 }}
          label={{ value: 'Eigenvalue index', position: 'insideBottom', offset: -2, fill: '#6b7280', fontSize: 11 }}
        />
        <YAxis
          tick={{ fill: '#9ca3af', fontSize: 11 }}
          label={{ value: 'λ', angle: -90, position: 'insideLeft', fill: '#6b7280', fontSize: 12 }}
          domain={[0, 'auto']}
        />
        <Tooltip content={<CustomTooltip />} />
        <Line
          type="monotone"
          dataKey="value"
          stroke="#6366f1"
          strokeWidth={2}
          dot={eigenvalues.length <= 60}
          activeDot={{ r: 4, fill: '#a5b4fc' }}
          isAnimationActive={false}
        />
        <ReferenceLine
          x={spectralGapIndex}
          stroke="#f59e0b"
          strokeWidth={2}
          strokeDasharray="4 3"
          label={{
            value: `k*=${spectralGapIndex + 1}`,
            position: 'top',
            fill: '#fbbf24',
            fontSize: 11,
            fontWeight: 600,
          }}
        />
      </LineChart>
    </ResponsiveContainer>
  )
}
