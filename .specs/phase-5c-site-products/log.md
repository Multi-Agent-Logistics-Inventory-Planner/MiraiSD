# Implementation log

## Current handoff

- Status: T-4b complete (AC-6's version/tri-state settings contract, AC-6b's shared-default
  inherited-vs-overridden behavior). T-1 through T-4 were already complete from PR #320.
  Remaining: T-5 (operational — apply to production, out of scope for this session per
  instruction), T-6 (concurrency IT for AC-6 — the deterministic staleness-rejection path T-4b
  built is what T-6's concurrent-race test will exercise), T-7 (CONTEXT.md isActive/isStocked
  glossary entry).
- Next action: T-6 (a genuine two-thread/two-transaction concurrency IT) or T-7
  (CONTEXT.md entry), whichever the next session picks up; T-5 requires explicit approval and a
  fresh Supabase backup first, per AGENTS.md scope-and-safety rules.
- Decisions that must survive compaction:
  - `SiteProductSettingsUpdate` now carries `expectedVersion` (required `Long`) plus
    `FieldUpdate<T>` for its six settable fields, replacing the old plain-nullable shape.
  - `FieldUpdate<T>` (new, `catalog.application`) is the tri-state wrapper: `omitted()` (leave
    unchanged) vs. `of(value)` where `value` may itself be `null` (explicit clear/set).
  - The legacy-sync direction (`syncExistingMainOverrides`) now takes a separate, simpler,
    unversioned `ProductFieldChanges` record (plain nullable fields, null = not provided) rather
    than reusing `SiteProductSettingsUpdate` - conflating the two would have forced an internal,
    unversioned sync to carry a version and a clear/omit distinction it has no use for.
  - `SiteProductVersionConflictException` maps to `409 Conflict` via `GlobalExceptionHandler`'s
    existing conflict-exception group (`ProductInUseException` et al.), naming the row's current
    version in the message body - no new DTO shape, matching that group's existing precedent of a
    message-only conflict body.
  - No REST controller/DTO was added - AC-6's "on the wire" tri-state language is Phase 5d's
    concern (the actual `/settings` endpoint and its request DTO). T-4b is scoped to the service
    contract 5d's controller will call.
  - **Review fix (P2): the optimistic-lock catch must not reuse the failed persistence context.**
    `updateSettings`'s `catch (ObjectOptimisticLockingFailureException e)` originally re-queried
    the current version through the same `siteProductRepository`/transaction whose `saveAndFlush`
    had just failed - once a flush throws, the JPA provider requires that persistence context to
    be treated as unusable, so the re-query could fail again (surfacing as a 500 instead of the
    intended 409) and could not reliably report the winner's version. Fixed by adding
    `catalog.application.SiteProductVersionReader` (new, package-private `@Component`), a single
    `@Transactional(propagation = REQUIRES_NEW, readOnly = true)` method that reads the row's
    current version through a fresh, independent transaction, called only from the catch block.
    The pre-flush mismatch path (`requireCurrentVersion`, called before any write is attempted)
    was left alone except to stop re-querying at all - the row is already loaded in memory there,
    so its version is safe to read directly. New test:
    `SiteProductServiceTest.updateSettings_translatesAConcurrentFlushFailureIntoAVersionConflictUsingAFreshRead`
    (mocks a `saveAndFlush` failure and asserts the conflict message uses the reader's fresh
    value, not the stale in-memory entity).
- Last verified: `./mvnw test -Dtest=SiteProductServiceTest,SiteAssortmentIT,SiteProductLegacyCompatibilityIT,`
  `GlobalExceptionHandlerTest,ArchitectureTest,SiteAssortmentTest,EffectiveProductSettingsTest,`
  `ProductServiceForecastingToggleTest,SiteProductRepositoryIT,SiteProductsMigrationIT,`
  `SiteProductBackfillIT,ProductLifecycleTransactionBehaviorIT,SiteProductNotFoundExceptionMappingTest`
  - 86/86, Maven exit 0 (`ArchitectureTest`, including the fragile frozen slice-cycle rule, passed
  cleanly). Full `./mvnw test` after the P2 fix - 404/404, Maven exit 0.
  `git status --porcelain packages/ services/inventory-service/src/main/resources/` - clean (no
  `openapi.json`/migration drift).
