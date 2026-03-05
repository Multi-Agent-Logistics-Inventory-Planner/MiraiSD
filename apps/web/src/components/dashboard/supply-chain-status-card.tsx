"use client";

import Link from "next/link";
import { differenceInDays, format } from "date-fns";
import { Package, Truck, ArrowRight, Plus } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  ProductImageCarousel,
  type ProductImageItem,
} from "./product-image-carousel";
import type { Shipment } from "@/types/api";
import { SHIPMENT_STATUS_LABELS, SHIPMENT_STATUS_VARIANTS } from "@/types/api";

interface SupplyChainStatusCardProps {
  nextShipment: Shipment | null;
  additionalShipmentCount?: number;
  isLoading?: boolean;
}

function formatDeliveryCountdown(date: Date): string {
  const now = new Date();
  const diffDays = differenceInDays(date, now);

  if (diffDays < 0) return "overdue";
  if (diffDays === 0) return "today";
  if (diffDays === 1) return "tomorrow";
  if (diffDays < 7) return `in ${diffDays} days`;
  return format(date, "MMM d");
}

function LoadingSkeleton() {
  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <Skeleton className="h-5 w-36" />
          <Skeleton className="h-8 w-24" />
        </div>
      </CardHeader>
      <CardContent>
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <Skeleton className="h-4 w-32" />
            <Skeleton className="h-5 w-20" />
          </div>
          <div className="flex gap-2">
            <Skeleton className="h-12 w-12 rounded-md" />
            <Skeleton className="h-12 w-12 rounded-md" />
            <Skeleton className="h-12 w-12 rounded-md" />
            <Skeleton className="h-12 w-12 rounded-md" />
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function EmptyState() {
  return (
    <div className="text-center">
      <Package className="h-8 w-8 mx-auto mb-2 text-muted-foreground opacity-50" />
      <p className="text-sm text-muted-foreground mb-3">
        No upcoming shipments
      </p>
    </div>
  );
}

interface NextShipmentDisplayProps {
  shipment: Shipment;
  additionalCount?: number;
}

function NextShipmentDisplay({
  shipment,
  additionalCount,
}: NextShipmentDisplayProps) {
  const deliveryDate = shipment.expectedDeliveryDate
    ? new Date(shipment.expectedDeliveryDate)
    : null;

  const countdown = deliveryDate ? formatDeliveryCountdown(deliveryDate) : null;

  // Convert shipment items to carousel format
  const carouselItems: ProductImageItem[] = shipment.items.map((si) => ({
    id: si.id,
    name: si.item.name,
    imageUrl: si.item.imageUrl,
    quantity: si.orderedQuantity,
  }));

  const totalUnits = shipment.items.reduce(
    (sum, si) => sum + si.orderedQuantity,
    0
  );

  return (
    <div className="space-y-3">
      {/* Header row: Supplier | Shipment number | Status + Countdown */}
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="font-medium text-sm truncate">
              {shipment.supplierName ?? "Unknown Supplier"}
            </span>
            <span className="text-xs text-muted-foreground font-mono">
              #{shipment.shipmentNumber}
            </span>
          </div>
          <div className="text-xs text-muted-foreground mt-0.5">
            {shipment.items.length} item{shipment.items.length !== 1 ? "s" : ""}{" "}
            ({totalUnits.toLocaleString()} units)
          </div>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <Badge variant={SHIPMENT_STATUS_VARIANTS[shipment.status]}>
            {SHIPMENT_STATUS_LABELS[shipment.status]}
          </Badge>
          {countdown && (
            <span className="text-xs text-muted-foreground">{countdown}</span>
          )}
        </div>
      </div>

      {/* Product image carousel */}
      <ProductImageCarousel items={carouselItems} />

      {/* Additional shipments badge */}
      {additionalCount !== undefined && additionalCount > 0 && (
        <p className="text-xs text-muted-foreground">
          +{additionalCount} more shipment{additionalCount !== 1 ? "s" : ""}{" "}
          arriving soon
        </p>
      )}
    </div>
  );
}

export function SupplyChainStatusCard({
  nextShipment,
  additionalShipmentCount,
  isLoading,
}: SupplyChainStatusCardProps) {
  if (isLoading) {
    return <LoadingSkeleton />;
  }

  return (
    <Card className="gap-3 shadow-none">
      <CardHeader className="pb-0">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base font-semibold flex items-center gap-2">
            <Truck className="h-4 w-4 text-muted-foreground" />
            Incoming Shipment
          </CardTitle>
          <Button asChild variant="ghost" size="sm">
            <Link href="/shipments" className="gap-1">
              View All
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </Button>
        </div>
      </CardHeader>
      <CardContent className="pt-0">
        <div className="bg-muted dark:bg-[#1c1c1c] rounded-xl p-4 -mx-2 min-h-[140px] flex flex-col justify-center">
          {!nextShipment ? (
            <EmptyState />
          ) : (
            <NextShipmentDisplay
              shipment={nextShipment}
              additionalCount={additionalShipmentCount}
            />
          )}
        </div>
      </CardContent>
    </Card>
  );
}
