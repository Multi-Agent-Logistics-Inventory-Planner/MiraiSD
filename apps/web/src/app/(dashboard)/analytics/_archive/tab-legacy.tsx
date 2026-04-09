"use client"

import { PredictionsTable, SalesMetricsCard } from "@/components/analytics"
import { useAllForecasts } from "@/hooks/queries/use-forecasts"
import { useSalesSummary } from "@/hooks/queries/use-analytics"

interface TabLegacyProps {
  isAdmin: boolean
}

export function TabLegacy({ isAdmin }: TabLegacyProps) {
  // Only fetch when this tab is rendered (lazy loading)
  const {
    data: forecasts,
    isLoading: isLoadingForecasts,
    isError: isForecastError,
  } = useAllForecasts()

  const { data: salesData, isLoading: isLoadingSales } = useSalesSummary()

  return (
    <div className="space-y-6">
      {isAdmin && (
        <SalesMetricsCard data={salesData} isLoading={isLoadingSales} />
      )}

      <PredictionsTable
        data={forecasts ?? []}
        isLoading={isLoadingForecasts}
        isError={isForecastError}
      />
    </div>
  )
}
