"use client";

import { useQuery } from "@tanstack/react-query";
import {
  getProducts,
  getProductWithChildren,
  getProductChildren,
  getProductById,
  type GetProductsOptions,
} from "@/lib/api/products";
import { ApiClientError } from "@/lib/api/client";
import { KujiType, type ProductListItem } from "@/types/api";

/**
 * Applies rootOnly/kujiOnly/excludeCustomKuji client-side against the one unfiltered fetch every
 * useProducts caller now shares (.specs/web-product-list-cache/spec.md AC-1). Mirrors each
 * server-side filter's exact semantics so callers see the same rows and order they did when the
 * server applied the filter:
 * - rootOnly: the server's dedicated root-products query (ProductRepository.findRootAsListItems
 *   et al.) is ORDER BY p.name; the unfiltered fetch is not, so the root subset is explicitly
 *   re-sorted by name here to match.
 * - kujiOnly: root products that have at least one child (hasChildren), the same predicate
 *   ProductController.getAllProducts's rootOnly+kujiOnly branch used server-side.
 * - excludeCustomKuji: applied last, as a plain filter with no re-sort - matching the server,
 *   which filters the controller's assembled list without touching its order.
 */
function applyProductListFilters(
  products: ProductListItem[],
  opts: GetProductsOptions
): ProductListItem[] {
  let result = products;
  if (opts.rootOnly) {
    result = result
      .filter((p) => !p.parentId)
      .sort((a, b) => a.name.localeCompare(b.name));
    if (opts.kujiOnly) {
      result = result.filter((p) => p.hasChildren === true);
    }
  }
  if (opts.excludeCustomKuji) {
    result = result.filter((p) => p.kujiType !== KujiType.CUSTOM);
  }
  return result;
}

/**
 * Legacy (unscoped) product list - catalog/Kuji display fields only, not site-owned data. Always
 * fetches the single unfiltered ["products", {}] cache entry - rootOnly/kujiOnly/excludeCustomKuji
 * are applied via `select`, not separate network calls, so three differently-optioned callers
 * mounting together issue one fetch instead of three (.specs/web-product-list-cache/spec.md AC-1).
 * The realtime patcher (use-realtime-broadcast.ts) targets this same shared key and keeps working
 * unchanged: with an always-empty second key element, its own matchesFilter({}) check accepts
 * every product, which is correct now that the cached list itself is always the unfiltered one.
 */
export function useProducts(
  rootOnlyOrOptions: boolean | GetProductsOptions = false,
  queryOptions?: { enabled?: boolean }
) {
  const opts: GetProductsOptions =
    typeof rootOnlyOrOptions === "boolean"
      ? { rootOnly: rootOnlyOrOptions }
      : rootOnlyOrOptions ?? {};

  return useQuery({
    queryKey: ["products", {}],
    queryFn: () => getProducts({}),
    enabled: queryOptions?.enabled ?? true,
    select: (data) => applyProductListFilters(data, opts),
  });
}

export function useProductWithChildren(productId: string | null) {
  return useQuery({
    queryKey: ["products", productId, "with-children"],
    queryFn: () => getProductWithChildren(productId!),
    enabled: !!productId,
    retry: (_, error) =>
      !(error instanceof ApiClientError && error.status === 404),
  });
}

export function useProductChildren(productId: string | null) {
  return useQuery({
    queryKey: ["products", productId, "children"],
    queryFn: () => getProductChildren(productId!),
    enabled: !!productId,
  });
}

export function useProduct(productId: string | null) {
  return useQuery({
    queryKey: ["products", productId],
    queryFn: () => getProductById(productId!),
    enabled: !!productId,
  });
}
