"use client";

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
 * Subscribe to real-time inventory changes via stock_movements table.
 * This captures all inventory changes across all location types.
 */
export function useRealtimeInventory(enabled = true) {
  return useSupabaseRealtime<StockMovementRow>({
    table: "stock_movements",
    event: "INSERT",
    queryKeys: [
      ["inventoryTotals"],
      ["products"],
      ["dashboard"],
      ["inventory"],
      ["locationInventory"],
      ["notAssignedInventory"],
    ],
    enabled,
  });
}

/**
 * Subscribe to inventory changes for a specific product.
 */
export function useRealtimeProductInventory(itemId: string, enabled = true) {
  return useSupabaseRealtime<StockMovementRow>({
    table: "stock_movements",
    event: "INSERT",
    filter: `item_id=eq.${itemId}`,
    queryKeys: [
      ["inventoryTotals"],
      ["products"],
      ["inventory", itemId],
      ["movementHistory", itemId],
    ],
    enabled: enabled && !!itemId,
  });
}
