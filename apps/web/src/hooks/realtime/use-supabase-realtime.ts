"use client";

import { useEffect, useRef, useCallback } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { getSupabaseClient } from "@/lib/supabase";
import type { RealtimeChannel } from "@supabase/supabase-js";

type PostgresChangeEvent = "INSERT" | "UPDATE" | "DELETE" | "*";

export interface RealtimePayload<T> {
  eventType: "INSERT" | "UPDATE" | "DELETE";
  new: T;
  old: Partial<T>;
  schema: string;
  table: string;
}

interface UseSupabaseRealtimeOptions<T> {
  /** Table name to subscribe to */
  table: string;
  /** Schema (defaults to 'public') */
  schema?: string;
  /** Event type to listen for */
  event?: PostgresChangeEvent;
  /** Filter string (e.g., 'item_id=eq.123') */
  filter?: string;
  /** Query keys to invalidate when changes occur */
  queryKeys: string[][];
  /** Optional callback when a change is received */
  onReceive?: (payload: RealtimePayload<T>) => void;
  /** Whether the subscription is enabled (defaults to true) */
  enabled?: boolean;
}

/**
 * Core hook for Supabase Realtime subscriptions.
 * Automatically invalidates React Query cache when database changes occur.
 */
export function useSupabaseRealtime<T>({
  table,
  schema = "public",
  event = "*",
  filter,
  queryKeys,
  onReceive,
  enabled = true,
}: UseSupabaseRealtimeOptions<T>) {
  const queryClient = useQueryClient();
  const channelRef = useRef<RealtimeChannel | null>(null);

  // Memoize the callback to avoid unnecessary re-subscriptions
  const stableOnReceive = useCallback(
    (payload: RealtimePayload<T>) => {
      onReceive?.(payload);
    },
    [onReceive]
  );

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
      // Don't hard-disable: in many dev setups (LAN IP) secureContext is false but realtime can still work.
      console.warn(
        "[Realtime] window is not a secure context (https/localhost recommended). Attempting realtime subscription anyway."
      );
    }

    // Create a unique channel name
    const channelName = `realtime:${schema}:${table}:${event}${filter ? `:${filter}` : ""}`;

    let channel: RealtimeChannel | null = null;

    try {
      // Create the channel and subscribe
      channel = supabase.channel(channelName);

      // Use type assertion to work around strict typing
      const subscribeConfig = {
        event,
        schema,
        table,
        ...(filter && { filter }),
      };

      channel
        .on(
          "postgres_changes" as "system",
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          subscribeConfig as any,
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          (payload: any) => {
            // Invalidate all specified query keys
            queryKeys.forEach((queryKey) => {
              queryClient.invalidateQueries({ queryKey });
            });

            // Call optional callback with typed payload
            stableOnReceive({
              eventType: payload.eventType,
              new: payload.new as T,
              old: payload.old as Partial<T>,
              schema: payload.schema,
              table: payload.table,
            });
          }
        )
        .subscribe((status) => {
          if (status === "SUBSCRIBED") {
            // Keep this as debug-ish signal; it's extremely helpful when users report “no realtime”
            // and we need to know if the WS connected.
            // eslint-disable-next-line no-console
            console.log(`[Realtime] Subscribed to ${channelName}`);
          } else if (status === "CHANNEL_ERROR") {
            console.warn(`[Realtime] Channel error for ${channelName}`);
          } else if (status === "TIMED_OUT") {
            console.warn(`[Realtime] Channel timed out for ${channelName}`);
          }
        });

      channelRef.current = channel;
    } catch {
      // Realtime subscription failed - continue without realtime
      channelRef.current = null;
    }

    // Cleanup on unmount
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
  }, [table, schema, event, filter, queryKeys, queryClient, stableOnReceive, enabled]);

  return channelRef.current;
}
