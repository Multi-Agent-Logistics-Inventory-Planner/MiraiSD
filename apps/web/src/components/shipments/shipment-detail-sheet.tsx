"use client";

import { useEffect, useMemo, useState } from "react";
import Image from "next/image";
import { format } from "date-fns";
import { Package, PackageCheck, Trash2, Truck, Loader2, Pencil, Check, X } from "lucide-react";
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
import { Input } from "@/components/ui/input";
import { Can, Permission } from "@/components/rbac";
import type { Shipment, ShipmentStatus, ShipmentItem, ShipmentItemAllocation } from "@/types/api";
import { LOCATION_TYPE_LABELS, LocationType } from "@/types/api";
import { cn, prizeLetterDisplay, sortPrizes } from "@/lib/utils";
import { getTracking, type TrackingLookupResponse } from "@/lib/api/tracking";

interface ShipmentDetailSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  shipment: Shipment | null;
  onReceiveClick?: () => void;
  onDeleteClick?: () => void;
  onEditClick?: () => void;
  onTrackingUpdate?: (trackingId: string) => Promise<void>;
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
  return new Date(dateStr + "T00:00:00").toLocaleDateString("en-US", {
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

interface ConsolidatedAllocation {
  key: string;
  displayLabel: string;
  quantity: number;
}

/** Consolidate allocations with the same location into a single entry */
function consolidateAllocations(allocations: ShipmentItemAllocation[]): ConsolidatedAllocation[] {
  const map = new Map<string, ConsolidatedAllocation>();

  for (const allocation of allocations) {
    const displayLabel = allocation.locationCode || getLocationLabel(allocation.locationType);
    const key = `${allocation.locationType}-${allocation.locationId || "none"}`;

    const existing = map.get(key);
    if (existing) {
      existing.quantity += allocation.quantity;
    } else {
      map.set(key, {
        key,
        displayLabel,
        quantity: allocation.quantity,
      });
    }
  }

  return Array.from(map.values());
}

function AllocationDisplay({ allocation }: { allocation: ConsolidatedAllocation }) {
  return (
    <div className="flex items-center gap-2 py-0.5">
      <Badge variant="outline" className="text-xs font-normal">
        {allocation.displayLabel}
      </Badge>
      <span className="text-xs text-muted-foreground">
        x{allocation.quantity}
      </span>
    </div>
  );
}

/** One block in item details: either a Kuji (parent + prizes) or a standalone item. */
interface DetailBlock {
  parentItem: ShipmentItem;
  prizeItems: ShipmentItem[];
}

function ItemRow({ item }: { item: ShipmentItem }) {
  const rawAllocations = item.allocations ?? [];
  const allocations = consolidateAllocations(rawAllocations);
  const hasAllocations = allocations.length > 0;
  const lineTotal = item.unitCost ? item.orderedQuantity * item.unitCost : undefined;
  const imageUrl = item.item?.imageUrl;
  const totalAccounted = item.receivedQuantity + (item.damagedQuantity ?? 0) + (item.displayQuantity ?? 0) + (item.shopQuantity ?? 0);
  const hasUnreceived = totalAccounted < item.orderedQuantity;

  return (
    <div className={cn(
      "py-3 border-b last:border-b-0",
      hasUnreceived && "bg-amber-50/50 dark:bg-amber-950/20 -mx-4 px-4 border-l-2 border-l-amber-400"
    )}>
      <div className="flex items-center gap-3">
        {/* Product Image */}
        <div className="relative h-10 w-10 rounded-lg bg-muted overflow-hidden flex items-center justify-center shrink-0">
          {imageUrl ? (
            <Image
              src={imageUrl}
              alt={item.item?.name || "Product"}
              fill
              sizes="40px"
              className="object-cover"
            />
          ) : (
            <Package className="h-4 w-4 text-muted-foreground" />
          )}
        </div>
        <div className="flex-1 min-w-0">
          <p className="font-medium text-sm">{item.item.name}</p>
          {item.item.sku && <p className="text-xs text-muted-foreground font-mono">{item.item.sku}</p>}
          {hasAllocations && (
            <div className="flex flex-wrap gap-1 mt-1">
              {allocations.map((allocation) => (
                <AllocationDisplay key={allocation.key} allocation={allocation} />
              ))}
            </div>
          )}
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-1 sm:hidden text-xs text-muted-foreground">
            <span>{item.orderedQuantity} ordered · {item.receivedQuantity} received</span>
            {item.unitCost && <span>{formatCurrency(item.unitCost)} each</span>}
            {lineTotal && <span className="font-medium text-foreground">{formatCurrency(lineTotal)}</span>}
          </div>
        </div>
        <div className="hidden sm:block text-right text-sm shrink-0">
          <p>{item.orderedQuantity} ordered</p>
          <p className="text-xs text-muted-foreground">{item.receivedQuantity} received</p>
          {(item.damagedQuantity ?? 0) > 0 && (
            <p className="text-xs text-amber-600">{item.damagedQuantity} damaged</p>
          )}
          {(item.displayQuantity ?? 0) > 0 && (
            <p className="text-xs text-blue-600">{item.displayQuantity} display</p>
          )}
          {(item.shopQuantity ?? 0) > 0 && (
            <p className="text-xs text-green-600">{item.shopQuantity} shop</p>
          )}
        </div>
        <div className="hidden sm:block text-right text-sm w-20 shrink-0">
          {item.unitCost ? (
            <>
              <p>{formatCurrency(item.unitCost)}</p>
              <p className="text-xs text-muted-foreground">each</p>
            </>
          ) : (
            <span className="text-muted-foreground">-</span>
          )}
        </div>
        <div className="hidden sm:block text-right text-sm font-medium w-24 shrink-0">
          {lineTotal ? formatCurrency(lineTotal) : "-"}
        </div>
      </div>
    </div>
  );
}

function KujiBlockSection({ block }: { block: DetailBlock }) {
  const { parentItem, prizeItems } = block;
  const imageUrl = parentItem.item?.imageUrl;
  const rawParentAllocations = parentItem.allocations ?? [];
  const parentAllocations = consolidateAllocations(rawParentAllocations);
  const hasParentAllocations = parentAllocations.length > 0;
  const parentTotalAccounted = parentItem.receivedQuantity + (parentItem.damagedQuantity ?? 0) + (parentItem.displayQuantity ?? 0) + (parentItem.shopQuantity ?? 0);
  const parentHasUnreceived = parentTotalAccounted < parentItem.orderedQuantity;

  // Check if any prize has unreceived items
  const anyPrizeUnreceived = prizeItems.some((prize) => {
    const totalAccounted = prize.receivedQuantity + (prize.damagedQuantity ?? 0) + (prize.displayQuantity ?? 0) + (prize.shopQuantity ?? 0);
    return totalAccounted < prize.orderedQuantity;
  });

  const hasUnreceived = parentHasUnreceived || anyPrizeUnreceived;

  return (
    <div className={cn(
      "py-3 border-b last:border-b-0",
      hasUnreceived && "bg-amber-50/50 dark:bg-amber-950/20 -mx-4 px-4 border-l-2 border-l-amber-400"
    )}>
      {/* Parent row (Kuji set) */}
      <div className="flex items-center gap-3">
        <div className="relative h-10 w-10 rounded-lg bg-muted overflow-hidden flex items-center justify-center shrink-0">
          {imageUrl ? (
            <Image
              src={imageUrl}
              alt={parentItem.item?.name || "Kuji"}
              fill
              sizes="40px"
              className="object-cover"
            />
          ) : (
            <Package className="h-4 w-4 text-muted-foreground" />
          )}
        </div>
        <div className="flex-1 min-w-0">
          <p className="font-medium text-sm">{parentItem.item.name}</p>
          {parentItem.item.sku && <p className="text-xs text-muted-foreground font-mono">{parentItem.item.sku}</p>}
          {hasParentAllocations && (
            <div className="flex flex-wrap gap-1 mt-1">
              {parentAllocations.map((allocation) => (
                <AllocationDisplay key={allocation.key} allocation={allocation} />
              ))}
            </div>
          )}
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-1 sm:hidden text-xs text-muted-foreground">
            <span>Total Kuji: {parentItem.orderedQuantity} ordered · {parentItem.receivedQuantity} received</span>
          </div>
        </div>
        <div className="hidden sm:block text-right text-sm shrink-0">
          <p>{parentItem.orderedQuantity} ordered</p>
          <p className="text-xs text-muted-foreground">{parentItem.receivedQuantity} received</p>
          {(parentItem.damagedQuantity ?? 0) > 0 && (
            <p className="text-xs text-amber-600">{parentItem.damagedQuantity} damaged</p>
          )}
          {(parentItem.displayQuantity ?? 0) > 0 && (
            <p className="text-xs text-blue-600">{parentItem.displayQuantity} display</p>
          )}
          {(parentItem.shopQuantity ?? 0) > 0 && (
            <p className="text-xs text-green-600">{parentItem.shopQuantity} shop</p>
          )}
        </div>
        <div className="hidden sm:block text-right text-sm w-20 shrink-0">
          {parentItem.unitCost ? (
            <>
              <p>{formatCurrency(parentItem.unitCost)}</p>
              <p className="text-xs text-muted-foreground">each</p>
            </>
          ) : (
            <span className="text-muted-foreground">-</span>
          )}
        </div>
        <div className="hidden sm:block text-right text-sm font-medium w-24 shrink-0">
          {parentItem.unitCost ? formatCurrency(parentItem.orderedQuantity * parentItem.unitCost) : "-"}
        </div>
      </div>
      {/* Prizes (same section, indented) */}
      {prizeItems.length > 0 && (
        <div className="mt-2 pl-4 border-l-2 border-muted space-y-2">
          <p className="text-xs font-medium text-muted-foreground">Prizes</p>
          {prizeItems.map((prize) => {
            const lineTotal = prize.unitCost ? prize.orderedQuantity * prize.unitCost : undefined;
            const letter = (prize.item as { letter?: string | null }).letter;
            const prizeLabel = letter
              ? prizeLetterDisplay(letter)
              : prize.item.name;
            const prizeTotalAccounted = prize.receivedQuantity + (prize.damagedQuantity ?? 0) + (prize.displayQuantity ?? 0) + (prize.shopQuantity ?? 0);
            const prizeHasUnreceived = prizeTotalAccounted < prize.orderedQuantity;
            return (
              <div key={prize.id} className={cn(
                "flex items-center gap-2 py-1 px-2 -mx-2 rounded",
                prizeHasUnreceived && "bg-amber-100/50 dark:bg-amber-900/20"
              )}>
                <span className="font-mono font-bold text-sm bg-muted px-2 py-0.5 rounded min-w-[40px] text-center">{prizeLabel}</span>
                <span className="text-xs text-muted-foreground">
                  {prize.orderedQuantity} ordered · {prize.receivedQuantity} received
                </span>
                {(prize.damagedQuantity ?? 0) > 0 && (
                  <span className="text-xs text-amber-600">{prize.damagedQuantity} damaged</span>
                )}
                {(prize.displayQuantity ?? 0) > 0 && (
                  <span className="text-xs text-blue-600">{prize.displayQuantity} display</span>
                )}
                {(prize.shopQuantity ?? 0) > 0 && (
                  <span className="text-xs text-green-600">{prize.shopQuantity} shop</span>
                )}
                {lineTotal != null && (
                  <span className="text-xs font-medium ml-auto">{formatCurrency(lineTotal)}</span>
                )}
              </div>
            );
          })}
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
  onEditClick,
  onTrackingUpdate,
}: ShipmentDetailSheetProps) {
  const [tracking, setTracking] = useState<TrackingLookupResponse | null>(null);
  const [trackingLoading, setTrackingLoading] = useState(false);
  const [trackingError, setTrackingError] = useState<string | null>(null);
  const [trackingExpanded, setTrackingExpanded] = useState(false);
  const [infoExpanded, setInfoExpanded] = useState(false);
  const [isEditingTracking, setIsEditingTracking] = useState(false);
  const [trackingInput, setTrackingInput] = useState("");
  const [trackingSaving, setTrackingSaving] = useState(false);

  async function handleTrackingSave() {
    const value = trackingInput.trim();
    if (!value || !onTrackingUpdate) return;
    setTrackingSaving(true);
    try {
      await onTrackingUpdate(value);
      setIsEditingTracking(false);
    } finally {
      setTrackingSaving(false);
    }
  }

  // Reset editing state when shipment changes or sheet closes
  useEffect(() => {
    setIsEditingTracking(false);
    setTrackingInput("");
  }, [shipment?.id, open]);

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

  // Group items into blocks (must run unconditionally to satisfy Rules of Hooks)
  const detailBlocks = useMemo((): DetailBlock[] => {
    if (!shipment?.items) return [];
    const roots = shipment.items.filter((i) => !i.item.parentId);
    const prizesByParentId: Record<string, ShipmentItem[]> = {};
    shipment.items.forEach((i) => {
      const pid = i.item.parentId;
      if (pid) {
        if (!prizesByParentId[pid]) prizesByParentId[pid] = [];
        prizesByParentId[pid].push(i);
      }
    });
    return roots.map((parentItem) => {
      // Sort prizes: LP first, then A, B, C, etc.
      const unsortedPrizes = prizesByParentId[parentItem.item.id] ?? [];
      const sortedPrizes = sortPrizes(
        unsortedPrizes.map((p) => ({ ...p, letter: p.item.letter }))
      ).map((p) => {
        // Remove the temporary letter property we added for sorting
        const { letter: _, ...rest } = p;
        return rest as ShipmentItem;
      });
      return {
        parentItem,
        prizeItems: sortedPrizes,
      };
    });
  }, [shipment?.items]);

  if (!shipment) {
    return null;
  }

  const canReceive =
    shipment.status === "PENDING" || shipment.status === "IN_TRANSIT";
  const canDelete = shipment.status !== "DELIVERED";
  const canEdit =
    shipment.status !== "DELIVERED" && shipment.status !== "CANCELLED";

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
            <Can permission={Permission.SHIPMENTS_UPDATE}>
              {canEdit && (
                <Button variant="outline" size="sm" onClick={onEditClick}>
                  <Pencil className="h-4 w-4 mr-2" />
                  Edit
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
            <div className="px-4 py-2 border-b bg-muted/20 hidden sm:block">
              <div className="flex items-center gap-3 text-xs text-muted-foreground font-medium">
                <span className="flex-1">Name</span>
                <span className="text-right w-24">Qty</span>
                <span className="text-right w-20">Price</span>
                <span className="text-right w-24">Total</span>
              </div>
            </div>

            {/* Items List (grouped: Kuji block or standalone) */}
            <div className="px-4">
              {shipment.items.length === 0 ? (
                <div className="py-8 text-center text-muted-foreground">
                  <Package className="h-8 w-8 mx-auto mb-2 opacity-50" />
                  <p>No items in this shipment</p>
                </div>
              ) : (
                detailBlocks.map((block) =>
                  block.prizeItems.length > 0 ? (
                    <KujiBlockSection key={block.parentItem.id} block={block} />
                  ) : (
                    <ItemRow key={block.parentItem.id} item={block.parentItem} />
                  )
                )
              )}
            </div>

            {/* Totals */}
            {shipment.items.length > 0 && (
              <div className="px-4 py-3 border-t bg-muted/20">
                <div className="flex justify-between items-center text-sm">
                  <span className="text-muted-foreground">
                    {totalReceived} / {totalOrdered} units received
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
                <div className="p-4 grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
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
                  {shipment.totalCost != null && shipment.totalCost > 0 && (
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
                  <div>
                    <p className="text-muted-foreground text-xs mb-1">Tracking Number</p>
                    {isEditingTracking ? (
                      <div className="flex items-center gap-1.5">
                        <Input
                          value={trackingInput}
                          onChange={(e) => setTrackingInput(e.target.value)}
                          placeholder="Enter tracking number"
                          className="h-8 text-sm font-mono"
                          autoFocus
                          disabled={trackingSaving}
                          onKeyDown={(e) => {
                            if (e.key === "Enter") {
                              handleTrackingSave();
                            } else if (e.key === "Escape") {
                              setIsEditingTracking(false);
                            }
                          }}
                        />
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-8 w-8 shrink-0"
                          onClick={handleTrackingSave}
                          disabled={trackingSaving || !trackingInput.trim()}
                        >
                          {trackingSaving ? (
                            <Loader2 className="h-3.5 w-3.5 animate-spin" />
                          ) : (
                            <Check className="h-3.5 w-3.5" />
                          )}
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-8 w-8 shrink-0"
                          onClick={() => setIsEditingTracking(false)}
                          disabled={trackingSaving}
                        >
                          <X className="h-3.5 w-3.5" />
                        </Button>
                      </div>
                    ) : shipment.trackingId ? (
                      <div className="flex items-center gap-1.5">
                        <p className="font-mono font-medium">{shipment.trackingId}</p>
                        <Can permission={Permission.SHIPMENTS_UPDATE}>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-6 w-6"
                            onClick={() => {
                              setTrackingInput(shipment.trackingId ?? "");
                              setIsEditingTracking(true);
                            }}
                          >
                            <Pencil className="h-3 w-3" />
                          </Button>
                        </Can>
                      </div>
                    ) : (
                      <Can
                        permission={Permission.SHIPMENTS_UPDATE}
                        fallback={<p className="text-muted-foreground">-</p>}
                      >
                        <button
                          className="text-sm text-primary hover:underline"
                          onClick={() => {
                            setTrackingInput("");
                            setIsEditingTracking(true);
                          }}
                        >
                          + Add Tracking Number
                        </button>
                      </Can>
                    )}
                  </div>
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
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
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
