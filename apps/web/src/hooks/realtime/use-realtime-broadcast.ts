"use client";

import { useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { getSupabaseClient } from "@/lib/supabase";
import { getProductById } from "@/lib/api/products";
import type { RealtimeChannel } from "@supabase/supabase-js";
import type { Product } from "@/types/api";

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
  /** Optional: specific entity IDs that were affected */
  ids?: string[];
  /** Optional: location type for inventory updates */
  locationType?: string;
  /** Optional: item ID for product-specific updates */
  itemId?: string;
}

/**
 * Query key mappings for each event type.
 * When an event is received, all matching query keys will be invalidated.
 */
const EVENT_QUERY_KEYS: Record<BroadcastEventType, string[][]> = {
  inventory_updated: [
    ["locationsWithCounts"],
    ["locationInventory"],
    ["notAssignedInventory"],
    ["productInventoryEntries"],
    ["products"],
    ["dashboard"],
  ],
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

          // Get the query keys to invalidate for this event type
          const queryKeys = EVENT_QUERY_KEYS[data.type];

          if (!queryKeys) {
            return;
          }

          // Invalidate all matching query keys
          queryKeys.forEach((queryKey) => {
            // If we have specific IDs, use them for more targeted invalidation
            if (data.itemId && queryKey[0] === "productInventoryEntries") {
              queryClient.invalidateQueries({
                queryKey: ["productInventoryEntries", data.itemId],
              });
            } else if (data.locationType && queryKey[0] === "locationsWithCounts") {
              // Invalidate specific location type
              queryClient.invalidateQueries({
                queryKey: ["locationsWithCounts", data.locationType],
              });
              // Also invalidate the general query
              queryClient.invalidateQueries({
                queryKey: ["locationsWithCounts"],
                exact: true,
              });
            } else if (queryKey[0] === "products") {
              // Surgical product update when itemId or single id available
              const itemId = data.itemId ?? (data.ids?.length === 1 ? data.ids[0] : null);
              if (itemId) {
                // Invalidate specific product queries (single product, not lists)
                queryClient.invalidateQueries({
                  queryKey: ["products", itemId],
                });
                queryClient.invalidateQueries({
                  queryKey: ["products", itemId, "with-children"],
                });
                queryClient.invalidateQueries({
                  queryKey: ["products", itemId, "children"],
                });
                // Fetch single product and update all list caches (avoid full refetch)
                getProductById(itemId)
                  .then((updatedProduct: Product) => {
                    // Update all products list queries that contain this product
                    queryClient.setQueriesData<Product[]>(
                      { queryKey: ["products"] },
                      (oldData) => {
                        if (!oldData || !Array.isArray(oldData)) return oldData;
                        const index = oldData.findIndex((p) => p.id === itemId);
                        if (index === -1) return oldData;
                        // Return new array with updated product
                        return [
                          ...oldData.slice(0, index),
                          updatedProduct,
                          ...oldData.slice(index + 1),
                        ];
                      }
                    );
                  })
                  .catch(() => {
                    // Fallback: if single fetch fails, invalidate all
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
          } else if (status === "CHANNEL_ERROR") {
            console.warn("[Realtime] Broadcast channel error");
          } else if (status === "TIMED_OUT") {
            console.warn("[Realtime] Broadcast channel timed out");
          }
        });

      channelRef.current = channel;
    } catch (error) {
      console.error("[Realtime] Failed to subscribe to broadcast channel:", error);
      channelRef.current = null;
    }

    return () => {
      if (channelRef.current) {
        try {
          supabase.removeChannel(channelRef.current);
        } catch {
          // Ignore cleanup errors
        }
        channelRef.current = null;
      }
    };
  }, [queryClient, enabled]);

  return channelRef.current;
}
