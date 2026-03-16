import { useQuery } from "@tanstack/react-query"
import { getPerformanceMetrics, getSalesSummary } from "@/lib/api/analytics"
import { queryKeys } from "@/lib/query-keys"

export function usePerformanceMetrics() {
  return useQuery({
    queryKey: queryKeys.analytics.performanceMetrics(),
    queryFn: getPerformanceMetrics,
    staleTime: 15 * 60 * 1000, // 15 minutes - less critical metrics
  })
}

export function useSalesSummary() {
  return useQuery({
    queryKey: queryKeys.analytics.salesSummary(),
    queryFn: getSalesSummary,
    staleTime: 60 * 60 * 1000, // 60 minutes - pure historical data, very stable
  })
}
