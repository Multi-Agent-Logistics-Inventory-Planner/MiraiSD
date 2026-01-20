"use client";

import { Pencil, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import type { ProductWithInventory } from "@/hooks/queries/use-product-inventory";
import type { StockStatus } from "@/types/dashboard";

interface ProductGridProps {
  items: ProductWithInventory[];
  isLoading?: boolean;
  onSelect: (item: ProductWithInventory) => void;
  onEdit: (item: ProductWithInventory) => void;
  onDelete: (item: ProductWithInventory) => void;
}

function getStatusColor(status: StockStatus) {
  switch (status) {
    case "good":
      return "bg-green-100 text-green-700";
    case "low":
      return "bg-amber-100 text-amber-700";
    case "critical":
      return "bg-red-100 text-red-700";
    case "out-of-stock":
      return "bg-gray-100 text-gray-700";
    default:
      return "bg-gray-100 text-gray-700";
  }
}

function formatStatus(status: StockStatus) {
  return status
    .split("-")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

export function ProductGrid({
  items,
  isLoading,
  onSelect,
  onEdit,
  onDelete,
}: ProductGridProps) {
  if (isLoading) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {Array.from({ length: 8 }).map((_, i) => (
          <Card key={i}>
            <CardHeader className="pb-2">
              <div className="flex items-start justify-between gap-3">
                <div className="space-y-2">
                  <Skeleton className="h-4 w-40" />
                  <Skeleton className="h-3 w-24" />
                </div>
                <Skeleton className="h-5 w-20" />
              </div>
            </CardHeader>
            <CardContent>
              <div className="space-y-2">
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-full" />
              </div>
              <div className="mt-4 flex gap-2">
                <Skeleton className="h-9 flex-1" />
                <Skeleton className="h-9 w-10" />
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    );
  }

  if (items.length === 0) {
    return <p className="text-sm text-muted-foreground">No products found.</p>;
  }

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      {items.map((row) => (
        <Card
          key={row.product.id}
          className="cursor-pointer"
          onClick={() => onSelect(row)}
        >
          <CardHeader className="pb-2">
            <div className="flex items-start justify-between gap-3">
              <div>
                <CardTitle className="text-base">{row.product.name}</CardTitle>
                <p className="font-mono text-sm text-muted-foreground">
                  {row.product.sku}
                </p>
              </div>
              <Badge
                variant="outline"
                className={cn("text-xs", getStatusColor(row.status))}
              >
                {formatStatus(row.status)}
              </Badge>
            </div>
          </CardHeader>
          <CardContent>
            <div className="grid gap-2 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Category</span>
                <span>{row.product.category}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Stock</span>
                <span>
                  {row.totalQuantity} / {row.product.targetStockLevel ?? "—"}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Unit Cost</span>
                <span>
                  {row.product.unitCost != null
                    ? `$${row.product.unitCost.toFixed(2)}`
                    : "—"}
                </span>
              </div>
            </div>
            <div className="mt-4 flex gap-2">
              <Button
                variant="outline"
                size="sm"
                className="flex-1 bg-transparent"
                onClick={(e) => {
                  e.stopPropagation();
                  onEdit(row);
                }}
              >
                <Pencil className="mr-2 h-3 w-3" />
                Edit
              </Button>
              <Button
                variant="outline"
                size="sm"
                className="text-destructive bg-transparent"
                onClick={(e) => {
                  e.stopPropagation();
                  onDelete(row);
                }}
                title="Delete"
              >
                <Trash2 className="h-3 w-3" />
              </Button>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

