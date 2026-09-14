import type { QueryFilters } from "@tanstack/react-query";

/**
 * A `setQueriesData`/`invalidateQueries` filter that matches only the legacy, unscoped
 * `["products"]`/`["products", {rootOnly}]` list query - never the site-scoped
 * `["products", siteId, "site"]` query (`SiteProduct[]`), and never a per-product entry key
 * like `["products", productId, "children"]`/`["products", productId, "with-children"]`
 * (useProductChildren/useProductWithChildren in hooks/queries/use-products.ts) or the bare
 * `["products", productId]` detail entry.
 *
 * Every realtime handler that surgically writes a freshly-fetched legacy `Product` into the
 * `["products"]` cache used to call `setQueriesData({queryKey: ["products"]})` with no further
 * filter - a default `exact: false` match that also caught every 3-element `["products", id, ...]`
 * key by prefix. A `queryKey[2] !== "site"` predicate alone excludes only the one sibling key
 * originally suspected, leaving `children`/`with-children` still matched -
 * `surgicalProductUpdate`'s `index === -1` branch would then append an unrelated product into
 * those per-product lists. Narrowed to `queryKey.length === 2`, following the precedent already
 * established in this codebase at use-realtime-broadcast.ts, which matches only the legacy list
 * shape (`["products"]` or `["products", {rootOnly}]`) and excludes every 3-element key,
 * site-scoped or per-product alike.
 */
export const legacyProductsListFilter: QueryFilters = {
  queryKey: ["products"],
  predicate: (query) => query.queryKey.length === 2,
};
