import {
  CarrierStatus,
  ShipmentStatus,
  type Shipment,
  type ShipmentItem,
} from "@/types/api";

export type ShipmentDisplayStatus =
  | "ACTIVE"
  | "AWAITING_RECEIPT"
  | "PARTIAL"
  | "COMPLETED"
  | "FAILED";

/**
 * Calculates total received for a single shipment item.
 * Includes received, damaged, display, and shop quantities.
 */
export function calculateItemTotalReceived(item: ShipmentItem): number {
  return (
    (item.receivedQuantity ?? 0) +
    (item.damagedQuantity ?? 0) +
    (item.displayQuantity ?? 0) +
    (item.shopQuantity ?? 0)
  );
}

/**
 * Calculates total received quantity for all items in a shipment.
 */
export function calculateTotalReceived(items: ShipmentItem[]): number {
  return items.reduce((sum, item) => sum + calculateItemTotalReceived(item), 0);
}

export const SHIPMENT_DISPLAY_STATUS_LABELS: Record<ShipmentDisplayStatus, string> = {
  ACTIVE: "Active",
  AWAITING_RECEIPT: "Awaiting Receipt",
  PARTIAL: "Partial",
  COMPLETED: "Completed",
  FAILED: "Failed",
};

export const SHIPMENT_DISPLAY_STATUS_COLORS: Record<ShipmentDisplayStatus, string> = {
  ACTIVE: "bg-blue-100 text-blue-700",
  AWAITING_RECEIPT: "bg-amber-100 text-amber-800 border border-amber-300",
  PARTIAL: "bg-amber-100 text-amber-700",
  COMPLETED: "bg-green-100 text-green-700",
  FAILED: "bg-red-100 text-red-700",
};

/**
 * Derives the display status from a shipment's inventory + carrier state.
 * Returns null for cancelled shipments (they should be hidden).
 *
 * Mapping:
 *   CANCELLED                                                -> null (filtered out)
 *   carrier_status=FAILED                                    -> FAILED
 *   status=RECEIVED                                          -> COMPLETED
 *   status=PENDING + carrier=DELIVERED + 0 receipts          -> AWAITING_RECEIPT
 *   status=PENDING + any receipts                            -> PARTIAL
 *   status=PENDING + (no carrier or pre-transit/in-transit)  -> ACTIVE
 */
export function getShipmentDisplayStatus(shipment: Shipment): ShipmentDisplayStatus | null {
  if (shipment.status === ShipmentStatus.CANCELLED) {
    return null;
  }

  // Carrier failure surfaces as FAILED regardless of inventory state
  if (shipment.carrierStatus === CarrierStatus.FAILED) {
    return "FAILED";
  }

  if (shipment.status === ShipmentStatus.RECEIVED) {
    return "COMPLETED";
  }

  // PENDING from here on
  const hasAnyReceived = shipment.items.some(
    item => calculateItemTotalReceived(item) > 0
  );

  if (hasAnyReceived) {
    return "PARTIAL";
  }

  if (shipment.carrierStatus === CarrierStatus.DELIVERED) {
    return "AWAITING_RECEIPT";
  }

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
    AWAITING_RECEIPT: 0,
    PARTIAL: 0,
    COMPLETED: 0,
    FAILED: 0,
  };

  for (const shipment of shipments) {
    const status = getShipmentDisplayStatus(shipment);
    if (status) {
      counts[status]++;
    }
  }

  return counts;
}
