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

export interface ForecastAccuracyWindow {
  days: number
  scoredItemDays: number
  wape: number | null
  mape: number | null
  bias: number | null
  totalActualUnits: number
  underPredictions: number
  overPredictions: number
}

export interface ForecastAccuracyCategoryRow {
  category: string
  scoredItemDays: number
  wape: number | null
  mape: number | null
  bias: number | null
  totalActualUnits: number
}

export interface ForecastAccuracy {
  headline: ForecastAccuracyWindow
  comparison: ForecastAccuracyWindow
  byCategory: ForecastAccuracyCategoryRow[]
}

/**
 * Rolling predicted-vs-actual accuracy of the forecast pipeline.
 * Headline = last 30 days WAPE/MAPE/bias, comparison = last 7 days.
 */
export async function getForecastAccuracy(): Promise<ForecastAccuracy> {
  return apiGet<ForecastAccuracy>(`${BASE_PATH}/accuracy`)
}

// ---- "Why this number" explanation ----

export interface ForecastExplanation {
  itemId: string
  computedAt: string
  features: Record<string, unknown>
  lastRestockAt: string | null
}

export async function getForecastExplanation(itemId: string): Promise<ForecastExplanation> {
  return apiGet<ForecastExplanation>(`${BASE_PATH}/${itemId}/explain`)
}
