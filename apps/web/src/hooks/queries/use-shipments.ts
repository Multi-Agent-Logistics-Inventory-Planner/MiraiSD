"use client";

import { useQuery, keepPreviousData, skipToken } from "@tanstack/react-query";
import { useCurrentSite } from "@/hooks/queries/use-current-site";
import { ShipmentFilters } from "@/lib/api/shipments";
import { getSiteShipmentsPaged, getSiteDisplayStatusCounts } from "@/lib/api/site-shipments";

export function useShipments(
  filters: ShipmentFilters = {},
  page: number = 0,
  size: number = 20
) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery({
    queryKey: ["shipments", siteId, filters, page, size],
    queryFn: siteId ? () => getSiteShipmentsPaged(siteId, filters, page, size) : skipToken,
    placeholderData: keepPreviousData,
  });

  return {
    ...query,
    isLoading: isSiteLoading || query.isLoading,
    error: siteError ?? query.error,
  };
}

export function useShipmentDisplayStatusCounts() {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery({
    queryKey: ["shipments", siteId, "display-status-counts"],
    queryFn: siteId ? () => getSiteDisplayStatusCounts(siteId) : skipToken,
  });

  return {
    ...query,
    isLoading: isSiteLoading || query.isLoading,
    error: siteError ?? query.error,
  };
}
