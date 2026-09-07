# Phase 5d — Catalog API v1 and web adoption

## Tier

Full — public contract additions, a new authorization surface (site-scoped product settings), and
a web migration on the app's busiest list (Products page).

## Problem and outcome

Catalog has no `/api/v1` surface yet, and `apps/web` reads products/categories/suppliers through
the legacy, site-blind wrapper. This record adds the global catalog v1 routes and the site-scoped
assortment/settings routes (per `multi-site-data-and-api.md` §7: global identity/catalog operations
stay outside the site route; per-site assortment and settings sit under
`/api/v1/sites/{siteId}/...`), then migrates the web Products list and category filter onto them,
following the pattern Track D already proved (new function alongside the untouched legacy one,
site-qualified TanStack Query key, query gated on `siteId` resolving).

This record is the phase's exit-gate proof: "one global product can be active/configured
independently at both sites" becomes a real, testable end-to-end path only once both the global and
site-scoped endpoints exist together.

## Durable context

- Plan: [Enterprise modernization](../../docs/plans/enterprise-modernization.md) §8 (Phase 5)
- Specs: [Multi-site data and API](../../docs/specs/multi-site-data-and-api.md) §7,
  [Client applications](../../docs/specs/client-applications.md) §4-5
- Depends on: [5a](../phase-5a-catalog-module-move/spec.md), [5b](../phase-5b-catalog-facade/spec.md),
  [5c](../phase-5c-site-products/spec.md)
- **Named inputs from siblings** (this record cannot be implemented without them): 5b's T-0
  association-reference inventory (which entity-reference strategy each aggregate uses), and 5c's
  T-4 field-by-field legacy/site compatibility table (which fields dual-write in which direction,
  and what a legacy edit does to an overridden vs. inherited field).
- Precedent: [Track D — web client adoption](../track-d-web-client-adoption/spec.md) (the read-only
  web-migration pattern this record reuses; also its scope-discipline precedent — narrow the diff to
  the smallest slice that reaches real UI, revert anything whose blast radius grows unexpectedly)

## Product decisions (recorded, not open)

- Global catalog endpoints get `/api/v1` but not `/sites/{siteId}` — the version prefix and the
  tenancy boundary are independent concerns, and only the second is inapplicable to global product
  identity.
- **Global v1 product routes are master-identity-only — they do NOT mirror legacy CRUD 1:1.** The
  legacy `/api/products` request DTO carries site-owned fields; accepting those on a global route
  would let a client write one site's pricing/assortment through an endpoint with no site context,
  silently defeating the whole phase. `CatalogProductRequest`/`CatalogProductResponse` are a
  **new, narrower** field set: `sku`, `name`, `description`, `categoryId`, `parentId`, `letter`,
  `templateQuantity`, `packsPerBox`, `kujiType`, `imageUrl`, `notes`. Explicitly rejected on the
  global routes (400, not silently ignored): `isActive`, `unitCost`, `msrp`, `reorderPoint`,
  `targetStockLevel`, `leadTimeDays`, `forecastingEnabled`, `quantity`, `initialStock`. Those are
  reachable only via `/api/v1/sites/{siteId}/products/{productId}/{assortment,settings}`.
- **New-product onboarding:** `POST /api/v1/catalog/products` creates the global identity and
  **no** `site_products` rows — a new product is carried nowhere until an explicit assortment
  mutation adds it to a site. This keeps creation consistent with 5c's "absent row = not stocked"
  rule rather than special-casing new products into every site. The legacy `POST /api/products`
  keeps its current MAIN-resolving behavior (including `initialStock`) until it is retired.
- Scope is narrowed to the product list/detail read path plus the two assortment/settings
  mutations, matching Track D's applied scope discipline. Kuji/lootbox product consumers and the
  supplier detail page stay on legacy for this record.
