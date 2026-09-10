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
- AC-6a (data-source split, since `SiteProductResponse`/`CatalogProductResponse` don't carry every
  field the page renders): the joined product row a T-5 hook produces draws each field from exactly
  one source, never a fallback chain between them:
  - **`site_products`-owned fields — `getSiteProducts` only, no legacy fallback ever:**
    `isStocked` (assortment eligibility, AC-6d), `unitCost`, `msrp`, `reorderPoint`,
    `targetStockLevel`, `leadTimeDays`, `forecastingEnabled`, `version`. A product the current site
    does not carry still renders (AC-2's absent-row/global-fallback rule resolves these server-side
    inside `SiteProductResponse` itself), so there is no case where the UI needs to reach past this
    endpoint for these fields.
  - **Global catalog identity — legacy `getProducts()` for now, since `CatalogProductResponse`
    already has these but the join needs `id` as the correlation key and the legacy shape is what
    every consuming component (`ProductTable`, `ProductModal`, category filter, sort utils) already
    expects:** `name`, `sku`, `imageUrl`, `category`, `kujiType`, `letter`, `templateQuantity`,
    `packsPerBox`, `parentId`, `updatedAt`. Swapping this half to `getCatalogProducts` is future
    work (tracked, not blocking T-5) once a component-level shape migration is scoped separately —
    T-5 does not touch `ProductListItem`'s shape, only which endpoints feed it.
  - **`hasChildren`, `preferredSupplierId/Name/Auto`, `kujiSlackWebhookUrl` — legacy `getProducts()`
    only,** because neither v1 endpoint exposes them at all today (not narrowed out by AC-1b's
    site-owned-field list — simply not yet built). Adding them is out of scope for a web-migration
    task per this record's precedent (Track D's scope discipline) and is tracked as backend
    follow-up work, not silently deferred.
  - **`hasActiveBox` — legacy `getProducts()`, and this is a known migration gap, not the intended
    end state:** `KujiBoxRepository`/the `kuji_boxes` table carry no `site_id` column yet (verified
    directly: `KujiOpenBoxAdapter.findProductIdsWithOpenBox` calls
    `KujiBoxRepository.findProductIdsWithStatus`, which has no site predicate). Kuji (and lootbox,
    the same shape of gap) have not yet been migrated to per-site state — that migration is Phase 7's
    scope, not this record's, and T-5 does not pull it forward. Until Phase 7, "has an open Kuji box"
    is unavoidably global/org-wide. **AC-6e** constrains how this gap may surface in a multi-site UI
    in the meantime (see below) — the Active/Closed Kuji tabs are not simply left showing global data
    unconditionally.
  - This split must be implemented as **two distinct queries joined client-side** (legacy
    `getProducts` for catalog/kuji display fields, `getSiteProducts` for site-scoped fields), not a
    single hook that silently prefers one source over the other per field — the source for each
    field is fixed, not negotiated at read time.
- Temporary exception (2026-09-10, user authorized): MAIN restores legacy counts
  without additional UI copy until Phase 6; all other sites still withhold them.
  See [temporary restoration](../temp-restore-legacy-inventory-counts/spec.md).
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
  mixed-data bug above. It must also cover AC-6a's join directly: a product carried (assorted) at
  site A but not at site B renders with A's `isStocked`/settings at A and B's own (or global
  fallback) values at B, while `name`/`category`/`imageUrl` stay identical across the switch —
  proving the two queries are actually joined by product ID and not accidentally rendering one
  site's site-scoped fields against the other site's catalog rows. Kuji-tab placement is *not*
  asserted identical across the switch — see AC-6e, which governs it separately at a non-MAIN site.
- AC-6d: No Phase 5 site view uses `products.is_active` as assortment eligibility — that column
  still means "has stock somewhere" (see 5b's recorded hazard). Site assortment eligibility comes
  only from `site_products.is_stocked`.
- AC-6e (Kuji/lootbox are not site-isolated yet — the UI must not imply they are): `hasActiveBox`
  and every other Kuji/lootbox fact this page reads stay backed by the legacy, global
  `getProducts()`/box-status data through this record (per AC-6a) because Phase 7, not this record,
  migrates kuji/lootbox to per-site state. Left unguarded, that global data would render inside a
  page whose surrounding chrome (a site-scoped product list, site-scoped settings) implies
  everything on screen belongs to the current site — at a non-MAIN site this would present another
  site's (or the org's undifferentiated) Kuji box activity as if it were this site's own. To prevent
  that misrepresentation before Phase 7 does the real migration:
  - The Custom Kuji tab and its Active/Closed split render normally at MAIN (today's only reachable
    site, `useCurrentSite`'s current hard-coded resolution) — behavior is unchanged from before this
    record for the one site anyone can actually view.
  - At any resolved site whose `siteCode !== "MAIN"`, the Custom Kuji tab renders a explicit
    "Kuji is not yet available per-site" unavailable state instead of the (globally-scoped, and
    therefore untrustworthy-per-site) tab content — never the real Kuji rows relabeled as if they
    belonged to that site.
  - This is a client-side display gate only, not a new authorization boundary and not a backend
    change — `hasActiveBox`/box data keep coming from the same global source either way; the gate
    only decides whether the page is allowed to *present* that data as this site's own.
  - Once Phase 7 gives kuji/lootbox real per-site state, this gate and its "not yet available"
    affordance are removed and the tab goes back to rendering unconditionally, now backed by
    genuinely site-scoped data.
  - Tested alongside the AC-6c site-switch test: a non-MAIN `siteCode` renders the unavailable state
    and does not fetch/display Kuji tab content; MAIN renders it exactly as before.
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
  migration: `getSiteProducts`, AC-6a's two-source client-side join (site-scoped fields from
  `getSiteProducts` only, catalog/Kuji display fields from legacy `getProducts()`, `hasActiveBox`
  documented as a Phase 7 migration gap, not this record's intended end state), query-key change,
  `invalidateQueries` audit, AC-6b's chosen inventory-totals disposition (withhold vs. scoped read —
  recorded explicitly in `log.md`), AC-6e's non-MAIN Kuji-tab unavailable gate (kuji/lootbox
  site-scoping itself stays out of scope, deferred to Phase 7 — T-5 only prevents the unmigrated
  global data from being presented as site data), and AC-6c's site-switch test covering quantities,
  status, detail state, offered mutations, the join itself (a product assorted at one site but not
  the other), and the AC-6e Kuji-tab gate.
- T-6: Web categories migration (`useCategories` → v1).
