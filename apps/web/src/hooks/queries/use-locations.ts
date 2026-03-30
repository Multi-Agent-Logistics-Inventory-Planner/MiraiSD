"use client";

import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import { LocationType, type Location } from "@/types/api";
import { getLocationsByType, getLocationById } from "@/lib/api/locations";

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
