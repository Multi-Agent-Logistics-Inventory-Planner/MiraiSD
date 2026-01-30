"use client";

import { useEffect, useState } from "react";
import { format } from "date-fns";
import { Package, PackageCheck, Trash2, MapPin, Truck, Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
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
    month: "short",
    day: "numeric",
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
    <div className="flex items-center gap-2 py-0.5">
      <Badge variant="outline" className="text-xs font-normal">
        {getLocationLabel(allocation.locationType)}
      </Badge>
      <span className="text-xs text-muted-foreground">
        x{allocation.quantity}
      </span>
    </div>
  );
}

function ItemRow({ item, index }: { item: ShipmentItem; index: number }) {
  const allocations = item.allocations ?? [];
  const hasAllocations = allocations.length > 0;
  const lineTotal = item.unitCost ? item.orderedQuantity * item.unitCost : undefined;

  return (
    <div className="py-3 border-b last:border-b-0">
      <div className="flex items-start gap-4">
        <span className="text-sm text-muted-foreground w-6">{index + 1}</span>
        <div className="flex-1 min-w-0">
          <p className="font-medium text-sm">{item.item.name}</p>
          <p className="text-xs text-muted-foreground font-mono">{item.item.sku}</p>
        </div>
        <div className="text-right text-sm">
          <p>{item.orderedQuantity} ordered</p>
          <p className="text-xs text-muted-foreground">{item.receivedQuantity} received</p>
        </div>
        <div className="text-right text-sm w-20">
          {item.unitCost ? (
            <>
              <p>{formatCurrency(item.unitCost)}</p>
              <p className="text-xs text-muted-foreground">each</p>
            </>
          ) : (
            <span className="text-muted-foreground">-</span>
          )}
        </div>
        <div className="text-right text-sm font-medium w-24">
          {lineTotal ? formatCurrency(lineTotal) : "-"}
        </div>
      </div>
      {hasAllocations && (
        <div className="mt-2 ml-10 flex items-start gap-2">
          <MapPin className="h-3 w-3 mt-1 text-muted-foreground shrink-0" />
          <div className="flex flex-wrap gap-1">
            {allocations.map((allocation) => (
              <AllocationDisplay key={allocation.id} allocation={allocation} />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export function ShipmentDetailSheet({
  open,
  onOpenChange,
  shipment,
  onReceiveClick,
  onDeleteClick,
}: ShipmentDetailSheetProps) {
  const [tracking, setTracking] = useState<TrackingLookupResponse | null>(null);
  const [trackingLoading, setTrackingLoading] = useState(false);
  const [trackingError, setTrackingError] = useState<string | null>(null);
  const [trackingExpanded, setTrackingExpanded] = useState(false);
  const [infoExpanded, setInfoExpanded] = useState(false);

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
  const canDelete = shipment.status !== "DELIVERED";

  const totalOrdered = shipment.items.reduce(
    (sum, item) => sum + item.orderedQuantity,
    0
  );
  const totalReceived = shipment.items.reduce(
    (sum, item) => sum + item.receivedQuantity,
    0
  );

  // Calculate items total from unit costs
  const itemsTotal = shipment.items.reduce((sum, item) => {
    if (item.unitCost) {
      return sum + item.orderedQuantity * item.unitCost;
    }
    return sum;
  }, 0);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl max-h-[90vh] overflow-y-auto p-0">
        {/* Header */}
        <DialogHeader className="px-6 pt-6 pb-4">
          <DialogTitle className="flex items-center gap-3">
            <span className="text-xl font-semibold">Shipment:</span>
            <span className="text-xl font-mono">{shipment.shipmentNumber}</span>
            <Badge variant={STATUS_VARIANTS[shipment.status]} className="text-xs ml-2">
              {STATUS_LABELS[shipment.status]}
            </Badge>
          </DialogTitle>
        </DialogHeader>

        <div className="px-6 pb-6 space-y-6">
          {/* Action Buttons */}
          <div className="flex items-center gap-2">
            <Can permission={Permission.SHIPMENTS_RECEIVE}>
              {canReceive && (
                <Button onClick={onReceiveClick} size="sm">
                  <PackageCheck className="h-4 w-4 mr-2" />
                  Receive Items
                </Button>
              )}
            </Can>
            <Can permission={Permission.SHIPMENTS_DELETE}>
              {canDelete && (
                <Button variant="destructive" size="sm" onClick={onDeleteClick}>
                  <Trash2 className="h-4 w-4 mr-2" />
                  Delete
                </Button>
              )}
            </Can>
          </div>

          {/* Items Section */}
          <div className="border rounded-lg">
            <div className="px-4 py-3 border-b bg-muted/30">
              <h3 className="font-medium">Item Details</h3>
            </div>

            {/* Items Header */}
            <div className="px-4 py-2 border-b bg-muted/20">
              <div className="flex items-center gap-4 text-xs text-muted-foreground font-medium">
                <span className="w-6">#</span>
                <span className="flex-1">Name</span>
                <span className="text-right w-24">Qty</span>
                <span className="text-right w-20">Price</span>
                <span className="text-right w-24">Total</span>
              </div>
            </div>

            {/* Items List */}
            <div className="px-4">
              {shipment.items.length === 0 ? (
                <div className="py-8 text-center text-muted-foreground">
                  <Package className="h-8 w-8 mx-auto mb-2 opacity-50" />
                  <p>No items in this shipment</p>
                </div>
              ) : (
                shipment.items.map((item, index) => (
                  <ItemRow key={item.id} item={item} index={index} />
                ))
              )}
            </div>

            {/* Totals */}
            {shipment.items.length > 0 && (
              <div className="px-4 py-3 border-t bg-muted/20">
                <div className="flex justify-between items-center text-sm">
                  <span className="text-muted-foreground">
                    {totalReceived} / {totalOrdered} items received
                  </span>
                  {itemsTotal > 0 && (
                    <div className="text-right">
                      <span className="text-muted-foreground mr-4">Items Total:</span>
                      <span className="font-semibold">{formatCurrency(itemsTotal)}</span>
                    </div>
                  )}
                </div>
                {shipment.totalCost != null && shipment.totalCost > 0 && shipment.totalCost !== itemsTotal && (
                  <div className="flex justify-end items-center text-sm mt-1">
                    <span className="text-muted-foreground mr-4">Shipment Total:</span>
                    <span className="font-semibold">{formatCurrency(shipment.totalCost)}</span>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Shipment Info */}
          <div className="border rounded-lg overflow-hidden">
            <Collapsible open={infoExpanded} onOpenChange={setInfoExpanded}>
              <CollapsibleTrigger asChild>
                <Button
                  variant="ghost"
                  className="w-full justify-between px-4 py-3 h-auto rounded-none bg-muted/30 hover:bg-muted/50"
                >
                  <div className="flex items-center gap-2">
                    <Package className="h-4 w-4" />
                    <span className="font-medium">Shipment Information</span>
                  </div>
                  <span className="text-xs text-muted-foreground">
                    {infoExpanded ? "Hide" : "Show"} details
                  </span>
                </Button>
              </CollapsibleTrigger>
              <CollapsibleContent>
                <div className="p-4 grid grid-cols-2 gap-4 text-sm">
                  {shipment.supplierName && (
                    <div>
                      <p className="text-muted-foreground text-xs mb-1">Supplier</p>
                      <p className="font-medium">{shipment.supplierName}</p>
                    </div>
                  )}
                  <div>
                    <p className="text-muted-foreground text-xs mb-1">Created By</p>
                    <p className="font-medium">{shipment.createdBy?.fullName || "-"}</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground text-xs mb-1">Order Date</p>
                    <p className="font-medium">{formatDate(shipment.orderDate)}</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground text-xs mb-1">Expected Delivery</p>
                    <p className="font-medium">{formatDate(shipment.expectedDeliveryDate)}</p>
                  </div>
                  {shipment.actualDeliveryDate && (
                    <div>
                      <p className="text-muted-foreground text-xs mb-1">Actual Delivery</p>
                      <p className="font-medium">{formatDate(shipment.actualDeliveryDate)}</p>
                    </div>
                  )}
                  {shipment.totalCost && (
                    <div>
                      <p className="text-muted-foreground text-xs mb-1">Total Cost</p>
                      <p className="font-medium">{formatCurrency(shipment.totalCost)}</p>
                    </div>
                  )}
                  {shipment.receivedBy && (
                    <div>
                      <p className="text-muted-foreground text-xs mb-1">Received By</p>
                      <p className="font-medium">{shipment.receivedBy.fullName}</p>
                    </div>
                  )}
                  {shipment.trackingId && (
                    <div>
                      <p className="text-muted-foreground text-xs mb-1">Tracking Number</p>
                      <p className="font-mono font-medium">{shipment.trackingId}</p>
                    </div>
                  )}
                </div>
              </CollapsibleContent>
            </Collapsible>
          </div>

          {/* Tracking Information */}
          {shipment.trackingId && (
            <div className="border rounded-lg overflow-hidden">
              <Collapsible open={trackingExpanded} onOpenChange={setTrackingExpanded}>
                <CollapsibleTrigger asChild>
                  <Button
                    variant="ghost"
                    className="w-full justify-between px-4 py-3 h-auto rounded-none bg-muted/30 hover:bg-muted/50"
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
                  <div className="px-4 py-4 space-y-4">
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
                        <div className="grid grid-cols-2 gap-4 text-sm">
                          <div>
                            <p className="text-muted-foreground text-xs mb-1">Status</p>
                            <p className="font-medium">{tracking.status}</p>
                          </div>
                          {tracking.actualDelivery && (
                            <div>
                              <p className="text-muted-foreground text-xs mb-1">Delivered</p>
                              <p className="font-medium">
                                {format(new Date(tracking.actualDelivery), "MMM d, yyyy")}
                              </p>
                            </div>
                          )}
                        </div>
                        {tracking.statusDetail && (
                          <p className="text-sm text-muted-foreground bg-muted/30 p-2 rounded">
                            {tracking.statusDetail}
                          </p>
                        )}
                        {tracking.events.length > 0 && (
                          <div>
                            <p className="text-xs font-medium text-muted-foreground mb-3">
                              Delivery Status
                            </p>
                            <div className="space-y-3">
                              {tracking.events.slice(0, 6).map((event, idx) => (
                                <div key={idx} className="flex items-start gap-3">
                                  <div className="relative">
                                    <div className={`w-2.5 h-2.5 rounded-full mt-1.5 ${
                                      idx === 0 ? "bg-primary" : "bg-muted-foreground/30"
                                    }`} />
                                    {idx < tracking.events.slice(0, 6).length - 1 && (
                                      <div className="absolute top-4 left-1 w-0.5 h-6 bg-muted-foreground/20" />
                                    )}
                                  </div>
                                  <div className="flex-1 min-w-0">
                                    <p className="text-sm font-medium">{event.message}</p>
                                    <p className="text-xs text-muted-foreground">
                                      {event.location}
                                    </p>
                                  </div>
                                  <p className="text-xs text-muted-foreground whitespace-nowrap">
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

          {/* Notes */}
          {shipment.notes && (
            <div className="border rounded-lg">
              <div className="px-4 py-3 border-b bg-muted/30">
                <h3 className="font-medium">Notes</h3>
              </div>
              <div className="p-4">
                <p className="text-sm text-muted-foreground">{shipment.notes}</p>
              </div>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
