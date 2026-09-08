# Phase 5c — site_products schema and assortment

## Tier

Full — schema migration applied to live production data (Flyway not yet canonical, so this is a
manual apply), plus a cross-service compatibility decision affecting forecasting-service.

## Problem and outcome

Products currently have no per-site assortment or settings concept: `Product.isActive`,
`unitCost`, `msrp`, `reorderPoint`, `targetStockLevel`, `leadTimeDays`, `forecastingEnabled` are all
global columns, so a product cannot be priced, reordered, or forecast differently at MAIN vs.
SECOND, and cannot be "carried" at one site but not the other. This record adds `site_products`
`(site_id, product_id)` and the `SiteAssortment`/`SiteProductService` facade that makes per-site
configuration real, using expand/backfill/verify (constrain is explicitly deferred — see below).

## Durable context

- Plan: [Enterprise modernization](../../docs/plans/enterprise-modernization.md) §8 (Phase 5)
- Specs: [Multi-site data and API](../../docs/specs/multi-site-data-and-api.md) §2 (global vs.
  site-owned data), §4 (`site_products` required), §5 (expand/backfill/verify/constrain)
- Depends on: [5a — catalog module move](../phase-5a-catalog-module-move/spec.md) (needs `Product`
  in `catalog.domain`)
- Can run in parallel with: [5b — catalog facade](../phase-5b-catalog-facade/spec.md) (independent
  once 5a lands; 5d needs both)
- Cross-service: `services/forecasting-service/src/adapters/supabase_repo.py` reads
  `products.reorder_point`/`lead_time_days`/`target_stock_level`/`is_active`/`forecasting_enabled`
  and writes `products.reorder_point` (`update_product_reorder_points`)

## Product decisions (recorded, confirmed with user 2026-09-05)

- **SECOND site assortment default: absent row = not stocked.** Backfill inserts one
  `site_products` row per product for MAIN only; SECOND starts with zero rows. A product is
  "carried" at a site only once an explicit row exists there. This is what makes the Phase 5 exit
  gate ("one global product independently configured at both sites") an observable fact rather than
  a backfilled no-op, and matches a real second-site-opening scenario where assortment is chosen,
  not inherited wholesale.
- **Forecasting stays decoupled from this migration.** `products.reorder_point`,
  `target_stock_level`, `lead_time_days` remain forecasting-service-owned (read and written exactly
  as today, no Python change). `site_products`' matching columns are a **manual override only**;
  effective value = `COALESCE(site_products.X, products.X)`. Rationale: repointing
  forecasting-service now would make this a three-deployable release for a phase whose actual goal
  is the catalog/assortment split, and per-site forecasting only becomes meaningful once Phase 6
  gives sites their own demand history — doing it earlier is cosmetic, not functionally correct.
  The override model is forward-compatible: `site_products.reorder_point` already means "this
  site's value" today, so the future forecasting cutover (Phase 6/7) only has to change which table
  it treats as authoritative, not add a new column. Known cost for the phase's lifetime: once a site
  sets an override, the nightly forecast job's write to the global column no longer reflects "the"
  reorder point for that site — surfaced as a UI note in the settings screen (5d), not a functional
  bug.
- Naming: `site_products.is_stocked`, not `is_active` — overloading `active` across both tables
  would make every future query ambiguous. Record this distinction in `CONTEXT.md` (T-7).
- **`products.is_active` does not currently mean what this record needs it to mean.**
  `StockMovementService:360` computes `shouldBeActive = total > 0` and writes it to
  `products.is_active` on every stock recalculation, so today the column effectively means "has
  stock somewhere", not "not retired from the master catalog". Phase 5 **does not change this**
  — kuji Active/Closed tabs and forecasting's `WHERE p.is_active = true` both depend on the current
  behavior. What this record does is (a) define the intended end-state semantics, (b) keep
  `is_stocked` strictly independent of it so no new code inherits the conflation, and (c) hand
  Phase 6 the split as explicit scope when `quantity` moves to `inventory`. Cross-referenced from
  [5b](../phase-5b-catalog-facade/spec.md), which pins the current derivation by test.
