"use client";

import { useQuery, skipToken } from "@tanstack/react-query";
import { useCurrentSite } from "@/hooks/queries/use-current-site";
import { getSiteTracking } from "@/lib/api/site-tracking";
import type { TrackingLookupResponse } from "@/lib/api/tracking";

export function useTracking(trackingNumber: string | null) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery<TrackingLookupResponse, Error>({
    queryKey: ["tracking", siteId, trackingNumber],
    queryFn:
      siteId && trackingNumber ? () => getSiteTracking(siteId, trackingNumber) : skipToken,
    staleTime: 15 * 60 * 1000, // 15 minutes
    gcTime: 30 * 60 * 1000, // keep in cache for 30 minutes
    retry: false, // Don't retry on failure to avoid duplicate requests
    refetchOnWindowFocus: false, // Don't refetch when window regains focus
  });

  return {
    ...query,
    isLoading: (Boolean(trackingNumber) && isSiteLoading) || query.isLoading,
    error: siteError ?? query.error,
  };
}
