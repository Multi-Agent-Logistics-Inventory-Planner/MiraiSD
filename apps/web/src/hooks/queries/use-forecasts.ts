import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { getForecasts, getAtRiskForecasts, getAllForecasts } from '@/lib/api/forecasts'

export function useForecasts(page: number = 1, limit: number = 10) {
  return useQuery({
    queryKey: ['forecasts', page, limit],
    queryFn: () => getForecasts({ page, limit }),
    placeholderData: keepPreviousData,
  })
}

export function useAtRiskForecasts(daysThreshold: number = 7) {
  return useQuery({
    queryKey: ['forecasts', 'at-risk', daysThreshold],
    queryFn: () => getAtRiskForecasts(daysThreshold),
  })
}

export function useAllForecasts() {
  return useQuery({
    queryKey: ['forecasts', 'all'],
    queryFn: getAllForecasts,
    staleTime: 5 * 60 * 1000,
  })
}
