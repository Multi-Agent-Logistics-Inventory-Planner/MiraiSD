import { useQuery } from '@tanstack/react-query'
import { getDemandLeaders } from '@/lib/api/analytics'
import { queryKeys } from '@/lib/query-keys'
import { DemandLeadersPeriod } from '@/types/analytics'

/**
 * Hook for fetching demand leaders data.
 * Returns demand velocity and stock velocity rankings.
 * Admin-only endpoint.
 *
 * @param period Time period: 7d, 30d, 90d, ytd
 */
export function useDemandLeaders(period: DemandLeadersPeriod = '30d') {
  return useQuery({
    queryKey: queryKeys.analytics.demandLeaders(period),
    queryFn: () => getDemandLeaders(period),
    staleTime: 30 * 60 * 1000, // 30 minutes - historical data, stable
    refetchOnMount: false,
    refetchOnWindowFocus: false,
  })
}
