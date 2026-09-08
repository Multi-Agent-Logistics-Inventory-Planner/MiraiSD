# Implementation log

## Assumptions and decisions

- **T-3 review fixes.** Two findings from T-3's review: (1) `SiteProductNotFoundException` was
  not registered in `GlobalExceptionHandler`, so it would 500 instead of 404 through MVC. (2)
  `SiteProductService.setStocked`'s new-row branch defaulted `forecastingEnabled` to the entity's
  bare `true` default rather than the product's global value, silently turning forecasting on for
  a product whose global `forecastingEnabled` is `false` the moment a site first carries it.
  - Fix (2) is straightforward: `newSiteProductSeededFromGlobal` seeds `forecastingEnabled` from
    the product's current global value, matching `EffectiveProductSettings.absent`'s fallback and
    V57's backfill semantics. Covered by `SiteAssortmentIT.firstCarryingAProductPreservesItsGlobalForecastingEnabledValue`.
  - Fix (1) initially added `SiteProductNotFoundException.class` to `GlobalExceptionHandler`'s
    `@ExceptionHandler` not-found list. That change made `ArchitectureTest.topLevelPackagesAreFreeOfCycles`
    fail: `catalog`, `services`, `exceptions`, and `identity` already form one mutually-cyclic
    legacy component (frozen/accepted architectural debt, e.g. `Slice catalog -> Slice services ->
    Slice exceptions -> Slice identity -> Slice catalog` is already in the frozen store), and
    adding one more class reference from `exceptions` into `catalog.domain` shifted which
    representative cycle ArchUnit's algorithm reports for that same pre-existing component -
    surfacing a *different*, not-yet-frozen 3-slice path (`catalog -> services -> exceptions ->
    catalog`) instead of the already-frozen 4-slice one. Regenerating the frozen store (the
    documented precedent - see the `fix: regenerate archunit` commit) did not converge even after
    repeated attempts with `allowStoreUpdate=true`: the newly-reported violation was never
    actually persisted into `archunit_store` across five consecutive runs, so this specific store
    entry does not reliably regenerate in this environment.
    **Resolution:** `SiteProductNotFoundException` now extends `ProductNotFoundException` (both in
    `catalog.domain`) instead of `RuntimeException` directly. `GlobalExceptionHandler` already maps
    `ProductNotFoundException` to 404, and nothing in the codebase catches `ProductNotFoundException`
    specifically (grepped - it is only ever thrown), so Spring's exception-hierarchy resolution
    gives the subtype the same 404 mapping with **zero new class reference** in the `exceptions`
    package - `GlobalExceptionHandler.java` is unchanged (byte-identical to its pre-review state).
    Verified end-to-end (not just by inspection): `SiteProductNotFoundExceptionMappingTest` drives
    real Spring MVC dispatch against a throwaway controller and asserts the 404 body; `Architecture
    Test` passes cleanly across 3 repeated runs with zero `archunit_store` diff.

- Flyway is not a dependency of this project and is not canonical for this schema (per the spec's
  Tier note and AC-7); `V56__create_site_products.sql` follows the existing `V50`-`V55` numbering
  purely as a naming/ordering convention and a record of what must be applied to Supabase by hand
  (T-5). No migration in this codebase is executed by an actual Flyway run today.
  `SiteProductRepositoryIT` follows the `UserSiteMembershipRepositoryIT` precedent: Hibernate
  `ddl-auto=create-drop` builds the shared test schema from the entity, not the migration SQL, so
  FK constraints (`ON DELETE RESTRICT`/`CASCADE`) are added by hand once in `@BeforeEach` to
  exercise the same constraints production has. Because that pattern never actually executes
  `V56`'s SQL, it cannot catch the migration file itself being broken or missing (flagged in
  review) - `SiteProductsMigrationIT` (added after review) closes that gap by executing the real
  file against a standalone Testcontainers Postgres with only the minimal prerequisite `sites`/
  `products` stub tables, and asserting AC-1's columns, nullability, defaults, indexes, unique
  constraint, and cascade/restrict behavior directly.
