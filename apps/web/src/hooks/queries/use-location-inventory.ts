"use client";

import { useQuery } from "@tanstack/react-query";
import { LocationType, type LocationInventory, DISPLAY_ONLY_LOCATION_TYPES } from "@/types/api";
import { getLocationInventory, getStorageLocationInventory } from "@/lib/api/inventory";

/** Filter inventory to root products only (exclude child/prize products from storage view). */
function filterToRootProducts<T extends { item: { parentId?: string | null } }>(
  items: T[]
): T[] {
  return items.filter((x) => !x.item.parentId);
}

/**
 * Hook to fetch inventory for a location.
 * Uses the unified /api/locations/{locationId}/inventory endpoint.
 * Returns only root products (excludes Kuji prizes) so storage operations apply to parents.
 *
 * For NOT_ASSIGNED, pass the storage location ID as locationId to use the
 * /api/storage-locations/{storageLocationId}/inventory endpoint.
 */
export function useLocationInventory(
  locationType: LocationType | undefined,
  locationId: string | undefined
) {
  const isDisplayOnly = locationType && DISPLAY_ONLY_LOCATION_TYPES.includes(locationType);
  const isNotAssigned = locationType === LocationType.NOT_ASSIGNED;

  return useQuery({
    queryKey: isNotAssigned
      ? ["notAssignedInventory", locationId]
      : ["locationInventory", locationType, locationId],
    queryFn: async () => {
      if (!locationId) {
        return [];
      }

      let data: LocationInventory[];

      if (isNotAssigned) {
        // For NOT_ASSIGNED, locationId is the storage location ID
        data = await getStorageLocationInventory(locationId);
      } else {
        data = await getLocationInventory(locationId);
      }

      return filterToRootProducts(data);
    },
    enabled: !isDisplayOnly && Boolean(locationType) && Boolean(locationId),
  });
}
