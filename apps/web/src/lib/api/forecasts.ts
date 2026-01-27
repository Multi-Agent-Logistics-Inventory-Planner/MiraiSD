import { apiClient, apiGet } from './client'
import type { ForecastPrediction, PaginatedResponse } from '@/types/api'

const BASE_PATH = '/api/forecasts'

export async function getForecasts(params: {
  page: number
  limit: number
}): Promise<PaginatedResponse<ForecastPrediction>> {
  // Spring Boot Pageable uses 0-indexed 'page' and 'size'
  const queryParams = new URLSearchParams({
    page: (params.page - 1).toString(),
    size: params.limit.toString(),
  })
  
  return apiGet<PaginatedResponse<ForecastPrediction>>(`${BASE_PATH}?${queryParams.toString()}`)
}

export async function getAtRiskForecasts(daysThreshold: number = 7): Promise<ForecastPrediction[]> {
  return apiGet<ForecastPrediction[]>(`${BASE_PATH}/at-risk?daysThreshold=${daysThreshold}`)
}

export async function getForecastByItem(itemId: string): Promise<ForecastPrediction> {
  return apiGet<ForecastPrediction>(`${BASE_PATH}/${itemId}`)
}
