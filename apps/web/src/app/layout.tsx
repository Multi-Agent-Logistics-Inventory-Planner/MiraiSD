import React from "react"
import type { Metadata } from 'next'
import { Geist, Geist_Mono } from 'next/font/google'
import { QueryProvider } from '@/components/providers/query-provider'
import { ThemeProvider } from '@/components/theme-provider'
import { Toaster } from '@/components/ui/toaster'
import './globals.css'

// These calls self-host the Geist fonts and inject their @font-face rules
// (via the next/font compiler plugin) even though the result isn't assigned;
// globals.css references the font by name ("Geist" / "Geist Mono") through
// the Tailwind --font-sans / --font-mono variables instead of a className.
Geist({ subsets: ["latin"] });
Geist_Mono({ subsets: ["latin"] });

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
