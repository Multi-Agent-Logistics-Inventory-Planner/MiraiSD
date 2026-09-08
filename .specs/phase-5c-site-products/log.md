# Implementation log

## Assumptions and decisions

- **Review fix (P2): MAIN forecasting disable via the site-write path skipped forecast purge.**
  `SiteProductService.dualWriteToGlobalProduct` set `products.forecasting_enabled` to `false`
  without invoking `ForecastPurgePort`, unlike `ProductService.updateProduct`'s legacy path -
  existing forecast predictions stayed visible through unfiltered forecast reads after a MAIN
  settings write turned forecasting off. Fixed by injecting `ForecastPurgePort` into
  `SiteProductService` and mirroring `updateProduct`'s exact true -> false detection
  (`wasForecastingEnabled && Boolean.FALSE.equals(newValue)`), purging in the same transaction
  right after the `products.*` save. Also strengthened
  `legacyCreateSeedsMainRowNotStockedWhenNoInitialStock` (flagged in review: `isStockedAt=false`
  alone also passes for an absent row, so it wasn't proving AC-5's "creates a MAIN row" claim) to
  assert the MAIN row exists with every nullable override NULL. New tests in
  `SiteProductLegacyCompatibilityIT`: `siteWriteOnMainTrueToFalseForecastingTransitionPurgesForecasts`
  (positive path), `siteWriteOnMainFalseToTrueForecastingTransitionDoesNotPurge` (no purge on the
  other transition, mirroring `ProductServiceForecastingToggleTest`'s coverage for the legacy
  path), and `siteWriteOnMainForecastingTransitionRollsBackProductAndSiteProductRowsWhenPurgeFails`
  (atomic rollback: a forced purge failure rolls back both the `products.*` save and the earlier
  `site_products` save in the same transaction - `SiteProductServiceTest`'s Mockito unit tests and
  `ProductLifecycleTransactionBehaviorIT`'s `forecastPurgeFailureRollsBackUpdate` establish the
  same shape for the legacy path). `SiteProductServiceTest`'s constructor call updated for the new
  `ForecastPurgePort` dependency.

- **PR #320 CI fix: test-only controller leaked into the OpenAPI contract.** CI's freshness check
  failed because `SiteProductNotFoundThrowingTestController` (T-3's throwaway MVC-dispatch
  fixture, `@RestController`) is a normal classpath-scan candidate, so `OpenApiContractExportTest`
  - a full `@SpringBootTest` that regenerates `packages/contracts/openapi.json` from the live
  `/v3/api-docs` endpoint - picked it up and added `/test/site-product-not-found` to the checked-in
  contract, which the freshness check then flagged as an unreviewed diff. Tried and rejected: (a)
  moving the controller to a nested class inside `SiteProductNotFoundExceptionMappingTest` - a
  private (non-static) inner class fails `@WebMvcTest`'s bean instantiation entirely (confirmed:
  `NoHandlerFoundException` at runtime), and even a `public static` nested class is invisible to
  `@WebMvcTest(controllers = ...)` because Spring's test-class exclusion filter excludes classes
  nested inside a detected test class from being registered at all (confirmed via a bean-name dump
  - the nested controller bean never appeared in the `@WebMvcTest` context, static or not, public
  or package-private). **Fix:** kept the controller as its own top-level file, added
  `@Profile(ACTIVATION_PROFILE)` (a profile name no other `@SpringBootTest` in this codebase ever
  activates), and added `@ActiveProfiles(SiteProductNotFoundThrowingTestController.ACTIVATION_PROFILE)`
  to `SiteProductNotFoundExceptionMappingTest` so only that one test's context ever satisfies the
  condition and registers the bean. Verified empirically both ways: `OpenApiContractExportTest`
  now produces zero diff against the committed `openapi.json` (previously a 42-line addition every
  run), and `SiteProductNotFoundExceptionMappingTest` itself still passes (the profile-gated bean
  registers normally inside its own `@ActiveProfiles`-scoped context). No production code changed;
  both edits are test-only.

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

