import { useQuery } from '@tanstack/react-query'
import { getForecastAccuracy } from '@/lib/api/forecasts'
import { queryKeys } from '@/lib/query-keys'

/**
 * Rolling 30d/7d predicted-vs-actual accuracy of the forecast pipeline.
 * Cached server-side for 10 minutes; client cache matches.
 */
export function useForecastAccuracy() {
  return useQuery({
    queryKey: queryKeys.forecasts.accuracy(),
    queryFn: getForecastAccuracy,
    staleTime: 10 * 60 * 1000,
    gcTime: 15 * 60 * 1000,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
  })
}
