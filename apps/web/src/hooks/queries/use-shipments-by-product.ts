"use client";

import { useQuery, skipToken } from "@tanstack/react-query";
import { useCurrentSite } from "@/hooks/queries/use-current-site";
import { getSiteShipmentsByProduct } from "@/lib/api/site-shipments";

export function useShipmentsByProduct(productId?: string | null) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery({
    queryKey: ["shipmentsByProduct", siteId, productId ?? "none"],
    queryFn:
      siteId && productId ? () => getSiteShipmentsByProduct(siteId, productId) : skipToken,
  });

  return {
    ...query,
    isLoading: (Boolean(productId) && isSiteLoading) || query.isLoading,
    error: siteError ?? query.error,
  };
}
