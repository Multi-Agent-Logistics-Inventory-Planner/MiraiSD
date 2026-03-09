"use client";

import Image from "next/image";
import { useState } from "react";
import {
  ImageOff,
  MapPin,
  Package,
  ArrowUpDown,
  RefreshCw,
  Pencil,
  Trophy,
} from "lucide-react";
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
  DataTableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { DeleteProductDialog } from "./delete-product-dialog";
import { KujiPrizesDialog } from "./kuji-prizes-dialog";
import { useProductInventoryEntries } from "@/hooks/queries/use-product-inventory-entries";
import { useDeleteProductMutation } from "@/hooks/mutations/use-product-mutations";
import { useShipmentsByProduct } from "@/hooks/queries/use-shipments-by-product";
import { usePermissions } from "@/hooks/use-permissions";
import type { ProductWithInventory } from "@/hooks/queries/use-product-inventory";
import {
  LocationType,
  LOCATION_TYPE_LABELS,
  SHIPMENT_STATUS_LABELS,
  SHIPMENT_STATUS_VARIANTS,
} from "@/types/api";
import { Card, CardContent } from "../ui/card";

interface ProductModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  product: ProductWithInventory | null;
  onAdjustClick?: () => void;
  onTransferClick?: () => void;
  onEditClick?: () => void;
}