- **Read semantics turn on row presence, not on `is_stocked`** — these are two distinct states and
  must not be collapsed:
  - **Absent row** (SECOND's initial state for every product): *never carried here*. Resolves as
    `is_stocked = false` with **all** effective settings falling back to global `products` values
    (never null-and-broken). The product still appears in a site's catalog browse so it can be
    added, but is excluded from that site's assortment/stock views.
  - **Retained row with `is_stocked = false`** (de-assorted, overrides kept): resolves its **saved
    overrides**, not the global fallback. A site that previously set `msrp = Y` and then stopped
    carrying the product still reads `Y`, and can still edit it, so re-assorting restores the
    configuration rather than silently reverting to global values. Only fields that are NULL on the
    retained row fall back to global.
  `PUT .../assortment` is an **upsert** — it creates the row if absent, which is how a product is
  first carried at a site. `PUT .../settings` **requires an existing row** and returns 404 when no
  assortment row exists, so overrides can never be set on a product a site has never carried. A
  retained row with `is_stocked = false` satisfies this and stays editable (AC-4c, AC-6).

## Acceptance criteria

- AC-1 (expand): `site_products` exists — `id` PK, `site_id` FK to `sites` (`ON DELETE RESTRICT`),
  `product_id` FK to `products` (`ON DELETE CASCADE`), `UNIQUE(site_id, product_id)`, `is_stocked`
  and `forecasting_enabled` `NOT NULL DEFAULT`, nullable `unit_cost`/`msrp`/`reorder_point`/
  `target_stock_level`/`lead_time_days`, `version` for optimistic locking, `created_at`/
  `updated_at`, indexes leading with `site_id`. Verified against Testcontainers PostgreSQL.
- AC-2 (backfill): one row per product for MAIN with **every nullable override column left NULL**,
  not copied from `products`; zero rows for SECOND; migration fails loudly (does not silently insert
  zero rows) if no MAIN site exists, matching the `V53` precedent. Only the two NOT NULL columns
  are seeded (`is_stocked` from `products.is_active`, `forecasting_enabled` from
  `products.forecasting_enabled`).
  **Why NULL and not a copy:** with `COALESCE(site_products.X, products.X)`, a copied value is
  indistinguishable from a deliberate override — it wins permanently. Forecasting-service's nightly
  `update_product_reorder_points` writes `products.reorder_point`, so a copied backfill would mean
  MAIN silently stops seeing every forecast update from the moment of migration, for every product,
  with nobody having chosen an override. NULL preserves inheritance and keeps "override" meaning
  something a human actually did.
- AC-3 (verify): a permanent regression test (not a one-off script) asserts MAIN row count equals
  `products` count, zero orphaned `product_id`s, zero duplicate `(site_id, product_id)` pairs,
  SECOND row count is 0, all override columns are NULL after backfill, and — instead of
  column-for-column equality — **effective-value equality**: `COALESCE(site_products.X,
  products.X)` equals the pre-migration `products.X` for a sampled set, which is the property that
  actually matters (nothing observable changed).
- AC-3b (inheritance stays live): two behavior tests, not just a snapshot —
  (a) after backfill, changing `products.reorder_point` (simulating forecasting's nightly write)
  changes MAIN's effective value; (b) after an explicit override is set on `site_products`,
  changing `products.reorder_point` does **not** change MAIN's effective value. These two together
  are what prove the override semantics rather than a frozen copy.
- AC-4: `SiteProduct` entity, `SiteProductRepository`, `SiteAssortment` (read facade), and
  `SiteProductService` (sole writer) exist in `catalog`; every read method requires `siteId`; a
  foreign-site `(siteId, productId)` lookup returns empty (never load-then-check on IDs alone).
  `EffectiveProductSettings` implements the `COALESCE(site_products.X, products.X)` resolution in
  one place.
- AC-4b: Absent-row behavior is tested directly against the SECOND site (which has zero rows after
  backfill, so this is its normal state, not an edge case): reading a product for SECOND returns
  `isStocked = false` with global fallback values for every setting and no null-pointer/empty
  failure; `SiteAssortment.stockedProductIds(SECOND)` returns empty; an assortment upsert creates
  the row; a settings write against a product SECOND does not carry is rejected rather than
  silently creating a partial row.
- AC-4c: Retained-row (de-assorted) behavior is tested as a distinct case from absent-row: set an
  override at a site → de-assort (`is_stocked = false`, row retained) → read returns the **saved
  override**, not the global fallback → the override is still editable → re-assort restores the
  product with its configuration intact. This is the round trip that proves de-assorting is
  reversible without data loss.
- AC-5: Compatibility is defined and tested in **both** directions. The earlier draft specified only
  site-write → global-write, which would leave the legacy path (still used by the web's
  `useUpdateProductMutation` → `updateProduct` in `apps/web/src/lib/api/products.ts`) writing
  `products` alone: after an override exists, a legacy price edit returns 200 while the site view
  keeps showing the old value, with no error anywhere.
  - **Site-write → legacy-read:** `SiteProductService` dual-writes MAIN settings changes back to
    `products.*` in the same transaction as the `site_products` write, so forecasting-service and
    legacy readers keep working unchanged. Rollback test proves atomicity.
  - **Exception — clearing an override does NOT dual-write.** Setting a MAIN override to explicit
    `null` (AC-6) restores inheritance and must update `site_products` **only**, leaving
    `products.*` at its current value. Applying the dual-write rule literally here would clear the
    global fallback at the same moment the row starts depending on it, so MAIN would resolve to
    NULL/zero and every other site inheriting that field would be silently reset too. Test:
    set a MAIN override → change the global value (simulating a forecast write) → clear the MAIN
    override → assert MAIN now resolves to the *changed global* value, `products.*` is unmodified
    by the clear, and SECOND's inherited value is likewise unaffected by the clear.
  - **Legacy-write → site-read:** legacy `PUT /api/products/{id}` (and activation/deactivation)
    resolves to MAIN and writes **both** `products.*` and MAIN's `site_products` row in one
    transaction, for the fields MAIN currently overrides. Where MAIN has no override (NULL), the
    legacy write updates `products.*` only and inheritance carries it — no override is
    manufactured by an edit that did not ask for one.
  - **Legacy create:** `POST /api/products` keeps its MAIN-resolving behavior and creates a MAIN
    `site_products` row with NULL overrides. `is_stocked` is seeded from the **same derivation
    `ProductService` already uses for `isActive`** — `initialStock != null && initialStock > 0`
    (`ProductService:125`) — because `ProductRequestDTO` has no active flag; an earlier draft
    referred to "the request's active flag", which does not exist. Tested both ways: creation with
    `initialStock > 0` yields a MAIN row with `is_stocked = true`; creation with null/zero
    `initialStock` yields a MAIN row with `is_stocked = false` (the row is still created, so the
    product is addressable at MAIN and can be stocked later without a special case).
  - **Forecast-owned vs. manual:** forecasting-service's write to `products.reorder_point` is
    explicitly *not* a manual override and must never populate `site_products.reorder_point`. Only
    an explicit site settings mutation creates an override.
  - Tests: legacy-write → site-read and site-write → legacy-read round trips, for both the
    has-override and inherits (NULL) cases.
- AC-6: The settings write contract is fully specified, not just "optimistic locking exists":
  - **Staleness:** the request carries the row `version` (request body field, or `If-Match` with the
    version as ETag — pick one and apply it to every site mutation consistently). A stale version
    is rejected with `409 Conflict` and a body naming the current version; a missing version on a
    row that exists is also rejected rather than defaulting to last-write-wins.
  - **Clearing an override:** an explicit `null` for a field clears the override and restores
    inheritance from `products`. An **omitted** field leaves the current value untouched. These
    must be distinguishable on the wire, so the DTO uses a tri-state (e.g. `JsonNullable`) rather
    than a plain nullable field, and both cases are tested.
  - **Row-exists vs. currently-stocked:** these are different conditions and the earlier draft
    conflated them. A row retained with `is_stocked = false` (a product de-assorted but whose
    overrides are kept) **satisfies row-exists**. Settings mutations require the *row* to exist,
    not the product to be currently stocked — so de-assorting does not silently discard overrides,
    and re-assorting restores them.
  - Concurrency test: two concurrent settings updates on one `(site_id, product_id)` — one
    succeeds, one gets `409`, no lost update.
- AC-6b (shared-default policy, made explicit): because MAIN's dual-write updates `products.*`, and
  sites with NULL overrides inherit from `products`, a MAIN settings change **does** move SECOND's
  effective value for any field SECOND has not overridden. This is accepted for Phase 5 —
  `products.*` remains the org-wide default and MAIN is its de-facto editor while it is the only
  operating site — but it is a real limit on "independent configuration" and must be recorded, not
  discovered. Tested explicitly: with SECOND inheriting, a MAIN change moves SECOND's effective
  value; with SECOND overriding, it does not. Phase 6/7 revisits this when MAIN stops being
  special-cased (the correct end state is editing the global default explicitly, not as a
  side effect of editing MAIN).
- AC-7: The migration is applied to live Supabase manually (Flyway not canonical yet), after taking
  a fresh backup, with row counts recorded in `log.md` — matching the `V50`/`V52`-`V55` precedent.
- AC-8 (constrain — explicitly deferred): no column is dropped from `products` in this record;
  `products.{unit_cost,msrp,reorder_point,target_stock_level,lead_time_days,forecasting_enabled,
  is_active}` remain in place and continue to be written for MAIN via the dual-write in AC-5.

## Tasks

- T-1: `V56__create_site_products.sql` (expand) + `SiteProductRepositoryIT` covering the unique
  constraint, cascade/restrict behavior, and an optimistic-lock conflict.
- T-2: `V57__backfill_site_products_main.sql` (backfill, MAIN only, fail-loud-if-no-MAIN) +
  `SiteProductBackfillIT` (AC-3's verify checks as a permanent test).
- T-3: `SiteProduct` entity, `SiteProductRepository`, `SiteAssortment`, `SiteProductService` in
  `catalog`, with the foreign-site-returns-empty test and the AC-4b absent-row/upsert/settings-404
  tests.
- T-4: **Review checkpoint (bidirectional compatibility).** Implement the MAIN dual-write from
  `SiteProductService` into `products.*` **and** the legacy-path writes (`PUT /api/products/{id}`,
  activation/deactivation, `POST /api/products`) into MAIN's `site_products` row, per AC-5's
  field-by-field policy. Add the atomic-rollback test and both round-trip tests. The resulting
  field-by-field compatibility table is a named input to 5d.
- T-4b: Implement AC-6's settings contract — version/`If-Match` staleness rejection, tri-state
  null-clears-override vs. omitted-leaves-unchanged, `409` shape, and row-exists-vs-stocked
  semantics — plus AC-6b's inherited-vs-overridden shared-default tests.
- T-5: **Operational checkpoint — irreversible on production data.** Take a fresh Supabase backup;
  apply V56/V57 to live Supabase; record applied timestamp and row counts in `log.md`.
- T-6: Concurrency IT for AC-6.
- T-7: Add the `isActive` vs. `isStocked` vs. effective-availability distinction to `CONTEXT.md`.
