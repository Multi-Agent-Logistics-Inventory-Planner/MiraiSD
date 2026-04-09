import { useQuery } from '@tanstack/react-query'
import { getPredictions } from '@/lib/api/analytics'
import { queryKeys } from '@/lib/query-keys'

/**
 * Hook for fetching predictions data.
 * Consolidates 10+ separate queries into a single endpoint.
 * Returns items needing reorder decisions, sorted by urgency.
 */
export function usePredictions() {
  return useQuery({
    queryKey: queryKeys.analytics.predictions(),
    queryFn: getPredictions,
    staleTime: 10 * 60 * 1000, // 10 minutes - matches backend cache TTL
    gcTime: 15 * 60 * 1000, // 15 minutes - keep in cache longer than staleTime
    refetchOnMount: false,
    refetchOnWindowFocus: false,
  })
}
