"use client";

import { useQuery } from "@tanstack/react-query";
import { LocationType, type LocationWithCounts } from "@/types/api";
import { getLocationsWithCounts } from "@/lib/api/locations";

/**
 * Fetch all locations with their inventory counts in a single request.
 * This is the optimized replacement for useLocationsOnly + useLocationCounts.
 *
 * Reduces API calls from N+1 (1 per location) to 1.
 *
 * @param locationType Optional filter by location type
 */
export function useLocationsWithCounts(locationType?: LocationType) {
  return useQuery<LocationWithCounts[]>({
    queryKey: locationType
      ? ["locationsWithCounts", locationType]
      : ["locationsWithCounts"],
    queryFn: () => getLocationsWithCounts(locationType),
    // Disable query when no location type or NOT_ASSIGNED (uses different endpoint)
    enabled: !!locationType && locationType !== LocationType.NOT_ASSIGNED,
    staleTime: 30_000, // 30 seconds - data is refreshed by realtime subscriptions
  });
}
