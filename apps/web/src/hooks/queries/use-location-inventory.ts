"use client";

import { useQuery } from "@tanstack/react-query";
import { LocationType, type Inventory, type NotAssignedInventory } from "@/types/api";
import { getInventoryByLocation, getNotAssignedInventory } from "@/lib/api/inventory";

/**
 * Hook to fetch inventory for a location.
 * Handles NOT_ASSIGNED specially by fetching from the not-assigned inventory API.
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
      if (isNotAssigned) {
        // Fetch not-assigned inventory and cast to Inventory[]
        // NotAssignedInventory is compatible with Inventory
        const notAssigned = await getNotAssignedInventory();
        return notAssigned as unknown as Inventory[];
      }
      return getInventoryByLocation(
        locationType as LocationType,
        locationId as string
      ) as Promise<Inventory[]>;
    },
    enabled: isNotAssigned || (Boolean(locationType) && Boolean(locationId)),
  });
}

