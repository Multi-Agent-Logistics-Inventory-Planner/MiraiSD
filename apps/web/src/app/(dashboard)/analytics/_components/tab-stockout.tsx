"use client"

import { PredictionsTable } from "@/components/analytics"
import type { ForecastPrediction } from "@/types/api"

interface TabStockoutProps {
  forecasts: ForecastPrediction[]
  isLoadingForecasts: boolean
  isForecastError: boolean
}

export function TabStockout({
  forecasts,
  isLoadingForecasts,
  isForecastError,
}: TabStockoutProps) {
  return (
    <div className="space-y-4">
      <PredictionsTable
        data={forecasts}
        isLoading={isLoadingForecasts}
        isError={isForecastError}
      />
    </div>
  )
}