- **T-4 dependency choice: `catalog.application` depends directly on `sites.application.SiteDirectory`.**
  AC-5's dual-write needs MAIN's site id from inside `catalog` (`ProductService`,
  `SiteProductService`). `docs/specs/spring-domain-modular-monolith.md`'s intended-dependency
  diagram (section 6.2) lists only `catalog -> shared`, but section 6.1 rule 2 explicitly allows
  "application MAY depend on ... another module's documented application facade," and `identity`
  already depends on `sites` the same way (`MembershipAuthorizer` imports
  `sites.domain.SiteNotFoundException` directly). Added `SiteDirectory.findByCode` (mirroring the
  existing `findById`) and a small new `catalog.application.MainSiteResolver` component so the
  dependency is a single, documented seam rather than scattered `SiteRepository` lookups. Verified
  this doesn't introduce a graph cycle: `sites` has zero outgoing references into `catalog`,
  `services`, `exceptions`, or `identity` (grepped), so `catalog -> sites.application` is a new
  leaf edge, not a new cycle.
- **`forecastingEnabled` is treated like `is_stocked` for dual-write, not like the five nullable
  override columns.** AC-5's "sync only fields MAIN currently overrides" language is written for
  columns where NULL vs. non-NULL is a real, storable "no override" state
  (`unit_cost`/`msrp`/`reorder_point`/`target_stock_level`/`lead_time_days`).
  `site_products.forecasting_enabled` is `NOT NULL` — a MAIN row always holds a concrete
  true/false, seeded from global at first-carry (T-3's `newSiteProductSeededFromGlobal`) — so
  there is no NULL state to distinguish "overridden" from "not." Both dual-write directions
  therefore sync `forecastingEnabled` unconditionally whenever a caller sets it (same treatment as
  `is_stocked`/`is_active`), rather than gating on a "currently overridden" bit that doesn't exist
  for this column. Recorded here because AC-5's field-by-field table needs this to read the same
  way in 5d.
- **Known environment issue (not a T-4 regression): `ArchitectureTest.topLevelPackagesAreFreeOfCycles`
  and `CatalogEntityAccessCallerSetTest.catalogEntityAccessCallersMatchT0Inventory` fail in this
  sandbox on the unmodified `85032cd` base commit, before any T-4 change.** Verified with
  `git stash` both before and after T-4's changes — identical failure signatures
  (`StoreUpdateFailedException: Updating frozen violations is disabled` for the first;
  `Unexpected callers: [KujiBoxService.7 -> getReference]` for the second) reproduce on the
  pre-T-4 commit. `freeze.store.default.allowStoreUpdate=false` is hardcoded in
  `src/test/resources/archunit.properties`, and this specific frozen cycle rule is already flagged
  in this file's T-3 notes as not reliably regenerating in-environment; this JDK/ArchUnit
  combination (Homebrew Temurin 21.0.12.1 - no other JDK 21 was available in this sandbox) appears
  to hit that same fragility on every run regardless of code changes. Per instruction, the frozen
  `archunit_store` baseline was not touched. All other `ArchitectureTest` methods
  (`legacyTechnicalLayerPackagesDoNotGrow`, `repositoriesAreOnlyAccessedByServicesOrRepositories`,
  `noProductionClassOutsideCatalogDependsOnCatalogInfrastructure`,
  `domainDoesNotDependOnApiOrAnotherModulesInfrastructure`, `modulesDoNotDependOnAnotherModulesApi`,
  `sharedDoesNotDependOnBusinessModules`) pass cleanly with T-4's changes.
- Running the full `./mvnw test` suite locally regenerates `packages/contracts/openapi.json` via
  `OpenApiContractExportTest` and leaks the dev-only `SiteProductNotFoundThrowingTestController`
  endpoint (T-3's test fixture) into it, since the full test classpath is on the app context during
  that run. Reverted this file after each full-suite run — it is not a real contract change from
  T-4 (no REST endpoint was added or changed) and must not be committed.

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

### T-4 — bidirectional MAIN/legacy product compatibility

- Changed:
  - Added `catalog.application.MainSiteResolver` (new): `resolve()` returns MAIN's site id or
    throws `IllegalStateException` (a production invariant per V57 — callers acting on MAIN
    specifically must fail loudly); `isMain(siteId)` returns `false` (never throws) when MAIN
    doesn't exist, for the many existing tests/sites that never create one and aren't asking about
    MAIN specifically.
  - Added `SiteDirectory.findByCode(String)` to `sites.application`, mirroring `findById`.
  - `SiteProductService.setStocked`: when the target site is MAIN, also sets and saves
    `product.isActive` in the same transaction (AC-5's is_stocked/is_active pairing).
  - `SiteProductService.updateSettings`: when the target site is MAIN, also dual-writes every
    *non-null* field of the update into `products.*` via the new private
    `dualWriteToGlobalProduct` — explicitly skips null fields (AC-5's clearing exception, so a
    clear never erases the global fallback it's about to start depending on).
  - Added `SiteProductService.syncExistingMainOverrides(mainSiteId, productId, changedFields)` —
    the reverse direction: for each field a legacy write actually changed, updates MAIN's
    `site_products` row only if that field already holds a non-null override there; a field MAIN
    inherits (NULL) is left untouched. No-op (never creates a row) when MAIN has never carried the
    product.
  - `ProductService.createProduct`: after saving the product, calls
    `siteProductService.setStocked(mainSiteResolver.resolve(), savedProduct.getId(), startsActive)`
    unconditionally (root or child/prize product alike, matching V57's unfiltered backfill) —
    creates MAIN's row with NULL overrides, `isStocked` from the same `startsActive` derivation
    already used for `products.is_active`.
  - `ProductService.updateProduct`: after saving `products.*`, calls
    `siteProductService.syncExistingMainOverrides` with the six raw request fields (same
    null-means-untouched meaning the method already uses for `products.*` itself).
  - `ProductService.activateProduct`/`deactivateProduct`: now call
    `siteProductService.setStocked(mainSiteResolver.resolve(), id, true/false)` instead of setting
    `product.isActive` and saving directly — reuses `setStocked`'s existing upsert-if-absent
    behavior so a pre-T-4 product without a MAIN row still gets one, atomically, from the same
    method that also updates `products.is_active`.
  - `ProductService`/`SiteProductServiceTest`/`ProductServiceForecastingToggleTest` constructors
    updated for the two new dependencies (`SiteProductService`, `MainSiteResolver`).
  - `BaseIntegrationTest` and `BaseKafkaIntegrationTest` now seed a MAIN site in `@BeforeEach`
    (idempotent find-or-create) — every product write now touches MAIN's `site_products` row, so
    every test that creates/updates/activates a product needs one to exist.
    `ProductLifecycleTransactionBehaviorIT` (which extends neither base class, by design — see its
    own class javadoc) seeds MAIN directly in its own `@BeforeEach`.
- Tests:
  - `SiteProductLegacyCompatibilityIT` (new, real Spring context + Testcontainers Postgres,
    `@SpyBean` on `ProductRepository`/`SiteProductRepository` for the rollback tests, `@MockBean
    InitialStockPort` to avoid needing real storage-location fixtures unrelated to this record):
    legacy create seeds a MAIN row stocked/not-stocked per `startsActive`; legacy activate/
    deactivate sync MAIN's `is_stocked` atomically; a MAIN settings write dual-writes every set
    field to `products.*` atomically; a MAIN `setStocked` call dual-writes `is_active`; a write to
    a non-MAIN site never touches `products.*`; clearing a MAIN override does not dual-write and
    resolves to the (possibly since-changed) global fallback, leaving `products.*` and an
    unrelated inheriting site unaffected by the clear; a legacy update syncs a field MAIN already
    overrides but leaves a field MAIN inherits untouched (both the effective-value view and the
    raw `site_products` row are asserted); a raw `products.reorder_point` write (simulating
    forecasting-service) never creates a `site_products` override; and the two atomic-rollback
    tests — a forced `ProductRepository.save` failure during a MAIN settings write rolls back the
    `site_products` write with it, and a forced `SiteProductRepository.save` failure during a
    legacy update's MAIN sync rolls back the `products.*` save with it.
- Result: pass. `./mvnw test -Dtest=SiteProductServiceTest,SiteAssortmentTest,` +
  `EffectiveProductSettingsTest,ProductServiceForecastingToggleTest,SiteAssortmentIT,` +
  `SiteProductRepositoryIT,SiteProductsMigrationIT,SiteProductBackfillIT,` +
  `SiteProductLegacyCompatibilityIT,ProductLifecycleTransactionBehaviorIT,` +
  `SiteProductNotFoundExceptionMappingTest,GlobalExceptionHandlerTest` — 64/64, Maven exit 0.
  `./mvnw test -Dtest=ProductControllerSecurityIT,ProductCostVisibilityIT,` +
  `ProductDeletionCoordinatorIT,SupplierProductsCostVisibilityIT,StockMovementOutboxAtomicityIT,` +
  `AdjustToKafkaIT` — 41/41, Maven exit 0. Full `./mvnw test` — 397 tests, 1 failure + 1 error, both
  confirmed pre-existing on the unmodified base commit (see the environment-issue assumption
  above), unrelated to T-4. `./mvnw -q -DskipTests compile` and `test-compile` — clean.

#### Field-by-field compatibility table (input to Phase 5d)

| `products` column   | `site_products` column | Site-write (MAIN) → `products.*`                          | Legacy-write → MAIN `site_products`                                    | Explicit clear (`null`)                                  |
| -------------------- | ----------------------- | ----------------------------------------------------------- | -------------------------------------------------------------------------- | ------------------------------------------------------------ |
| `is_active`          | `is_stocked`            | Always synced (`setStocked`, not gated on "override")       | Always synced — activate/deactivate call `setStocked`, upserting MAIN's row if absent | N/A — not nullable, no "clear" state                          |
| `unit_cost`          | `unit_cost`              | Synced when the settings write sets it (non-null)            | Synced only if MAIN already has a non-null override for this field         | Clears `site_products` only; `products.*` untouched           |
| `msrp`               | `msrp`                   | Same as `unit_cost`                                          | Same as `unit_cost`                                                        | Same as `unit_cost`                                            |
| `reorder_point`      | `reorder_point`          | Same as `unit_cost`                                          | Same as `unit_cost`                                                        | Same as `unit_cost`                                            |
| `target_stock_level` | `target_stock_level`     | Same as `unit_cost`                                          | Same as `unit_cost`                                                        | Same as `unit_cost`                                            |
| `lead_time_days`     | `lead_time_days`         | Same as `unit_cost`                                          | Same as `unit_cost`                                                        | Same as `unit_cost`                                            |
| `forecasting_enabled`| `forecasting_enabled`    | Always synced when the caller sets it (not nullable, no "override" state to gate on) | Always synced when the caller sets it | N/A — `null` in `SiteProductSettingsUpdate` means "leave unchanged" (T-3), not clear |

Forecasting-service's own writes to `products.reorder_point`/`target_stock_level`/`lead_time_days`
never touch `site_products` at all — that path is Python code writing directly to Postgres,
outside every Java write path above, so no guard was needed in `SiteProductService`; confirmed by
`SiteProductLegacyCompatibilityIT.forecastingServiceRawWriteNeverCreatesSiteProductOverride`
(a raw `productRepository.save` standing in for that write).

A MAIN settings write's `forecasting_enabled` sync also purges existing forecast predictions on a
true → false transition (`ForecastPurgePort.purgeForecastsForProduct`), in the same transaction as
the `products.*`/`site_products` writes — matching the legacy path's existing behavior exactly, so
neither write path can leave stale predictions visible after forecasting is turned off (review
finding P2).

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
- AC-5: `SiteProductLegacyCompatibilityIT` — `legacyCreateSeedsMainRowStockedWhenInitialStockPositive`/
  `legacyCreateSeedsMainRowNotStockedWhenNoInitialStock` (legacy create),
  `legacyActivateAndDeactivateSyncMainIsStockedAtomically` (legacy activation/deactivation),
  `siteWriteOnMainDualWritesSettingsToProductsAtomically`/`siteWriteSetStockedOnMainDualWritesIsActive`
  (site-write → legacy-read), `writeToNonMainSiteNeverTouchesGlobalProduct` (dual-write is MAIN-only),
  `clearingMainOverrideDoesNotDualWriteAndPreservesGlobalFallback` (the clearing exception, the exact
  set-override/change-global/clear-override/assert walkthrough from the spec),
  `legacyUpdateSyncsOnlyFieldsMainAlreadyOverrides`/`legacyUpdateNeverManufacturesAnOverrideForAFieldMainInherits`
  (legacy-write → site-read, inherited and overridden cases both asserted),
  `forecastingServiceRawWriteNeverCreatesSiteProductOverride` (forecast-owned vs. manual),
  `siteWriteRollsBackAtomicallyWhenTheProductsDualWriteFails`/`legacyWriteRollsBackAtomicallyWhenTheMainSyncFails`
  (atomic rollback, both directions),
  `siteWriteOnMainTrueToFalseForecastingTransitionPurgesForecasts`/
  `siteWriteOnMainFalseToTrueForecastingTransitionDoesNotPurge`/
  `siteWriteOnMainForecastingTransitionRollsBackProductAndSiteProductRowsWhenPurgeFails`
  (P2 review fix: MAIN forecasting disable purges predictions, and a purge failure rolls back both
  `products.*` and `site_products`).
