"use client"

import {
  RiskDistributionChart,
  PerformanceMetrics,
  SalesMetricsCard,
} from "@/components/analytics"
import type { ForecastPrediction, PerformanceMetrics as PerformanceMetricsType, SalesSummary } from "@/types/api"

interface TabOverviewProps {
  isAdmin: boolean
  forecasts: ForecastPrediction[]
  isLoadingForecasts: boolean
  performanceData: PerformanceMetricsType | undefined
  isLoadingPerformance: boolean
  salesData: SalesSummary | undefined
  isLoadingSales: boolean
}

export function TabOverview({
  isAdmin,
  forecasts,
  isLoadingForecasts,
  performanceData,
  isLoadingPerformance,
  salesData,
  isLoadingSales,
}: TabOverviewProps) {
  return (
    <div className="space-y-6">
      {isAdmin && (
        <SalesMetricsCard data={salesData} isLoading={isLoadingSales} />
      )}

      <div className="grid gap-4 lg:grid-cols-5">
        <div className="lg:col-span-2">
          <RiskDistributionChart
            forecasts={forecasts}
            isLoading={isLoadingForecasts}
          />
        </div>
        <div className="lg:col-span-3">
          <PerformanceMetrics
            metrics={performanceData}
            isLoading={isLoadingPerformance}
          />
        </div>
      </div>
    </div>
  )
}
