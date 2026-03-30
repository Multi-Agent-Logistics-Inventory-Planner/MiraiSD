"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { LocationInventory, InventoryRequest } from "@/types/api";
import {
  updateLocationInventory,
  deleteLocationInventory,
} from "@/lib/api/inventory";

function invalidateNotAssignedInventory(qc: ReturnType<typeof useQueryClient>) {
  return Promise.all([
    qc.invalidateQueries({ queryKey: ["notAssignedInventory"] }),
    qc.invalidateQueries({ queryKey: ["locationInventory"] }),
    qc.invalidateQueries({ queryKey: ["products"] }),
    qc.invalidateQueries({ queryKey: ["dashboardStats"] }),
    qc.invalidateQueries({ queryKey: ["inventoryTotals"] }),
  ]);
}

/**
 * Hook for updating NOT_ASSIGNED inventory.
 * Requires the NOT_ASSIGNED location ID.
 */
export function useUpdateNotAssignedInventoryMutation(notAssignedLocationId: string) {
  const qc = useQueryClient();

  return useMutation<
    LocationInventory,
    Error,
    { inventoryId: string; payload: InventoryRequest }
  >({
    mutationFn: async ({ inventoryId, payload }) => {
      return updateLocationInventory(notAssignedLocationId, inventoryId, payload);
    },
    onSuccess: async () => {
      await invalidateNotAssignedInventory(qc);
    },
  });
}

/**
 * Hook for deleting NOT_ASSIGNED inventory.
 * Requires the NOT_ASSIGNED location ID.
 */
export function useDeleteNotAssignedInventoryMutation(notAssignedLocationId: string) {
  const qc = useQueryClient();

  return useMutation<void, Error, { inventoryId: string }>({
    mutationFn: async ({ inventoryId }) => {
      return deleteLocationInventory(notAssignedLocationId, inventoryId);
    },
    onSuccess: async () => {
      await invalidateNotAssignedInventory(qc);
    },
  });
}
