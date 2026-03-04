"use client";

import { MoreVertical } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { ShipmentProgress } from "./shipment-progress";
import type { Shipment } from "@/types/api";
import {
  getShipmentDisplayStatus,
  SHIPMENT_DISPLAY_STATUS_LABELS,
  SHIPMENT_DISPLAY_STATUS_COLORS,
} from "@/lib/shipment-utils";

interface ShipmentCardProps {
  shipment: Shipment;
  onClick?: () => void;
}

function formatDate(dateStr?: string) {
  if (!dateStr) return "";
  return new Date(dateStr).toLocaleDateString("en-US", {
    month: "long",
    day: "numeric",
    year: "numeric",
  });
}

function formatCurrency(value?: number) {
  if (value == null) return "-";
  return `$${value.toFixed(2)}`;
}

function getStatusText(shipment: Shipment): { text: string; date: string } {
  const displayStatus = getShipmentDisplayStatus(shipment);

  // Calculate if partial received (used as proxy for "on the way" without tracking)
  const totalReceived = shipment.items.reduce((sum, item) => sum + item.receivedQuantity, 0);
  const totalOrdered = shipment.items.reduce((sum, item) => sum + item.orderedQuantity, 0);
  const hasPartialReceived = totalReceived > 0 && totalReceived < totalOrdered;

  // Delivered
  if (displayStatus === "COMPLETED") {
    return {
      text: "Delivered",
      date: formatDate(shipment.actualDeliveryDate || shipment.expectedDeliveryDate),
    };
  }

  // On the way - either IN_TRANSIT status or partial items received
  if (shipment.status === "IN_TRANSIT" || hasPartialReceived) {
    return {
      text: "Order on the way",
      date: formatDate(shipment.expectedDeliveryDate || shipment.orderDate),
    };
  }

  // Being packaged (PENDING status, no items received yet)
  if (shipment.status === "PENDING") {
    return {
      text: "Order being packaged",
      date: formatDate(shipment.orderDate),
    };
  }

  // Default: Order placed
  return {
    text: "Order placed",
    date: formatDate(shipment.orderDate),
  };
}

function getInitials(name?: string): string {
  if (!name) return "?";
  return name.charAt(0).toUpperCase();
}

function getAvatarColor(name?: string): string {
  if (!name) return "bg-gray-500";
  const colors = [
    "bg-violet-500",
    "bg-blue-500",
    "bg-emerald-500",
    "bg-amber-500",
    "bg-rose-500",
    "bg-cyan-500",
    "bg-indigo-500",
    "bg-pink-500",
  ];
  const index = name.charCodeAt(0) % colors.length;
  return colors[index];
}

export function ShipmentCard({ shipment, onClick }: ShipmentCardProps) {
  const displayStatus = getShipmentDisplayStatus(shipment);
  const statusInfo = getStatusText(shipment);

  // Calculate totals
  const totalOrdered = shipment.items.reduce(
    (sum, item) => sum + item.orderedQuantity,
    0
  );
  const totalReceived = shipment.items.reduce(
    (sum, item) => sum + item.receivedQuantity,
    0
  );

  // Get primary product info (first item)
  const primaryItem = shipment.items[0];
  const productName = primaryItem?.item?.name || "No items";
  const itemCount = shipment.items.length;

  // Calculate total cost
  const totalCost =
    shipment.totalCost ??
    shipment.items.reduce((sum, item) => {
      if (item.unitCost) {
        return sum + item.orderedQuantity * item.unitCost;
      }
      return sum;
    }, 0);

  return (
    <div
      className={cn(
        "rounded-xl border bg-card p-4 transition-colors cursor-pointer",
        "hover:bg-muted/50"
      )}
      onClick={onClick}
    >
      {/* Header Row */}
      <div className="flex items-start justify-between gap-4 mb-3">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-3 mb-1">
            <span className="font-mono text-sm font-semibold text-primary">
              {shipment.shipmentNumber}
            </span>
            <span className="text-sm text-foreground font-medium truncate">
              {productName}
              {itemCount > 1 && (
                <span className="text-muted-foreground ml-1">
                  +{itemCount - 1} more
                </span>
              )}
              <span className="text-muted-foreground ml-1">x{totalOrdered}</span>
            </span>
          </div>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          {totalCost > 0 && (
            <span className="font-semibold text-sm">
              {formatCurrency(totalCost)}
            </span>
          )}
          {displayStatus && (
            <Badge
              variant="outline"
              className={cn(
                "text-xs font-medium",
                SHIPMENT_DISPLAY_STATUS_COLORS[displayStatus]
              )}
            >
              {SHIPMENT_DISPLAY_STATUS_LABELS[displayStatus]}
            </Badge>
          )}
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8 shrink-0"
            onClick={(e) => {
              e.stopPropagation();
              onClick?.();
            }}
          >
            <MoreVertical className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {/* Supplier Row */}
      <div className="flex items-center gap-2 mb-4 text-sm text-muted-foreground">
        {shipment.supplierName && (
          <>
            <div
              className={cn(
                "flex h-6 w-6 items-center justify-center rounded-full text-xs font-medium text-white",
                getAvatarColor(shipment.supplierName)
              )}
            >
              {getInitials(shipment.supplierName)}
            </div>
            <span>{shipment.supplierName}</span>
            <span className="text-muted-foreground/50">•</span>
          </>
        )}
        <span>{shipment.items.length} item{shipment.items.length !== 1 ? "s" : ""}</span>
      </div>

      {/* Status Section */}
      <div className="rounded-lg bg-muted/30 p-3">
        <div className="flex items-center justify-between mb-2">
          <div>
            <span className="font-medium text-sm">{statusInfo.text}</span>
            {statusInfo.date && (
              <span className="text-sm text-muted-foreground ml-2">
                {statusInfo.date}
              </span>
            )}
          </div>
          {displayStatus === "COMPLETED" ? (
            <span className="text-sm font-medium text-emerald-600 dark:text-emerald-400">
              Fulfilled
            </span>
          ) : shipment.trackingId ? (
            <button
              className="text-sm font-medium text-primary hover:underline"
              onClick={(e) => {
                e.stopPropagation();
                onClick?.();
              }}
            >
              Track shipment
            </button>
          ) : null}
        </div>

        {/* Progress Tracker */}
        <ShipmentProgress
          status={shipment.status}
          receivedQuantity={totalReceived}
          orderedQuantity={totalOrdered}
        />
      </div>
    </div>
  );
}
