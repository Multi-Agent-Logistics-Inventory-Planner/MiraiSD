"use client"

import { useMemo, useRef } from "react"
import { SidebarTrigger } from "@/components/ui/sidebar"
import { useAllForecasts, useAtRiskForecasts } from "@/hooks/queries/use-forecasts"
import {
  usePerformanceMetrics,
  useSalesSummary,
} from "@/hooks/queries/use-analytics"
import {
  KPICards,
  CriticalActionPanel,
  PredictionsTable,
  PerformanceMetrics,
  SalesMetricsCard,
} from "@/components/analytics"
import { useAuth } from "@/hooks/use-auth"
import { UserRole } from "@/types/api"

export default function AnalyticsPage() {
  const tableRef = useRef<HTMLDivElement>(null)

  const { user } = useAuth()
  const isAdmin = user?.role === UserRole.ADMIN

  const {
    data: allForecastsData,
    isLoading: isLoadingForecasts,
    isError: isForecastError,
  } = useAllForecasts()

  const { data: atRiskData, isLoading: isLoadingAtRisk } = useAtRiskForecasts(7)

  const { data: metricsData, isLoading: isLoadingMetrics } =
    usePerformanceMetrics()

  const { data: salesData, isLoading: isLoadingSales } = useSalesSummary()

  const criticalItems = useMemo(
    () => atRiskData?.filter((item) => item.daysToStockout <= 3) ?? [],
    [atRiskData]
  )

  const itemsAtRisk = atRiskData?.length ?? 0

  const reorderValue =
    atRiskData?.reduce(
      (sum, p) => sum + p.suggestedReorderQty * (p.unitCost ?? 0),
      0
    ) ?? 0

  const handleViewCritical = () => {
    tableRef.current?.scrollIntoView({ behavior: "smooth" })
  }

  return (
    <div className="flex flex-col p-4 md:p-8 space-y-4">
      <div className="flex items-center gap-2">
        <SidebarTrigger className="md:hidden" />
        <h1 className="text-2xl font-semibold tracking-tight">Analytics</h1>
      </div>
      <div className="flex-1 space-y-6">
        <KPICards
          itemsAtRisk={itemsAtRisk}
          reorderValue={reorderValue}
          forecastAccuracy={metricsData?.forecastAccuracy ?? 0}
          fillRate={metricsData?.fillRate ?? 0}
          isLoading={isLoadingAtRisk || isLoadingMetrics}
        />

        <CriticalActionPanel
          criticalItems={criticalItems}
          isLoading={isLoadingAtRisk}
          onViewCritical={handleViewCritical}
        />

        {isAdmin && (
          <SalesMetricsCard data={salesData} isLoading={isLoadingSales} />
        )}

        <div ref={tableRef}>
          <PredictionsTable
            data={allForecastsData ?? []}
            isLoading={isLoadingForecasts}
            isError={isForecastError}
          />
        </div>

        <PerformanceMetrics
          metrics={metricsData}
          isLoading={isLoadingMetrics}
        />
      </div>
    </div>
  )
}
