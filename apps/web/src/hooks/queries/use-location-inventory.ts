"use client";

import { useQuery } from "@tanstack/react-query";
import { LocationType, type Inventory, type NotAssignedInventory } from "@/types/api";
import { getInventoryByLocation, getNotAssignedInventory } from "@/lib/api/inventory";

/** Filter inventory to root products only (exclude child/prize products from storage view). */
function filterToRootProducts<T extends { item: { parentId?: string | null } }>(
  items: T[]
): T[] {
  return items.filter((x) => !x.item.parentId);
}

/**
 * Hook to fetch inventory for a location.
 * Handles NOT_ASSIGNED specially by fetching from the not-assigned inventory API.
 * Returns only root products (excludes Kuji prizes) so storage operations apply to parents.
 */
export function useLocationInventory(
  locationType: LocationType | undefined,
  locationId: string | undefined
) {
  const isNotAssigned = locationType === LocationType.NOT_ASSIGNED;

  return useQuery({
    queryKey: isNotAssigned
      ? ["notAssignedInventory"]
      : ["locationInventory", locationType, locationId],
    queryFn: async () => {
      let data: Inventory[];
      if (isNotAssigned) {
        const notAssigned = await getNotAssignedInventory();
        data = notAssigned as unknown as Inventory[];
      } else {
        data = (await getInventoryByLocation(
          locationType as LocationType,
          locationId as string
        )) as Inventory[];
      }
      return filterToRootProducts(data);
    },
    enabled: isNotAssigned || (Boolean(locationType) && Boolean(locationId)),
  });
}

