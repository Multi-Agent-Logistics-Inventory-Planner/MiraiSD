import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { getForecasts, getAtRiskForecasts, getAllForecasts, getHighestDemandForecast } from '@/lib/api/forecasts'
import { queryKeys } from '@/lib/query-keys'

/**
 * Hook for fetching paginated forecasts.
 * Uses server-side pagination to avoid loading all forecasts at once.
 */
export function useForecasts(page: number = 1, limit: number = 10) {
  return useQuery({
    queryKey: queryKeys.forecasts.list(page, limit),
    queryFn: () => getForecasts({ page, limit }),
    placeholderData: keepPreviousData,
    staleTime: 5 * 60 * 1000, // 5 minutes - Kafka-driven updates
  })
}

/**
 * Hook for fetching at-risk forecasts.
 * Returns items below the days threshold.
 */
export function useAtRiskForecasts(daysThreshold: number = 7) {
  return useQuery({
    queryKey: queryKeys.forecasts.atRisk(daysThreshold),
    queryFn: () => getAtRiskForecasts(daysThreshold),
    staleTime: 5 * 60 * 1000,
  })
}

/**
 * Hook for fetching all forecasts.
 * @deprecated Use useForecasts with pagination instead for better performance.
 */
export function useAllForecasts() {
  return useQuery({
    queryKey: queryKeys.forecasts.all(),
    queryFn: getAllForecasts,
    staleTime: 5 * 60 * 1000, // 5 minutes - matches paginated forecasts
  })
}

/**
 * Hook for fetching the single highest-demand forecast.
 * Returns the item with the most negative avgDailyDelta (highest consumption).
 */
export function useHighestDemandForecast() {
  return useQuery({
    queryKey: queryKeys.forecasts.highestDemand(),
    queryFn: getHighestDemandForecast,
    staleTime: 5 * 60 * 1000, // 5 minutes
  })
}
