import { apiGet } from './client'
import { PerformanceMetrics, SalesSummary } from '@/types/api'
import {
  PredictionsData,
  InsightsData,
  DemandLeadersData,
  DemandLeadersPeriod,
} from '@/types/analytics'

const BASE_PATH = '/api/analytics'

export async function getPerformanceMetrics(): Promise<PerformanceMetrics> {
  return apiGet<PerformanceMetrics>(`${BASE_PATH}/performance-metrics`)
}

export async function getSalesSummary(): Promise<SalesSummary> {
  return apiGet<SalesSummary>(`${BASE_PATH}/sales-summary`)
}

/**
 * Get predictions data - consolidated view for reorder decisions.
 * Replaces multiple separate queries with a single endpoint.
 * Now includes demand velocity, volatility, and forecast accuracy.
 */
export async function getPredictions(): Promise<PredictionsData> {
  return apiGet<PredictionsData>(`${BASE_PATH}/action-center`)
}

/**
 * Get insights data - category performance and day-of-week patterns.
 * Uses demand-based metrics instead of revenue-based metrics.
 */
export async function getInsights(): Promise<InsightsData> {
  return apiGet<InsightsData>(`${BASE_PATH}/insights`)
}

const VALID_PERIODS: DemandLeadersPeriod[] = ['7d', '30d', '90d', 'ytd'];

/**
 * Get demand leaders data - rankings by demand velocity and stock velocity.
 * Replaces top-sellers endpoint with demand-based metrics.
 * @param period Time period: 7d, 30d (default), 90d, ytd
 */
export async function getDemandLeaders(
  period: DemandLeadersPeriod = '30d'
): Promise<DemandLeadersData> {
  if (!VALID_PERIODS.includes(period)) {
    throw new Error(`Invalid period: ${period}. Must be one of: ${VALID_PERIODS.join(', ')}`);
  }
  return apiGet<DemandLeadersData>(`${BASE_PATH}/demand-leaders?period=${encodeURIComponent(period)}`)
}
