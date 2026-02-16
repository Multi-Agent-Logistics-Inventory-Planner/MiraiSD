"use client";

import { useCallback } from "react";
import { useSupabaseRealtime, type RealtimePayload } from "./use-supabase-realtime";
import { toast } from "@/hooks/use-toast";

interface ShipmentRow {
  id: string;
  shipment_number: string;
  status: string;
  supplier: string | null;
  expected_date: string | null;
  received_date: string | null;
}

/**
 * Subscribe to real-time shipment updates.
 * Shows a toast when shipment status changes.
 */
export function useRealtimeShipments(enabled = true) {
  const handleReceive = useCallback(
    (payload: RealtimePayload<ShipmentRow>) => {
      if (payload.eventType === "UPDATE") {
        const oldStatus = payload.old?.status;
        const newStatus = payload.new.status;

        // Notify on status changes
        if (oldStatus && oldStatus !== newStatus) {
          toast({
            title: "Shipment Updated",
            description: `Shipment ${payload.new.shipment_number} is now ${formatStatus(newStatus)}`,
          });
        }
      } else if (payload.eventType === "INSERT") {
        toast({
          title: "New Shipment",
          description: `Shipment ${payload.new.shipment_number} has been created`,
        });
      }
    },
    []
  );

  return useSupabaseRealtime<ShipmentRow>({
    table: "shipments",
    event: "*",
    queryKeys: [["shipments"], ["dashboard"]],
    onReceive: handleReceive,
    enabled,
  });
}

function formatStatus(status: string): string {
  return status
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}
