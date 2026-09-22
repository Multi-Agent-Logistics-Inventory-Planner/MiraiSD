"use client";

import { useQuery, skipToken } from "@tanstack/react-query";
import { LocationType, type LocationWithCounts } from "@/types/api";
import { getSiteLocationsWithCounts } from "@/lib/api/locations";
import { useCurrentSite } from "./use-current-site";

/**
 * Site-scoped fetch of locations with their inventory counts (.specs/phase-6-inventory 6e,
 * T-6e-9). Closes the legacy `/api/locations/with-counts` cross-site leak - see
 * `getSiteLocationsWithCounts`. Replaces the storage-location tab bar's prior direct call to
 * the site-blind legacy endpoint.
 *
 * @param locationType Optional filter by location type
 */
export function useLocationsWithCounts(locationType?: LocationType) {
  const { siteId } = useCurrentSite();

  return useQuery<LocationWithCounts[]>({
    queryKey: ["locationsWithCounts", siteId, locationType ?? null],
    queryFn:
      siteId && locationType && locationType !== LocationType.NOT_ASSIGNED
        ? () => getSiteLocationsWithCounts(siteId, locationType)
        : skipToken,
    staleTime: 30_000, // 30 seconds - data is refreshed by realtime subscriptions
  });
}

/**
 * All locations across every type, unfiltered (.specs/phase-6-inventory 6e, T-6e-9). Used by
 * the dashboard's location-utilization metric, which previously held its own separate,
 * never-invalidated cache of the legacy site-blind endpoint (`use-dashboard-metrics.ts`'s
 * `["locations", "with-counts"]`); now shares this module's site-scoped query/key family so a
 * single realtime/mutation invalidation reaches both call sites.
 */
export function useAllLocationsWithCounts() {
  const { siteId } = useCurrentSite();

  return useQuery<LocationWithCounts[]>({
    queryKey: ["locationsWithCounts", siteId, "ALL"],
    queryFn: siteId ? () => getSiteLocationsWithCounts(siteId) : skipToken,
    staleTime: 60_000,
  });
}
