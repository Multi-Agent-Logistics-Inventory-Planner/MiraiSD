"use client";

import { useEffect, useState } from "react";
import { format } from "date-fns";
import { Package, Pencil, X, PackageCheck, Trash2, MapPin, Truck, Loader2 } from "lucide-react";
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
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { Can, Permission } from "@/components/rbac";
import type { Shipment, ShipmentStatus, ShipmentItem, ShipmentItemAllocation } from "@/types/api";
import { LOCATION_TYPE_LABELS, LocationType } from "@/types/api";
import { getTracking, type TrackingLookupResponse } from "@/lib/api/tracking";

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

function getLocationLabel(locationType?: LocationType): string {
  if (!locationType) return "-";
  return LOCATION_TYPE_LABELS[locationType] ?? locationType;
}

function AllocationDisplay({ allocation }: { allocation: ShipmentItemAllocation }) {
  return (
    <div className="flex items-center gap-2 py-1">
      <Badge variant="outline" className="text-xs font-normal">
        {getLocationLabel(allocation.locationType)}
      </Badge>
      <span className="text-xs text-muted-foreground">
        x{allocation.quantity}
      </span>
      {allocation.locationId && (
        <span className="font-mono text-xs text-muted-foreground">
          (ID: {allocation.locationId.slice(0, 8)}...)
        </span>
      )}
    </div>
  );
}

