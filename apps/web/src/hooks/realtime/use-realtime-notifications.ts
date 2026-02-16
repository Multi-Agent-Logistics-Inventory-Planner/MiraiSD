"use client";

import { useCallback } from "react";
import { useSupabaseRealtime, type RealtimePayload } from "./use-supabase-realtime";
import { toast } from "@/hooks/use-toast";

interface NotificationRow {
  id: string;
  type: string;
  severity: string;
  message: string;
  item_id: string | null;
  created_at: string;
  read_at: string | null;
  metadata: Record<string, unknown> | null;
}

/**
 * Subscribe to real-time notification updates.
 * Shows a toast when new notifications arrive.
 */
export function useRealtimeNotifications(enabled = true) {
  const handleReceive = useCallback(
    (payload: RealtimePayload<NotificationRow>) => {
      if (payload.eventType === "INSERT") {
        const notification = payload.new;

        // Show toast for new notifications
        const variant =
          notification.severity === "CRITICAL"
            ? "destructive"
            : notification.severity === "WARNING"
              ? "default"
              : "default";

        toast({
          title: getNotificationTitle(notification.type),
          description: notification.message,
          variant,
        });
      }
    },
    []
  );

  return useSupabaseRealtime<NotificationRow>({
    table: "notifications",
    event: "*",
    queryKeys: [["notifications"], ["notifications", "counts"]],
    onReceive: handleReceive,
    enabled,
  });
}

function getNotificationTitle(type: string): string {
  switch (type) {
    case "LOW_STOCK":
      return "Low Stock Alert";
    case "OUT_OF_STOCK":
      return "Out of Stock";
    case "REORDER_SUGGESTION":
      return "Reorder Suggestion";
    case "UNASSIGNED_ITEM":
      return "Unassigned Item";
    case "EXPIRY_WARNING":
      return "Expiry Warning";
    case "SYSTEM_ALERT":
      return "System Alert";
    default:
      return "Notification";
  }
}
