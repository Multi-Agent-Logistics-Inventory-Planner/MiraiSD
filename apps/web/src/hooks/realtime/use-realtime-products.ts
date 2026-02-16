"use client";

import { useSupabaseRealtime } from "./use-supabase-realtime";

interface ProductRow {
  id: string;
  name: string;
  sku: string;
  category: string | null;
  subcategory: string | null;
  reorder_point: number | null;
  is_active: boolean;
}

/**
 * Subscribe to real-time product updates.
 * Automatically refreshes product lists when products are added, updated, or deleted.
 */
export function useRealtimeProducts(enabled = true) {
  return useSupabaseRealtime<ProductRow>({
    table: "products",
    event: "*",
    queryKeys: [["products"], ["inventoryTotals"], ["dashboard"]],
    enabled,
  });
}
