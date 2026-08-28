import React from "react"
import type { Metadata } from 'next'
import { Geist, Geist_Mono } from 'next/font/google'
import { QueryProvider } from '@/components/providers/query-provider'
import { ThemeProvider } from '@/components/theme-provider'
import { Toaster } from '@/components/ui/toaster'
import './globals.css'

// These calls self-host the Geist fonts and inject their @font-face rules
// (via the next/font compiler plugin); globals.css references the font by
// name ("Geist" / "Geist Mono") through the Tailwind --font-sans/--font-mono
// variables instead of a className, so the results below are unused on
// purpose. Turbopack (next build's default bundler since Next 15/16, per
// next.config's `turbopack` block) requires the call be assigned to a
// module-scope const even when discarded - a bare expression statement fails
// the build with "Font loaders must be called and assigned to a const in the
// module scope".
const _geistSans = Geist({ subsets: ["latin"] });
const _geistMono = Geist_Mono({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: 'Mirai Inventory',
  description: 'Track inventory, manage shipments, and forecast stock levels with AI-powered analytics',
  generator: 'v0.app',
  icons: {
    icon: '/favicon.ico',
    apple: '/favicon.ico',
  },
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="font-sans antialiased">
        <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
          <QueryProvider>
            {children}
          </QueryProvider>
          <Toaster />
        </ThemeProvider>
      </body>
    </html>
  )
}
