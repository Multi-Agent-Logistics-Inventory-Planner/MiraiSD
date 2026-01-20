"use client";

import { useMemo } from "react";
import type { Product } from "@/types/api";
import type { StockStatus } from "@/types/dashboard";
import { useProducts } from "@/hooks/queries/use-products";
import { useInventoryTotals } from "@/hooks/queries/use-inventory-totals";

export interface ProductWithInventory {
  product: Product;
  totalQuantity: number;
  lastUpdatedAt?: string;
  status: StockStatus;
}

function getStatus(totalQuantity: number, reorderPoint?: number): StockStatus {
  if (totalQuantity <= 0) return "out-of-stock";
  if (!reorderPoint || reorderPoint <= 0) return "good";

  const criticalThreshold = Math.max(1, Math.floor(reorderPoint / 2));
  if (totalQuantity <= criticalThreshold) return "critical";
  if (totalQuantity <= reorderPoint) return "low";
  return "good";
}

export function useProductInventory() {
  const productsQuery = useProducts();
  const totalsQuery = useInventoryTotals();

  const data: ProductWithInventory[] | null = useMemo(() => {
    const products = productsQuery.data;
    const totals = totalsQuery.data?.byItemId;
    if (!products || !totals) return null;

    return products.map((p) => {
      const t = totals[p.id];
      const qty = t?.quantity ?? 0;
      return {
        product: p,
        totalQuantity: qty,
        lastUpdatedAt: t?.lastUpdatedAt,
        status: getStatus(qty, p.reorderPoint),
      };
    });
  }, [productsQuery.data, totalsQuery.data]);

  return {
    data,
    isLoading: productsQuery.isLoading || totalsQuery.isLoading,
    error: productsQuery.error ?? totalsQuery.error,
  };
}

