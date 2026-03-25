import { apiGet } from './client'
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

export async function getAllForecasts(): Promise<ForecastPrediction[]> {
  return apiGet<ForecastPrediction[]>(`${BASE_PATH}/all`)
}

/**
 * Get the forecast for the item with highest demand (most consumption).
 * Returns null if no consuming forecasts exist.
 */
export async function getHighestDemandForecast(): Promise<ForecastPrediction | null> {
  const result = await apiGet<ForecastPrediction | undefined>(`${BASE_PATH}/highest-demand`)
  return result ?? null
}
