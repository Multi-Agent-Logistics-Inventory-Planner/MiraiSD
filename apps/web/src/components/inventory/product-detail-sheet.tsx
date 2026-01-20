"use client";

import { Badge } from "@/components/ui/badge";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { cn } from "@/lib/utils";
import type { ProductWithInventory } from "@/hooks/queries/use-product-inventory";
import type { StockStatus } from "@/types/dashboard";

interface ProductDetailSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  item: ProductWithInventory | null;
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

function field(label: string, value: React.ReactNode) {
  return (
    <div className="flex items-start justify-between gap-6 text-sm">
      <span className="text-muted-foreground">{label}</span>
      <span className="text-right">{value}</span>
    </div>
  );
}

export function ProductDetailSheet({
  open,
  onOpenChange,
  item,
}: ProductDetailSheetProps) {
  const p = item?.product;

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="sm:max-w-md">
        <SheetHeader>
          <SheetTitle>{p?.name ?? "Product"}</SheetTitle>
          <SheetDescription>
            {p?.sku ? `SKU ${p.sku}` : "Product details"}
          </SheetDescription>
        </SheetHeader>

        {!item || !p ? (
          <div className="p-4 text-sm text-muted-foreground">
            No product selected.
          </div>
        ) : (
          <div className="space-y-4 p-4">
            <div className="flex items-center justify-between">
              <div className="space-y-1">
                <div className="text-sm text-muted-foreground">Status</div>
                <Badge
                  variant="outline"
                  className={cn("text-xs", getStatusColor(item.status))}
                >
                  {formatStatus(item.status)}
                </Badge>
              </div>
              <div className="text-right">
                <div className="text-sm text-muted-foreground">Total Stock</div>
                <div className="text-2xl font-semibold">{item.totalQuantity}</div>
              </div>
            </div>

            <div className="space-y-3">
              {field("Category", p.category)}
              {field("Subcategory", p.subcategory ?? "—")}
              {field("Unit Cost", p.unitCost != null ? `$${p.unitCost.toFixed(2)}` : "—")}
              {field("Reorder Point", p.reorderPoint ?? "—")}
              {field("Target Stock", p.targetStockLevel ?? "—")}
              {field("Lead Time (days)", p.leadTimeDays ?? "—")}
              {field("Active", p.isActive ? "Yes" : "No")}
              {field("Last Updated", item.lastUpdatedAt ?? "—")}
            </div>

            {p.description ? (
              <div className="space-y-1">
                <div className="text-sm font-medium">Description</div>
                <p className="text-sm text-muted-foreground">{p.description}</p>
              </div>
            ) : null}

            {p.notes ? (
              <div className="space-y-1">
                <div className="text-sm font-medium">Notes</div>
                <p className="text-sm text-muted-foreground">{p.notes}</p>
              </div>
            ) : null}
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}

