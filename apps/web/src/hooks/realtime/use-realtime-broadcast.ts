"use client";

import { useCallback, useEffect, useRef } from "react";
import { isCancelledError, useQueryClient } from "@tanstack/react-query";
import { getSupabaseClient } from "@/lib/supabase";
import { getProductById, type GetProductsOptions } from "@/lib/api/products";
import type { RealtimeChannel } from "@supabase/supabase-js";
import { KujiType, type Product } from "@/types/api";
import { useCurrentSite } from "@/hooks/queries/use-current-site";
import { useCoalescedInventoryRefresh } from "./use-coalesced-inventory-refresh";
import { flushInventorySiteRefresh } from "./inventory-refresh";
import { isRelevantToCurrentSite } from "./site-relevance";

/**
 * Event types that can be broadcast from the backend
 */
export type BroadcastEventType =
  | "inventory_updated"
  | "product_updated"
  | "shipment_updated"
  | "notification_created"
  | "audit_log_created";

interface BroadcastPayload {
  type: BroadcastEventType;
  /** Optional: specific entity IDs that were affected (product_updated/shipment_updated) */
  ids?: string[];
  /** Optional: site that owns this event - absent means "possibly relevant to any site" */
  siteId?: string;
  /** Optional: affected product IDs for inventory_updated (6e, T-6e-be-4/5) */
  productIds?: string[];
  /** Optional: location type for inventory updates */
  locationType?: string;
  /** Optional: item ID for product-specific updates */
  itemId?: string;
}

/**
 * Query key mappings for non-inventory event types. inventory_updated is handled separately
 * (site-qualified + coalesced, see below) rather than through this bare-prefix table - the
 * dead keys `notAssignedInventory` (retired by T-6d-9) and `dashboard` (no such query) were
 * removed here in 6e, T-6e-3/T-6e-8.
 */
const EVENT_QUERY_KEYS: Record<Exclude<BroadcastEventType, "inventory_updated">, string[][]> = {
  product_updated: [
    ["products"],
    ["dashboard"],
  ],
  shipment_updated: [
    ["shipments"],
    ["activity-feed"],
    ["dashboard"],
  ],
  notification_created: [
    ["notifications"],
    ["notifications", "counts"],
    ["activity-feed"],
  ],
  audit_log_created: [
    ["audit-log"],
    ["audit-logs"],
    ["audit-log-detail"],
    ["activity-feed"],
    ["movementHistory"],
  ],
};

/**
 * Hook that subscribes to Supabase broadcast channel for real-time updates.
 * The backend sends broadcast messages when data changes, and this hook
 * invalidates the relevant React Query caches.
 *
 * This approach works even when the backend makes direct database changes
 * (not through Supabase client), as long as the backend sends broadcast messages.
 */
