"use client";

import { useQuery } from "@tanstack/react-query";
import { getInventoryEntriesByItemId } from "@/lib/api/inventory";

export function useInventoryByItemId(itemId?: string | null) {
  return useQuery({
    queryKey: ["inventoryByItem", itemId ?? "none"],
    queryFn: () => getInventoryEntriesByItemId(itemId as string),
    enabled: Boolean(itemId),
  });
}