- `SiteProduct.siteId` and `productId` are plain `UUID` columns, not JPA relations — `siteId`
  because catalog/sites are separate modules (rule 8, no new cross-module JPA relations);
  `productId` for consistency and to keep this entity independent of `Product`'s lazy-loading
  graph, since every read is meant to go through `catalog.application.SiteAssortment` (T-3), not
  entity navigation.
- Test fixture `Site.code` is `varchar(20)`; used a base-36 encoding of `System.nanoTime()` rather
  than the decimal value used elsewhere in the codebase, since the decimal form overflowed 20
  chars once prefixed with a label.

## Task record

### T-1 — site_products schema (expand) + repository IT

- Changed:
  - Added `services/inventory-service/src/main/resources/db/migration/V56__create_site_products.sql`
    (AC-1: `id` PK, `site_id` FK `ON DELETE RESTRICT`, `product_id` FK `ON DELETE CASCADE`,
    `UNIQUE(site_id, product_id)`, `is_stocked`/`forecasting_enabled` `NOT NULL DEFAULT`, nullable
    override columns, `version`, timestamps, indexes leading with `site_id`).
  - Added `catalog.domain.SiteProduct` entity and `catalog.infrastructure.SiteProductRepository`
    (CRUD + `findBySiteIdAndProductId`, used by the foreign-site-returns-empty test; broader
    finder methods deferred to T-3's `SiteAssortment`).
- Tests:
  - `SiteProductRepositoryIT` (new) — duplicate `(site_id, product_id)` rejected, cascade delete
    on product removal, restrict delete on a site with existing site_products, optimistic lock
    conflict on concurrent update, foreign-site lookup returns empty. Runs against the shared
    Hibernate `ddl-auto=create-drop` schema with hand-added FKs (`UserSiteMembershipRepositoryIT`
    precedent) — proves entity/repository behavior, not that `V56` itself is correct.
  - `SiteProductsMigrationIT` (new, added after review) — executes the actual
    `V56__create_site_products.sql` file from the test classpath against a standalone
    Testcontainers PostgreSQL (no Spring context, minimal stub `sites`/`products` prerequisite
    tables), then asserts via `information_schema`/`pg_indexes` that AC-1's columns, nullability,
    and defaults are exactly as specified, the named indexes exist, and the unique constraint and
    cascade/restrict FK behavior hold when driven by plain JDBC against the migration-created
    schema itself.
- Result: pass. `./mvnw -q test -Dtest=SiteProductRepositoryIT,SiteProductsMigrationIT,ArchitectureTest`
  — all green against real PostgreSQL via Testcontainers. `./mvnw -q -DskipTests compile` — clean.

### T-2 — site_products backfill (MAIN only) + verify

- Changed:
  - Added `services/inventory-service/src/main/resources/db/migration/V57__backfill_site_products_main.sql`
    (AC-2): fails loudly (matching `V53`) if no `MAIN` site row exists; otherwise inserts one
    `site_products` row per existing product for `MAIN` only, seeding `is_stocked` directly from
    `products.is_active` and `forecasting_enabled` directly from `products.forecasting_enabled`
    (no `COALESCE` fallback — a NULL there is bad data that should fail the insert loudly, not be
    silently defaulted), and leaving every nullable override column NULL. `SECOND` gets zero rows.
    `ON CONFLICT (site_id, product_id) DO NOTHING` makes the migration re-runnable.
- Tests: `SiteProductBackfillIT` (new) — standalone Testcontainers Postgres (no Spring context),
  schema dropped/rebuilt per test via `@BeforeEach`, executing the real `V56` and `V57` files
  verbatim from the test classpath:
  - `failsLoudlyWhenNoMainSiteExists` — no `MAIN` row present, `V57` raises and zero rows land.
  - `backfillsExactlyOneRowPerProductForMainAndZeroRowsForSecond` — AC-2's row-count shape plus
    AC-3's verify checks (zero orphaned `product_id`s, zero duplicate `(site_id, product_id)`
    pairs, all override columns NULL).
  - `seedsIsStockedAndForecastingEnabledFromProductsBooleans` — all four boolean combinations of
    `is_active`/`forecasting_enabled` map to the matching `is_stocked`/`forecasting_enabled`.
  - `effectiveValueAfterBackfillEqualsThePreMigrationGlobalValueForEveryOverrideColumn` — AC-3's
    effective-value equality (`COALESCE(site_products.X, products.X)` equals the pre-migration
    `products.X`) for every override column, via the same `COALESCE` expression
    `EffectiveProductSettings` (T-3) will implement.
  - `inheritedEffectiveValueStaysLiveAfterBackfillWhenNoOverrideIsSet` — AC-3b(a): a global write
    to `products.reorder_point` after backfill (simulating forecasting's nightly write) still
    changes MAIN's effective value while no override exists.
  - `explicitOverrideStopsFollowingLaterGlobalWrites` — AC-3b(b): once a `site_products` override
    is set, a later global write to `products.reorder_point` no longer changes the effective value.
- Result: pass. `./mvnw -q test -Dtest=SiteProductBackfillIT` — 6/6 against real PostgreSQL via
  Testcontainers. `./mvnw -q -DskipTests compile` — clean.

### T-3 — SiteAssortment (read) + SiteProductService (write) + EffectiveProductSettings

- Changed:
  - Added `catalog.application.EffectiveProductSettings` (record): the single place
    `COALESCE(site_products.X, products.X)` is computed. `absent(siteId, product)` for no-row
    (`isStocked = false`, every setting from `product`); `from(siteProduct, product)` for an
    existing row (`isStocked`/`forecastingEnabled` read directly off the row — both are `NOT NULL`
    columns, never coalesced — each nullable override column falls back to `product` only when
    NULL on the row).
  - Added `catalog.application.SiteAssortment` (read facade, `@Transactional(readOnly = true)`):
    `effectiveSettingsFor`, `isStockedAt`, `stockedProductIds`. Every method takes `siteId` and
    resolves through `SiteProductRepository.findBySiteIdAndProductId` — never a bare-id lookup
    followed by an in-memory site check.
  - Added `catalog.application.SiteProductService` (sole writer): `setStocked` (upsert — creates
    the row if absent, never touches override columns, so de-assorting retains them) and
    `updateSettings` (requires an existing row; throws the new `SiteProductNotFoundException`
    otherwise). `updateSettings` here is a full-replacement baseline — explicit `null` on the five
    nullable override fields clears to inherit, but `forecastingEnabled` (a `NOT NULL` column)
    treats `null` as "leave unchanged" since it cannot be nulled. The tri-state
    null-clears-vs-omitted-leaves-unchanged distinction for the other five fields, version/
    staleness checking, and the `409` contract are explicitly deferred to AC-6 (T-4b) — this
    baseline exists for T-4b to layer that contract onto.
  - Added `catalog.application.SiteProductSettingsUpdate` (plain record carrying the update).
  - Added `catalog.domain.SiteProductNotFoundException`.
  - Added `findStockedProductIdsBySiteId` to `SiteProductRepository` (id-only projection backing
    `stockedProductIds`).
- Tests:
  - `EffectiveProductSettingsTest` — pure resolution logic: absent/global-fallback, all-overrides-
    NULL inherits everything but keeps the row's own `isStocked`, an explicit override wins over
    global, and a de-assorted row's retained override resolves over the global fallback.
  - `SiteAssortmentTest` (Mockito) — `ProductNotFoundException` when the product itself doesn't
    exist; absent-row resolves not-stocked with global fallback; a foreign-site row is invisible
    from another site's read; `isStockedAt` false on no row; `stockedProductIds` matches the
    repository projection.
  - `SiteProductServiceTest` (Mockito) — `setStocked` 404s on an unknown product, creates a row
    when absent, and de-assorting an existing row leaves its overrides untouched; `updateSettings`
    throws `SiteProductNotFoundException` when no row exists, is accepted against a retained
    de-assorted row, treats `null` `forecastingEnabled` as leave-unchanged, and treats `null`
    override fields as clear-to-inherit.
  - `SiteAssortmentIT` (new, real Spring context + Testcontainers Postgres) — AC-4b tested
    directly against a freshly-created SECOND-shaped site with zero rows (its actual production
    starting state per T-2): absent-row global fallback, rejected settings write against a
    never-carried product, and assortment upsert carrying a product for the first time. AC-4c's
    full round trip: set an override → de-assort (row retained) → read returns the saved override,
    not the global fallback → still editable while de-assorted → re-assort → configuration intact.
    Plus a foreign-site isolation check at the facade level (AC-4), and
    `firstCarryingAProductPreservesItsGlobalForecastingEnabledValue` (review fix 2).
  - `SiteProductNotFoundExceptionMappingTest` (new, `@WebMvcTest` + throwaway controller) — proves
    `SiteProductNotFoundException` maps to a real HTTP 404 through Spring MVC dispatch (review
    fix 1), via its `ProductNotFoundException` supertype, not a direct handler-method call.
- Result: pass. `./mvnw -q test -Dtest=EffectiveProductSettingsTest,SiteAssortmentTest,SiteProductServiceTest,SiteAssortmentIT,SiteProductRepositoryIT,SiteProductsMigrationIT,SiteProductBackfillIT,ArchitectureTest,SiteProductNotFoundExceptionMappingTest,GlobalExceptionHandlerTest`
  — 48/48 across all ten classes, Maven exit 0. `ArchitectureTest` (including the frozen slice-cycle
  rule) passes cleanly across 3 repeated runs with zero `archunit_store` diff.
  `./mvnw -q -DskipTests compile` — clean.

## Test plan

- AC-1: `SiteProductsMigrationIT` executes `V56` itself and verifies its columns, nullability,
  defaults, indexes, unique constraint, and cascade/restrict FK behavior directly.
  `SiteProductRepositoryIT` additionally verifies the same constraint/version behavior through the
  entity and repository layer.
- AC-2: `SiteProductBackfillIT.failsLoudlyWhenNoMainSiteExists`,
  `backfillsExactlyOneRowPerProductForMainAndZeroRowsForSecond`,
  `seedsIsStockedAndForecastingEnabledFromProductsBooleans`.
- AC-3: `SiteProductBackfillIT.backfillsExactlyOneRowPerProductForMainAndZeroRowsForSecond`
  (row counts, orphan/duplicate checks, all-NULL overrides) and
  `effectiveValueAfterBackfillEqualsThePreMigrationGlobalValueForEveryOverrideColumn`
  (effective-value equality).
- AC-3b: `SiteProductBackfillIT.inheritedEffectiveValueStaysLiveAfterBackfillWhenNoOverrideIsSet`
  (a) and `explicitOverrideStopsFollowingLaterGlobalWrites` (b).
- AC-4: `SiteAssortmentTest`/`SiteProductServiceTest` (unit) and `SiteAssortmentIT`
  (`aSitesAssortmentIsInvisibleToAnotherSite`) — foreign-site pairs never leak;
  `EffectiveProductSettingsTest` — the single COALESCE resolution point.
- AC-4b: `SiteAssortmentIT.aSiteWithNoAssortmentRowResolvesEveryProductAsNotStockedWithGlobalFallback`,
  `settingsWriteAgainstAProductTheSiteHasNeverCarriedIsRejected`,
  `assortmentUpsertCreatesTheRowAndCarriesTheProduct` — tested directly against a SECOND-shaped
  site with zero rows, its real starting state.
- AC-4c: `SiteAssortmentIT.deAssortingRetainsOverridesAndReAssortingRestoresConfigurationIntact` —
  the full set-override → de-assort → read-saved-override → still-editable → re-assort round trip.
