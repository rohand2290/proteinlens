import type { Metadata } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: 'ProteinLens',
  description: 'Protein interaction network analysis',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-gray-950 text-gray-100 antialiased">
        <nav className="border-b border-gray-800 bg-gray-900 px-6 py-3 flex items-center gap-8">
          <span className="text-indigo-400 font-semibold text-lg tracking-tight">ProteinLens</span>
          <a href="/" className="text-sm text-gray-300 hover:text-white transition-colors">Ingest</a>
          <a href="/graph/" className="text-sm text-gray-300 hover:text-white transition-colors">Graph</a>
        </nav>
        <main className="px-6 py-8 max-w-7xl mx-auto">
          {children}
        </main>
      </body>
    </html>
  )
}