- Open risks/questions:
  - T-6 (a real two-thread/two-transaction concurrency IT proving "one succeeds, one gets 409, no
    lost update") has not been written yet - `updateSettings`'s `saveAndFlush` +
    `ObjectOptimisticLockingFailureException` handling (now reading the conflict version through
    `SiteProductVersionReader`'s fresh transaction) is what that IT will exercise.
  - None otherwise.

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

### T-4b — AC-6 settings contract (version/tri-state) + AC-6b shared-default tests

- Changed:
  - Added `catalog.application.FieldUpdate<T>` (new): the tri-state wrapper -
    `FieldUpdate.omitted()` (field not part of the request, leave stored value unchanged) vs.
    `FieldUpdate.of(value)` where `value` may itself be `null` (explicit clear/set) - a plain
    nullable field cannot distinguish these two states, which AC-6 requires.
  - `SiteProductSettingsUpdate` now carries `expectedVersion` (`Long`, required - `null` means the
    caller omitted it) and `FieldUpdate<T>` for each of its six fields, replacing the previous
    plain-nullable shape. A compact constructor rejects a `null` `FieldUpdate` reference itself
    (callers must pass `FieldUpdate.omitted()`, never bare `null`, for "not provided").
  - Added `catalog.application.ProductFieldChanges` (new): a separate, simpler, unversioned record
    (plain nullable fields, `null` = not provided) for `syncExistingMainOverrides`'s internal
    legacy-sync direction, which needs neither a version nor a clear/omit distinction - kept
    distinct from `SiteProductSettingsUpdate` rather than forcing the versioned/tri-state contract
    onto an unversioned internal call. `ProductService.updateProduct`'s call site
    (`syncExistingMainOverrides`) updated to construct this instead.
  - `SiteProductService.updateSettings`: applies each field only when its `FieldUpdate` is present
    (`isPresent()`), checks `expectedVersion` against the row's current version before writing
    (missing or mismatched both throw `SiteProductVersionConflictException`), and uses
    `siteProductRepository.saveAndFlush` inside a `try/catch` for
    `ObjectOptimisticLockingFailureException` so a genuine concurrent version race (not just the
    explicit pre-check) also maps to the same conflict exception - built for T-6's concurrency IT,
    which has not been written yet. `forecastingEnabled`'s NOT-NULL invariant is preserved: both
    omitted and an explicit `FieldUpdate.of(null)` leave the current value, since the column can
    never be cleared.
  - `dualWriteToGlobalProduct` updated to the same present-and-non-null gating (a present-but-null
    field is a clear, which must not dual-write, matching AC-5's existing exception unchanged).
  - Added `catalog.domain.SiteProductVersionConflictException` (extends `RuntimeException`,
    `catalog.domain` per the T-3 precedent for keeping exceptions in-module) and registered it in
    `GlobalExceptionHandler`'s existing conflict-exception `@ExceptionHandler` group (alongside
    `ProductInUseException` et al.) - `409 Conflict`, message names the row's current version. No
    new DTO shape: the existing group's `ErrorResponse.message` (a plain string) already satisfies
    "a body naming the current version"; a dedicated `currentVersion` field is left to 5d's actual
    controller/DTO if that turns out to matter to the frontend.
  - **Review fix (P2):** added `catalog.application.SiteProductVersionReader` (new,
    package-private `@Component`, injected into `SiteProductService`) - a single
    `@Transactional(propagation = REQUIRES_NEW, readOnly = true)` method reading a row's current
    version through a fresh, independent persistence context. `updateSettings`'s
    `ObjectOptimisticLockingFailureException` catch now calls this instead of re-querying through
    `siteProductRepository` directly: once `saveAndFlush` fails, the enclosing transaction's
    persistence context must be treated as unusable for further work, so reusing it for the
    recovery query risked a second failure there (surfacing as an unhandled 500 instead of the
    intended 409) and could not reliably report the actual winner's version. The unrelated
    pre-flush mismatch path (`requireCurrentVersion`, called before any write is attempted) was
    simplified to read the already-loaded entity's in-memory version directly instead of
    re-querying at all - that path never touches a poisoned context, so the extra query was both
    unnecessary and, per the same finding's reasoning, best avoided on principle.
- Tests:
  - `SiteProductServiceTest` (Mockito, updated + new): version-missing and version-stale rejection
    (no save attempted, verified via `never()` on both `save` and `saveAndFlush`), a matching
    version accepted; omitted vs. explicit-null forecastingEnabled both leave the current value
    unchanged (two separate tests, since AC-6 explicitly calls out this column can't be cleared);
    explicit-null override field clears to inherit; omitted override field leaves the stored value
    untouched (distinct test from the clear case, proving the two are not conflated); existing
    row-exists/de-assorted-row tests updated to the new tri-state construction.
    `updateSettings_translatesAConcurrentFlushFailureIntoAVersionConflictUsingAFreshRead` (review
    fix P2) - mocks `saveAndFlush` throwing `ObjectOptimisticLockingFailureException` and asserts
    the resulting conflict message's current-version comes from `SiteProductVersionReader` (a
    distinct mock returning a different value than the stale in-memory entity would), proving the
    fresh-read path is actually exercised, not just present in the code.
  - `SiteAssortmentIT` (real Spring context + Testcontainers Postgres, updated + new):
    `updateSettings_rejectsAStaleVersionAndAppliesNoWrite`,
    `updateSettings_rejectsAMissingVersionAndAppliesNoWrite` (both assert the effective value is
    unchanged, not just that the exception is thrown - the "applies no write" half of AC-6),
    `updateSettings_acceptsAMatchingVersionAndAdvancesIt` (proves the row's `@Version` actually
    advances on a real save, not just that the application-level check passes). All prior tests in
    this file updated to thread the real row's version returned by `setStocked`/`updateSettings`
    through subsequent calls, since a matching version is now required end-to-end.
  - `SiteProductLegacyCompatibilityIT` (real Spring context + Testcontainers Postgres, updated +
    new): every existing `updateSettings` call site updated to pass a real current version (via a
    new `mainVersion(productId)` helper reading the MAIN row, or a `setStocked`/`updateSettings`
    return value) and the new tri-state construction (a `settingsUpdate(...)` test helper
    preserves each pre-existing test's original null-means-omitted intent, except
    `clearingMainOverrideDoesNotDualWriteAndPreservesGlobalFallback`'s second call, which now uses
    an explicit `FieldUpdate.of(null)` for `reorderPoint` to keep proving AC-5's clearing exception
    under the new, more precise vocabulary). Two new tests for AC-6b:
    `mainSettingsChangeMovesAnInheritingSitesEffectiveValue` (SECOND never carries the product, so
    it inherits `products.*` - a MAIN dual-write moves SECOND's effective value) and
    `mainSettingsChangeDoesNotMoveAnOverridingSitesEffectiveValue` (SECOND sets its own override
    for the same field first - the same MAIN dual-write must not move it). Both assert against the
    real `ProductService.createProduct` default (`reorderPoint` defaults to 10 when not supplied),
    not an assumed `null`.
  - `GlobalExceptionHandlerTest` (updated): direct-call test proving `SiteProductVersionConflictException`
    maps to `409`/`"Conflict"` through `handleConflictException`, with the message naming the
    current version in the body.
- Result: pass. `./mvnw test -Dtest=SiteProductServiceTest,SiteAssortmentTest,EffectiveProductSettingsTest,`
  `ProductServiceForecastingToggleTest,SiteAssortmentIT,SiteProductRepositoryIT,SiteProductsMigrationIT,`
  `SiteProductBackfillIT,SiteProductLegacyCompatibilityIT,ProductLifecycleTransactionBehaviorIT,`
  `SiteProductNotFoundExceptionMappingTest,GlobalExceptionHandlerTest,ArchitectureTest` — 85/85,
  Maven exit 0 (`ArchitectureTest`, including the frozen slice-cycle rule, passed cleanly). Full
  `./mvnw test` — 403/403, Maven exit 0 (no pre-existing failures reproduced in this run).
  `./mvnw -q -DskipTests compile` and `test-compile` — clean.
  `git status --porcelain packages/ services/inventory-service/src/main/resources/` — clean (no
  `openapi.json` or migration drift; no REST controller was added, per instruction to keep 5d's
  API/frontend work separate).

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
- AC-6: `SiteProductServiceTest.updateSettings_throwsWhenVersionIsMissing`/
  `updateSettings_throwsWhenVersionIsStale`/`updateSettings_acceptsWhenVersionMatches` (unit-level
  staleness/missing-version rejection and message content) plus
  `SiteAssortmentIT.updateSettings_rejectsAStaleVersionAndAppliesNoWrite`/
  `updateSettings_rejectsAMissingVersionAndAppliesNoWrite`/`updateSettings_acceptsAMatchingVersionAndAdvancesIt`
  (real Postgres: rejection applies no write, and a matching version advances the row's real
  `@Version`). Tri-state: `SiteProductServiceTest.updateSettings_explicitNullOverrideFieldClearsItToInherit`/
  `updateSettings_omittedOverrideFieldLeavesTheStoredValueUntouched` (clear vs. leave-unchanged,
  as separate tests) and `updateSettings_omittedForecastingEnabledLeavesTheCurrentValueUnchanged`/
  `updateSettings_explicitNullForecastingEnabledAlsoLeavesTheCurrentValueUnchanged` (both
  omitted-forms behave identically for the one NOT-NULL field). Row-exists-vs-stocked was already
  covered by T-3's `updateSettings_onARetainedDeAssortedRowIsAccepted`/`updateSettings_throwsWhenNoRowExistsForThisSite`,
  updated for the new construction. `GlobalExceptionHandlerTest.handleConflictException_siteProductVersionConflict_shouldReturn409`
  (409 shape). Concurrency IT (two concurrent writes, one 409, no lost update) is T-6, not yet
  written.
- AC-6b: `SiteProductLegacyCompatibilityIT.mainSettingsChangeMovesAnInheritingSitesEffectiveValue`/
  `mainSettingsChangeDoesNotMoveAnOverridingSitesEffectiveValue` — a MAIN settings change moves an
  inheriting site's effective value but not an overriding site's, tested against real Postgres
  with a genuine SECOND site.
