"use client";

import { useQuery, skipToken, type UseQueryResult } from "@tanstack/react-query";
import { LocationType, STORAGE_LOCATION_CODES, type Location } from "@/types/api";
import { getLocationsByType, getLocationById, getSiteLocations } from "@/lib/api/locations";

import { useCurrentSite } from "./use-current-site";

/** Phase 6 inventory pickers. Legacy hooks below remain for Phase 7 consumers. */
export function useSiteLocations(locationType?: LocationType) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();
  const query = useQuery({
    queryKey: ["locations", siteId, locationType],
    queryFn: siteId && locationType && locationType !== LocationType.NOT_ASSIGNED
      ? () => getSiteLocations(siteId, STORAGE_LOCATION_CODES[locationType])
      : skipToken,
  });
  const error = siteError ?? (!siteId && !isSiteLoading ? new Error("No active site") : null) ?? query.error;
  return { ...query, siteId, error, isLoading: isSiteLoading || query.isLoading };
}

export function useLocations(locationType: LocationType) {
  return useQuery({
    queryKey: ["locations", locationType],
    queryFn: () => getLocationsByType(locationType),
    enabled: locationType !== LocationType.NOT_ASSIGNED,
  });
}

/**
 * Fetch locations without inventory counts (single API call).
 * Use this for getting the full list for filtering/pagination.
 */
export function useLocationsOnly(locationType: LocationType) {
  return useQuery({
    queryKey: ["locationsOnly", locationType],
    queryFn: async (): Promise<Location[]> => {
      return getLocationsByType(locationType);
    },
    enabled: locationType !== LocationType.NOT_ASSIGNED,
  });
}

/**
 * Fetch a single location by ID.
 */
export function useLocation(
  locationType: LocationType | undefined,
  locationId: string | undefined
): UseQueryResult<Location> {
  return useQuery({
    queryKey: ["location", locationType, locationId],
    queryFn: () => getLocationById(locationId!),
    enabled:
      !!locationType &&
      locationType !== LocationType.NOT_ASSIGNED &&
      !!locationId,
  });
}
