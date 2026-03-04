"use client";

import { useMemo, useCallback, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { getAuditLog } from "@/lib/api/stock-movements";
import { getShipments } from "@/lib/api/shipments";
import { getNotifications } from "@/lib/api/notifications";
import { StockMovementReason } from "@/types/api";
import type { ActivityFeedEvent, ActivityEventType } from "@/types/dashboard";

const INITIAL_LIMIT = 50;
const LOAD_MORE_INCREMENT = 20;

function mapReasonToEventType(reason: StockMovementReason): ActivityEventType {
  switch (reason) {
    case StockMovementReason.SALE:
      return "sale";
    case StockMovementReason.RESTOCK:
    case StockMovementReason.INITIAL_STOCK:
    case StockMovementReason.RETURN:
      return "restock";
    case StockMovementReason.TRANSFER:
      return "transfer";
    case StockMovementReason.ADJUSTMENT:
    case StockMovementReason.DAMAGE:
    default:
      return "adjustment";
  }
}

function formatMovementTitle(
  reason: StockMovementReason,
  itemName: string,
  quantityChange: number
): string {
  const absQty = Math.abs(quantityChange);
  const qtyStr = absQty === 1 ? "1 unit" : `${absQty} units`;

  switch (reason) {
    case StockMovementReason.SALE:
      return `Sold ${qtyStr} of ${itemName}`;
    case StockMovementReason.RESTOCK:
      return `Restocked ${qtyStr} of ${itemName}`;
    case StockMovementReason.INITIAL_STOCK:
      return `Added initial stock: ${qtyStr} of ${itemName}`;
    case StockMovementReason.RETURN:
      return `Returned ${qtyStr} of ${itemName}`;
    case StockMovementReason.TRANSFER:
      return `Transferred ${qtyStr} of ${itemName}`;
    case StockMovementReason.ADJUSTMENT:
      return quantityChange >= 0
        ? `Adjusted +${qtyStr} of ${itemName}`
        : `Adjusted -${qtyStr} of ${itemName}`;
    case StockMovementReason.DAMAGE:
      return `Damaged ${qtyStr} of ${itemName}`;
    default:
      return `Stock change: ${qtyStr} of ${itemName}`;
  }
}

export interface ActivityFeedFilters {
  types: ActivityEventType[];
  showResolved: boolean;
}

const DEFAULT_FILTERS: ActivityFeedFilters = {
  types: ["alert", "restock", "sale", "shipment", "adjustment", "transfer"],
  showResolved: false,
};

export function useActivityFeed(filters: ActivityFeedFilters = DEFAULT_FILTERS) {
  const [limit, setLimit] = useState(INITIAL_LIMIT);

  // Fetch recent audit log entries (last 7 days for performance)
  const auditLogQuery = useQuery({
    queryKey: ["activity-feed", "audit-log"],
    queryFn: async () => {
      const now = new Date();
      const sevenDaysAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
      const fromDate = sevenDaysAgo.toISOString().split("T")[0];
      const toDate = now.toISOString().split("T")[0];
      const response = await getAuditLog({ fromDate, toDate }, 0, 100);
      return response.content;
    },
    staleTime: 60 * 1000,
  });

  // Fetch recent shipments
  const shipmentsQuery = useQuery({
    queryKey: ["activity-feed", "shipments"],
    queryFn: () => getShipments(),
    staleTime: 60 * 1000,
  });

  // Fetch notifications
  const notificationsQuery = useQuery({
    queryKey: ["activity-feed", "notifications"],
    queryFn: () => getNotifications(),
    staleTime: 30 * 1000,
  });

  const isLoading =
    auditLogQuery.isLoading ||
    shipmentsQuery.isLoading ||
    notificationsQuery.isLoading;

  const error =
    auditLogQuery.error ?? shipmentsQuery.error ?? notificationsQuery.error;

  const events: ActivityFeedEvent[] = useMemo(() => {
    const allEvents: ActivityFeedEvent[] = [];

    // Convert audit log entries to events
    const auditLogs = auditLogQuery.data ?? [];
    for (const entry of auditLogs) {
      const eventType = mapReasonToEventType(entry.reason);

      // Skip if type is not in filter
      if (!filters.types.includes(eventType)) continue;

      allEvents.push({
        id: `audit-${entry.id}`,
        type: eventType,
        title: formatMovementTitle(entry.reason, entry.itemName, entry.quantityChange),
        description: entry.actorName ? `by ${entry.actorName}` : null,
        timestamp: entry.at,
        severity: null,
        metadata: {
          itemId: entry.itemId,
          itemName: entry.itemName,
          itemSku: entry.itemSku ?? undefined,
          quantity: entry.quantityChange,
        },
      });
    }

    // Convert shipments to events
    if (filters.types.includes("shipment")) {
      const shipments = shipmentsQuery.data ?? [];
      for (const shipment of shipments) {
        const itemCount = shipment.items.length;
        const totalQty = shipment.items.reduce((sum, i) => sum + i.orderedQuantity, 0);

        allEvents.push({
          id: `shipment-${shipment.id}`,
          type: "shipment",
          title: `Shipment ${shipment.shipmentNumber}: ${itemCount} items (${totalQty} units)`,
          description: shipment.supplierName ? `from ${shipment.supplierName}` : null,
          timestamp: shipment.updatedAt,
          severity: null,
          metadata: {
            shipmentId: shipment.id,
            shipmentNumber: shipment.shipmentNumber,
          },
        });
      }
    }

    // Convert notifications to events
    if (filters.types.includes("alert")) {
      const notifications = notificationsQuery.data ?? [];
      for (const notification of notifications) {
        const isResolved = !!notification.resolvedAt;

        // Skip resolved if not showing resolved
        if (isResolved && !filters.showResolved) continue;

        allEvents.push({
          id: `notification-${notification.id}`,
          type: "alert",
          title: notification.message,
          description: isResolved ? "Resolved" : null,
          timestamp: notification.createdAt,
          severity: notification.severity,
          metadata: {
            notificationId: notification.id,
            itemId: notification.itemId ?? undefined,
            resolved: isResolved,
          },
        });
      }
    }

    // Sort by timestamp descending (most recent first)
    allEvents.sort((a, b) => {
      const dateA = new Date(a.timestamp).getTime();
      const dateB = new Date(b.timestamp).getTime();
      return dateB - dateA;
    });

    return allEvents;
  }, [
    auditLogQuery.data,
    shipmentsQuery.data,
    notificationsQuery.data,
    filters.types,
    filters.showResolved,
  ]);

  const visibleEvents = useMemo(() => {
    return events.slice(0, limit);
  }, [events, limit]);

  const hasMore = events.length > limit;

  const loadMore = useCallback(() => {
    setLimit((prev) => prev + LOAD_MORE_INCREMENT);
  }, []);

  const resetLimit = useCallback(() => {
    setLimit(INITIAL_LIMIT);
  }, []);

  return {
    events: visibleEvents,
    totalCount: events.length,
    isLoading,
    error: error as Error | null,
    hasMore,
    loadMore,
    resetLimit,
  };
}
