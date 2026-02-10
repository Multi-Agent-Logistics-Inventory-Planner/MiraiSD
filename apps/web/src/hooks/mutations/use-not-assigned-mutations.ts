"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { NotAssignedInventory, InventoryRequest } from "@/types/api";
import {
  updateNotAssignedInventory,
  deleteNotAssignedInventory,
} from "@/lib/api/inventory";

function invalidateNotAssignedInventory(qc: ReturnType<typeof useQueryClient>) {
  return Promise.all([
    qc.invalidateQueries({ queryKey: ["notAssignedInventory"] }),
    qc.invalidateQueries({ queryKey: ["inventoryTotals"] }),
    qc.invalidateQueries({ queryKey: ["dashboardStats"] }),
  ]);
}

export function useUpdateNotAssignedInventoryMutation() {
  const qc = useQueryClient();

  return useMutation<
    NotAssignedInventory,
    Error,
    { inventoryId: string; payload: InventoryRequest }
  >({
    mutationFn: async ({ inventoryId, payload }) => {
      return updateNotAssignedInventory(inventoryId, payload);
    },
    onSuccess: async () => {
      await invalidateNotAssignedInventory(qc);
    },
  });
}

export function useDeleteNotAssignedInventoryMutation() {
  const qc = useQueryClient();

  return useMutation<void, Error, { inventoryId: string }>({
    mutationFn: async ({ inventoryId }) => {
      return deleteNotAssignedInventory(inventoryId);
    },
    onSuccess: async () => {
      await invalidateNotAssignedInventory(qc);
    },
  });
}
