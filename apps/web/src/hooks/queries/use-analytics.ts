
import { useQuery } from "@tanstack/react-query"
import { getInventoryByCategory, getPerformanceMetrics } from "@/lib/api/analytics"

export function useInventoryByCategory() {
  return useQuery({
    queryKey: ['analytics', 'inventory-by-category'],
    queryFn: getInventoryByCategory,
  })
}

export function usePerformanceMetrics() {
  return useQuery({
    queryKey: ['analytics', 'performance-metrics'],
    queryFn: getPerformanceMetrics,
  })
}
