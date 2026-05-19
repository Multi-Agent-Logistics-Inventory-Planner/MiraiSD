"use client";

import { useQuery } from "@tanstack/react-query";
import { LocationType, DISPLAY_ONLY_LOCATION_TYPES } from "@/types/api";
import { getLocationInventory, getStorageLocationInventory } from "@/lib/api/inventory";
import { getStorageLocationByCode } from "@/lib/api/locations";

/**
 * Hook to fetch inventory for a location.
 * Uses the unified /api/locations/{locationId}/inventory endpoint.
 * Backend already excludes child/prize products (parent_id IS NULL filter),
 * so consumers receive root products only.
 *
 * For NOT_ASSIGNED, automatically looks up the storage location ID.
 */
export function useLocationInventory(
  locationType: LocationType | undefined,
  locationId: string | undefined
) {
  const isDisplayOnly = locationType && DISPLAY_ONLY_LOCATION_TYPES.includes(locationType);
  const isNotAssigned = locationType === LocationType.NOT_ASSIGNED;

  return useQuery({
    queryKey: isNotAssigned
      ? ["notAssignedInventory"]
      : ["locationInventory", locationType, locationId],
    queryFn: async () => {
      if (isNotAssigned) {
        const storageLocation = await getStorageLocationByCode("NOT_ASSIGNED");
        return getStorageLocationInventory(storageLocation.id);
      }
      if (!locationId) {
        return [];
      }
      return getLocationInventory(locationId);
    },
    enabled: !isDisplayOnly && Boolean(locationType) && (isNotAssigned || Boolean(locationId)),
  });
}
