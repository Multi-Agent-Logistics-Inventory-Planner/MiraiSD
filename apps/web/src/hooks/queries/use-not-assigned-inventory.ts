"use client";

import { useQuery } from "@tanstack/react-query";
import { getStorageLocationInventory } from "@/lib/api/inventory";
import { getStorageLocationByCode } from "@/lib/api/locations";
import type { LocationInventory } from "@/types/api";

/**
 * Hook to fetch NOT_ASSIGNED inventory.
 * Backend already excludes child/prize products via parent_id IS NULL.
 */
export function useNotAssignedInventory() {
  return useQuery<LocationInventory[]>({
    queryKey: ["notAssignedInventory"],
    queryFn: async () => {
      const storageLocation = await getStorageLocationByCode("NOT_ASSIGNED");
      return getStorageLocationInventory(storageLocation.id);
    },
    staleTime: 30_000,
  });
}
