"use client";

import { useQuery } from "@tanstack/react-query";
import { getStorageLocations } from "@/lib/api/locations";

export interface StorageLocationCategory {
  id: string;
  code: string;
  name: string;
  codePrefix?: string;
  icon?: string;
  hasDisplay: boolean;
  isDisplayOnly: boolean;
  displayOrder: number;
}

/**
 * Hook to fetch all storage location categories.
 * Storage location types are fixed and seeded automatically.
 */
export function useStorageLocations() {
  return useQuery<StorageLocationCategory[]>({
    queryKey: ["storageLocations"],
    queryFn: getStorageLocations,
    staleTime: 5 * 60 * 1000, // 5 minutes - this data rarely changes
  });
}

/**
 * Hook to get the NOT_ASSIGNED storage location.
 * Returns the storage location ID needed for NOT_ASSIGNED inventory queries.
 */
export function useNotAssignedStorageLocation() {
  const { data: storageLocations, ...rest } = useStorageLocations();

  const notAssignedLocation = storageLocations?.find(
    (sl) => sl.code === "NOT_ASSIGNED"
  );

  return {
    data: notAssignedLocation,
    storageLocationId: notAssignedLocation?.id,
    ...rest,
  };
}
