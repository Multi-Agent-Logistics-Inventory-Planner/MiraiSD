"use client";

import { useCallback, useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { flushInventorySiteRefresh } from "./inventory-refresh";

const DEFAULT_WINDOW_MS = 300;

interface PendingBuffer {
  siteId: string;
  /** null means "unknown affected IDs" - escalates the eventual flush to a full refresh. */
  ids: Set<string> | null;
}

/**
 * Coalesces rapid realtime inventory notifications into one bounded, site-scoped flush per
 * window (.specs/phase-6-inventory 6e, AC-7). Buffers by site so two sites' notifications
 * arriving close together never merge into one flush or leak into each other's cache. A
 * notification for a different site than the one currently buffered flushes the pending buffer
 * immediately before starting a new one.
 *
 * Local mutations use the same underlying executor (`flushInventorySiteRefresh`) but call it
 * directly (see `use-stock-mutations.ts`/`use-location-mutations.ts`) rather than through this
 * buffer, satisfying AC-7's "share a coalesced, bounded strategy" while keeping a user's own
 * action visibly immediate - only realtime notifications wait out the window.
 */
export function useCoalescedInventoryRefresh(windowMs = DEFAULT_WINDOW_MS) {
  const queryClient = useQueryClient();
  const bufferRef = useRef<PendingBuffer | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const flushNow = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    const buffer = bufferRef.current;
    bufferRef.current = null;
    if (!buffer) {
      return;
    }
    void flushInventorySiteRefresh(
      queryClient,
      buffer.siteId,
      buffer.ids ? Array.from(buffer.ids) : undefined
    );
  }, [queryClient]);

  const notify = useCallback(
    (siteId: string, productIds: string[] | undefined) => {
      const existing = bufferRef.current;
      if (existing && existing.siteId !== siteId) {
        flushNow();
      }

      const current = bufferRef.current;
      const isUnknown = !productIds || productIds.length === 0;
      const nextIds: Set<string> | null =
        isUnknown || (current && current.ids === null)
          ? null
          : new Set([...(current?.ids ?? []), ...productIds]);
      bufferRef.current = { siteId, ids: nextIds };

      if (!timerRef.current) {
        timerRef.current = setTimeout(flushNow, windowMs);
      }
    },
    [flushNow, windowMs]
  );

  useEffect(() => {
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
    };
  }, []);

  return { notify, flushNow };
}
