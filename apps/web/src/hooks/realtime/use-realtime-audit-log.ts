"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useSupabaseRealtime } from "./use-supabase-realtime";

interface StockMovementRow {
  id: number;
  item_id: string;
  location_type: string;
  quantity_change: number;
  reason: string;
  at: string;
}

/**
 * Hook to enable real-time updates for audit log queries.
 * Listens to stock_movements table and invalidates audit log queries.
 */
export function useRealtimeAuditLog(enabled = true) {
  const queryClient = useQueryClient();

  useSupabaseRealtime<StockMovementRow>({
    table: "stock_movements",
    event: "INSERT",
    queryKeys: [],
    onReceive: () => {
      // Invalidate all audit log related queries
      queryClient.invalidateQueries({ queryKey: ["audit-log"] });
      queryClient.invalidateQueries({ queryKey: ["audit-logs"] });
      queryClient.invalidateQueries({ queryKey: ["audit-log-detail"] });
      queryClient.invalidateQueries({ queryKey: ["activity-feed"] });
    },
    enabled,
  });
}
