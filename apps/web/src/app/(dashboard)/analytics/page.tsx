"use client"

import { Suspense, useEffect, useState } from "react"
import { useSearchParams, useRouter } from "next/navigation"
import { SidebarTrigger } from "@/components/ui/sidebar"
import { useAuth } from "@/hooks/use-auth"
import { UserRole } from "@/types/api"
import { AnalyticsTab } from "@/types/analytics"
import {
  AnalyticsTabs,
  TabPredictions,
  TabOverview,
} from "./_components"

// Old tab values that should redirect to overview
const DEPRECATED_TABS = ["insights", "demand-leaders", "legacy", "notifications"]

function isValidTab(value: string | null): value is AnalyticsTab {
  return Object.values(AnalyticsTab).includes(value as AnalyticsTab)
}

function AnalyticsContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const tabParam = searchParams.get("tab")

  const { user } = useAuth()
  const isAdmin = user?.role === UserRole.ADMIN

  // Redirect deprecated tabs to overview
  useEffect(() => {
    if (tabParam && DEPRECATED_TABS.includes(tabParam)) {
      router.replace("/analytics?tab=overview")
    }
  }, [tabParam, router])

  const currentTab = isValidTab(tabParam) ? tabParam : AnalyticsTab.OVERVIEW

  // Track which tabs have been visited (lazy mount - only mount on first visit)
  const [mountedTabs, setMountedTabs] = useState<Set<AnalyticsTab>>(
    () => new Set([currentTab]),
  )

  useEffect(() => {
    setMountedTabs((prev) => {
      if (prev.has(currentTab)) return prev
      return new Set([...prev, currentTab])
    })
  }, [currentTab])

  const handleTabChange = (tab: AnalyticsTab) => {
    router.push(`/analytics?tab=${tab}`)
  }

  return (
    <div className="flex-1 space-y-4">
      <AnalyticsTabs
        value={currentTab}
        onValueChange={handleTabChange}
        isAdmin={isAdmin}
      />
      <div className={currentTab !== AnalyticsTab.OVERVIEW ? "hidden" : undefined}>
        {mountedTabs.has(AnalyticsTab.OVERVIEW) && <TabOverview />}
      </div>
      <div className={currentTab !== AnalyticsTab.PREDICTIONS ? "hidden" : undefined}>
        {mountedTabs.has(AnalyticsTab.PREDICTIONS) && <TabPredictions />}
      </div>
    </div>
  )
}

function AnalyticsFallback() {
  return (
    <div className="flex-1 space-y-4">
      <div className="h-9 bg-muted/30 animate-pulse rounded-md w-full max-w-xl" />
      <div className="space-y-6">
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="h-28 bg-muted/30 animate-pulse rounded-lg" />
          ))}
        </div>
        <div className="h-96 bg-muted/30 animate-pulse rounded-lg" />
      </div>
    </div>
  )
}

export default function AnalyticsPage() {
  return (
    <div className="flex flex-col p-4 md:p-8 space-y-4">
      <div className="flex items-center gap-2">
        <SidebarTrigger className="md:hidden" />
        <h1 className="text-2xl font-semibold tracking-tight">Analytics</h1>
      </div>
      <Suspense fallback={<AnalyticsFallback />}>
        <AnalyticsContent />
      </Suspense>
    </div>
  )
}
