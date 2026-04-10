import { useQuery } from "@tanstack/react-query";

import {
  fetchDetailBundle,
  type DetailBundle,
} from "@/lib/api/product-assistant";

/**
 * Fetches the full deterministic Product Assistant report bundle once per
 * session. Stale time matches the backend's private Cache-Control: max-age=10.
 */
export function useProductReportBundle(productId: string | null | undefined) {
  return useQuery<DetailBundle>({
    queryKey: ["product-assistant", "detail", productId],
    queryFn: () => fetchDetailBundle(productId as string, 90),
    enabled: !!productId,
    staleTime: 10_000,
  });
}
