"use client";

import { useState } from "react";
import { useMutation, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { adjustStock, transferStock } from "@/lib/api/stock-movements";
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

export interface BatchTransferProgress {
  completed: number;
  total: number;
  currentItem?: string;
  errors: Array<{ productName: string; error: string }>;
}

interface BatchTransferResult {
  successful: StockMovement[];
  failed: Array<{ productName: string; error: string }>;
}

async function invalidateStockQueries(
  qc: QueryClient,
  productId?: string,
  locationType?: LocationType
) {
  await qc.invalidateQueries({ queryKey: ["inventoryTotals"] });

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
  const [progress, setProgress] = useState<BatchTransferProgress>({
    completed: 0,
    total: 0,
    errors: [],
  });

  const mutation = useMutation<BatchTransferResult, Error, BatchTransferVariables>({
    mutationFn: async ({ transfers }) => {
      const successful: StockMovement[] = [];
      const failed: Array<{ productName: string; error: string }> = [];

      setProgress({
        completed: 0,
        total: transfers.length,
        errors: [],
      });

      for (let i = 0; i < transfers.length; i++) {
        const transfer = transfers[i];
        setProgress((prev) => ({
          ...prev,
          currentItem: transfer.productName,
        }));

        try {
          const result = await transferStock(transfer.payload);
          successful.push(result);
        } catch (err) {
          const errorMessage = err instanceof Error ? err.message : "Transfer failed";
          failed.push({ productName: transfer.productName, error: errorMessage });
        }

        setProgress((prev) => ({
          ...prev,
          completed: i + 1,
          errors: failed,
        }));
      }

      return { successful, failed };
    },
    onSuccess: async ({ successful }, variables) => {
      await qc.invalidateQueries({ queryKey: ["inventoryTotals"] });

      // Invalidate not-assigned inventory if source or destination is NOT_ASSIGNED
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
    onSettled: () => {
      setProgress({ completed: 0, total: 0, errors: [] });
    },
  });

  return { ...mutation, progress };
}
