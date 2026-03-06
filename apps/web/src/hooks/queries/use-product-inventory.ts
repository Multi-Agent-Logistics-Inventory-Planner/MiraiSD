"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import type { Product } from "@/types/api";
import type { StockStatus } from "@/types/dashboard";
import { useProducts } from "@/hooks/queries/use-products";
import { getInventoryTotals } from "@/lib/api/inventory";

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

export function useProductInventory(rootOnly = false) {
  const productsQuery = useProducts(rootOnly);
  const totalsQuery = useQuery({
    queryKey: ["inventoryTotals"],
    queryFn: getInventoryTotals,
    staleTime: 30_000,
  });

  const data: ProductWithInventory[] | null = useMemo(() => {
    const products = productsQuery.data;
    const totals = totalsQuery.data;
    if (!products) return null;

    const totalsByItemId = new Map(
      (totals ?? []).map((t) => [t.itemId, t.totalQuantity])
    );

    return products.map((p) => {
      // Use actual inventory across all storage locations (parent's own stock)
      const qty = totalsByItemId.get(p.id) ?? 0;
      return {
        product: p,
        totalQuantity: qty,
        lastUpdatedAt: totals?.find((t) => t.itemId === p.id)?.lastUpdatedAt ?? p.updatedAt,
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

