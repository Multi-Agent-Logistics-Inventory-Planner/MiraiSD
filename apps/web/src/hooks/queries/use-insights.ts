import { useQuery } from '@tanstack/react-query'
import { getInsights } from '@/lib/api/analytics'
import { queryKeys } from '@/lib/query-keys'

/**
 * Hook for fetching insights data.
 * Returns category performance and day-of-week patterns.
 * Uses pre-aggregated rollup tables when available.
 */
export function useInsights() {
  return useQuery({
    queryKey: queryKeys.analytics.insights(),
    queryFn: getInsights,
    staleTime: 30 * 60 * 1000, // 30 minutes - historical data, stable
    refetchOnMount: false,
    refetchOnWindowFocus: false,
  })
}
