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
    staleTime: 5 * 60 * 1000, // 5 minutes - near real-time for reorder decisions
    gcTime: 10 * 60 * 1000, // 10 minutes - keep in cache longer than staleTime
    refetchOnMount: false,
    refetchOnWindowFocus: false,
  })
}
