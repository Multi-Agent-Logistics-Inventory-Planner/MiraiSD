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
      return;
    }

    // Create a unique channel name
    const channelName = `realtime:${schema}:${table}:${event}${filter ? `:${filter}` : ""}`;

    // Create the channel and subscribe
    const channel = supabase.channel(channelName);

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
          console.log(`[Realtime] Subscribed to ${table}`);
        } else if (status === "CHANNEL_ERROR") {
          console.error(`[Realtime] Error subscribing to ${table}`);
        }
      });

    channelRef.current = channel;

    // Cleanup on unmount
    return () => {
      if (channelRef.current) {
        supabase.removeChannel(channelRef.current);
        channelRef.current = null;
      }
    };
  }, [table, schema, event, filter, queryKeys, queryClient, stableOnReceive, enabled]);

  return channelRef.current;
}
