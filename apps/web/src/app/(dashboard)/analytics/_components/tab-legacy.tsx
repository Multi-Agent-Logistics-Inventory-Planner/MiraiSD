"use client"

import { PredictionsTable, SalesMetricsCard } from "@/components/analytics"
import type { ForecastPrediction, SalesSummary } from "@/types/api"

interface TabLegacyProps {
  isAdmin: boolean
  forecasts: ForecastPrediction[]
  isLoadingForecasts: boolean
  isForecastError: boolean
  salesData?: SalesSummary
  isLoadingSales: boolean
}

export function TabLegacy({
  isAdmin,
  forecasts,
  isLoadingForecasts,
  isForecastError,
  salesData,
  isLoadingSales,
}: TabLegacyProps) {
  return (
    <div className="space-y-6">
      {isAdmin && (
        <SalesMetricsCard data={salesData} isLoading={isLoadingSales} />
      )}

      <PredictionsTable
        data={forecasts}
        isLoading={isLoadingForecasts}
        isError={isForecastError}
      />
    </div>
  )
}
