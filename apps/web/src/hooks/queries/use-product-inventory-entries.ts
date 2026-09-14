"use client";

import { useQuery, skipToken } from "@tanstack/react-query";
import { type ProductInventoryResponse } from "@/types/api";
import { getProductInventoryEntries } from "@/lib/api/inventory";
import { getSiteProductInventory } from "@/lib/api/site-inventory";
import { useCurrentSite } from "@/hooks/queries/use-current-site";

/**
 * Fetch all inventory entries for a product across all location types in a single request.
 * This is the optimized replacement for getInventoryEntriesByItemId.
 *
 * Reduces API calls from 70+ (1 per location) to 1.
 *
 * Legacy, unscoped - kept for Kuji dialogs, which stay on legacy per this checkpoint's scope
 * (see .specs/phase-6-inventory/log.md's T-6d-12 residual-debt note). Products page/product
 * detail use useSiteProductInventoryEntries below instead.
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

/**
 * Site-scoped counterpart to useProductInventoryEntries (T-6d-5). Query key is site-qualified
 * and the query stays disabled (skipToken) until siteId resolves, per T-6d-3.
 */
export function useSiteProductInventoryEntries(productId?: string | null) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery<ProductInventoryResponse>({
    queryKey: ["productInventoryEntries", siteId, productId],
    queryFn: siteId && productId ? () => getSiteProductInventory(siteId, productId) : skipToken,
    staleTime: 30_000,
  });

  return {
    data: query.data,
    isLoading: (Boolean(productId) && isSiteLoading) || query.isLoading,
    error: siteError ?? query.error,
  };
}
