"use client"

import { Suspense, useEffect } from "react"
import { useSearchParams, useRouter } from "next/navigation"
import {
  LayoutDashboard,
  TrendingDown,
  PieChart,
  Trophy,
} from "lucide-react"
import { SidebarTrigger } from "@/components/ui/sidebar"
import { useAllForecasts } from "@/hooks/queries/use-forecasts"
import { useSalesSummary, usePerformanceMetrics, useInventoryByCategory } from "@/hooks/queries/use-analytics"
import { useAuth } from "@/hooks/use-auth"
import { UserRole } from "@/types/api"
import { AnalyticsTab } from "@/types/analytics"
import {
  AnalyticsTabs,
  TabLegacy,
  TabPlaceholder,
  TabOverview,
  TabStockout,
  TabCategories,
  TabTopSellers,
} from "./_components"

const ADMIN_ONLY_TABS = new Set([AnalyticsTab.TOP_SELLERS])

function isValidTab(value: string | null): value is AnalyticsTab {
  return Object.values(AnalyticsTab).includes(value as AnalyticsTab)
}

function AnalyticsContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const tabParam = searchParams.get("tab")

  const { user } = useAuth()
  const isAdmin = user?.role === UserRole.ADMIN

  const currentTab = isValidTab(tabParam) ? tabParam : AnalyticsTab.OVERVIEW

  // Redirect non-admins from admin-only tabs
  useEffect(() => {
    if (!isAdmin && ADMIN_ONLY_TABS.has(currentTab)) {
      router.replace("/analytics?tab=overview")
    }
  }, [isAdmin, currentTab, router])

  const handleTabChange = (tab: AnalyticsTab) => {
    router.push(`/analytics?tab=${tab}`)
  }

  const {
    data: allForecastsData,
    isLoading: isLoadingForecasts,
    isError: isForecastError,
  } = useAllForecasts()

  const { data: salesData, isLoading: isLoadingSales } = useSalesSummary()
  const { data: performanceData, isLoading: isLoadingPerformance } = usePerformanceMetrics()
  const { data: categoryData, isLoading: isLoadingCategory } = useInventoryByCategory()

  const forecasts = allForecastsData ?? []

  const renderTabContent = () => {
    switch (currentTab) {
      case AnalyticsTab.OVERVIEW:
        return (
          <TabOverview
            isAdmin={isAdmin}
            forecasts={forecasts}
            isLoadingForecasts={isLoadingForecasts}
            performanceData={performanceData}
            isLoadingPerformance={isLoadingPerformance}
            salesData={salesData}
            isLoadingSales={isLoadingSales}
          />
        )
      case AnalyticsTab.PREDICTIONS:
        return (
          <TabStockout
            forecasts={forecasts}
            isLoadingForecasts={isLoadingForecasts}
            isForecastError={isForecastError}
          />
        )
      case AnalyticsTab.CATEGORIES:
        return (
          <TabCategories
            categoryData={categoryData}
            isLoadingCategory={isLoadingCategory}
          />
        )
      case AnalyticsTab.TOP_SELLERS:
        return (
          <TabTopSellers
            forecasts={forecasts}
            isLoadingForecasts={isLoadingForecasts}
          />
        )
      case AnalyticsTab.LEGACY:
      default:
        return (
          <TabLegacy
            isAdmin={isAdmin}
            forecasts={forecasts}
            isLoadingForecasts={isLoadingForecasts}
            isForecastError={isForecastError}
            salesData={salesData}
            isLoadingSales={isLoadingSales}
          />
        )
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
