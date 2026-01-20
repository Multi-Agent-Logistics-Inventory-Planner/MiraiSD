"use client";

import { useMemo } from "react";
import { formatDistanceToNowStrict } from "date-fns";
import type { ProductCategory, Product } from "@/types/api";
import type { DashboardStats, StockLevelItem, StockStatus, CategoryData } from "@/types/dashboard";
import { useProducts } from "@/hooks/queries/use-products";
import { useInventoryTotals } from "@/hooks/queries/use-inventory-totals";

function getStatus(quantity: number, reorderPoint?: number): StockStatus {
  if (quantity <= 0) return "out-of-stock";
  if (!reorderPoint || reorderPoint <= 0) return "good";

  const criticalThreshold = Math.max(1, Math.floor(reorderPoint / 2));
  if (quantity <= criticalThreshold) return "critical";
  if (quantity <= reorderPoint) return "low";
  return "good";
}

function getCategoryLabel(category: ProductCategory): string {
  // Convert enum-ish values like "BLIND_BOX" -> "Blind Box"
  return category
    .toString()
    .toLowerCase()
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

function getChartFill(index: number): string {
  const slot = (index % 5) + 1;
  return `var(--chart-${slot})`;
}

function toRelativeTime(isoOrDateLike: string | undefined): string {
  if (!isoOrDateLike) return "Unknown";
  const d = new Date(isoOrDateLike);
  if (Number.isNaN(d.getTime())) return "Unknown";
  return `${formatDistanceToNowStrict(d)} ago`;
}

function buildStockLevelItem(
  product: Product,
  quantity: number,
  lastUpdatedAt: string | undefined
): StockLevelItem {
  const maxStock =
    product.targetStockLevel ??
    product.reorderPoint ??
    Math.max(quantity, 1);

  return {
    itemId: product.id,
    sku: product.sku,
    name: product.name,
    stock: quantity,
    maxStock: Math.max(1, maxStock),
    status: getStatus(quantity, product.reorderPoint),
    lastUpdated: toRelativeTime(lastUpdatedAt),
  };
}

export function useDashboardStats() {
  const productsQuery = useProducts();
  const totalsQuery = useInventoryTotals();

  const data: DashboardStats | null = useMemo(() => {
    const products = productsQuery.data;
    const totals = totalsQuery.data?.byItemId;
    if (!products || !totals) return null;

    const totalsByItemId = totals;

    let totalStockValue = 0;
    let lowStockItems = 0;
    let criticalItems = 0;
    let outOfStockItems = 0;

    const categoryQty = new Map<string, number>();

    const stockLevels: StockLevelItem[] = products.map((p) => {
      const total = totalsByItemId[p.id];
      const qty = total?.quantity ?? 0;

      const status = getStatus(qty, p.reorderPoint);
      if (status === "low") lowStockItems += 1;
      if (status === "critical") criticalItems += 1;
      if (status === "out-of-stock") outOfStockItems += 1;

      const unitCost = p.unitCost ?? 0;
      totalStockValue += qty * unitCost;

      const catLabel = getCategoryLabel(p.category);
      categoryQty.set(catLabel, (categoryQty.get(catLabel) ?? 0) + qty);

      return buildStockLevelItem(p, qty, total?.lastUpdatedAt);
    });

    // Sort: worst status first, then lowest stock
    const statusRank: Record<StockStatus, number> = {
      "out-of-stock": 0,
      critical: 1,
      low: 2,
      good: 3,
    };
    stockLevels.sort((a, b) => {
      const ra = statusRank[a.status];
      const rb = statusRank[b.status];
      if (ra !== rb) return ra - rb;
      return a.stock - b.stock;
    });

    const categoriesSorted = Array.from(categoryQty.entries())
      .filter(([, qty]) => qty > 0)
      .sort((a, b) => b[1] - a[1]);

    const categoryDistribution: CategoryData[] = categoriesSorted.map(
      ([name, value], idx) => ({
        name,
        value,
        fill: getChartFill(idx),
      })
    );

    return {
      totalStockValue,
      stockValueChange: 0,
      lowStockItems,
      criticalItems,
      outOfStockItems,
      totalSKUs: products.length,
      stockLevels,
      categoryDistribution,
    };
  }, [productsQuery.data, totalsQuery.data]);

  return {
    data,
    isLoading: productsQuery.isLoading || totalsQuery.isLoading,
    error: productsQuery.error ?? totalsQuery.error,
  };
}

