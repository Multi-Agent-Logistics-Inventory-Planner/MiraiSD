import { useQuery } from "@tanstack/react-query"
import { getSalesSummary } from "@/lib/api/analytics"
import { queryKeys } from "@/lib/query-keys"

export function useSalesSummary() {
  return useQuery({
    queryKey: queryKeys.analytics.salesSummary(),
    queryFn: getSalesSummary,
    staleTime: 60 * 60 * 1000, // 60 minutes - pure historical data, very stable
  })
}
