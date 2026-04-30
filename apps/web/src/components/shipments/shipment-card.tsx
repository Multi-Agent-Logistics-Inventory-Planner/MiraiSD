"use client";

import { MoreVertical } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { ProductThumbnail } from "@/components/products/product-thumbnail";
import { ShipmentProgress } from "./shipment-progress";
import { usePermissions } from "@/hooks/use-permissions";
import { CarrierStatus, type Shipment } from "@/types/api";
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

function formatCarrierDeliveredAt(iso?: string | null) {
  if (!iso) return "";
  return new Date(iso).toLocaleDateString("en-US", {
    month: "long",
    day: "numeric",
    year: "numeric",
  });
}

function getStatusText(shipment: Shipment): { text: string; date: string } {
  const displayStatus = getShipmentDisplayStatus(shipment);

  if (displayStatus === "COMPLETED") {
    return {
      text: "Received",
      date: formatDate(
        shipment.actualDeliveryDate || shipment.expectedDeliveryDate,
      ),
    };
  }

  if (displayStatus === "AWAITING_RECEIPT") {
    return {
      text: "Carrier delivered - awaiting receipt",
      date: formatCarrierDeliveredAt(shipment.carrierDeliveredAt),
    };
  }

  if (displayStatus === "FAILED") {
    return {
      text: "Carrier delivery failed",
      date: formatDate(shipment.expectedDeliveryDate || shipment.orderDate),
    };
  }

  // PARTIAL or carrier IN_TRANSIT - both surface as "on the way"
  if (
    displayStatus === "PARTIAL" ||
    shipment.carrierStatus === CarrierStatus.IN_TRANSIT
  ) {
    return {
      text: "Order on the way",
      date: formatDate(shipment.expectedDeliveryDate || shipment.orderDate),
    };
  }

  // ACTIVE (PENDING with no carrier signal yet)
  return {
    text: "Order being packaged",
    date: formatDate(shipment.orderDate),
  };
}

export function ShipmentCard({ shipment, onClick }: ShipmentCardProps) {
  const { canViewCosts } = usePermissions();
  const displayStatus = getShipmentDisplayStatus(shipment);
  const statusInfo = getStatusText(shipment);

  // Calculate totals (units = sum of all quantities)
  const totalOrdered = shipment.items.reduce(
    (sum, item) => sum + item.orderedQuantity,
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
          {canViewCosts && totalCost > 0 && (
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
          <ProductThumbnail
            key={item.id}
            imageUrl={item.item?.imageUrl}
            alt={item.item?.name || "Product"}
            size="lg"
            fallbackVariant="package"
            badge={item.orderedQuantity}
            badgeStyle="quantity"
          />
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
          carrierStatus={shipment.carrierStatus}
        />
      </div>
    </div>
  );
}
