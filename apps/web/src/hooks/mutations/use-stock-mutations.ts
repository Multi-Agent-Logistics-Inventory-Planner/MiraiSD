"use client";

import { useMutation, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { adjustStock, transferStock } from "@/lib/api/stock-movements";
import type {
  AdjustStockRequest,
  LocationType,
  StockMovement,
  TransferStockRequest,
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

async function invalidateStockQueries(
  qc: QueryClient,
  productId?: string
) {
  await qc.invalidateQueries({ queryKey: ["inventoryTotals"] });
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
      await invalidateStockQueries(qc, variables.productId);
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
