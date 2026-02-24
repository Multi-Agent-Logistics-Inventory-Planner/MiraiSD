import React from "react"
import type { Metadata } from 'next'
import { Geist, Geist_Mono } from 'next/font/google'
import { Analytics } from '@vercel/analytics/next'
import { QueryProvider } from '@/components/providers/query-provider'
import { Toaster } from '@/components/ui/toaster'
import './globals.css'

const _geist = Geist({ subsets: ["latin"] });
const _geistMono = Geist_Mono({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: 'Mirai Inventory',
  description: 'Track inventory, manage shipments, and forecast stock levels with AI-powered analytics',
  generator: 'v0.app',
  icons: {
    icon: '/mirai-logo.png',
    apple: '/mirai-logo.png',
  },
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="en">
      <body className={`font-sans antialiased`}>
        <QueryProvider>
          {children}
        </QueryProvider>
        <Toaster />
        <Analytics />
      </body>
    </html>
  )
}
