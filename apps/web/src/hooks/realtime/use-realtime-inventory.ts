"use client";

import { useQueryClient, type QueryClient } from "@tanstack/react-query";
import { useSupabaseRealtime, type RealtimePayload } from "./use-supabase-realtime";
import { getProductById } from "@/lib/api/products";
import { useCurrentSite } from "@/hooks/queries/use-current-site";
import { legacyProductsListFilter } from "./legacy-products-query-filter";
import type { Product } from "@/types/api";

interface StockMovementRow {
  id: number;
  item_id: string;
  location_type: string;
  quantity_change: number;
  reason: string;
  at: string;
  /**
   * Null for rows written before the site backfill (legacy/pre-backfill data) - per T-6d-11,
   * these are treated as *possibly relevant* to every site and never filtered out, since there
   * is no way to know which site (if any) they actually belong to.
   */
  site_id?: string | null;
}

/**
 * Whether this row could plausibly affect the current site's cached data. A row explicitly
 * tagged with a different site is definitely not relevant and is dropped before any
 * invalidation runs (T-6d-11) - this is the only site-scoping this hook does; the broader
 * org-wide `db-changes` broadcast channel (a separate subscription, SupabaseBroadcastService)
 * is left over-invalidating-but-safe for 6d, per the checkpoint's scope (6e's AC-7 job).
 */
function isRelevantToCurrentSite(row: StockMovementRow, siteId: string | undefined): boolean {
  if (!siteId) return false;
  if (row.site_id === null || row.site_id === undefined) return true;
  return row.site_id === siteId;
}

/**
 * Surgical product update: fetch single product and update cache.
 * Avoids full products list refetch.
 */
function surgicalProductUpdate(queryClient: QueryClient, itemId: string) {
  // Invalidate specific product queries
  queryClient.invalidateQueries({ queryKey: ["products", itemId] });
  queryClient.invalidateQueries({ queryKey: ["products", itemId, "with-children"] });
  queryClient.invalidateQueries({ queryKey: ["products", itemId, "children"] });

  getProductById(itemId)
    .then((updatedProduct: Product) => {
      queryClient.setQueriesData<Product[]>(legacyProductsListFilter, (oldData) => {
        if (!oldData || !Array.isArray(oldData)) return oldData;
        const index = oldData.findIndex((p) => p.id === itemId);
        if (index === -1) {
          // Product not in list - INSERT event, add it
          return [...oldData, updatedProduct];
        }
        // Existing product - UPDATE in place
        return [
          ...oldData.slice(0, index),
          updatedProduct,
          ...oldData.slice(index + 1),
        ];
      });
    })
    .catch(() => {
      // Fallback for DELETE (404) or network error
      queryClient.invalidateQueries(legacyProductsListFilter);
    });
}

/**
 * Subscribe to real-time inventory changes via stock_movements table.
 * Uses surgical invalidation to only refetch affected queries.
 *
 * Site-scoped as of Phase 6 checkpoint 6d (T-6d-11): events for a different site are ignored
 * before any invalidation runs; the subscription itself re-subscribes whenever the resolved
 * site changes, since `onReceive`'s closure (and therefore its identity) changes with `siteId`.
 *
 * Before: Invalidated 6+ query keys per event, causing 11-88 refetches
 * After: Invalidates only affected queries (2-4 per event)
 */
export function useRealtimeInventory(enabled = true) {
  const queryClient = useQueryClient();
  const { siteId } = useCurrentSite();

  return useSupabaseRealtime<StockMovementRow>({
    table: "stock_movements",
    event: "INSERT",
    queryKeys: [], // Handled in onReceive for surgical invalidation
    onReceive: (payload: RealtimePayload<StockMovementRow>) => {
      if (!isRelevantToCurrentSite(payload.new, siteId)) {
        return;
      }
      const { item_id, location_type } = payload.new;

      // Surgical invalidation - only affected queries
      // 1. Invalidate the specific location type's counts (legacy, site-blind - residual debt,
      //    see .specs/phase-6-inventory/log.md's T-6d-12 note on getLocationsWithCounts).
      queryClient.invalidateQueries({
        queryKey: ["locationsWithCounts", location_type],
      });

      // 2. Also invalidate the "all locations" query
      queryClient.invalidateQueries({
        queryKey: ["locationsWithCounts"],
        exact: true,
      });

      // 3. Invalidate the specific product's inventory entries - both the legacy key (kuji
      // dialogs, which stay on legacy per this checkpoint's scope) and the site-scoped key.
      queryClient.invalidateQueries({
        queryKey: ["productInventoryEntries", item_id],
      });
      if (siteId) {
        queryClient.invalidateQueries({
          queryKey: ["productInventoryEntries", siteId, item_id],
        });
      }

      // 4. Surgical product update (avoid full list refetch)
      surgicalProductUpdate(queryClient, item_id);
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });

      // 5. Invalidate this site's location-inventory family (NOT_ASSIGNED included - items may
      // have been (re)assigned).
      if (siteId) {
        queryClient.invalidateQueries({ queryKey: ["locationInventory", siteId] });
        queryClient.invalidateQueries({ queryKey: ["inventoryTotals", siteId] });
      }
    },
    enabled: enabled && Boolean(siteId),
  });
}

/**
 * Subscribe to inventory changes for a specific product.
 */
export function useRealtimeProductInventory(itemId: string, enabled = true) {
  const queryClient = useQueryClient();
  const { siteId } = useCurrentSite();

  return useSupabaseRealtime<StockMovementRow>({
    table: "stock_movements",
    event: "INSERT",
    filter: `item_id=eq.${itemId}`,
    queryKeys: [], // Handled in onReceive for surgical invalidation
    onReceive: (payload: RealtimePayload<StockMovementRow>) => {
      if (!isRelevantToCurrentSite(payload.new, siteId)) {
        return;
      }
      const { location_type } = payload.new;

      // Surgical invalidation for this product
      queryClient.invalidateQueries({
        queryKey: ["productInventoryEntries", itemId],
      });
      if (siteId) {
        queryClient.invalidateQueries({
          queryKey: ["productInventoryEntries", siteId, itemId],
        });
        queryClient.invalidateQueries({
          queryKey: ["movementHistory", siteId, itemId],
        });
      }
      surgicalProductUpdate(queryClient, itemId);
      queryClient.invalidateQueries({
        queryKey: ["movementHistory", itemId],
      });
      queryClient.invalidateQueries({
        queryKey: ["locationsWithCounts", location_type],
      });
    },
    enabled: enabled && !!itemId && Boolean(siteId),
  });
}
