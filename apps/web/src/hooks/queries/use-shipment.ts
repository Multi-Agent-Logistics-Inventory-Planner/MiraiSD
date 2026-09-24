"use client";

import { useQuery, skipToken } from "@tanstack/react-query";
import { useCurrentSite } from "@/hooks/queries/use-current-site";
import { getSiteShipmentById } from "@/lib/api/site-shipments";

export function useShipment(id: string | undefined) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery({
    queryKey: ["shipment", siteId, id],
    queryFn: siteId && id ? () => getSiteShipmentById(siteId, id) : skipToken,
  });

  return {
    ...query,
    isLoading: (Boolean(id) && isSiteLoading) || query.isLoading,
    error: siteError ?? query.error,
  };
}
