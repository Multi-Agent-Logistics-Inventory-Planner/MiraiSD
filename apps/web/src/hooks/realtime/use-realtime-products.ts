"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useSupabaseRealtime, type RealtimePayload } from "./use-supabase-realtime";
import { getProductById } from "@/lib/api/products";
import type { Product } from "@/types/api";

interface ProductRow {
  id: string;
  name: string;
  sku: string;
  category_id: string | null;
  reorder_point: number | null;
  is_active: boolean;
}

/**
 * Subscribe to real-time product updates.
 * Uses surgical cache updates to avoid refetching all products.
 */
export function useRealtimeProducts(enabled = true) {
  const queryClient = useQueryClient();

  return useSupabaseRealtime<ProductRow>({
    table: "products",
    event: "*",
    queryKeys: [["dashboard"]], // Only invalidate dashboard, handle products surgically
    onReceive: (payload: RealtimePayload<ProductRow>) => {
      const productId = payload.new?.id ?? payload.old?.id;

      if (!productId) {
        // No ID available, fall back to full invalidation
        queryClient.invalidateQueries({ queryKey: ["products"] });
        return;
      }

      if (payload.eventType === "DELETE") {
        // Remove deleted product from all list caches
        queryClient.setQueriesData<Product[]>(
          { queryKey: ["products"] },
          (oldData) => {
            if (!oldData || !Array.isArray(oldData)) return oldData;
            return oldData.filter((p) => p.id !== productId);
          }
        );
        queryClient.removeQueries({ queryKey: ["products", productId] });
        return;
      }

      // INSERT or UPDATE: fetch single product and update cache
      queryClient.invalidateQueries({ queryKey: ["products", productId] });
      queryClient.invalidateQueries({ queryKey: ["products", productId, "with-children"] });
      queryClient.invalidateQueries({ queryKey: ["products", productId, "children"] });

      getProductById(productId)
        .then((updatedProduct: Product) => {
          queryClient.setQueriesData<Product[]>(
            { queryKey: ["products"] },
            (oldData) => {
              if (!oldData || !Array.isArray(oldData)) return oldData;
              const index = oldData.findIndex((p) => p.id === productId);
              if (index === -1) {
                // New product, add to list
                return [...oldData, updatedProduct];
              }
              // Update existing product
              return [
                ...oldData.slice(0, index),
                updatedProduct,
                ...oldData.slice(index + 1),
              ];
            }
          );
        })
        .catch(() => {
          // Fallback: if single fetch fails, invalidate all
          queryClient.invalidateQueries({ queryKey: ["products"] });
        });
    },
    enabled,
  });
}
