"use client";

import { useQuery } from "@tanstack/react-query";
import { type ProductInventoryResponse } from "@/types/api";
import { getProductInventoryEntries } from "@/lib/api/inventory";

/**
 * Fetch all inventory entries for a product across all location types in a single request.
 * This is the optimized replacement for getInventoryEntriesByItemId.
 *
 * Reduces API calls from 70+ (1 per location) to 1.
 *
 * @param productId The product ID to look up (optional, query is disabled if not provided)
 */
export function useProductInventoryEntries(productId?: string | null) {
  return useQuery<ProductInventoryResponse>({
    queryKey: ["productInventoryEntries", productId],
    queryFn: () => getProductInventoryEntries(productId!),
    enabled: Boolean(productId),
    staleTime: 30_000, // 30 seconds - data is refreshed by realtime subscriptions
  });
}
