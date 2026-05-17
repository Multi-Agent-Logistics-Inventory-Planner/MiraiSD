"use client";

import { useMutation, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { batchAdjustStock, batchTransferStock, transferStock } from "@/lib/api/stock-movements";
import { getProductById } from "@/lib/api/products";
import {
  LocationType,
  type BatchAdjustStockRequest,
  type Product,
  type StockMovement,
  type TransferStockRequest,
} from "@/types/api";

export interface BatchAdjustVariables {
  payload: BatchAdjustStockRequest;
  /** Product ids touched by this batch — used to invalidate per-product query keys. */
  productIds: string[];
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
  // Surgical product update: fetch single product and update cache (avoid full list refetch)
  if (productId) {
    // Invalidate specific product queries
    await qc.invalidateQueries({ queryKey: ["products", productId] });
    await qc.invalidateQueries({ queryKey: ["products", productId, "with-children"] });
    await qc.invalidateQueries({ queryKey: ["products", productId, "children"] });

    // Fetch single product and update all list caches
    try {
      const updatedProduct = await getProductById(productId);
      qc.setQueriesData<Product[]>(
        { queryKey: ["products"] },
        (oldData) => {
          if (!oldData || !Array.isArray(oldData)) return oldData;
          const index = oldData.findIndex((p) => p.id === productId);
          if (index === -1) return oldData;
          return [
            ...oldData.slice(0, index),
            updatedProduct,
            ...oldData.slice(index + 1),
          ];
        }
      );
    } catch {
      // Fallback: if single fetch fails, invalidate all
      await qc.invalidateQueries({ queryKey: ["products"] });
    }

    await qc.invalidateQueries({ queryKey: ["inventoryByItem", productId] });
    await qc.invalidateQueries({ queryKey: ["movementHistory", productId] });
  } else {
    // No specific productId, fall back to full invalidation
    await qc.invalidateQueries({ queryKey: ["products"] });
  }

  // Invalidate not-assigned inventory if dealing with NOT_ASSIGNED location
  if (locationType === LocationType.NOT_ASSIGNED) {
    await qc.invalidateQueries({ queryKey: ["notAssignedInventory"] });
  }
}

export function useBatchAdjustStockMutation() {
  const qc = useQueryClient();
  return useMutation<void, Error, BatchAdjustVariables>({
    mutationFn: ({ payload }) => batchAdjustStock(payload),
    onSuccess: async (_data, variables) => {
      const { payload, productIds } = variables;
      const uniqueProductIds = [...new Set(productIds)];

      const tasks: Promise<unknown>[] = [
        qc.invalidateQueries({
          queryKey: ["locationInventory", payload.locationType, payload.locationId],
        }),
        qc.invalidateQueries({ queryKey: ["auditLogs"] }),
        qc.invalidateQueries({ queryKey: ["auditLog"] }),
      ];

      for (const id of uniqueProductIds) {
        tasks.push(qc.invalidateQueries({ queryKey: ["products", id] }));
        tasks.push(qc.invalidateQueries({ queryKey: ["products", id, "with-children"] }));
        tasks.push(qc.invalidateQueries({ queryKey: ["products", id, "children"] }));
        tasks.push(qc.invalidateQueries({ queryKey: ["productInventoryEntries", id] }));
        tasks.push(qc.invalidateQueries({ queryKey: ["inventoryByItem", id] }));
        tasks.push(qc.invalidateQueries({ queryKey: ["movementHistory", id] }));
      }

      if (payload.locationType === LocationType.NOT_ASSIGNED) {
        tasks.push(qc.invalidateQueries({ queryKey: ["notAssignedInventory"] }));
      }

      await Promise.all(tasks);
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
      // Batch transfers affect multiple products - fetch each and update cache
      const productIds = [...new Set(variables.transfers.map((t) => t.productId))];

      try {
        // Fetch all affected products in parallel
        const updatedProducts = await Promise.all(
          productIds.map((id) => getProductById(id))
        );

        // Update all products list caches with the new data
        qc.setQueriesData<Product[]>(
          { queryKey: ["products"] },
          (oldData) => {
            if (!oldData || !Array.isArray(oldData)) return oldData;
            const updatedMap = new Map(updatedProducts.map((p) => [p.id, p]));
            return oldData.map((p) => updatedMap.get(p.id) ?? p);
          }
        );

        // Invalidate specific product queries
        for (const id of productIds) {
          await qc.invalidateQueries({ queryKey: ["products", id] });
          await qc.invalidateQueries({ queryKey: ["products", id, "with-children"] });
          await qc.invalidateQueries({ queryKey: ["products", id, "children"] });
        }
      } catch {
        // Fallback: if fetching fails, invalidate all
        await qc.invalidateQueries({ queryKey: ["products"] });
      }

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
