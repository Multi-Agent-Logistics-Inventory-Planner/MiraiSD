"use client";

import { useQuery, skipToken } from "@tanstack/react-query";
import { useCurrentSite } from "@/hooks/queries/use-current-site";
import { getSiteMovements } from "@/lib/api/site-inventory";

/**
 * Site-scoped movement history for one product (T-6d-10). Query key is site-qualified and gated
 * on siteId resolving. Rows whose `siteAttribution` is "UNKNOWN" (predating the site backfill)
 * are included, not filtered out - render them with an explicit "unknown site" label rather than
 * hiding them (per the user's confirmed 6d planning decision).
 */
export function useMovementHistory(
  productId?: string | null,
  page: number = 0,
  size: number = 20
) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery({
    queryKey: ["movementHistory", siteId, productId ?? "none", page, size],
    queryFn:
      siteId && productId
        ? () => getSiteMovements(siteId, { itemId: productId }, page, size)
        : skipToken,
  });

  return {
    data: query.data,
    isLoading: (Boolean(productId) && isSiteLoading) || query.isLoading,
    error: siteError ?? query.error,
  };
}
