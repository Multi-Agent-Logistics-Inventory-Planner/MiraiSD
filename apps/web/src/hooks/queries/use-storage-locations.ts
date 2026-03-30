"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { getStorageLocations, createStorageLocation, type CreateStorageLocationRequest } from "@/lib/api/locations";

export interface StorageLocationCategory {
  id: string;
  code: string;
  name: string;
  hasDisplay: boolean;
  isDisplayOnly: boolean;
  displayOrder: number;
}

/**
 * Hook to fetch all storage location categories.
 * Use this to get storage location IDs for querying inventory.
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

/**
 * Hook to create a new storage location category.
 */
export function useCreateStorageLocationMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateStorageLocationRequest) => createStorageLocation(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["storageLocations"] });
    },
  });
}
