"use client";

import { Package } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { ShipmentCard } from "./shipment-card";
import type { Shipment } from "@/types/api";

interface ShipmentsListProps {
  shipments: Shipment[];
  isLoading: boolean;
  onShipmentClick: (shipment: Shipment) => void;
}

function ShipmentCardSkeleton() {
  return (
    <div className="rounded-xl border dark:border-none bg-card p-4">
      {/* Header */}
      <div className="flex items-start justify-between gap-4 mb-3">
        <div className="flex-1">
          <div className="flex items-center gap-3 mb-1">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="h-4 w-40" />
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Skeleton className="h-4 w-16" />
          <Skeleton className="h-5 w-16 rounded-full" />
          <Skeleton className="h-8 w-8 rounded" />
        </div>
      </div>

      {/* Supplier Row */}
      <div className="flex items-center gap-2 mb-4">
        <Skeleton className="h-6 w-6 rounded-full" />
        <Skeleton className="h-4 w-32" />
      </div>

      {/* Status Section */}
      <div className="rounded-lg bg-muted/30 p-3">
        <div className="flex items-center justify-between mb-2">
          <Skeleton className="h-4 w-48" />
          <Skeleton className="h-4 w-20" />
        </div>
        {/* Progress */}
        <div className="flex items-center justify-between py-2">
          <Skeleton className="h-8 w-8 rounded-full" />
          <Skeleton className="h-1 flex-1 mx-2" />
          <Skeleton className="h-8 w-8 rounded-full" />
          <Skeleton className="h-1 flex-1 mx-2" />
          <Skeleton className="h-8 w-8 rounded-full" />
          <Skeleton className="h-1 flex-1 mx-2" />
          <Skeleton className="h-8 w-8 rounded-full" />
        </div>
      </div>
    </div>
  );
}

export function ShipmentsList({
  shipments,
  isLoading,
  onShipmentClick,
}: ShipmentsListProps) {
  if (isLoading) {
    return (
      <div className="space-y-4">
        {Array.from({ length: 5 }).map((_, i) => (
          <ShipmentCardSkeleton key={i} />
        ))}
      </div>
    );
  }

  if (shipments.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <Package className="h-12 w-12 text-muted-foreground/50 mb-4" />
        <p className="text-muted-foreground">No shipments found</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {shipments.map((shipment) => (
        <ShipmentCard
          key={shipment.id}
          shipment={shipment}
          onClick={() => onShipmentClick(shipment)}
        />
      ))}
    </div>
  );
}
