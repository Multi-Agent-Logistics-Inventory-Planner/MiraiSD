"use client";

import Image from "next/image";
import { ImageOff, MapPin, Package, ArrowUpDown, RefreshCw, Pencil } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
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
import { useInventoryByItemId } from "@/hooks/queries/use-inventory-by-item";
import { useShipmentsByProduct } from "@/hooks/queries/use-shipments-by-product";
import type { ProductWithInventory } from "@/hooks/queries/use-product-inventory";
import type { ShipmentStatus } from "@/types/api";
import {
  LocationType,
  PRODUCT_CATEGORY_LABELS,
  PRODUCT_SUBCATEGORY_LABELS,
} from "@/types/api";

interface ProductDetailSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  product: ProductWithInventory | null;
  onAdjustClick?: () => void;
  onTransferClick?: () => void;
  onEditClick?: () => void;
}

const LOCATION_TYPE_LABELS: Record<LocationType, string> = {
  BOX_BIN: "Box Bin",
  SINGLE_CLAW_MACHINE: "Single Claw",
  DOUBLE_CLAW_MACHINE: "Double Claw",
  KEYCHAIN_MACHINE: "Keychain",
  CABINET: "Cabinet",
  RACK: "Rack",
  NOT_ASSIGNED: "Not Assigned",
};

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

export function ProductDetailSheet({
  open,
  onOpenChange,
  product,
  onAdjustClick,
  onTransferClick,
  onEditClick,
}: ProductDetailSheetProps) {
  const { data: locations, isLoading: locationsLoading } = useInventoryByItemId(
    product?.product.id
  );
  const { data: shipments, isLoading: shipmentsLoading } = useShipmentsByProduct(
    product?.product.id
  );

  if (!product) {
    return null;
  }

  const { product: p, totalQuantity } = product;

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return "-";
    return new Date(dateStr).toLocaleDateString("en-US", {
      month: "2-digit",
      day: "2-digit",
      year: "numeric",
    });
  };

  const formatCurrency = (value?: number) =>
    value != null ? `$${value.toFixed(2)}` : "-";

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="text-xl font-semibold flex items-center gap-3">
            {p.name}
            <span className="font-mono text-sm font-normal text-muted-foreground">{p.sku}</span>
          </DialogTitle>
        </DialogHeader>

        {/* Product Image + Details */}
        <div className="flex gap-6 mt-2">
          {/* Image */}
          <div className="shrink-0">
            {p.imageUrl ? (
              <div className="relative h-32 w-32 overflow-hidden rounded-lg bg-muted">
                <Image
                  src={p.imageUrl}
                  alt={p.name}
                  fill
                  sizes="128px"
                  className="object-cover"
                />
              </div>
            ) : (
              <div className="flex h-32 w-32 items-center justify-center rounded-lg bg-muted">
                <ImageOff className="h-10 w-10 text-muted-foreground" />
              </div>
            )}
          </div>

          {/* Details */}
          <div className="flex-1 space-y-2 text-sm">
            <div className="flex items-center gap-2">
              <span className="text-muted-foreground">Category:</span>
              <Badge variant="secondary">{PRODUCT_CATEGORY_LABELS[p.category]}</Badge>
              {p.subcategory && (
                <Badge variant="outline">{PRODUCT_SUBCATEGORY_LABELS[p.subcategory]}</Badge>
              )}
            </div>
            <div className="flex items-center gap-2">
              <span className="text-muted-foreground">Unit Cost:</span>
              <span>{formatCurrency(p.unitCost)}</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-muted-foreground">Status:</span>
              <span>{p.isActive ? "Active" : "Inactive"}</span>
            </div>
            <div className="flex items-center gap-2 pt-2">
              <Button variant="outline" size="sm" onClick={() => onAdjustClick?.()}>
                <ArrowUpDown className="h-4 w-4 mr-1" />
                Adjust
              </Button>
              <Button variant="outline" size="sm" onClick={() => onTransferClick?.()}>
                <RefreshCw className="h-4 w-4 mr-1" />
                Transfer
              </Button>
              <Button variant="outline" size="sm" onClick={() => onEditClick?.()}>
                <Pencil className="h-4 w-4 mr-1" />
                Edit
              </Button>
            </div>
          </div>
        </div>

        {/* Current Qty Section */}
        <div className="mt-6">
          <h3 className="text-base font-medium text-primary mb-3">
            Current Qty{" "}
            <span className="text-foreground">({totalQuantity.toLocaleString()})</span>
          </h3>
          <div className="rounded-lg border overflow-hidden">
            <Table>
              <TableHeader className="bg-muted">
                <TableRow>
                  <TableHead>Location</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead className="text-right">Qty</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {locationsLoading ? (
                  <>
                    {Array.from({ length: 3 }).map((_, i) => (
                      <TableRow key={i}>
                        <TableCell><Skeleton className="h-4 w-12" /></TableCell>
                        <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                        <TableCell><Skeleton className="h-4 w-10 ml-auto" /></TableCell>
                      </TableRow>
                    ))}
                  </>
                ) : !locations || locations.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={3} className="h-16 text-center text-muted-foreground">
                      <div className="flex flex-col items-center gap-1">
                        <MapPin className="h-5 w-5 text-muted-foreground/50" />
                        <span>No inventory at any location</span>
                      </div>
                    </TableCell>
                  </TableRow>
                ) : (
                  locations.map((entry) => (
                    <TableRow key={entry.inventoryId}>
                      <TableCell className="font-mono">{entry.locationCode}</TableCell>
                      <TableCell className="text-muted-foreground">
                        {LOCATION_TYPE_LABELS[entry.locationType]}
                      </TableCell>
                      <TableCell className="text-right font-medium">
                        {entry.quantity.toLocaleString()}
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
        </div>

        {/* Recent Orders Section */}
        <div className="mt-6">
          <h3 className="text-base font-medium text-primary mb-3">Recent Orders</h3>
          <div className="rounded-lg border overflow-hidden">
            <Table>
              <TableHeader className="bg-muted">
                <TableRow>
                  <TableHead>Order</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Date</TableHead>
                  <TableHead className="text-right">Qty</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {shipmentsLoading ? (
                  <>
                    {Array.from({ length: 3 }).map((_, i) => (
                      <TableRow key={i}>
                        <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                        <TableCell><Skeleton className="h-5 w-16" /></TableCell>
                        <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                        <TableCell><Skeleton className="h-4 w-12 ml-auto" /></TableCell>
                      </TableRow>
                    ))}
                  </>
                ) : !shipments || shipments.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={4} className="h-16 text-center text-muted-foreground">
                      <div className="flex flex-col items-center gap-1">
                        <Package className="h-5 w-5 text-muted-foreground/50" />
                        <span>No orders found</span>
                      </div>
                    </TableCell>
                  </TableRow>
                ) : (
                  shipments.map((shipment) => {
                    const itemQty = shipment.items
                      .filter((item) => item.item.id === p.id)
                      .reduce((sum, item) => sum + item.orderedQuantity, 0);
                    return (
                      <TableRow key={shipment.id}>
                        <TableCell className="font-mono">{shipment.shipmentNumber}</TableCell>
                        <TableCell>
                          <Badge variant={STATUS_VARIANTS[shipment.status]} className="text-xs">
                            {STATUS_LABELS[shipment.status]}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-muted-foreground">
                          {formatDate(shipment.orderDate)}
                        </TableCell>
                        <TableCell className="text-right font-medium">
                          {itemQty.toLocaleString()}
                        </TableCell>
                      </TableRow>
                    );
                  })
                )}
              </TableBody>
            </Table>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
