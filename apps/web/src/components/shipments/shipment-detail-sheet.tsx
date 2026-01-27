"use client";

import { Package, Pencil, X, PackageCheck, Trash2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Can, Permission } from "@/components/rbac";
import type { Shipment, ShipmentStatus } from "@/types/api";

interface ShipmentDetailSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  shipment: Shipment | null;
  onEditClick?: () => void;
  onCancelClick?: () => void;
  onReceiveClick?: () => void;
  onDeleteClick?: () => void;
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

function formatDate(dateStr?: string) {
  if (!dateStr) return "-";
  return new Date(dateStr).toLocaleDateString("en-US", {
    month: "2-digit",
    day: "2-digit",
    year: "numeric",
  });
}

function formatCurrency(value?: number) {
  return value != null ? `$${value.toFixed(2)}` : "-";
}

export function ShipmentDetailSheet({
  open,
  onOpenChange,
  shipment,
  onEditClick,
  onCancelClick,
  onReceiveClick,
  onDeleteClick,
}: ShipmentDetailSheetProps) {
  if (!shipment) {
    return null;
  }

  const canReceive =
    shipment.status === "PENDING" || shipment.status === "IN_TRANSIT";
  const canModify =
    shipment.status !== "DELIVERED" && shipment.status !== "CANCELLED";
  const canDelete = shipment.status !== "DELIVERED";

  const totalOrdered = shipment.items.reduce(
    (sum, item) => sum + item.orderedQuantity,
    0
  );
  const totalReceived = shipment.items.reduce(
    (sum, item) => sum + item.receivedQuantity,
    0
  );

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="text-xl font-semibold flex items-center gap-3">
            <span className="font-mono">{shipment.shipmentNumber}</span>
            <Badge variant={STATUS_VARIANTS[shipment.status]} className="text-xs">
              {STATUS_LABELS[shipment.status]}
            </Badge>
          </DialogTitle>
        </DialogHeader>

        {/* Shipment Info Grid */}
        <div className="grid grid-cols-2 gap-4 mt-4 text-sm">
          <div>
            <span className="text-muted-foreground">Supplier:</span>
            <p className="font-medium">{shipment.supplierName || "-"}</p>
          </div>
          <div>
            <span className="text-muted-foreground">Created By:</span>
            <p className="font-medium">{shipment.createdBy?.fullName || "-"}</p>
          </div>
          <div>
            <span className="text-muted-foreground">Order Date:</span>
            <p className="font-medium">{formatDate(shipment.orderDate)}</p>
          </div>
          <div>
            <span className="text-muted-foreground">Expected Delivery:</span>
            <p className="font-medium">
              {formatDate(shipment.expectedDeliveryDate)}
            </p>
          </div>
          <div>
            <span className="text-muted-foreground">Actual Delivery:</span>
            <p className="font-medium">
              {formatDate(shipment.actualDeliveryDate)}
            </p>
          </div>
          <div>
            <span className="text-muted-foreground">Total Cost:</span>
            <p className="font-medium">{formatCurrency(shipment.totalCost)}</p>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-2 mt-4">
          <Can permission={Permission.SHIPMENTS_RECEIVE}>
            {canReceive && (
              <Button onClick={onReceiveClick}>
                <PackageCheck className="h-4 w-4 mr-2" />
                Receive Items
              </Button>
            )}
          </Can>
          <Can permission={Permission.SHIPMENTS_UPDATE}>
            {canModify && (
              <Button variant="outline" onClick={onEditClick}>
                <Pencil className="h-4 w-4 mr-2" />
                Edit
              </Button>
            )}
          </Can>
          <Can permission={Permission.SHIPMENTS_UPDATE}>
            {canModify && (
              <Button variant="outline" onClick={onCancelClick}>
                <X className="h-4 w-4 mr-2" />
                Cancel
              </Button>
            )}
          </Can>
          <Can permission={Permission.SHIPMENTS_DELETE}>
            {canDelete && (
              <Button
                variant="destructive"
                onClick={onDeleteClick}
                className="ml-auto"
              >
                <Trash2 className="h-4 w-4 mr-2" />
                Delete
              </Button>
            )}
          </Can>
        </div>

        {/* Items Table */}
        <div className="mt-6">
          <h3 className="text-base font-medium text-primary mb-3">
            Items ({shipment.items.length})
            <span className="text-muted-foreground font-normal ml-2">
              {totalReceived} / {totalOrdered} received
            </span>
          </h3>
          <div className="rounded-lg border overflow-hidden">
            <Table>
              <TableHeader className="bg-muted">
                <TableRow>
                  <TableHead>Product</TableHead>
                  <TableHead>SKU</TableHead>
                  <TableHead className="text-right">Ordered</TableHead>
                  <TableHead className="text-right">Received</TableHead>
                  <TableHead className="text-right">Unit Cost</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {shipment.items.length === 0 ? (
                  <TableRow>
                    <TableCell
                      colSpan={6}
                      className="h-16 text-center text-muted-foreground"
                    >
                      <div className="flex flex-col items-center gap-1">
                        <Package className="h-5 w-5 text-muted-foreground/50" />
                        <span>No items in this shipment</span>
                      </div>
                    </TableCell>
                  </TableRow>
                ) : (
                  shipment.items.map((item) => {
                    const isComplete =
                      item.receivedQuantity >= item.orderedQuantity;
                    const isPartial =
                      item.receivedQuantity > 0 && !isComplete;
                    return (
                      <TableRow key={item.id}>
                        <TableCell className="font-medium">
                          {item.item.name}
                        </TableCell>
                        <TableCell className="font-mono text-sm text-muted-foreground">
                          {item.item.sku}
                        </TableCell>
                        <TableCell className="text-right">
                          {item.orderedQuantity}
                        </TableCell>
                        <TableCell className="text-right">
                          {item.receivedQuantity}
                        </TableCell>
                        <TableCell className="text-right">
                          {formatCurrency(item.unitCost)}
                        </TableCell>
                        <TableCell>
                          {isComplete ? (
                            <Badge variant="default" className="text-xs">
                              Complete
                            </Badge>
                          ) : isPartial ? (
                            <Badge variant="secondary" className="text-xs">
                              Partial
                            </Badge>
                          ) : (
                            <Badge variant="outline" className="text-xs">
                              Pending
                            </Badge>
                          )}
                        </TableCell>
                      </TableRow>
                    );
                  })
                )}
              </TableBody>
            </Table>
          </div>
        </div>

        {/* Notes */}
        {shipment.notes && (
          <div className="mt-6">
            <h3 className="text-base font-medium text-primary mb-2">Notes</h3>
            <p className="text-sm text-muted-foreground">{shipment.notes}</p>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
