"use client";

import { useQueryClient, type QueryClient } from "@tanstack/react-query";
import { useSupabaseRealtime, type RealtimePayload } from "./use-supabase-realtime";
import { getProductById } from "@/lib/api/products";
import type { Product } from "@/types/api";

interface StockMovementRow {
  id: number;
  item_id: string;
  location_type: string;
  quantity_change: number;
  reason: string;
  at: string;
}

/**
 * Surgical product update: fetch single product and update cache.
 * Avoids full products list refetch.
 */
function surgicalProductUpdate(queryClient: QueryClient, itemId: string) {
  // Invalidate specific product queries
  queryClient.invalidateQueries({ queryKey: ["products", itemId] });
  queryClient.invalidateQueries({ queryKey: ["products", itemId, "with-children"] });
  queryClient.invalidateQueries({ queryKey: ["products", itemId, "children"] });

  // Fetch single product and update all list caches
  getProductById(itemId)
    .then((updatedProduct: Product) => {
      queryClient.setQueriesData<Product[]>(
        { queryKey: ["products"] },
        (oldData) => {
          if (!oldData || !Array.isArray(oldData)) return oldData;
          const index = oldData.findIndex((p) => p.id === itemId);
          if (index === -1) {
            // Product not in list - INSERT event, add it
            return [...oldData, updatedProduct];
          }
          // Existing product - UPDATE in place
          return [
            ...oldData.slice(0, index),
            updatedProduct,
            ...oldData.slice(index + 1),
          ];
        }
      );
    })
    .catch(() => {
      // Fallback for DELETE (404) or network error
      queryClient.invalidateQueries({ queryKey: ["products"] });
    });
}

/**
 * Subscribe to real-time inventory changes via stock_movements table.
 * Uses surgical invalidation to only refetch affected queries.
 *
 * Before: Invalidated 6+ query keys per event, causing 11-88 refetches
 * After: Invalidates only affected queries (2-4 per event)
 */
export function useRealtimeInventory(enabled = true) {
  const queryClient = useQueryClient();

  return useSupabaseRealtime<StockMovementRow>({
    table: "stock_movements",
    event: "INSERT",
    queryKeys: [], // Handled in onReceive for surgical invalidation
    onReceive: (payload: RealtimePayload<StockMovementRow>) => {
      const { item_id, location_type } = payload.new;

      // Surgical invalidation - only affected queries
      // 1. Invalidate the specific location type's counts
      queryClient.invalidateQueries({
        queryKey: ["locationsWithCounts", location_type],
      });

      // 2. Also invalidate the "all locations" query
      queryClient.invalidateQueries({
        queryKey: ["locationsWithCounts"],
        exact: true,
      });

      // 3. Invalidate the specific product's inventory entries
      queryClient.invalidateQueries({
        queryKey: ["productInventoryEntries", item_id],
      });

      // 4. Surgical product update (avoid full list refetch)
      surgicalProductUpdate(queryClient, item_id);
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });

      // 5. Invalidate not-assigned inventory (items may have been assigned)
      queryClient.invalidateQueries({ queryKey: ["notAssignedInventory"] });
    },
    enabled,
  });
}

/**
 * Subscribe to inventory changes for a specific product.
 */
export function useRealtimeProductInventory(itemId: string, enabled = true) {
  const queryClient = useQueryClient();

  return useSupabaseRealtime<StockMovementRow>({
    table: "stock_movements",
    event: "INSERT",
    filter: `item_id=eq.${itemId}`,
    queryKeys: [], // Handled in onReceive for surgical invalidation
    onReceive: (payload: RealtimePayload<StockMovementRow>) => {
      const { location_type } = payload.new;

      // Surgical invalidation for this product
      queryClient.invalidateQueries({
        queryKey: ["productInventoryEntries", itemId],
      });
      surgicalProductUpdate(queryClient, itemId);
      queryClient.invalidateQueries({
        queryKey: ["movementHistory", itemId],
      });
      queryClient.invalidateQueries({
        queryKey: ["locationsWithCounts", location_type],
      });
    },
    enabled: enabled && !!itemId,
  });
}
