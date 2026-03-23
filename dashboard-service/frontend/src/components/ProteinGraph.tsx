'use client'

import { useEffect, useRef } from 'react'
import type cytoscape from 'cytoscape'

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

interface Props {
  nodes: NodeDto[]
  edges: EdgeDto[]
}

// 8 distinct cluster colors
const CLUSTER_COLORS = [
  '#6366f1', // indigo
  '#f59e0b', // amber
  '#10b981', // emerald
  '#ef4444', // red
  '#3b82f6', // blue
  '#8b5cf6', // violet
  '#ec4899', // pink
  '#14b8a6', // teal
]

function clusterColor(clusterId: number): string {
  return CLUSTER_COLORS[clusterId % CLUSTER_COLORS.length]
}

export default function ProteinGraph({ nodes, edges }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const cyRef = useRef<cytoscape.Core | null>(null)

  useEffect(() => {
    if (!containerRef.current || nodes.length === 0) return

    // Lazy-load cytoscape to avoid SSR issues
    import('cytoscape').then(({ default: Cytoscape }) => {
      if (cyRef.current) {
        cyRef.current.destroy()
      }

      const maxCentrality = Math.max(...nodes.map(n => n.centrality), 1)

      const elements: cytoscape.ElementDefinition[] = [
        ...nodes.map(n => ({
          data: {
            id: n.id,
            label: n.label,
            clusterId: n.clusterId,
            centrality: n.centrality,
          },
        })),
        ...edges.map((e, i) => ({
          data: {
            id: `e-${i}`,
            source: e.source,
            target: e.target,
            score: e.score,
          },
        })),
      ]

      cyRef.current = Cytoscape({
        container: containerRef.current!,
        elements,
        style: [
          {
            selector: 'node',
            style: {
              'background-color': (ele: cytoscape.NodeSingular) =>
                clusterColor(ele.data('clusterId') as number),
              'width': (ele: cytoscape.NodeSingular) => {
                const c = (ele.data('centrality') as number) / maxCentrality
                return Math.max(18, 18 + c * 28)
              },
              'height': (ele: cytoscape.NodeSingular) => {
                const c = (ele.data('centrality') as number) / maxCentrality
                return Math.max(18, 18 + c * 28)
              },
              'label': 'data(label)',
              'font-size': 9,
              'color': '#f3f4f6',
              'text-valign': 'bottom',
              'text-margin-y': 4,
              'text-outline-color': '#111827',
              'text-outline-width': 2,
              'border-width': 1.5,
              'border-color': '#1f2937',
            },
          },
          {
            selector: 'edge',
            style: {
              'width': (ele: cytoscape.EdgeSingular) =>
                0.5 + ((ele.data('score') as number) / 1000) * 3,
              'line-color': '#374151',
              'opacity': 0.7,
              'curve-style': 'bezier',
            },
          },
          {
            selector: 'node:selected',
            style: {
              'border-color': '#a5b4fc',
              'border-width': 3,
            },
          },
        ],
        layout: {
          name: 'cose',
          animate: false,
          nodeRepulsion: () => 4096,
          idealEdgeLength: () => 80,
          gravity: 0.25,
        },
        userZoomingEnabled: true,
        userPanningEnabled: true,
      })
    })

    return () => {
      cyRef.current?.destroy()
      cyRef.current = null
    }
  }, [nodes, edges])

  if (nodes.length === 0) {
    return <p className="text-gray-500 text-sm">No nodes to display.</p>
  }

  const clusterCounts = nodes.reduce<Record<number, number>>((acc, n) => {
    acc[n.clusterId] = (acc[n.clusterId] ?? 0) + 1
    return acc
  }, {})

  return (
    <div>
      <div
        ref={containerRef}
        className="w-full rounded bg-gray-950 border border-gray-800"
        style={{ height: 500 }}
      />
      <div className="mt-3 flex flex-wrap gap-4">
        {Object.keys(clusterCounts).map(Number).sort().map(cid => (
          <div key={cid} className="flex items-center gap-2 text-xs text-gray-500">
            <span
              className="inline-block h-2 w-2 rounded-sm flex-shrink-0"
              style={{ backgroundColor: clusterColor(cid) }}
            />
            <span className="text-white font-medium">{clusterCounts[cid]}</span> proteins
          </div>
        ))}
      </div>
    </div>
  )
}
