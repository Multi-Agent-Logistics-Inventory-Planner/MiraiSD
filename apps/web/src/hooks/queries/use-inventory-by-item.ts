"use client";

import { useQuery } from "@tanstack/react-query";
import { getInventoryEntriesByItemId } from "@/lib/api/inventory";

/**
 * @deprecated Use useProductInventoryEntries() from use-product-inventory-entries.ts instead.
 * This function makes N+1 API calls and will be removed in a future version.
 */
export function useInventoryByItemId(itemId?: string | null) {
  return useQuery({
    queryKey: ["inventoryByItem", itemId ?? "none"],
    queryFn: () => getInventoryEntriesByItemId(itemId as string),
    enabled: Boolean(itemId),
  });
}
