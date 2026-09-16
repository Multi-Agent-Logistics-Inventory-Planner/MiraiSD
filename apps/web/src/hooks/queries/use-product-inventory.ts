"use client";

import { useMemo } from "react";
import { useQuery, useQueryClient, skipToken } from "@tanstack/react-query";
import type { ProductListItem } from "@/types/api";
import type { StockStatus } from "@/types/dashboard";
import { useProducts } from "@/hooks/queries/use-products";
import { useSiteProducts } from "@/hooks/queries/use-site-products";
import { fetchSequencedInventoryTotals } from "@/hooks/realtime/inventory-refresh";
import type { SiteInventoryTotal } from "@/lib/api/site-inventory";

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

  // Split into a fetch-trigger query and a read-only cache mirror (follow-up review, fifth
  // round) - a single useQuery whose own queryFn write into ["inventoryTotals", siteId] could
  // never be fully protected from React Query's own unconditional, un-interceptable application
  // of that queryFn's return value, which happens some microtask hops after the function
  // returns on React Query's own schedule. No amount of internal delay/yielding closes that gap
  // (verified: a longer competing delay always defeats a shorter mitigating one). The only real
  // fix is to make sure nothing ever registers a queryFn for the real key at all, so every write
  // to it goes exclusively through the one atomic path (`commitFullTotals`, via
  // `fetchSequencedInventoryTotals` here or a targeted flush elsewhere) with nothing left to
  // race against.
  //
  // The trigger query drives the actual network fetch and lifecycle (mount, window focus,
  // staleTime, manual refetch) on a private key nothing reads for display; its own queryFn
  // return value gets written only into that throwaway key by React Query, which is harmless
  // since nothing consumes it. The real work - claiming sequence and atomically committing the
  // merged result - already happened synchronously inside fetchSequencedInventoryTotals before
  // it returned.
  const totalsFetchTrigger = useQuery({
    queryKey: ["inventoryTotalsFetchTrigger", siteId],
    queryFn: siteId ? () => fetchSequencedInventoryTotals(queryClient, siteId) : skipToken,
  });

  // The mirror: never fetches on its own (queryFn: skipToken), so React Query never
  // independently applies anything to this key - it only ever observes whatever `setQueryData`
  // writes here (from the trigger above, from a targeted flush, or from recovery), and re-renders
  // reactively when any of those commit. This is the only reader of the real, shared cache key.
  const totalsQuery = useQuery<SiteInventoryTotal[]>({
    queryKey: ["inventoryTotals", siteId],
    queryFn: skipToken,
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
    // The mirror query never fetches (queryFn: skipToken), so its own isLoading/error are
    // always false/undefined regardless of whether data has arrived - loading/error state must
    // come from the trigger query, which is the one that actually fetches.
    isLoading: productsQuery.isLoading || siteProductsQuery.isLoading || totalsFetchTrigger.isLoading,
    error: productsQuery.error ?? siteProductsQuery.error ?? totalsFetchTrigger.error,
  };
}

