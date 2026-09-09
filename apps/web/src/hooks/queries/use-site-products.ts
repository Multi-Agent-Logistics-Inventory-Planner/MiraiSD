"use client";

import { useQuery, skipToken } from "@tanstack/react-query";
import { getSiteProducts, type SiteProduct } from "@/lib/api/products";
import { useCurrentSite } from "@/hooks/queries/use-current-site";

/**
 * Fetch every product's effective, site-scoped view (assortment + settings) for the current
 * site. Per spec.md AC-6a, this is the *only* source for site_products-owned fields (isStocked,
 * money fields, version) - never joined with or overridden by legacy, unscoped data.
 */
export function useSiteProducts() {
  const { siteId, siteCode, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery<SiteProduct[]>({
    queryKey: ["products", siteId, "site"],
    // skipToken (rather than a `siteId!` assertion gated by `enabled`) keeps the query
    // disabled and its unresolved-siteId case visible to the type checker.
    queryFn: siteId ? () => getSiteProducts(siteId) : skipToken,
  });

  // A disabled query (query.isLoading === false in TanStack Query v5) would otherwise read as
  // "loaded, zero products" while /api/v1/me/sites is still resolving - see
  // use-storage-locations.ts for the identical precedent this mirrors.
  const unresolvedSiteError =
    !isSiteLoading && !siteId && !siteError
      ? new Error("No active site membership for the current user")
      : null;

  return {
    data: query.data,
    siteId,
    siteCode,
    isLoading: isSiteLoading || query.isLoading,
    error: siteError ?? unresolvedSiteError ?? query.error,
  };
}
