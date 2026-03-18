"use client";

import Link from "next/link";
import { Package, Truck, ArrowRight } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  ProductImageCarousel,
  type ProductImageItem,
} from "./product-image-carousel";
import type { Shipment } from "@/types/api";

interface SupplyChainStatusCardProps {
  nextShipment: Shipment | null;
  additionalShipmentCount?: number;
  isLoading?: boolean;
}

function LoadingSkeleton() {
  return (
    <Card className="gap-3 h-[240px] flex flex-col">
      <CardHeader className="pb-0 flex-shrink-0">
        <div className="flex items-center justify-between">
          <Skeleton className="h-5 w-36" />
          <Skeleton className="h-8 w-24" />
        </div>
      </CardHeader>
      <CardContent className="pt-0 flex-1 flex flex-col min-h-0">
        <div className="bg-muted dark:bg-[#1c1c1c] rounded-xl p-4 -mx-2 flex-1">
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
    <div className="flex flex-col justify-between h-full">
      <div>
        {/* Header row: Supplier | Shipment number */}
        <div className="flex items-center gap-2">
          <span className="font-medium text-sm">
            {shipment.supplierName ?? "Unknown Supplier"}
          </span>
          <span className="text-xs text-muted-foreground font-mono">
            #{shipment.shipmentNumber}
          </span>
        </div>

        {/* Items count */}
        <div className="text-xs text-muted-foreground mt-0.5">
          {shipment.items.length} item{shipment.items.length !== 1 ? "s" : ""}{" "}
          ({totalUnits.toLocaleString()} units)
        </div>

        {/* Product image carousel */}
        <div className="mt-2">
          <ProductImageCarousel items={carouselItems} />
        </div>
      </div>

      {/* Additional shipments badge */}
      {additionalCount !== undefined && additionalCount > 0 && (
        <p className="text-xs text-muted-foreground mt-3">
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
    <Card className="gap-3 shadow-none h-[240px] flex flex-col">
      <CardHeader className="pb-0 flex-shrink-0">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base font-semibold flex items-center gap-2">
            <Truck className="h-4 w-4 text-muted-foreground" />
            Incoming Shipment
          </CardTitle>
          <Button asChild variant="ghost" size="sm">
            <Link href="/shipments" className="gap-1 text-muted-foreground hover:text-muted-foreground">
              View All
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </Button>
        </div>
      </CardHeader>
      <CardContent className="pt-0 flex-1 flex flex-col min-h-0">
        <div className="bg-muted dark:bg-[#1c1c1c] rounded-xl px-4 pt-3 pb-4 -mx-2 flex-1 flex flex-col overflow-hidden">
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
