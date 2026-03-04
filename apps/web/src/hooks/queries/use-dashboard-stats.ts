"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { formatDistanceToNowStrict } from "date-fns";
import type { Product } from "@/types/api";
import type { DashboardStats, StockLevelItem, StockStatus, CategoryData } from "@/types/dashboard";
import { useProducts } from "@/hooks/queries/use-products";
import { getAuditLogLast30Days } from "@/lib/api/dashboard";

function getStatus(quantity: number, reorderPoint?: number): StockStatus {
  if (quantity <= 0) return "out-of-stock";
  if (!reorderPoint || reorderPoint <= 0) return "good";

  const criticalThreshold = Math.max(1, Math.floor(reorderPoint / 2));
  if (quantity <= criticalThreshold) return "critical";
  if (quantity <= reorderPoint) return "low";
  return "good";
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
  lastUpdatedAt: string | undefined,
  daysToStockout?: number
): StockLevelItem {
  const maxStock =
    product.targetStockLevel ??
    product.reorderPoint ??
    Math.max(quantity, 1);

  return {
    itemId: product.id,
    sku: product.sku ?? undefined,
    name: product.name,
    stock: quantity,
    maxStock: Math.max(1, maxStock),
    status: getStatus(quantity, product.reorderPoint),
    lastUpdated: toRelativeTime(lastUpdatedAt),
    daysToStockout,
  };
}

interface UseDashboardStatsOptions {
  forecastByItemId?: Record<string, number>;
}

export function useDashboardStats(options: UseDashboardStatsOptions = {}) {
  const { forecastByItemId } = options;
  const productsQuery = useProducts();
  const auditLogQuery = useQuery({
    queryKey: ["audit-log", "last-30-days"],
    queryFn: getAuditLogLast30Days,
    staleTime: 5 * 60 * 1000,
  });

  const data: DashboardStats | null = useMemo(() => {
    const products = productsQuery.data;
    if (!products) return null;

    let totalStockValue = 0;
    let lowStockItems = 0;
    let criticalItems = 0;
    let outOfStockItems = 0;

    const categoryQty = new Map<string, number>();
    const unitCostByItemId = new Map<string, number>();

    const lastMovementByItemId = new Map<string, string>();
    for (const entry of auditLogQuery.data ?? []) {
      const existing = lastMovementByItemId.get(entry.itemId);
      if (!existing || entry.at > existing) {
        lastMovementByItemId.set(entry.itemId, entry.at);
      }
    }

    const stockLevels: StockLevelItem[] = products.map((p) => {
      const qty = p.quantity ?? 0;

      const status = getStatus(qty, p.reorderPoint);
      const reorderPoint = p.reorderPoint ?? 0;
      const approachingThreshold = reorderPoint > 0 ? Math.floor(reorderPoint * 1.5) : 0;
      const isApproaching =
        status === "good" &&
        qty > 0 &&
        approachingThreshold > 0 &&
        qty <= approachingThreshold;
      if (status === "low" || isApproaching) lowStockItems += 1;
      if (status === "critical") criticalItems += 1;
      if (status === "out-of-stock") outOfStockItems += 1;

      const unitCost = p.unitCost ?? 0;
      totalStockValue += qty * unitCost;
      unitCostByItemId.set(p.id, unitCost);

      const catLabel = p.category.name;
      categoryQty.set(catLabel, (categoryQty.get(catLabel) ?? 0) + qty);

      return buildStockLevelItem(
        p,
        qty,
        lastMovementByItemId.get(p.id) ?? p.updatedAt,
        forecastByItemId?.[p.id]
      );
    });

    // Compute stockValueChange from 30-day audit log
    let stockValueChange: number | null = null;
    if (auditLogQuery.data && auditLogQuery.data.length > 0) {
      let netValueChange = 0;
      for (const entry of auditLogQuery.data) {
        const unitCost = unitCostByItemId.get(entry.itemId) ?? 0;
        netValueChange += entry.quantityChange * unitCost;
      }
      const previousValue = totalStockValue - netValueChange;
      if (previousValue !== 0) {
        stockValueChange = Math.round((netValueChange / Math.abs(previousValue)) * 1000) / 10;
      }
    }

    const categoriesSorted = Array.from(categoryQty.entries())
      .filter(([, qty]) => qty > 0)
      .sort((a, b) => b[1] - a[1]);

    const categoryTotal = categoriesSorted.reduce((sum, [, qty]) => sum + qty, 0);
    const smallCategoryThreshold = categoryTotal * 0.05;
    const significantCategories = categoriesSorted.filter(
      ([, qty]) => qty >= smallCategoryThreshold
    );
    const otherTotal = categoriesSorted
      .filter(([, qty]) => qty < smallCategoryThreshold)
      .reduce((sum, [, qty]) => sum + qty, 0);
    const groupedCategories: Array<[string, number]> =
      otherTotal > 0
        ? [...significantCategories, ["Other", otherTotal]]
        : significantCategories;

    const categoryDistribution: CategoryData[] = groupedCategories.map(
      ([name, value], idx) => ({
        name,
        value,
        fill: getChartFill(idx),
      })
    );

    return {
      totalStockValue,
      stockValueChange,
      lowStockItems,
      criticalItems,
      outOfStockItems,
      totalSKUs: products.length,
      stockLevels,
      categoryDistribution,
    };
  }, [productsQuery.data, auditLogQuery.data, forecastByItemId]);

  return {
    data,
    isLoading: productsQuery.isLoading || auditLogQuery.isLoading,
    error: productsQuery.error ?? auditLogQuery.error,
  };
}