function ItemAllocationRow({ item }: { item: ShipmentItem }) {
  const allocations = item.allocations ?? [];
  const hasAllocations = allocations.length > 0;
  const isComplete = item.receivedQuantity >= item.orderedQuantity;
  const isPartial = item.receivedQuantity > 0 && !isComplete;

  // Show allocations if they exist
  const showAllocations = hasAllocations;

  return (
    <>
      <TableRow className={showAllocations ? "border-b-0" : ""}>
        <TableCell className="font-medium">{item.item.name}</TableCell>
        <TableCell className="font-mono text-sm text-muted-foreground">
          {item.item.sku}
        </TableCell>
        <TableCell className="text-right">{item.orderedQuantity}</TableCell>
        <TableCell className="text-right">{item.receivedQuantity}</TableCell>
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
      {showAllocations && (
        <TableRow className="bg-muted/30">
          <TableCell colSpan={6} className="py-2 pl-8">
            <div className="flex items-start gap-2 text-sm text-muted-foreground">
              <MapPin className="h-3.5 w-3.5 mt-1.5 shrink-0" />
              <div className="flex flex-col">
                <span className="text-xs font-medium mb-1">Assigned to:</span>
                {allocations.map((allocation) => (
                  <AllocationDisplay key={allocation.id} allocation={allocation} />
                ))}
              </div>
            </div>
          </TableCell>
        </TableRow>
      )}
    </>
  );
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
  const [tracking, setTracking] = useState<TrackingLookupResponse | null>(null);
  const [trackingLoading, setTrackingLoading] = useState(false);
  const [trackingError, setTrackingError] = useState<string | null>(null);
  const [trackingExpanded, setTrackingExpanded] = useState(false);

  // Fetch tracking info when shipment has a trackingId
  useEffect(() => {
    if (!shipment?.trackingId) {
      setTracking(null);
      setTrackingError(null);
      return;
    }

    async function fetchTracking() {
      setTrackingLoading(true);
      setTrackingError(null);
      try {
        const result = await getTracking(shipment!.trackingId!);
        setTracking(result);
      } catch (err) {
        setTrackingError(
          err instanceof Error ? err.message : "Failed to load tracking"
        );
      } finally {
        setTrackingLoading(false);
      }
    }

    fetchTracking();
  }, [shipment?.trackingId]);

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

  const isCompleted = shipment.status === "DELIVERED";
  const hasPartialReceipts = totalReceived > 0 && totalReceived < totalOrdered;

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
          <div>
            <span className="text-muted-foreground">Received By:</span>
            <p className="font-medium">{shipment.receivedBy?.fullName || "-"}</p>
          </div>
          {shipment.trackingId && (
            <div>
              <span className="text-muted-foreground">Tracking #:</span>
              <p className="font-mono font-medium">{shipment.trackingId}</p>
            </div>
          )}
        </div>

        {/* Tracking Information */}
        {shipment.trackingId && (
          <div className="mt-4 border rounded-lg">
            <Collapsible open={trackingExpanded} onOpenChange={setTrackingExpanded}>
              <CollapsibleTrigger asChild>
                <Button
                  variant="ghost"
                  className="w-full justify-between px-4 py-3 h-auto"
                >
                  <div className="flex items-center gap-2">
                    <Truck className="h-4 w-4" />
                    <span className="font-medium">Tracking Information</span>
                    {tracking && (
                      <Badge variant="secondary" className="text-xs capitalize">
                        {tracking.carrier}
                      </Badge>
                    )}
                  </div>
                  {trackingLoading ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <span className="text-xs text-muted-foreground">
                      {trackingExpanded ? "Hide" : "Show"} details
                    </span>
                  )}
                </Button>
              </CollapsibleTrigger>
              <CollapsibleContent>
                <div className="px-4 pb-4 space-y-3">
                  {trackingLoading && (
                    <div className="flex items-center justify-center py-4">
                      <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                    </div>
                  )}
                  {trackingError && (
                    <p className="text-sm text-destructive">{trackingError}</p>
                  )}
                  {tracking && !trackingLoading && (
                    <>
                      <div className="grid grid-cols-2 gap-3 text-sm">
                        <div>
                          <span className="text-muted-foreground">Tracking #:</span>
                          <p className="font-mono font-medium">{tracking.trackingNumber}</p>
                        </div>
                        <div>
                          <span className="text-muted-foreground">Status:</span>
                          <p className="font-medium">{tracking.status}</p>
                        </div>
                        {tracking.expectedDelivery && (
                          <div>
                            <span className="text-muted-foreground">Est. Delivery:</span>
                            <p className="font-medium">
                              {format(new Date(tracking.expectedDelivery), "MMM d, yyyy")}
                            </p>
                          </div>
                        )}
                        {tracking.actualDelivery && (
                          <div>
                            <span className="text-muted-foreground">Delivered:</span>
                            <p className="font-medium">
                              {format(new Date(tracking.actualDelivery), "MMM d, yyyy")}
                            </p>
                          </div>
                        )}
                      </div>
                      {tracking.statusDetail && (
                        <p className="text-sm text-muted-foreground">
                          {tracking.statusDetail}
                        </p>
                      )}
                      {tracking.events.length > 0 && (
                        <div className="mt-3">
                          <p className="text-xs font-medium text-muted-foreground mb-2">
                            Recent Events
                          </p>
                          <div className="space-y-2">
                            {tracking.events.slice(0, 5).map((event, idx) => (
                              <div
                                key={idx}
                                className="text-xs border-l-2 border-muted pl-3 py-1"
                              >
                                <p className="font-medium">{event.message}</p>
                                <p className="text-muted-foreground">
                                  {event.location} -{" "}
                                  {format(new Date(event.occurredAt), "MMM d, h:mm a")}
                                </p>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </>
                  )}
                </div>
              </CollapsibleContent>
            </Collapsible>
          </div>
        )}

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
                  shipment.items.map((item) => (
                    <ItemAllocationRow key={item.id} item={item} />
                  ))
                )}
              </TableBody>
            </Table>
          </div>

          {/* Legend for completed/partial shipments */}
          {(isCompleted || hasPartialReceipts) && (
            <p className="text-xs text-muted-foreground mt-2 flex items-center gap-1">
              <MapPin className="h-3 w-3" />
              Items with allocations show their assigned storage locations
            </p>
          )}
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
