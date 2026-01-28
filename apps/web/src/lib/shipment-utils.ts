import { ShipmentStatus, type Shipment } from "@/types/api";

export type ShipmentDisplayStatus = "ACTIVE" | "PARTIAL" | "COMPLETED";

export const SHIPMENT_DISPLAY_STATUS_LABELS: Record<ShipmentDisplayStatus, string> = {
  ACTIVE: "Active",
  PARTIAL: "Partial",
  COMPLETED: "Completed",
};

export const SHIPMENT_DISPLAY_STATUS_COLORS: Record<ShipmentDisplayStatus, string> = {
  ACTIVE: "bg-blue-100 text-blue-700",
  PARTIAL: "bg-amber-100 text-amber-700",
  COMPLETED: "bg-green-100 text-green-700",
};

/**
 * Derives the display status from a shipment's backend status and item receipts.
 * Returns null for cancelled shipments (they should be hidden).
 */
export function getShipmentDisplayStatus(shipment: Shipment): ShipmentDisplayStatus | null {
  // Exclude cancelled shipments
  if (shipment.status === ShipmentStatus.CANCELLED) {
    return null;
  }

  // COMPLETED = backend status is DELIVERED
  if (shipment.status === ShipmentStatus.DELIVERED) {
    return "COMPLETED";
  }

  // For PENDING and IN_TRANSIT, check received quantities
  const hasAnyReceived = shipment.items.some(item => item.receivedQuantity > 0);

  if (hasAnyReceived) {
    return "PARTIAL";
  }

  // No items received yet
  return "ACTIVE";
}

/**
 * Filters shipments by display status.
 * Excludes cancelled shipments from all results.
 */
export function filterShipmentsByDisplayStatus(
  shipments: Shipment[],
  status: ShipmentDisplayStatus | "all"
): Shipment[] {
  // First exclude cancelled shipments
  const nonCancelled = shipments.filter(s => s.status !== ShipmentStatus.CANCELLED);

  if (status === "all") {
    return nonCancelled;
  }

  return nonCancelled.filter(s => getShipmentDisplayStatus(s) === status);
}

/**
 * Gets counts for each display status.
 */
export function getShipmentDisplayStatusCounts(
  shipments: Shipment[]
): Record<ShipmentDisplayStatus, number> {
  const counts: Record<ShipmentDisplayStatus, number> = {
    ACTIVE: 0,
    PARTIAL: 0,
    COMPLETED: 0,
  };

  for (const shipment of shipments) {
    const status = getShipmentDisplayStatus(shipment);
    if (status) {
      counts[status]++;
    }
  }

  return counts;
}
