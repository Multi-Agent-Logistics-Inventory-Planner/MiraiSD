"use client";

import { useMemo } from "react";
import { useQuery, useQueryClient, skipToken } from "@tanstack/react-query";
import type { ProductListItem } from "@/types/api";
import type { StockStatus } from "@/types/dashboard";
import { useProducts } from "@/hooks/queries/use-products";
import { useSiteProducts } from "@/hooks/queries/use-site-products";
import { fetchSequencedInventoryTotals } from "@/hooks/realtime/inventory-refresh";

export interface ProductWithInventory {
  product: ProductListItem;
  /**
   * Present for both the legacy and the site-scoped view as of Phase 6 checkpoint 6d (restored
   * from the site-scoped totals route - see .specs/phase-6-inventory/spec.md AC-6). Was withheld
   * on the site-scoped view during Phase 5 (phase-5d spec.md AC-6b) before Phase 6 existed.
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

// --- Site-scoped view (phase-5d T-5, quantity restored in phase-6 T-6d-4) ------------------
// Joins the untouched legacy getProducts() (catalog/Kuji display fields - name, sku, category,
// imageUrl, kujiType, hasChildren, hasActiveBox, preferredSupplier*) with getSiteProducts (the
// *only* source for site_products-owned fields - isStocked, money/settings fields, version) and,
// as of 6d, getSiteInventoryTotals (the *only* source for quantity/status - AC-6). See
// .specs/phase-5d-catalog-v1-and-web/spec.md AC-6a: each field's source is fixed, never a
// fallback chain.

/**
 * Site-scoped product list for the Products page. Quantity/status come from the site-scoped
 * totals route (T-6d-4) - never falls back to the legacy, unscoped inventory totals.
 */
export function useSiteProductInventory(rootOnly = false) {
  const productsQuery = useProducts(rootOnly);
  const siteProductsQuery = useSiteProducts();
  const siteId = siteProductsQuery.siteId;
  const queryClient = useQueryClient();

  const totalsQuery = useQuery({
    queryKey: ["inventoryTotals", siteId],
    // Routes through the same request-issuance sequencing as the realtime/mutation flush paths
    // (follow-up review finding, P1) - this query's own lifecycle refetches (mount, window
    // focus, staleTime, a manual refetch) used to write via a bare fetch with no sequencing at
    // all, so they could silently overwrite a newer targeted flush's already-applied value.
    queryFn: siteId ? () => fetchSequencedInventoryTotals(queryClient, siteId) : skipToken,
  });

  const data: ProductWithInventory[] | null = useMemo(() => {
    const products = productsQuery.data;
    const siteProducts = siteProductsQuery.data;
    // Wait for the totals query's own data too - not just products/siteProducts - otherwise
    // every row would fabricate `totalQuantity: 0`/`status: "out-of-stock"` while totals are
    // still in flight (review finding 5). The Products page's own loading skeleton happened to
    // mask this, but location-detail-sheet.tsx's embedded ProductModal has no such gate around
    // this hook and would flash a false out-of-stock state. `siteId` gates the totals query
    // itself (skipToken until resolved), so once siteId is known we must also wait for its data.
    if (!products || !siteProducts || (siteId && totalsQuery.data === undefined)) return null;

    const bySiteProductId = new Map(siteProducts.map((sp) => [sp.productId, sp]));
    const totalsByProductId = new Map((totalsQuery.data ?? []).map((t) => [t.productId, t]));

    return products.map((p) => {
      const sp = bySiteProductId.get(p.id);
      const total = totalsByProductId.get(p.id);
      const qty = total?.totalQuantity ?? 0;
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
        totalQuantity: qty,
        lastUpdatedAt: total?.lastUpdatedAt ?? p.updatedAt,
        status: getStatus(qty, sp?.reorderPoint),
      };
    });
  }, [productsQuery.data, siteProductsQuery.data, totalsQuery.data, siteId]);

  return {
    data,
    siteId,
    siteCode: siteProductsQuery.siteCode,
    isLoading: productsQuery.isLoading || siteProductsQuery.isLoading || totalsQuery.isLoading,
    error: productsQuery.error ?? siteProductsQuery.error ?? totalsQuery.error,
  };
}