export function useRealtimeBroadcast(enabled = true) {
  const queryClient = useQueryClient();
  const channelRef = useRef<RealtimeChannel | null>(null);
  const { siteId } = useCurrentSite();
  const siteIdRef = useRef(siteId);
  useEffect(() => {
    siteIdRef.current = siteId;
  }, [siteId]);
  const { notify } = useCoalescedInventoryRefresh();
  // Tracks whether the channel has previously errored/timed out, so a full recovery refresh
  // fires only on a SUBSCRIBED that follows a real interruption - never on the first, normal
  // mount subscribe (6e, T-6e-6).
  const hasErroredRef = useRef(false);

  useEffect(() => {
    const supabase = getSupabaseClient();

    if (!supabase || !enabled) {
      if (enabled && !supabase && typeof window !== "undefined") {
        console.warn(
          "[Realtime] Supabase client not available. Check NEXT_PUBLIC_SUPABASE_URL and NEXT_PUBLIC_SUPABASE_ANON_KEY."
        );
      }
      return;
    }

    // Check if we're in a secure context (HTTPS or localhost)
    if (typeof window !== "undefined" && !window.isSecureContext) {
      console.warn(
        "[Realtime] Disabled: window is not a secure context (use https or localhost)."
      );
      // Don't hard-disable: in many dev setups (LAN IP) secureContext is false but realtime can still work.
    }

    const channelName = "db-changes";

    try {
      const channel = supabase
        .channel(channelName)
        .on("broadcast", { event: "db_change" }, (payload) => {
          const data = payload.payload as BroadcastPayload;

          if (!data?.type) {
            return;
          }

          if (data.type === "inventory_updated") {
            const currentSiteId = siteIdRef.current;
            if (!currentSiteId || !isRelevantToCurrentSite(data.siteId, currentSiteId)) {
              return;
            }
            notify(currentSiteId, data.productIds);
            // Non-totals inventory reads still use a direct, site-qualified invalidation -
            // they're not part of the totals-merge lever, so coalescing them buys nothing.
            // Both families are invalidated regardless of known/unknown IDs (follow-up review
            // finding, P1): locationInventory is read by location sheets/stock dialogs keyed
            // by location, not product, so a known-ID event still needs it refreshed; an
            // unknown-ID batch still needs productInventoryEntries refreshed too, since "unknown
            // IDs" means we can't target specific products, not that no product view is stale.
            queryClient.invalidateQueries({ queryKey: ["locationInventory", currentSiteId] });
            if (data.productIds && data.productIds.length > 0) {
              data.productIds.forEach((id) => {
                queryClient.invalidateQueries({
                  queryKey: ["productInventoryEntries", currentSiteId, id],
                });
                // Legacy, unscoped two-element key (6e independent review, Required 4) - see
                // inventory-refresh.ts's identical comment; the Kuji dialogs still read this.
                queryClient.invalidateQueries({ queryKey: ["productInventoryEntries", id] });
              });
            } else {
              queryClient.invalidateQueries({ queryKey: ["productInventoryEntries"] });
            }
            // Unconditionally invalidate the whole locationsWithCounts prefix for this site
            // (6e independent review, R-2/Required-3): data.locationType is the backend's
            // storage_locations.code vocabulary ("RACKS", "BOX_BINS"), not the frontend
            // LocationType enum this cache key uses, so a type-qualified invalidation could
            // never match; Kuji/Shipment producers also send no locationType at all, so even
            // the "ALL" branch never fired for those. A prefix invalidation matches every
            // locationsWithCounts entry for this site regardless of shape, same pattern
            // use-location-mutations.ts already uses.
            queryClient.invalidateQueries({ queryKey: ["locationsWithCounts", currentSiteId] });
            return;
          }

          // Get the query keys to invalidate for this event type
          const queryKeys = EVENT_QUERY_KEYS[data.type];

          if (!queryKeys) {
            return;
          }

          // Invalidate all matching query keys
          queryKeys.forEach((queryKey) => {
            if (queryKey[0] === "products") {
              // Surgical product update when itemId or single id available
              const itemId = data.itemId ?? (data.ids?.length === 1 ? data.ids[0] : null);
              if (itemId) {
                // Child views have separate payloads and still require their own refresh.
                queryClient.invalidateQueries({
                  queryKey: ["products", itemId, "with-children"],
                });
                queryClient.invalidateQueries({
                  queryKey: ["products", itemId, "children"],
                });
                // Fetch single product and update all list caches (avoid full refetch).
                // Each list query carries its own filter options in queryKey[1] (e.g.
                // {rootOnly:true}). Inserting a fresh product into every list ignores
                // those filters — e.g. a kuji prize child would briefly appear on the
                // root-only Products page until the next refetch removed it. So iterate
                // the cache and respect each query's filter when deciding INSERT/keep.
                // A pre-event read may contain an old snapshot. Supersede it before
                // sharing one authoritative refresh between detail and list consumers.
                queryClient.cancelQueries({ queryKey: ["products", itemId], exact: true })
                  .then(async () => {
                    await queryClient.invalidateQueries({
                      queryKey: ["products", itemId], exact: true, refetchType: "none",
                    });
                    return queryClient.fetchQuery({
                      queryKey: ["products", itemId],
                      queryFn: () => getProductById(itemId),
                      retry: false,
                    });
                  })
                  .then((updatedProduct: Product) => {
                    const matchesFilter = (opts: GetProductsOptions): boolean => {
                      if (opts.rootOnly && updatedProduct.parentId != null) return false;
                      if (opts.excludeCustomKuji && updatedProduct.kujiType === KujiType.CUSTOM) return false;
                      // kujiOnly requires hasChildren which we cannot infer reliably
                      // for a freshly inserted product; let invalidation handle it.
                      if (opts.kujiOnly) return false;
                      return true;
                    };
                    const queries = queryClient
                      .getQueryCache()
                      .findAll({ queryKey: ["products"] });
                    for (const q of queries) {
                      // Target only ["products", opts] list caches — skip single-product
                      // caches (["products", id]) and child summaries (["products", id, "children"]).
                      if (q.queryKey.length !== 2) continue;
                      const second = q.queryKey[1];
                      if (second === null || typeof second !== "object") continue;
                      const list = q.state.data;
                      if (!Array.isArray(list)) continue;
                      const opts = second as GetProductsOptions;
                      const products = list as Product[];
                      const index = products.findIndex((p) => p.id === itemId);
                      if (index === -1) {
                        if (matchesFilter(opts)) {
                          queryClient.setQueryData<Product[]>(q.queryKey, [
                            ...products,
                            updatedProduct,
                          ]);
                        }
                      } else {
                        queryClient.setQueryData<Product[]>(q.queryKey, [
                          ...products.slice(0, index),
                          updatedProduct,
                          ...products.slice(index + 1),
                        ]);
                      }
                    }
                  })
                  .catch((error: unknown) => {
                    // A newer broadcast owns the replacement read; don't restart its work.
                    if (isCancelledError(error)) return;
                    // Fallback for DELETE (404) or network error
                    queryClient.invalidateQueries({ queryKey: ["products"] });
                  });
              } else {
                // Batch operation - no specific itemId, invalidate all
                queryClient.invalidateQueries({ queryKey });
              }
            } else {
              queryClient.invalidateQueries({ queryKey });
            }
          });
        })
        .subscribe((status) => {
          if (status === "SUBSCRIBED") {
            console.log("[Realtime] Connected to broadcast channel");
            if (hasErroredRef.current) {
              // Missed-event recovery (6e, T-6e-6): a reconnect following a real
              // interruption might have missed notifications, so do one full,
              // authoritative refresh of the current site rather than trusting whatever
              // was buffered before the drop. Broadened (6e independent review, Required 5)
              // to cover every inventory-shaped cache this handler ever writes to, not just
              // totals - a missed-event window can affect location-level reads and
              // locations-with-counts too, and AC-7 asks for "full selected-site recovery",
              // not "full totals-only recovery".
              hasErroredRef.current = false;
              const currentSiteId = siteIdRef.current;
              if (currentSiteId) {
                flushInventorySiteRefresh(queryClient, currentSiteId, undefined).catch(() => {
                  // Best-effort recovery; nothing else to fall back to here.
                });
                queryClient.invalidateQueries({ queryKey: ["locationInventory", currentSiteId] });
                queryClient.invalidateQueries({ queryKey: ["locationsWithCounts", currentSiteId] });
                queryClient.invalidateQueries({ queryKey: ["productInventoryEntries"] });
              }
            }
          } else if (status === "CHANNEL_ERROR") {
            console.warn("[Realtime] Broadcast channel error");
            hasErroredRef.current = true;
          } else if (status === "TIMED_OUT") {
            console.warn("[Realtime] Broadcast channel timed out");
            hasErroredRef.current = true;
          }
        });

      channelRef.current = channel;
    } catch (error) {
      console.error("[Realtime] Failed to subscribe to broadcast channel:", error);
      channelRef.current = null;
    }

    return () => {
      // Deliberately does NOT flush a pending coalesced buffer on unmount (6e independent
      // review, Advisory 7) - matches useCoalescedInventoryRefresh's own documented/tested
      // behavior (cancels without flushing); there is no mounted component left to observe the
      // result, and the buffer's own timer cleanup (inside that hook) already cancels it.
      if (channelRef.current) {
        try {
          supabase.removeChannel(channelRef.current);
        } catch {
          // Ignore cleanup errors
        }
        channelRef.current = null;
      }
    };
  }, [queryClient, enabled, notify]);

  // Expose the channel via a stable accessor instead of reading channelRef.current
  // during render: a ref's live value can change without triggering a re-render, so
  // returning it directly could hand callers a stale (or since-torn-down) channel.
  // A getter callback is safe to call from event handlers/effects, where refs are
  // meant to be read.
  return useCallback(() => channelRef.current, []);
}
