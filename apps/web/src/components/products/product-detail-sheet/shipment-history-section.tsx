"use client";

import { Package } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useShipmentsByProduct } from "@/hooks/queries/use-shipments-by-product";
import type { ShipmentStatus } from "@/types/api";

interface ShipmentHistorySectionProps {
  productId: string;
}

const STATUS_VARIANTS: Record<
  ShipmentStatus,
  "default" | "secondary" | "destructive" | "outline"
> = {
  PENDING: "outline",
  IN_TRANSIT: "secondary",
  DELIVERED: "default",
  CANCELLED: "destructive",
};

const STATUS_LABELS: Record<ShipmentStatus, string> = {
  PENDING: "Pending",
  IN_TRANSIT: "In Transit",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
};

function TableSkeleton() {
  return (
    <>
      {Array.from({ length: 3 }).map((_, i) => (
        <TableRow key={i}>
          <TableCell>
            <Skeleton className="h-4 w-20" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-24" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-5 w-16" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-20" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-20" />
          </TableCell>
        </TableRow>
      ))}
    </>
  );
}

export function ShipmentHistorySection({
  productId,
}: ShipmentHistorySectionProps) {
  const { data: shipments, isLoading, error } = useShipmentsByProduct(productId);

  if (error) {
    return (
      <div className="rounded-lg border bg-card p-4 text-center text-sm text-muted-foreground">
        Failed to load shipment history
      </div>
    );
  }

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return "-";
    const date = new Date(dateStr);
    return date.toLocaleDateString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
    });
  };

  return (
    <div className="rounded-lg border bg-card overflow-hidden">
      <Table>
        <TableHeader className="bg-muted">
          <TableRow>
            <TableHead>Shipment #</TableHead>
            <TableHead>Supplier</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Ordered</TableHead>
            <TableHead>Received</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {isLoading ? (
            <TableSkeleton />
          ) : !shipments || shipments.length === 0 ? (
            <TableRow>
              <TableCell
                colSpan={5}
                className="h-24 text-center text-muted-foreground"
              >
                <div className="flex flex-col items-center gap-2">
                  <Package className="h-8 w-8 text-muted-foreground/50" />
                  <span>No shipments found</span>
                </div>
              </TableCell>
            </TableRow>
          ) : (
            shipments.map((shipment) => (
              <TableRow key={shipment.id}>
                <TableCell className="font-mono text-sm">
                  {shipment.shipmentNumber}
                </TableCell>
                <TableCell className="text-sm">
                  {shipment.supplierName || "-"}
                </TableCell>
                <TableCell>
                  <Badge variant={STATUS_VARIANTS[shipment.status]}>
                    {STATUS_LABELS[shipment.status]}
                  </Badge>
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {formatDate(shipment.orderDate)}
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {formatDate(shipment.actualDeliveryDate)}
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </div>
  );
}
