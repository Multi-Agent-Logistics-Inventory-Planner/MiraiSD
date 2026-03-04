"use client";

import { useMutation, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { adjustStock, batchTransferStock, transferStock } from "@/lib/api/stock-movements";
import {
  LocationType,
  type AdjustStockRequest,
  type StockMovement,
  type TransferStockRequest,
} from "@/types/api";

interface AdjustStockVariables {
  locationType: LocationType;
  inventoryId: string;
  payload: AdjustStockRequest;
  productId?: string;
}

interface TransferStockVariables {
  payload: TransferStockRequest;
  productId?: string;
}

export interface BatchTransferItem {
  payload: TransferStockRequest;
  productId: string;
  productName: string;
}

interface BatchTransferVariables {
  transfers: BatchTransferItem[];
  sourceLocationId: string;
  destinationLocationId: string;
  sourceLocationType?: LocationType;
  destinationLocationType?: LocationType;
}

async function invalidateStockQueries(
  qc: QueryClient,
  productId?: string,
  locationType?: LocationType
) {
  await qc.invalidateQueries({ queryKey: ["products"] });

  // Invalidate not-assigned inventory if dealing with NOT_ASSIGNED location
  if (locationType === LocationType.NOT_ASSIGNED) {
    await qc.invalidateQueries({ queryKey: ["notAssignedInventory"] });
  }

  if (productId) {
    await qc.invalidateQueries({ queryKey: ["inventoryByItem", productId] });
    await qc.invalidateQueries({ queryKey: ["movementHistory", productId] });
  }
}

export function useAdjustStockMutation() {
  const qc = useQueryClient();
  return useMutation<StockMovement, Error, AdjustStockVariables>({
    mutationFn: ({ locationType, inventoryId, payload }) =>
      adjustStock(locationType, inventoryId, payload),
    onSuccess: async (_data, variables) => {
      await invalidateStockQueries(qc, variables.productId, variables.locationType);
    },
  });
}

export function useTransferStockMutation() {
  const qc = useQueryClient();
  return useMutation<StockMovement, Error, TransferStockVariables>({
    mutationFn: ({ payload }) => transferStock(payload),
    onSuccess: async (_data, variables) => {
      await invalidateStockQueries(qc, variables.productId);
    },
  });
}

export function useBatchTransferMutation() {
  const qc = useQueryClient();

  return useMutation<void, Error, BatchTransferVariables>({
    mutationFn: ({ transfers }) =>
      batchTransferStock({ transfers: transfers.map((t) => t.payload) }),
    onSuccess: async (_data, variables) => {
      await qc.invalidateQueries({ queryKey: ["products"] });

      if (
        variables.sourceLocationType === LocationType.NOT_ASSIGNED ||
        variables.destinationLocationType === LocationType.NOT_ASSIGNED
      ) {
        await qc.invalidateQueries({ queryKey: ["notAssignedInventory"] });
      }

      await qc.invalidateQueries({
        queryKey: ["locationInventory", variables.sourceLocationId],
      });
      await qc.invalidateQueries({
        queryKey: ["locationInventory", variables.destinationLocationId],
      });

      for (const transfer of variables.transfers) {
        await qc.invalidateQueries({
          queryKey: ["inventoryByItem", transfer.productId],
        });
      }
    },
  });
}
