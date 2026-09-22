# Catalog product list egress and webhook redaction

- Tier: Full
- Status: In progress
- Plan: `docs/plans/phase-4-6-closeout.md` (egress follow-up); see local plan
  `/Users/mjpark019/.claude/plans/yes-the-implementation-has-ancient-curry.md` for full context

## Problem

`refs/product-inventory-query-egress.md` traced Supabase pooler egress to
unbounded/heavy product list queries. The Phase 6 deploy fixed the largest
contributor (inventory totals). Three causes remain:

1. `SiteProductController.getSiteProducts` (v1, every Products page load) and
   `CatalogProductController` (v1 catalog list/search) call
   `productService.getAllProducts()` -> `ProductRepository.findAllWithCategories()`,
   a `JOIN FETCH` across category, parent category, and parent product, for
   endpoints that need 9 scalar columns and no joins.
2. Legacy `GET /api/products` runs an uncached `SELECT DISTINCT parent_id FROM
   products` on every call via `ProductService.withHasChildren` -- the most-called
   query in the database (44,185 calls since stats reset).
3. `ProductListItemDTO` / `ProductResponseDTO` return `kujiSlackWebhookUrl` -- a
   Slack incoming-webhook bearer credential -- to every authenticated caller.
   `CostVisibilityPolicy` redacts cost/MSRP but not this field. Production has 17
   products carrying 2 distinct live webhook URLs, exposed to 13 employee-role
   users on every list call.

The v1 catalog additionally cannot replace legacy `/api/products` yet: it lacks
`hasChildren`, `preferredSupplier*`, the full `category` object, and the webhook
field. This record fills those gaps (excluding `hasActiveBox`, deferred to
Phase 7's kuji slice since `kuji_boxes` has no `site_id` yet).

## Acceptance criteria

- AC-1: `kujiSlackWebhookUrl` is null for callers without `PRODUCTS_UPDATE` on
  every endpoint that serializes it (legacy list, legacy detail, v1 catalog).
- AC-2: `hasChildren` is computed in the same query as the product list (no
  separate parent-ID query) for both the legacy list and v1 catalog list.
- AC-3: `SiteProductController.getSiteProducts` resolves its product settings via one query
  with no joins (previously a JOIN FETCH across category/parent-category/parent-product). The
  endpoint's separate `site_products` assortment query is a distinct, always-necessary lookup
  against a different table -- not part of this claim, and not something this record removes.
- AC-4: `/api/v1/catalog/products` list/detail responses include `hasChildren`,
  `preferredSupplierId/Name/Auto`, and a full `category` object, in addition to
  existing fields. The contract change is additive only (oasdiff passes).
- AC-5: Every `ProductRepository` `*WithCategories` list method and
  `ProductService` wrapper with no remaining caller is deleted, and a caller-set
  test pins the exact remaining callers of full-entity hydration.
- AC-6: An egress integration test proves, for each of legacy `/api/products`, v1 catalog list,
  v1 catalog detail, and v1 site products: no JOIN FETCH across category/parent-category/
  parent-product remains, and every statement issued past the endpoint's own authentication
  preamble is accounted for and justified. That total is one statement for v1 catalog list; two
  for legacy `/api/products` (the slim list query plus the separate open-Kuji-box lookup that
  populates `hasActiveBox`) and for v1 site products (the slim settings query plus the separate
  `site_products` assortment query, which must have no `JOIN` in either statement).

## Non-goals

- Migrating web callers from legacy `/api/products` to v1 catalog.
- Adding `hasActiveBox` to v1 catalog (Phase 7 kuji slice).
- Pagination of either list endpoint.
- Removing `sku`/`name` from `SiteProductResponse`.
