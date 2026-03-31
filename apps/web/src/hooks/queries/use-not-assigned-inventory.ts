"use client";

import { useQuery } from "@tanstack/react-query";
import { getStorageLocationInventory } from "@/lib/api/inventory";
import { getStorageLocationByCode } from "@/lib/api/locations";
import type { LocationInventory } from "@/types/api";

/** Exclude child/prize products - storage shows only root (parent) products. */
function filterToRootProducts(items: LocationInventory[]): LocationInventory[] {
  return items.filter((x) => !x.item.parentId);
}

/**
 * Hook to fetch NOT_ASSIGNED inventory.
 * Automatically looks up the NOT_ASSIGNED storage location ID.
 */
export function useNotAssignedInventory() {
  return useQuery<LocationInventory[]>({
    queryKey: ["notAssignedInventory"],
    queryFn: async () => {
      // First, get the NOT_ASSIGNED storage location
      const storageLocation = await getStorageLocationByCode("NOT_ASSIGNED");

      // Then get the single location within NOT_ASSIGNED (code "NA")
      // For NOT_ASSIGNED, we query by storage location ID
      const data = await getStorageLocationInventory(storageLocation.id);
      return filterToRootProducts(data);
    },
    staleTime: 30_000, // 30 seconds
  });
}
