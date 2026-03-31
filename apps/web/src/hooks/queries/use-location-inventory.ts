"use client";

import { useQuery } from "@tanstack/react-query";
import { LocationType, type LocationInventory, DISPLAY_ONLY_LOCATION_TYPES } from "@/types/api";
import { getLocationInventory, getStorageLocationInventory } from "@/lib/api/inventory";
import { getStorageLocationByCode } from "@/lib/api/locations";

/** Virtual ID used for NOT_ASSIGNED when no real ID is available */
const NOT_ASSIGNED_VIRTUAL_ID = "__not_assigned__";

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
      let data: LocationInventory[];

      if (isNotAssigned) {
        // For NOT_ASSIGNED, look up the storage location ID
        const storageLocation = await getStorageLocationByCode("NOT_ASSIGNED");
        data = await getStorageLocationInventory(storageLocation.id);
      } else {
        if (!locationId) {
          return [];
        }
        data = await getLocationInventory(locationId);
      }

      return filterToRootProducts(data);
    },
    enabled: !isDisplayOnly && Boolean(locationType) && (isNotAssigned || Boolean(locationId)),
  });
}
