import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { getForecasts, getAtRiskForecasts } from '@/lib/api/forecasts'

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
