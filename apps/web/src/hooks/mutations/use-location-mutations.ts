"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import type {
  LocationType,
  InventoryRequest,
  LocationInventory,
  Location,
} from "@/types/api";
import { STORAGE_LOCATION_CODES } from "@/types/api";
import {
  createLocation,
  updateLocation,
  deleteLocation,
  getStorageLocationByCode,
} from "@/lib/api/locations";
import {
  createInventory,
  updateInventory,
  deleteInventory,
} from "@/lib/api/inventory";

function invalidateLocations(qc: ReturnType<typeof useQueryClient>, locationType: LocationType) {
  return Promise.all([
    qc.invalidateQueries({ queryKey: ["locations", locationType] }),
    qc.invalidateQueries({ queryKey: ["locationsWithCounts", locationType] }),
    qc.invalidateQueries({ queryKey: ["locationsWithCounts"] }),
  ]);
}

function invalidateLocationInventory(
  qc: ReturnType<typeof useQueryClient>,
  locationType: LocationType,
  locationId: string
) {
  return Promise.all([
    qc.invalidateQueries({ queryKey: ["locationInventory", locationType, locationId] }),
    qc.invalidateQueries({ queryKey: ["locationInventory", locationId] }),
    qc.invalidateQueries({ queryKey: ["locationsWithCounts", locationType] }),
    qc.invalidateQueries({ queryKey: ["locationsWithCounts"] }),
    qc.invalidateQueries({ queryKey: ["notAssignedInventory"] }),
    qc.invalidateQueries({ queryKey: ["products"] }),
    qc.invalidateQueries({ queryKey: ["dashboardStats"] }),
    qc.invalidateQueries({ queryKey: ["inventoryTotals"] }),
  ]);
}

export function useCreateLocationMutation(locationType: LocationType) {
  const qc = useQueryClient();

  return useMutation<Location, Error, { locationCode: string }>({
    mutationFn: async ({ locationCode }) => {
      // Look up the storage location ID for this location type
      const storageLocationCode = STORAGE_LOCATION_CODES[locationType];
      const storageLocation = await getStorageLocationByCode(storageLocationCode);

      return createLocation({
        locationCode,
        storageLocationId: storageLocation.id,
      });
    },
    onSuccess: async () => {
      await invalidateLocations(qc, locationType);
    },
  });
}

export function useUpdateLocationMutation(locationType: LocationType) {
  const qc = useQueryClient();

  return useMutation<Location, Error, { id: string; payload: { locationCode: string } }>({
    mutationFn: async ({ id, payload }) => {
      return updateLocation(id, payload);
    },
    onSuccess: async () => {
      await invalidateLocations(qc, locationType);
    },
  });
}

export function useDeleteLocationMutation(locationType: LocationType) {
  const qc = useQueryClient();

  return useMutation<void, Error, { id: string }>({
    mutationFn: async ({ id }) => {
      return deleteLocation(id);
    },
    onSuccess: async () => {
      await invalidateLocations(qc, locationType);
    },
  });
}

export function useCreateInventoryMutation(locationType: LocationType, locationId: string) {
  const qc = useQueryClient();

  return useMutation<LocationInventory, Error, InventoryRequest>({
    mutationFn: async (payload) => {
      return createInventory(locationType, locationId, payload);
    },
    onSuccess: async () => {
      await invalidateLocationInventory(qc, locationType, locationId);
    },
  });
}

export function useUpdateInventoryMutation(
  locationType: LocationType,
  locationId: string
) {
  const qc = useQueryClient();

  return useMutation<LocationInventory, Error, { inventoryId: string; payload: InventoryRequest }>({
    mutationFn: async ({ inventoryId, payload }) => {
      return updateInventory(locationType, locationId, inventoryId, payload);
    },
    onSuccess: async () => {
      await invalidateLocationInventory(qc, locationType, locationId);
    },
  });
}

export function useDeleteInventoryMutation(
  locationType: LocationType,
  locationId: string
) {
  const qc = useQueryClient();

  return useMutation<void, Error, { inventoryId: string }>({
    mutationFn: async ({ inventoryId }) => {
      return deleteInventory(locationType, locationId, inventoryId);
    },
    onSuccess: async () => {
      await invalidateLocationInventory(qc, locationType, locationId);
    },
  });
}