- The settings UI surfaces a note when a site-level `reorder_point`/`lead_time_days`/
  `target_stock_level` override is active, so "why does forecasting suggest X but this site shows Y"
  (the accepted cost of 5c's decoupling decision) isn't silently confusing.

## Acceptance criteria

- AC-1: `GET|POST /api/v1/catalog/products`, `GET|PUT|DELETE /api/v1/catalog/products/{id}`,
  `GET /api/v1/catalog/products/sku/{sku}`, and the `/api/v1/catalog/categories/**` and
  `/api/v1/catalog/suppliers/**` routes exist with distinct handler and operation names (not
  reassigning the legacy operation IDs), returning DTOs/records only — never the JPA entity.
  Categories and suppliers are wholly global, so those routes do mirror their legacy equivalents;
  **products do not** (see AC-1b).
- AC-1b: `POST`/`PUT /api/v1/catalog/products` accept only the master-identity field set and reject
  every site-owned field with a 400, proven by a test that posts each forbidden field
  (`isActive`, `unitCost`, `msrp`, `reorderPoint`, `targetStockLevel`, `leadTimeDays`,
  `forecastingEnabled`, `quantity`, `initialStock`) and asserts rejection — not silent ignoring,
  which would look like success while discarding the caller's intent. `POST` creates zero
  `site_products` rows, asserted directly against the table.
- AC-2: `GET /api/v1/sites/{siteId}/products`, `GET .../{productId}`,
  `PUT .../{productId}/assortment`, `PUT .../{productId}/settings` exist, gated by the existing
  `SiteAccessAuthorizationFilter`, reading `siteId` from `AuthorizedSiteContextHolder.require()`.
  A foreign-site `productId` returns 404. `Permission.COSTS_VIEW`/`MSRP_VIEW` nulling is verified by
  an explicit integration test on these new routes (not assumed from the legacy behavior).
  Per 5c's recorded semantics: `assortment` is an upsert (creates the row, first carrying the
  product at that site); `settings` requires an existing row (a retained row with
  `is_stocked = false` counts) and returns 404 otherwise rather than creating a partial one.
  `GET` behavior depends on **row presence**, not on `is_stocked`:
  - **absent row** → `isStocked = false` with global fallback values, not 404, so the UI can offer
    "add to this site";
  - **retained row with `is_stocked = false`** (de-assorted) → resolves that row's **saved
    overrides**, with global fallback only for fields left NULL. The global-fallback rule applies
    to absent rows only; applying it to retained rows would make a de-assorted product appear to
    lose its configuration.
- AC-2b (authorization, not just access): `SiteAccessAuthorizationFilter` establishes *which site*
  the caller may reach; it does **not** authorize mutations. Without an explicit gate, any
  EMPLOYEE with a membership would gain price and assortment editing simply by reaching these
  routes — a privilege escalation relative to the legacy routes, which gate on
  `hasAnyRole('ADMIN','ASSISTANT_MANAGER')`. Permission matrix for the new routes:
  | Route | Required |
  | --- | --- |
  | `GET .../products`, `GET .../products/{id}` | `PRODUCTS_VIEW` (money fields still subject to `COSTS_VIEW`/`MSRP_VIEW` nulling) |
  | `PUT .../{productId}/assortment` | `PRODUCTS_UPDATE` (ADMIN / ASSISTANT_MANAGER) |
  | `PUT .../{productId}/settings` | `PRODUCTS_UPDATE` (ADMIN / ASSISTANT_MANAGER) |
  Tested on the **mutation** endpoints specifically, not only the reads: EMPLOYEE with a valid
  membership is rejected (403); ASSISTANT_MANAGER and ADMIN succeed; a caller with no membership
  and a caller whose membership was revoked are rejected at the filter (404/403 per the existing
  Phase 4 convention) on both reads and mutations.
- AC-2c: "Foreign-site `productId` returns 404" is stated precisely, since product identity is
  global and a product is never foreign in itself: the 404 applies to the **`(siteId, productId)`
  assortment resource** — a settings mutation against a site that carries no row for that product.
  A `GET` of a globally-valid product at a site that does not carry it is **not** 404; it is
  `isStocked = false` with inherited values (AC-2). A `siteId` the caller cannot access is rejected
  by the filter before either rule applies.
- AC-3: Legacy `/api/products`, `/api/categories`, `/api/suppliers` gain `Deprecation`, `Sunset`
  (where known), and `Link` headers pointing at migration docs; behavior is otherwise byte-identical
  to today.
- AC-4: `packages/contracts/openapi.json` and `packages/api-client/src/schema.d.ts` are regenerated
  in the same commit as every endpoint change; `oasdiff breaking --fail-on ERR` reports no breaking
  changes (purely additive).
