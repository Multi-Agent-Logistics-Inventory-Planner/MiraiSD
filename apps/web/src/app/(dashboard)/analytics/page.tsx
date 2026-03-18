"use client"

import { Suspense, useEffect } from "react"
import { useSearchParams, useRouter } from "next/navigation"
import { SidebarTrigger } from "@/components/ui/sidebar"
import { useAuth } from "@/hooks/use-auth"
import { UserRole } from "@/types/api"
import { AnalyticsTab } from "@/types/analytics"
import {
  AnalyticsTabs,
  TabPredictions,
  TabInsights,
  TabDemandLeaders,
  TabLegacy,
} from "./_components"

const ADMIN_ONLY_TABS = new Set([AnalyticsTab.DEMAND_LEADERS])

function isValidTab(value: string | null): value is AnalyticsTab {
  return Object.values(AnalyticsTab).includes(value as AnalyticsTab)
}

function AnalyticsContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const tabParam = searchParams.get("tab")

  const { user } = useAuth()
  const isAdmin = user?.role === UserRole.ADMIN

  const currentTab = isValidTab(tabParam) ? tabParam : AnalyticsTab.INSIGHTS

  // Redirect non-admins from admin-only tabs
  useEffect(() => {
    if (!isAdmin && ADMIN_ONLY_TABS.has(currentTab)) {
      router.replace("/analytics?tab=insights")
    }
  }, [isAdmin, currentTab, router])

  const handleTabChange = (tab: AnalyticsTab) => {
    router.push(`/analytics?tab=${tab}`)
  }

  const renderTabContent = () => {
    switch (currentTab) {
      case AnalyticsTab.PREDICTIONS:
        return <TabPredictions />
      case AnalyticsTab.INSIGHTS:
        return <TabInsights />
      case AnalyticsTab.DEMAND_LEADERS:
        return <TabDemandLeaders />
      case AnalyticsTab.LEGACY:
      default:
        return <TabLegacy isAdmin={isAdmin} />
    }
  }

  return (
    <div className="flex-1 space-y-4">
      <AnalyticsTabs
        value={currentTab}
        onValueChange={handleTabChange}
        isAdmin={isAdmin}
      />
      {renderTabContent()}
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
