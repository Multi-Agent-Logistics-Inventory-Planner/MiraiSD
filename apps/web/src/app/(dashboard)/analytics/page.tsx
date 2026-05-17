"use client"

import { Suspense, useEffect } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { SidebarTrigger } from "@/components/ui/sidebar"
import { useAuth } from "@/hooks/use-auth"
import { useTabParam } from "@/hooks/use-tab-param"
import { UserRole } from "@/types/api"
import { AnalyticsTab } from "@/types/analytics"
import {
  AnalyticsTabs,
  TabPredictions,
  TabOverview,
  TabAssistant,
} from "./_components"

const ANALYTICS_TAB_VALUES = [
  AnalyticsTab.OVERVIEW,
  AnalyticsTab.PREDICTIONS,
  AnalyticsTab.ASSISTANT,
] as const

// Old tab values that should redirect to overview
const DEPRECATED_TABS = ["insights", "demand-leaders", "legacy", "notifications"]

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

  const { value, setValue, mountedValues } = useTabParam<AnalyticsTab>({
    values: ANALYTICS_TAB_VALUES,
    defaultValue: AnalyticsTab.OVERVIEW,
  })
  const currentTab = value ?? AnalyticsTab.OVERVIEW

  return (
    <div className="flex-1 space-y-4">
      <AnalyticsTabs
        value={currentTab}
        onValueChange={setValue}
        isAdmin={isAdmin}
      />
      <div className={currentTab !== AnalyticsTab.OVERVIEW ? "hidden" : undefined}>
        {mountedValues.has(AnalyticsTab.OVERVIEW) && <TabOverview />}
      </div>
      <div className={currentTab !== AnalyticsTab.PREDICTIONS ? "hidden" : undefined}>
        {mountedValues.has(AnalyticsTab.PREDICTIONS) && <TabPredictions />}
      </div>
      {isAdmin && (
        <div className={currentTab !== AnalyticsTab.ASSISTANT ? "hidden" : undefined}>
          {mountedValues.has(AnalyticsTab.ASSISTANT) && <TabAssistant />}
        </div>
      )}
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
        <SidebarTrigger />
        <h1 className="text-2xl font-semibold tracking-tight">Analytics</h1>
      </div>
      <Suspense fallback={<AnalyticsFallback />}>
        <AnalyticsContent />
      </Suspense>
    </div>
  )
}
