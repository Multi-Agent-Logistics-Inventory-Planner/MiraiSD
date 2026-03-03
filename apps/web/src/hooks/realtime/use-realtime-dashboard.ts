"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useSupabaseRealtime } from "./use-supabase-realtime";

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
 */
export function useRealtimeDashboard(enabled = true) {
  const queryClient = useQueryClient();

  useSupabaseRealtime<StockMovementRow>({
    table: "stock_movements",
    event: "INSERT",
    queryKeys: [],
    onReceive: () => {
      queryClient.invalidateQueries({ queryKey: ["forecasts"] });
      queryClient.invalidateQueries({ queryKey: ["analytics"] });
      queryClient.invalidateQueries({ queryKey: ["activity-feed"] });
      queryClient.invalidateQueries({ queryKey: ["products"] });
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
      queryClient.invalidateQueries({ queryKey: ["shipments"] });
    },
    enabled,
  });
}
