import { apiGet } from './client'
import { CategoryInventory, PerformanceMetrics } from '@/types/api'

const BASE_PATH = '/api/analytics'

export async function getInventoryByCategory(): Promise<CategoryInventory[]> {
  return apiGet<CategoryInventory[]>(`${BASE_PATH}/inventory-by-category`)
}

export async function getPerformanceMetrics(): Promise<PerformanceMetrics> {
  return apiGet<PerformanceMetrics>(`${BASE_PATH}/performance-metrics`)
}
