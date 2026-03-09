"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useSupabaseRealtime, type RealtimePayload } from "./use-supabase-realtime";
import { getProductById } from "@/lib/api/products";
import type { Product } from "@/types/api";

interface StockMovementRow {
  id: number;
  item_id: string;
  quantity_change: number;
  reason: string;
  at: string;
}

/**
 * Hook to enable real-time updates for dashboard queries.
 * Listens to stock_movements table and invalidates relevant queries.
 * Uses surgical product updates to avoid refetching all products.
 */
export function useRealtimeDashboard(enabled = true) {
  const queryClient = useQueryClient();

  useSupabaseRealtime<StockMovementRow>({
    table: "stock_movements",
    event: "INSERT",
    queryKeys: [],
    onReceive: (payload: RealtimePayload<StockMovementRow>) => {
      const itemId = payload.new?.item_id;

      queryClient.invalidateQueries({ queryKey: ["forecasts"] });
      queryClient.invalidateQueries({ queryKey: ["analytics"] });
      queryClient.invalidateQueries({ queryKey: ["activity-feed"] });
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
      queryClient.invalidateQueries({ queryKey: ["shipments"] });

      // Surgical product update (avoid full list refetch)
      if (itemId) {
        queryClient.invalidateQueries({ queryKey: ["products", itemId] });
        queryClient.invalidateQueries({ queryKey: ["products", itemId, "with-children"] });
        queryClient.invalidateQueries({ queryKey: ["products", itemId, "children"] });

        getProductById(itemId)
          .then((updatedProduct: Product) => {
            queryClient.setQueriesData<Product[]>(
              { queryKey: ["products"] },
              (oldData) => {
                if (!oldData || !Array.isArray(oldData)) return oldData;
                const index = oldData.findIndex((p) => p.id === itemId);
                if (index === -1) return oldData;
                return [
                  ...oldData.slice(0, index),
                  updatedProduct,
                  ...oldData.slice(index + 1),
                ];
              }
            );
          })
          .catch(() => {
            queryClient.invalidateQueries({ queryKey: ["products"] });
          });
      } else {
        queryClient.invalidateQueries({ queryKey: ["products"] });
      }
    },
    enabled,
  });
}
