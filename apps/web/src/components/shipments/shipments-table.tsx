"use client";

import { Package } from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import type { Shipment } from "@/types/api";
import { cn } from "@/lib/utils";
import {
  getShipmentDisplayStatus,
  SHIPMENT_DISPLAY_STATUS_LABELS,
  SHIPMENT_DISPLAY_STATUS_COLORS,
  type ShipmentDisplayStatus,
} from "@/lib/shipment-utils";

interface ShipmentsTableProps {
  shipments: Shipment[];
  isLoading: boolean;
  onRowClick: (shipment: Shipment) => void;
}

function formatDate(dateStr?: string) {
  if (!dateStr) return "-";
  return new Date(dateStr).toLocaleDateString("en-US", {
    month: "2-digit",
    day: "2-digit",
    year: "numeric",
  });
}

function getReceiveProgress(shipment: Shipment) {
  const totalOrdered = shipment.items.reduce(
    (sum, item) => sum + item.orderedQuantity,
    0
  );
  const totalReceived = shipment.items.reduce(
    (sum, item) => sum + item.receivedQuantity,
    0
  );
  if (totalOrdered === 0) return 0;
  return Math.round((totalReceived / totalOrdered) * 100);
}

export function ShipmentsTable({
  shipments,
  isLoading,
  onRowClick,
}: ShipmentsTableProps) {
  if (isLoading) {
    return (
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Shipment #</TableHead>
            <TableHead>Supplier</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Items</TableHead>
            <TableHead>Progress</TableHead>
            <TableHead>Order Date</TableHead>
            <TableHead>Est. Delivery</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {Array.from({ length: 5 }).map((_, i) => (
            <TableRow key={i}>
              <TableCell><Skeleton className="h-4 w-20" /></TableCell>
              <TableCell><Skeleton className="h-4 w-32" /></TableCell>
              <TableCell><Skeleton className="h-5 w-16" /></TableCell>
              <TableCell><Skeleton className="h-4 w-8" /></TableCell>
              <TableCell><Skeleton className="h-4 w-16" /></TableCell>
              <TableCell><Skeleton className="h-4 w-24" /></TableCell>
              <TableCell><Skeleton className="h-4 w-24" /></TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
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
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Shipment #</TableHead>
          <TableHead>Supplier</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Items</TableHead>
          <TableHead>Progress</TableHead>
          <TableHead>Order Date</TableHead>
          <TableHead>Est. Delivery</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {shipments.map((shipment) => {
          const progress = getReceiveProgress(shipment);
          const displayStatus = getShipmentDisplayStatus(shipment);

          return (
            <TableRow
              key={shipment.id}
              className="cursor-pointer hover:bg-muted/50"
              onClick={() => onRowClick(shipment)}
            >
              <TableCell className="font-mono text-sm">
                {shipment.shipmentNumber}
              </TableCell>
              <TableCell>{shipment.supplierName || "-"}</TableCell>
              <TableCell>
                {displayStatus && (
                  <Badge
                    variant="outline"
                    className={cn("text-xs", SHIPMENT_DISPLAY_STATUS_COLORS[displayStatus])}
                  >
                    {SHIPMENT_DISPLAY_STATUS_LABELS[displayStatus]}
                  </Badge>
                )}
              </TableCell>
              <TableCell>{shipment.items.length}</TableCell>
              <TableCell>
                <div className="flex items-center gap-2">
                  <div className="w-16 h-2 bg-muted rounded-full overflow-hidden">
                    <div
                      className="h-full bg-primary rounded-full transition-all"
                      style={{ width: `${progress}%` }}
                    />
                  </div>
                  <span className="text-xs text-muted-foreground">
                    {progress}%
                  </span>
                </div>
              </TableCell>
              <TableCell>{formatDate(shipment.orderDate)}</TableCell>
              <TableCell>{formatDate(shipment.expectedDeliveryDate)}</TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
