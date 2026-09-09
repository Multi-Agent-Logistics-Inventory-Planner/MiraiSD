"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import type { ProductListItem } from "@/types/api";
import type { StockStatus } from "@/types/dashboard";
import { useProducts } from "@/hooks/queries/use-products";
import { useSiteProducts } from "@/hooks/queries/use-site-products";
import { getInventoryTotals } from "@/lib/api/inventory";

export interface ProductWithInventory {
  product: ProductListItem;
  /**
   * Undefined when this row comes from the site-scoped view (useSiteProductInventory), which
   * withholds quantity until Phase 6 provides site-scoped inventory (phase-5d spec.md AC-6b) -
   * never present-but-zeroed. Always a number for the legacy, unscoped view.
   */
  totalQuantity?: number;
  lastUpdatedAt?: string;
  status?: StockStatus;
  /**
   * Site-scoped assortment flag (site_products.is_stocked) - only set on rows from
   * useSiteProductInventory. Per spec.md AC-6d, this is the only field eligibility decisions may
   * use; product.isActive means "has stock somewhere" org-wide and must not be used for it.
   */
  isStocked?: boolean;
  /** site_products row version, or null if the site has never carried this product; undefined
   * for the legacy (non-site) view. */
  siteProductVersion?: number | null;
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
    // No staleTime override: this fetches an unpaginated, whole-catalog
    // aggregate (InventoryTotalsRepository.findAllInventoryTotals). At the old
    // 30s staleTime, every remount of a component that calls this hook
    // (products page, location-detail-sheet) past 30s re-ran the full-table
    // query — the dominant contributor to Supabase pooler egress. Falls back
    // to the app-wide 5-minute default in lib/query-client.ts.
    // See refs/product-inventory-query-egress.md.
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

// --- Site-scoped view (phase-5d T-5) ---------------------------------------
// Joins the untouched legacy getProducts() (catalog/Kuji display fields - name, sku, category,
// imageUrl, kujiType, hasChildren, hasActiveBox, preferredSupplier*) with getSiteProducts (the
// *only* source for site_products-owned fields - isStocked, money/settings fields, version).
// See .specs/phase-5d-catalog-v1-and-web/spec.md AC-6a: each field's source is fixed, never a
// fallback chain. Quantity/lastUpdatedAt/status are intentionally left undefined (AC-6b).

/**
 * Site-scoped product list for the Products page. Unlike useProductInventory, this never falls
 * back to unscoped inventory totals - quantity/stock-status are withheld entirely until Phase 6.
 */
export function useSiteProductInventory(rootOnly = false) {
  const productsQuery = useProducts(rootOnly);
  const siteProductsQuery = useSiteProducts();

  const data: ProductWithInventory[] | null = useMemo(() => {
    const products = productsQuery.data;
    const siteProducts = siteProductsQuery.data;
    if (!products || !siteProducts) return null;

    const bySiteProductId = new Map(siteProducts.map((sp) => [sp.productId, sp]));

    return products.map((p) => {
      const sp = bySiteProductId.get(p.id);
      return {
        product: {
          ...p,
          unitCost: sp?.unitCost,
          msrp: sp?.msrp,
          reorderPoint: sp?.reorderPoint,
          targetStockLevel: sp?.targetStockLevel,
          leadTimeDays: sp?.leadTimeDays,
          forecastingEnabled: sp?.forecastingEnabled,
        },
        isStocked: sp?.isStocked ?? false,
        siteProductVersion: sp?.version ?? null,
      };
    });
  }, [productsQuery.data, siteProductsQuery.data]);

  return {
    data,
    siteCode: siteProductsQuery.siteCode,
    isLoading: productsQuery.isLoading || siteProductsQuery.isLoading,
    error: productsQuery.error ?? siteProductsQuery.error,
  };
}

