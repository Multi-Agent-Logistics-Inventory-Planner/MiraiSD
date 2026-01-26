"use client";

import type { Product } from "@/types/api";
import type { StockStatus } from "@/types/dashboard";
import {
  PRODUCT_CATEGORY_LABELS,
  PRODUCT_SUBCATEGORY_LABELS,
} from "@/types/api";

interface ProductInfoSectionProps {
  product: Product;
  totalQuantity: number;
  status: StockStatus;
}

function InfoRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex justify-between py-2 border-b border-border last:border-b-0">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span className="text-sm font-medium text-right">{value}</span>
    </div>
  );
}

export function ProductInfoSection({
  product,
  totalQuantity,
  status,
}: ProductInfoSectionProps) {
  const formatCurrency = (value?: number) =>
    value != null ? `$${value.toFixed(2)}` : "-";

  const formatNumber = (value?: number) =>
    value != null ? value.toString() : "-";

  const statusColors: Record<StockStatus, string> = {
    good: "text-green-600 dark:text-green-400",
    low: "text-yellow-600 dark:text-yellow-400",
    critical: "text-orange-600 dark:text-orange-400",
    "out-of-stock": "text-red-600 dark:text-red-400",
  };

  const statusLabels: Record<StockStatus, string> = {
    good: "Good",
    low: "Low Stock",
    critical: "Critical",
    "out-of-stock": "Out of Stock",
  };

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-4">
        <div className="rounded-lg border bg-card p-4">
          <div className="text-sm text-muted-foreground">Total Stock</div>
          <div className="text-2xl font-bold">{totalQuantity}</div>
          <div className={`text-sm ${statusColors[status]}`}>
            {statusLabels[status]}
          </div>
        </div>
        <div className="rounded-lg border bg-card p-4">
          <div className="text-sm text-muted-foreground">Unit Cost</div>
          <div className="text-2xl font-bold">
            {formatCurrency(product.unitCost)}
          </div>
        </div>
      </div>

      <div className="rounded-lg border bg-card p-4 space-y-1">
        <InfoRow
          label="Category"
          value={PRODUCT_CATEGORY_LABELS[product.category]}
        />
        {product.subcategory && (
          <InfoRow
            label="Subcategory"
            value={PRODUCT_SUBCATEGORY_LABELS[product.subcategory]}
          />
        )}
        <InfoRow label="Reorder Point" value={formatNumber(product.reorderPoint)} />
        <InfoRow
          label="Target Stock Level"
          value={formatNumber(product.targetStockLevel)}
        />
        <InfoRow
          label="Lead Time (Days)"
          value={formatNumber(product.leadTimeDays)}
        />
        <InfoRow
          label="Status"
          value={product.isActive ? "Active" : "Inactive"}
        />
      </div>

      {product.description && (
        <div className="rounded-lg border bg-card p-4">
          <div className="text-sm font-medium mb-2">Description</div>
          <div className="text-sm text-muted-foreground">
            {product.description}
          </div>
        </div>
      )}

      {product.notes && (
        <div className="rounded-lg border bg-card p-4">
          <div className="text-sm font-medium mb-2">Notes</div>
          <div className="text-sm text-muted-foreground">{product.notes}</div>
        </div>
      )}

      <div className="text-xs text-muted-foreground">
        Last updated: {new Date(product.updatedAt).toLocaleString()}
      </div>
    </div>
  );
}
