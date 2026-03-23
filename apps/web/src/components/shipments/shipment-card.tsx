"use client";

import Image from "next/image";
import { MoreVertical, Package } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { ShipmentProgress } from "./shipment-progress";
import type { Shipment, ShipmentItem } from "@/types/api";
import { getShipmentDisplayStatus } from "@/lib/shipment-utils";

interface ShipmentCardProps {
  shipment: Shipment;
  onClick?: () => void;
}

function formatDate(dateStr?: string) {
  if (!dateStr) return "";
  return new Date(dateStr + "T00:00:00").toLocaleDateString("en-US", {
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
  const totalReceived = shipment.items.reduce(
    (sum, item) => sum + item.receivedQuantity,
    0,
  );
  const totalOrdered = shipment.items.reduce(
    (sum, item) => sum + item.orderedQuantity,
    0,
  );
  const hasPartialReceived = totalReceived > 0 && totalReceived < totalOrdered;

  // Delivered
  if (displayStatus === "COMPLETED") {
    return {
      text: "Delivered",
      date: formatDate(
        shipment.actualDeliveryDate || shipment.expectedDeliveryDate,
      ),
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

function ProductThumbnail({ item }: { item: ShipmentItem }) {
  const imageUrl = item.item?.imageUrl;
  const quantity = item.orderedQuantity;

  return (
    <div className="relative">
      <div className="relative h-12 w-12 rounded-lg bg-muted overflow-hidden flex items-center justify-center">
        {imageUrl ? (
          <Image
            src={imageUrl}
            alt={item.item?.name || "Product"}
            fill
            sizes="48px"
            className="object-cover"
          />
        ) : (
          <Package className="h-5 w-5 text-muted-foreground" />
        )}
      </div>
      <span className="absolute -bottom-1 -right-1 flex h-5 w-5 items-center justify-center rounded-full bg-background border border-border text-[10px] font-medium text-muted-foreground">
        {quantity}
      </span>
    </div>
  );
}

export function ShipmentCard({ shipment, onClick }: ShipmentCardProps) {
  const displayStatus = getShipmentDisplayStatus(shipment);
  const statusInfo = getStatusText(shipment);

  // Calculate totals (units = sum of all quantities)
  const totalOrdered = shipment.items.reduce(
    (sum, item) => sum + item.orderedQuantity,
    0,
  );
  const totalReceived = shipment.items.reduce(
    (sum, item) => sum + item.receivedQuantity,
    0,
  );

  // Show only parent/root items for count and thumbnails (Kuji = 1 block, not parent + prizes)
  const rootItems = shipment.items.filter((item) => !item.item.parentId);
  const itemCount = rootItems.length;

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
        "rounded-xl border dark:border-none bg-card p-4 transition-colors cursor-pointer",
        "hover:bg-muted/50 dark:hover:bg-card/80",
      )}
      onClick={onClick}
    >
      {/* Header Row */}
      <div className="flex items-start justify-between gap-4 mb-2">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            {shipment.supplierName && (
              <span className="font-semibold text-sm">
                {shipment.supplierName}
              </span>
            )}
            <span className="font-mono text-sm text-muted-foreground">
              {shipment.shipmentNumber}
            </span>
          </div>
          <div className="text-sm text-muted-foreground mt-0.5">
            {itemCount} item{itemCount !== 1 ? "s" : ""} ({totalOrdered} units)
          </div>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          {totalCost > 0 && (
            <span className="font-semibold text-sm">
              {formatCurrency(totalCost)}
            </span>
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

      {/* Product Images Row (only parent/root items; prizes are under parent Kuji) */}
      <div className="flex items-start gap-3 mb-4 overflow-x-auto scrollbar-none pb-1">
        {rootItems.map((item) => (
          <ProductThumbnail key={item.id} item={item} />
        ))}
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
            <span className="text-sm font-medium text-emerald-700">
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
