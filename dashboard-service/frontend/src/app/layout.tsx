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
        <nav className="border-b border-gray-800 px-6 py-0 flex items-stretch gap-0">
          <span className="text-white font-semibold text-sm tracking-widest uppercase flex items-center pr-6 border-r border-gray-800">
            ProteinLens
          </span>
          <div className="flex items-stretch gap-0 ml-2">
            <a href="/" className="flex items-center px-4 text-sm text-gray-400 hover:text-white hover:bg-gray-900 transition-colors h-11">
              Ingest
            </a>
            <a href="/graph/" className="flex items-center px-4 text-sm text-gray-400 hover:text-white hover:bg-gray-900 transition-colors h-11">
              Graph
            </a>
          </div>
        </nav>
        <main className="px-6 py-10 max-w-7xl mx-auto">
          {children}
        </main>
      </body>
    </html>
  )
}