- AC-5 (exit gate): an integration test proves **independent configuration**, not merely
  non-leakage. A single global product is carried at **both** sites with materially different
  settings, and each site's state is mutated independently:
  1. Create one product via `/api/v1/catalog/products` (zero assortment rows).
  2. Assortment-upsert it at MAIN and at SECOND — proving a product can be carried at both.
  3. Set **distinct** settings at each: MAIN `msrp = X`, `reorderPoint = A`; SECOND `msrp = Y`,
     `reorderPoint = B`, with `X != Y` and `A != B`. Assert each site's `GET` returns its own
     values.
  4. Update MAIN's settings again and assert SECOND's row is **byte-for-byte unchanged** (and the
     reverse) — the actual cross-site non-interference proof.
  5. Deactivate the product's assortment at SECOND only; assert MAIN still reports
     `isStocked = true` with its own settings intact.
  6. Assert `/api/v1/catalog/products/{id}` returns only global identity fields and none of the
     per-site values from either site.
  A test that configures MAIN and merely observes SECOND as unstocked does **not** satisfy this AC —
  that proves isolation of an unwritten row, which is also true of a broken implementation.
- AC-6: `apps/web/src/lib/api/products.ts` gains `getSiteProducts` alongside the untouched legacy
  `getProducts`; `siteId` sourced from the existing `useCurrentSite()`; the TanStack Query key
  becomes `["products", siteId, opts]`; the query stays disabled until `siteId` resolves. Every
  `invalidateQueries(["products"])` call site in `hooks/mutations/` is audited and updated to the
  new key shape in this same task. The Products page renders end-to-end against the new path
  (manual or Playwright smoke, per Track D's AC-7 precedent). Kuji Active/Closed tab behavior,
  the `excludeCustomKuji` filter, and cost/MSRP nulling in the UI are unchanged.
- AC-6b (the page must not mix scoped and unscoped data): migrating the product query alone is
  **not sufficient**. `hooks/queries/use-product-inventory.ts` separately fetches
  `/api/inventory/totals` under the unscoped key `["inventoryTotals"]` and joins by product ID, and
  the page derives displayed quantity and stock status from it. Switching sites would therefore
  keep showing MAIN-wide (site-blind) quantities next to correctly-scoped product rows — worse than
  not migrating, because it looks right. One of two dispositions must be chosen and recorded, not
  left implicit:
  - **(a) Withhold (default for Phase 5):** the site-scoped Products view does not render
    quantity/stock-status at all until Phase 6 provides site-scoped inventory. The columns are
    removed from the v1 path (not silently zeroed), with a visible "available after inventory
    migration" affordance.
  - **(b) Scoped totals read:** only if Phase 6's site-scoped totals endpoint is available in time;
    then `["inventoryTotals", siteId]` and the join both become site-qualified.
  Option (a) is assumed unless 5c/Phase 6 sequencing changes; it keeps this record inside its
  stated "no inventory work" boundary.
- AC-6c: The web acceptance test switches sites and verifies the **whole page state**, not just
  that the list request carried a `siteId`: rendered quantities and stock status (or their
  documented absence under AC-6b), product detail state, and which mutations are offered/enabled
  for the current role. A test asserting only the outgoing request URL would pass against the
  mixed-data bug above.
- AC-6d: No Phase 5 site view uses `products.is_active` as assortment eligibility — that column
  still means "has stock somewhere" (see 5b's recorded hazard). Site assortment eligibility comes
  only from `site_products.is_stocked`.
- AC-7: `useCategories` migrates to `/api/v1/catalog/categories` (global, no `siteId`); the products
  page category filter, `manage-categories-dialog`, and subcategory grouping in
  `product-sort-utils` are unchanged.

## Tasks

- T-1: `CatalogProductController`/`CatalogCategoryController`/`CatalogSupplierController` (global
  v1 routes) + the narrowed `CatalogProductRequest`/`CatalogProductResponse` master-only field set,
  the AC-1b forbidden-field rejection tests, and the "creates no assortment rows" test + contract
  regeneration.
- T-2: **Review checkpoint (new authorization surface).** `SiteProductController` (assortment +
  settings routes), AC-2b's full permission matrix with mutation-endpoint role tests, AC-2c's
  precise 404 semantics, cost-visibility IT, contract regeneration.
- T-3: Deprecation/Sunset/Link headers on legacy catalog routes.
- T-4: Exit-gate integration test (AC-5).
- T-5: **Review checkpoint (busiest list; query-key invalidation audit).** Web Products list
  migration: `getSiteProducts`, query-key change, `invalidateQueries` audit, AC-6b's chosen
  inventory-totals disposition (withhold vs. scoped read — recorded explicitly in `log.md`), and
  AC-6c's site-switch test covering quantities, status, detail state and offered mutations.
- T-6: Web categories migration (`useCategories` → v1).