export function ProductModal({
  open,
  onOpenChange,
  product,
  onAdjustClick,
  onTransferClick,
  onEditClick,
}: ProductModalProps) {
  const { data: inventoryData, isLoading: locationsLoading } =
    useProductInventoryEntries(product?.product.id);
  const locations = inventoryData?.entries;
  const { data: shipments, isLoading: shipmentsLoading } =
    useShipmentsByProduct(product?.product.id);
  const { isAdmin } = usePermissions();
  const deleteProduct = useDeleteProductMutation();
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [prizesDialogOpen, setPrizesDialogOpen] = useState(false);

  if (!product) {
    return null;
  }

  const handleDelete = () => {
    deleteProduct.mutate(
      { id: product.product.id },
      {
        onSuccess: () => {
          setDeleteDialogOpen(false);
          onOpenChange(false);
        },
      },
    );
  };

  const { product: p, totalQuantity } = product;

  // Check if this is a Kuji product (has children or is in Kuji category)
  const isKuji = p.hasChildren || p.category.name.toLowerCase() === "kuji" || p.category.slug?.toLowerCase() === "kuji";

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
    <>
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[calc(100%-2rem)] sm:max-w-2xl max-h-[90vh] overflow-y-auto overflow-hidden px-4! sm:px-6! dark:bg-background">
        <DialogHeader className="text-left min-w-0 overflow-hidden">
          <DialogTitle className="text-lg sm:text-xl font-semibold flex flex-col sm:flex-row sm:items-center gap-1 sm:gap-3 min-w-0">
            <span className="min-w-0 truncate" title={p.name}>
              {p.name}
            </span>
            {p.sku && (
              <span className="font-mono text-xs sm:text-sm font-normal text-muted-foreground shrink-0">
                {p.sku}
              </span>
            )}
          </DialogTitle>
        </DialogHeader>

        <div className="flex gap-4 sm:gap-6 mt-2 min-w-0">
          <div className="shrink-0">
            {p.imageUrl ? (
              <div className="relative h-24 w-24 sm:h-32 sm:w-32 overflow-hidden rounded-lg bg-muted">
                <Image
                  src={p.imageUrl}
                  alt={p.name}
                  fill
                  sizes="(max-width: 640px) 96px, 128px"
                  className="object-cover"
                />
              </div>
            ) : (
              <div className="flex h-24 w-24 sm:h-32 sm:w-32 items-center justify-center rounded-lg bg-muted">
                <ImageOff className="h-8 w-8 sm:h-10 sm:w-10 text-muted-foreground" />
              </div>
            )}
          </div>

          <div className="flex-1 min-w-0 space-y-1 sm:space-y-2 text-sm">
            <div className="flex items-center gap-1 sm:gap-2">
              <span className="text-muted-foreground text-xs sm:text-sm shrink-0">
                Category:
              </span>
              <Badge variant="secondary" className="text-xs shrink-0">
                {p.category.name}
              </Badge>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-muted-foreground text-xs sm:text-sm">
                Unit Cost:
              </span>
              <span className="text-xs sm:text-sm">
                {formatCurrency(p.unitCost)}
              </span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-muted-foreground text-xs sm:text-sm">
                Status:
              </span>
              <span className="text-xs sm:text-sm">
                {p.isActive ? "Active" : "Inactive"}
              </span>
            </div>

            <div className="hidden sm:flex items-center gap-2 pt-2">
              {isKuji && isAdmin && (
                <Button
                  size="sm"
                  className="bg-black text-white hover:bg-black/90"
                  onClick={() => setPrizesDialogOpen(true)}
                >
                  <Trophy className="h-4 w-4 mr-1" />
                  Prizes
                </Button>
              )}
              <Button
                size="sm"
                className="bg-black text-white hover:bg-black/90"
                onClick={() => onAdjustClick?.()}
              >
                <ArrowUpDown className="h-4 w-4 mr-1" />
                Adjust
              </Button>
              <Button
                size="sm"
                className="bg-black text-white hover:bg-black/90"
                onClick={() => onTransferClick?.()}
              >
                <RefreshCw className="h-4 w-4 mr-1" />
                Transfer
              </Button>
              {isAdmin && (
                <Button
                  size="sm"
                  className="bg-black text-white hover:bg-black/90"
                  onClick={() => onEditClick?.()}
                >
                  <Pencil className="h-4 w-4 mr-1" />
                  Edit
                </Button>
              )}
              {isAdmin && (
                <DeleteProductDialog
                  open={deleteDialogOpen}
                  onOpenChange={setDeleteDialogOpen}
                  productName={p.name}
                  cascadeMessage={
                    p.hasChildren && (p.children?.length ?? 0) > 0
                      ? ` and all ${p.children!.length} prize(s)`
                      : undefined
                  }
                  isPending={deleteProduct.isPending}
                  onDelete={handleDelete}
                />
              )}
            </div>
          </div>
        </div>

        <div className="flex sm:hidden justify-center gap-1.5 mt-2">
          {isKuji && isAdmin && (
            <Button
              size="sm"
              className="bg-black text-white hover:bg-black/90 h-8 px-2 text-xs"
              onClick={() => setPrizesDialogOpen(true)}
            >
              <Trophy className="h-3.5 w-3.5 mr-1" />
              Prizes
            </Button>
          )}
          <Button
            size="sm"
            className="bg-black text-white hover:bg-black/90 h-8 px-2 text-xs"
            onClick={() => onAdjustClick?.()}
          >
            <ArrowUpDown className="h-3.5 w-3.5 mr-1" />
            Adjust
          </Button>
          <Button
            size="sm"
            className="bg-black text-white hover:bg-black/90 h-8 px-2 text-xs"
            onClick={() => onTransferClick?.()}
          >
            <RefreshCw className="h-3.5 w-3.5 mr-1" />
            Transfer
          </Button>
          {isAdmin && (
            <Button
              size="sm"
              className="bg-black text-white hover:bg-black/90 h-8 px-2 text-xs"
              onClick={() => onEditClick?.()}
            >
              <Pencil className="h-3.5 w-3.5 mr-1" />
              Edit
            </Button>
          )}
          {isAdmin && (
            <DeleteProductDialog
              open={deleteDialogOpen}
              onOpenChange={setDeleteDialogOpen}
              productName={p.name}
              cascadeMessage={
                p.hasChildren && (p.children?.length ?? 0) > 0
                  ? ` and all ${p.children!.length} prize(s)`
                  : undefined
              }
              isPending={deleteProduct.isPending}
              onDelete={handleDelete}
            />
          )}
        </div>

        {/* Current Stock Section */}
        <div className="mt-4 sm:mt-6 min-w-0">
          <h3 className="text-sm sm:text-base font-medium text-primary mb-2 sm:mb-3">
            Current Stock{" "}
            <span className="text-foreground">
              ({totalQuantity.toLocaleString()})
            </span>
          </h3>
          <div className="rounded-lg overflow-x-auto">
            <Card className="p-2 border-none">
              <CardContent className="p-0">
                <Table>
                  <DataTableHeader>
                    <TableHead className="rounded-l-lg">Location</TableHead>
                    <TableHead>Type</TableHead>
                    <TableHead className="text-right rounded-r-lg">
                      Qty
                    </TableHead>
                  </DataTableHeader>
                  <TableBody>
                    {locationsLoading ? (
                      <>
                        {Array.from({ length: 3 }).map((_, i) => (
                          <TableRow key={i}>
                            <TableCell>
                              <Skeleton className="h-4 w-12" />
                            </TableCell>
                            <TableCell>
                              <Skeleton className="h-4 w-20" />
                            </TableCell>
                            <TableCell>
                              <Skeleton className="h-4 w-10 ml-auto" />
                            </TableCell>
                          </TableRow>
                        ))}
                      </>
                    ) : !locations || locations.length === 0 ? (
                      <TableRow>
                        <TableCell
                          colSpan={3}
                          className="h-16 text-center text-muted-foreground"
                        >
                          <div className="flex flex-col items-center gap-1 py-6">
                            <MapPin className="h-5 w-5 text-muted-foreground/50" />
                            <span>No inventory at any location</span>
                          </div>
                        </TableCell>
                      </TableRow>
                    ) : (
                      locations.map((entry) => {
                        const locationLabel =
                          entry.locationType === LocationType.NOT_ASSIGNED
                            ? "NA"
                            : entry.locationCode || "-";
                        return (
                          <TableRow key={entry.inventoryId}>
                            <TableCell className="font-mono rounded-l-lg">
                              {locationLabel}
                            </TableCell>
                            <TableCell className="text-muted-foreground">
                              {LOCATION_TYPE_LABELS[entry.locationType]}
                            </TableCell>
                            <TableCell className="text-right font-medium rounded-r-lg">
                              {entry.quantity.toLocaleString()}
                            </TableCell>
                          </TableRow>
                        );
                      })
                    )}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          </div>
        </div>

        {/* Recent Orders Section */}
        <div className="mt-4 sm:mt-6 min-w-0">
          <h3 className="text-sm sm:text-base font-medium text-primary mb-2 sm:mb-3">
            Recent Orders
          </h3>
          <div className="rounded-lg overflow-x-auto">
            <Card className="p-2 border-none">
              <CardContent className="p-0">
                <Table>
                  <DataTableHeader>
                    <TableHead className="hidden sm:table-cell rounded-l-lg">
                      Order
                    </TableHead>
                    <TableHead className="rounded-l-lg sm:rounded-none">
                      Date
                    </TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="text-right rounded-r-lg">
                      Qty
                    </TableHead>
                  </DataTableHeader>
                  <TableBody>
                    {shipmentsLoading ? (
                      <>
                        {Array.from({ length: 3 }).map((_, i) => (
                          <TableRow key={i}>
                            <TableCell className="hidden sm:table-cell">
                              <Skeleton className="h-4 w-20" />
                            </TableCell>
                            <TableCell>
                              <Skeleton className="h-4 w-20" />
                            </TableCell>
                            <TableCell>
                              <Skeleton className="h-5 w-16" />
                            </TableCell>
                            <TableCell>
                              <Skeleton className="h-4 w-12 ml-auto" />
                            </TableCell>
                          </TableRow>
                        ))}
                      </>
                    ) : !shipments || shipments.length === 0 ? (
                      <TableRow>
                        <TableCell
                          colSpan={3}
                          className="h-16 text-center text-muted-foreground sm:hidden"
                        >
                          <div className="flex flex-col items-center gap-1 py-6">
                            <Package className="h-5 w-5 text-muted-foreground/50" />
                            <span>No orders found</span>
                          </div>
                        </TableCell>
                        <TableCell
                          colSpan={4}
                          className="hidden sm:table-cell h-16 text-center text-muted-foreground"
                        >
                          <div className="flex flex-col items-center gap-1 py-6">
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
                            <TableCell className="hidden sm:table-cell font-mono rounded-l-lg">
                              {shipment.shipmentNumber}
                            </TableCell>
                            <TableCell className="text-muted-foreground rounded-l-lg sm:rounded-none">
                              {formatDate(shipment.orderDate)}
                            </TableCell>
                            <TableCell>
                              <Badge
                                variant={
                                  SHIPMENT_STATUS_VARIANTS[shipment.status]
                                }
                                className="text-xs"
                              >
                                {SHIPMENT_STATUS_LABELS[shipment.status]}
                              </Badge>
                            </TableCell>
                            <TableCell className="text-right font-medium rounded-r-lg">
                              {itemQty.toLocaleString()}
                            </TableCell>
                          </TableRow>
                        );
                      })
                    )}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          </div>
        </div>
      </DialogContent>
    </Dialog>

    {isKuji && (
      <KujiPrizesDialog
        open={prizesDialogOpen}
        onOpenChange={setPrizesDialogOpen}
        productId={p.id}
        productName={p.name}
        categoryId={p.category.id}
      />
    )}
    </>
  );
}
