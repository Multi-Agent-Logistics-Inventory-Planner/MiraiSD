"use client";

import { useMemo } from "react";
import type { Product } from "@/types/api";
import type { StockStatus } from "@/types/dashboard";
import { useProducts } from "@/hooks/queries/use-products";

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

  const data: ProductWithInventory[] | null = useMemo(() => {
    const products = productsQuery.data;
    if (!products) return null;

    return products.map((p) => {
      // For parent products with children, use totalChildStock
      const qty = p.hasChildren ? (p.totalChildStock ?? 0) : (p.quantity ?? 0);
      return {
        product: p,
        totalQuantity: qty,
        lastUpdatedAt: p.updatedAt,
        status: getStatus(qty, p.reorderPoint),
      };
    });
  }, [productsQuery.data]);

  return {
    data,
    isLoading: productsQuery.isLoading,
    error: productsQuery.error,
  };
}

