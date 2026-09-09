# Implementation log

## Current handoff

- Status: 6a baseline inventory complete and reviewed. T-0 done (ArchUnit `isRepositoryClass()`
  extended). T-1 done (`LocationService.getNotAssignedLocation()` overloads added). T-2 done
  (`LastActorActivityPort`/`LastActorActivityAdapter`, R-3 cycle resolved). T-3 done (moved
  `LocationInventory`, `StockMovement`, their repositories/specifications/projection, and the
  three inventory exceptions into `inventory.domain`/`inventory.infrastructure`). T-4 done (moved
  `StockMovementService`/`LocationInventoryService`/`InventoryAggregateService` into
  `inventory.application`; dropped the dead `KujiBoxTierRepository` injection; consolidated the
  R-8 `DEFAULT_SITE_CODE` duplication through `LocationService`). T-5 (introduce
  `InventoryOperations`/`InventoryQueries` and migrate external callers off direct repository
  access) is the next concrete implementation step.
- Next action: implement T-5 through T-8 (see 6a task list below) with TDD per task, on
  `refactor/inventory-stock` (already checked out, clean).
- Committed 2026-09-09: `dbc2c1d` "feat(inventory): establish inventory module boundary (Phase 6a,
  T-0-T-4)" — T-0 through T-4 as one commit. The reviewer asked for a three-way split (guard/facade
  prep; mechanical entity/service moves; R-8 consolidation/dead-injection removal); the user then
  asked to commit it all as one instead, so the split was not carried out. T-5 onward should start
  its own commit(s) rather than accumulate further into this one.
- Surviving decisions: one branch/PR and one Full-tier record; five logical commits/review
  checkpoints with fix commits allowed. A production prerequisite can require a separate PR.
- Last verified: `./mvnw -q clean test-compile` (clean build, not incremental — this distinction
  matters, see the T-3 and T-4 fix entries below) then `./mvnw -q -Dtest=ArchitectureTest,
  LocationServiceTest,UserServiceTest,LastActorActivityAdapterTest,LocationInventoryServiceTest,
  StockMovementServiceActiveStatusDerivationTest,KujiBoxServiceTest,
  KujiBoxServiceCatalogFacadeMigrationTest,ShipmentServiceOverrideTest,StockMovementActorNameTest,
  EventOutboxServicePublishTest,EventOutboxServiceCreateEventTest,EventOutboxServiceDeadLetterTest,
  CatalogEntityAccessCallerSetTest,InventoryServiceApplicationTests test` — all pass
  (services/inventory-service). Confirmed `ArchitectureTest` stable across two independent clean
  rebuilds for both T-3 and T-4's store regenerations (see their task records below). No frozen
  ArchUnit store regeneration needed by T-0, T-1 or T-2 (T-2 changed the live
  `module-dependency-edges-baseline.txt`, not a frozen store). Doc-level `git diff --check` and
  local-link checks from the planning pass still hold.
- Open risks: actual migration mechanism and old-writer compatibility (6b); global quantity/activity
  semantics and downstream consumers (still to reconcile before 6c); event payload coverage and
  targeted-refresh baseline (6c/6e). R-4 through R-8 below remain open, not yet resolved.

## Assumptions and decisions

- 2026-09-09: user chose five checkpoints within one branch/PR, superseding the proposed separate
  slice PRs. Updated both durable plans and created this single execution record.
- Commit order cannot establish deployed backfill/writer prerequisites. Resolve rollout before 6b;
  do not deploy, drop columns, or redefine global activity as part of this documentation update.
- 2026-09-09: 6a caller/table/endpoint/event/ArchUnit baseline inventory completed (backend
  architecture pass). No code moved by that pass.
- 2026-09-09 (user decision, R-1): `LocationAggregateController/Service/Repository`'s cross-module
  native query (locations/storage_locations from `sites`, `location_inventory` from `inventory`,
  `machine_display` from `displays`) is assigned to `sites` and registered as an approved
  cross-module read projection under spec §7.4, rather than decomposed or re-deferred.
- 2026-09-09 (user decision, R-2): `StockMovement` moves from `models.audit` into
  `inventory.domain` in 6a now, recorded as transitional debt — its association back into the
  not-yet-migrated `audit` module (Stage G/Phase 7) is an accepted existing relationship under
  spec §6.1 rule 8, not a new violation to resolve here. Executed in T-3 (done): `AuditLog` stays
  in `models.audit`, `AuditLog.movements` now imports `inventory.domain.StockMovement` across the
  boundary — recorded in `module-dependency-edges-baseline.txt` as the `models -> inventory` edge.
- R-3 (identity/inventory cycle): resolved via port inversion — `LastActorActivityPort` declared
  in `identity.application`, implemented by an `inventory.application` adapter — so the edge is
  `inventory -> identity` only, per T-2 (done). Do not approve an `identity -> inventory` edge in
  the baseline file; that would hide a cycle the edges check does not itself detect.
- R-4 (`AuditLog.user` needs a managed `identity.domain.User`): needs an identity entity-access
  facade (`CatalogEntityAccess`-style, `MANDATORY` propagation) exposed to inventory. This adds an
  `inventory -> identity` edge not currently listed in spec §6.2's target graph; carrying it
  requires a spec amendment alongside T-2, not just a baseline-file addition.
- R-5 through R-7 (kuji-flavored `aggregateKujiDailyPayouts` query, `MachineDisplayService`
  ledger-only writes through inventory, the `EventOutboxService`/`StockMovementService` `@Lazy`
  circular bean dependency): accepted as transitional debt for 6a per the architect's
  recommendation; not to be resolved as part of this checkpoint. Flagged for their respective
  owning phases (R-5/R-6: Phase 7 kuji/displays migration; R-7: outbox's eventual move to
  `shared`).
- R-8 (five duplicated `DEFAULT_SITE_CODE = "MAIN"` copies): resolved in T-4 (done) for the two
  inventory-side copies — `LocationService.DEFAULT_SITE_CODE`/`getDefaultSiteId()` went public and
  `StockMovementService`/`LocationInventoryService` call it instead of duplicating the lookup, per
  the plan's "consolidate... without changing site-resolution behavior." The other three copies
  (`ShipmentService`, `DevSeedController`, and one already-fixed in `LocationService` itself before
  6a) are outside this checkpoint's scope — not inventory's own duplication, left as recorded debt.
- Branch creation and PR publication have not been performed. Implementation for 6a begins with
  this log entry.
- 2026-09-09 (T-1 design deviation): the baseline pass suggested a new `sites.application`
  entity-access facade mirroring `CatalogEntityAccess` (MANDATORY propagation). Re-checked every
  concrete inventory caller (§1a/§1b of the baseline inventory): `StockMovementService`/
  `LocationInventoryService` are themselves already `@Transactional`, so a `REQUIRED`-propagation
  call from either into `LocationService` joins the same transaction a `MANDATORY` call would have
  required — the caller-side transaction guarantee `CatalogEntityAccess`'s stricter propagation
  exists to enforce (correction, 2026-09-09 review: this applies equally to eager managed entities
  and lazy proxies — any entity fetched inside a transaction becomes unusable once that
  transaction closes, not only a lazy proxy) is already met by these callers' existing boundaries.
  `sites.application.LocationService` already exposes matching-semantics lookups
  (`getLocationById`, `getStorageLocationByCode`, `getStorageLocationById`,
  `getLocationsByStorageLocation`, `getAllStorageLocations`), so a second facade class would
  duplicate it for no behavioral gain (CLAUDE.md: no premature abstraction). Went with extending
  `LocationService` instead of inventing a parallel facade; revisit only if T-4/T-5 wiring
  surfaces a caller that is not already transactional, where `MANDATORY`'s stricter fail-fast
  guarantee would earn its keep.

## 6a baseline inventory (2026-09-09)

Full detail lives in this log entry's originating architecture pass; summarized findings kept here
for traceability.

**Write callers of `location_inventory`/`stock_movements`:** `ShipmentService` (4 delta blocks),
`KujiBoxService` (6 delta blocks + 8 ledger-only rows + one kuji analytic query), `MachineDisplayService`
(ledger-only rows only, no `location_inventory` change), `catalog.application.ProductDeletionCoordinator`/
`ProductService` (already migrated via `InitialStockPort`/`InventoryCleanupPort`), `DevSeedController`/
`AnalyticsSeedService` (dev-profile seeding, exempted by class name).

**Read callers:** `ProductReportBundleService`, `ForecastService`, `AnalyticsService`,
`analytics.application.SalesRollupRecomputeService`, `identity.application.UserService`
(last-activity — the R-3 cycle), `AuditLogService`, `ActivityFeedService`, `EventOutboxService`,
plus DTO/spec mappers (`AuditLogMapper`, `AuditLogDTOMapper`, `StockMovementMapper`,
`AuditLogSpecifications`, `StockMovementSpecifications`).

**Out-of-module dependencies `StockMovementService` drags in:** `sites.infrastructure.{LocationRepository,
StorageLocationRepository,SiteRepository}`, `identity.infrastructure.UserRepository`,
`services.{SupabaseBroadcastService,EventOutboxService,AuditLogService-adjacent AuditLogRepository}`,
`models.audit.AuditLog`, and an unused/dead `KujiBoxTierRepository` injection (delete, not move).

**Entities/tables:** `LocationInventory` (`location_inventory`, `sites.Location`/`sites.Site`/
`catalog.Product` associations) in `models.inventory`; `StockMovement` (`stock_movements`,
`AuditLog` association both directions, `catalog.Product`) in `models.audit` — see R-2 above;
`ShipmentItemAllocation` (`shipment_item_allocations`) belongs to shipments, has zero production
callers via its repository (only the `ShipmentItem.allocations` association is used) — candidate
for deletion, decide explicitly in T-3. `InventoryTotalsRepository` does a cross-module native
read of `products`/`categories` for `InventoryTotalDTO`; the slim projection replacing it is 6c,
not 6a.

**Endpoints (all legacy `/api`, none `/api/v1` yet):** `InventoryAggregateController`
(`/api/inventory/totals`, `/api/inventory/by-product/{id}`), `LocationInventoryController`
(`/api/locations/{id}/inventory[...]`, `/api/storage-locations/{id}/inventory`),
`LocationAggregateController` (`/api/locations/with-counts` — see R-1), `StockMovementController`
(`/api/stock-movements/{batch-adjust,transfer,batch-transfer,history,audit-log}`). All nine appear
in `packages/contracts/openapi.json`; a pure package move (T-3–T-6) must leave it byte-identical.

**Events:** `EventOutboxService.createStockMovementEvent` writes to topic `inventory-changes`
(no `site_id`, no event version, no idempotency key yet — that gap is AC-4/6c). Consumers:
`messaging-service`, `forecasting-service` (unaffected by a 6a package move). Realtime (non-Kafka):
`SupabaseBroadcastService.broadcastInventoryUpdated` — 15 call sites, the 6e egress target.

**ArchUnit baseline:** `ArchitectureTest.isRepositoryClass()` currently matches legacy-package
residence OR Spring Data `Repository` assignability — `LocationAggregateRepository` and
`InventoryTotalsRepository` are plain `@Repository` classes over `EntityManager` and will silently
drop out of rule 2's coverage when relocated unless `isRepositoryClass()` gains a third arm first
(T-0). The catalog precedent (`noProductionClassOutsideCatalogDependsOnCatalogInfrastructure`,
unfrozen/live) is the pattern to mirror for inventory in T-7, including the caller-set pinning test.

## 6a task list

Mechanical moves and behavioral edits are kept in separate commits per the plan's instruction.

- **T-0** (done): extend `ArchitectureTest.isRepositoryClass()` to cover `@Repository`-annotated
  non-Spring-Data classes; add a probe test; regenerate frozen stores.
- **T-1** (done): add the location-lookup overloads inventory needs to `sites.application`;
  behavior-neutral. Implemented as `LocationService.getNotAssignedLocation()`/
  `getNotAssignedLocation(UUID)` rather than a new entity-access facade class — see the T-1 design
  deviation entry above.
- **T-2** (done): `LastActorActivityPort` in `identity.application` + `inventory.application`
  adapter; `UserService` stops importing `StockMovementRepository`. Updated baseline edges.
- **T-3** (done): moved `LocationInventory`, `StockMovement` (R-2), repositories, projection and
  exceptions into `inventory.domain`/`inventory.infrastructure`. `ShipmentItemAllocationRepository`
  decided explicitly: left in place in `repositories/`, untouched — re-confirmed zero production
  callers (grep), shipments' territory (Stage F/Phase 7), not this checkpoint's to move or delete.
- **T-4** (done): moved `StockMovementService`, `LocationInventoryService`,
  `InventoryAggregateService` into `inventory.application`; dropped the dead `KujiBoxTierRepository`
  injection; consolidated the two inventory `DEFAULT_SITE_CODE` copies (R-8) through
  `LocationService`, without changing site-resolution behavior.
- **T-5**: introduce `InventoryOperations` (`applyDelta`, `recordMovement`, `syncProductTotals`)
  and `InventoryQueries`; migrate all external callers off direct repository access. Assert
  identical outbox payloads and identical `location_inventory` end-states (behavioral commit).
- **T-6**: move controllers/DTOs/mappers into `inventory.api`; `InventoryAggregateController`
  goes through `InventoryQueries`. `packages/contracts/openapi.json` stays byte-identical; run
  `tests/contracts`. `LocationAggregateController` stays owned by `sites` per R-1 and gets a named
  ArchUnit exemption as an approved cross-module read projection.
- **T-7**: add `noProductionClassOutsideInventoryDependsOnInventoryInfrastructure` (live, exempt
  only `DevSeedController`/`AnalyticsSeedService` by FQN) + an `InventoryOperationsCallerSetTest`
  pinning the origin set. Prune/extend `module-dependency-edges-baseline.txt` with justifications;
  do not approve an `identity -> inventory` edge (R-3).
- **T-8**: record R-1/R-2 resolutions and the `inventory -> identity` edge (R-4) in
  `docs/specs/spring-domain-modular-monolith.md` §5/§6.2/§11 and the module `package-info`.

Test plan: reuse `StockMovementOutboxAtomicityIT`/`AdjustToKafkaIT` unchanged as the atomicity/event
proof; add a Testcontainers IT proving `applyDelta` inside a caller-opened transaction rolls back
inventory row + movement + outbox together (AC-1 "facades preserve caller transactions"); add a
delete-on-zero/find-or-create IT covering the now-shared ShipmentService/KujiBoxService path; keep
`LocationInventoryControllerSecurityIT`/`StockMovementControllerSecurityIT`/`RBACAlignmentIT` green.
Expect import churn across the ~31 test files referencing these classes.

## Task record

Planning: 6a–6e not started as of the prior entry. Planning review found no blocking issues.

2026-09-09: 6a baseline inventory (caller/table/endpoint/event/ArchUnit) completed via architecture
pass; R-1 and R-2 decided by user (see Assumptions and decisions); concrete T-0–T-8 task list
recorded above.

### T-0 — Extend `isRepositoryClass()` for plain `@Repository` classes

- Changed: `services/inventory-service/src/test/java/com/mirai/inventoryservice/architecture/ArchitectureTest.java`
  — `isRepositoryClass()` gained a third arm, `target.isAnnotatedWith(org.springframework.stereotype.Repository.class)`,
  alongside the existing legacy-package and Spring Data `Repository`-assignability arms. Comment
  above the method records why: `LocationAggregateRepository`/`InventoryTotalsRepository` are
  plain `EntityManager`-backed classes, not Spring Data interfaces, and are only caught today
  because they still live in `repositories..`; T-3's move to `inventory.infrastructure` would
  otherwise silently drop them out of `repositoryAccessRule()` coverage.
- Tests: added a temporary probe (`ArchitectureTestRepositoryRuleProbeTest`, calling the exact
  package-visible `ArchitectureTest.repositoryAccessRule()` factory, not a re-typed copy) plus two
  temporary fixtures (`inventory.infrastructure.ArchProbePlainRepositoryStyle` — a plain
  `@Repository` class mimicking the post-move shape; `inventory.api.ArchProbeApiCaller` — a
  non-allowed caller reaching it directly, mirroring `InventoryAggregateController` →
  `InventoryTotalsRepository` today). Confirmed RED first (`./mvnw -q
  -Dtest=ArchitectureTestRepositoryRuleProbeTest test` failed: violation report omitted the
  fixture pair entirely under the old two-arm implementation), then GREEN after the fix. Fixtures
  and probe test deleted afterward, per the phase-5a/5b "add probe, confirm, remove" pattern —
  `git status` confirms no residue.
- Verified the new arm doesn't reclassify anything currently in the codebase: grepped every
  `@Repository`-annotated class (`repositories..`, `catalog/identity/sites.infrastructure`) — all
  are either already in the legacy package or Spring Data interfaces, so today's violation set is
  unchanged. Confirmed by running the full `ArchitectureTest` suite
  (`./mvnw -q -Dtest=ArchitectureTest test`) after the fix: all rules pass, including the frozen
  `repositoriesAreOnlyAccessedByServicesOrRepositories` store, with **no store regeneration
  needed** — the fix is purely forward-looking until T-3 actually moves the two classes.
- Result: pass. `./mvnw -q -DskipTests compile` also confirms clean compilation.
- Review correction (independent verification, 2026-09-09): the code comment originally implied
  both `LocationAggregateRepository` and `InventoryTotalsRepository` move into
  `inventory.infrastructure`, but R-1 assigns `LocationAggregateRepository` to `sites`. Comment
  corrected to name each class's actual T-3/R-1 destination. `./mvnw -q -Dtest=ArchitectureTest
  test`, `./mvnw -q -DskipTests compile` and `git diff --check` re-confirmed pass after the edit.

### T-1 — Sites location-lookup overloads for inventory

- Changed: `services/inventory-service/src/main/java/com/mirai/inventoryservice/sites/application/LocationService.java`
  — added `getNotAssignedLocation()` (default-site) and `getNotAssignedLocation(UUID siteId)`
  (site-scoped), consolidating the two-step lookup
  (`storageLocationRepository.findByCodeAndSite_Id/Code("NOT_ASSIGNED", ...)` then
  `locationRepository.findByStorageLocationCodeAndSiteId("NOT_ASSIGNED", ...)`) that
  `StockMovementService.getNotAssignedLocationId()` (lines 638-645) duplicates directly against
  `sites.infrastructure` repositories today. Not wired into any caller yet — that migration is
  T-4/T-5's job, once `StockMovementService` itself moves into `inventory.application`. No
  existing `LocationService` method was changed.
- Design deviation from the baseline pass's suggested shape (new MANDATORY-propagation entity-
  access facade mirroring `CatalogEntityAccess`): recorded above under "Assumptions and
  decisions" — the callers' own existing transaction boundaries already give `LocationService`'s
  `REQUIRED` propagation the same join-caller's-transaction guarantee `MANDATORY` would have
  enforced, so extended the existing `LocationService` facade instead of adding a duplicate class.
- Tests: added `LocationServiceTest.GetNotAssignedLocationTests` (4 cases: default-site overload
  delegates correctly, site-scoped overload resolves directly, throws
  `StorageLocationNotFoundException` when the site has no NOT_ASSIGNED storage location, throws
  `LocationNotFoundException` when the storage location exists but has no location row) — mirrors
  the existing Mockito-based nested-class pattern in that file. Confirmed RED first (`./mvnw -q
  -Dtest=LocationServiceTest test` failed to compile: `getNotAssignedLocation()`/`(UUID)`
  undefined), then GREEN after implementation.
- Result: pass. `./mvnw -q -Dtest=LocationServiceTest,ArchitectureTest test` — 36 tests total (29
  in `LocationServiceTest`, 7 in `ArchitectureTest`), all green; `ArchitectureTest` unaffected — no
  new cross-module edge, since the new methods live entirely inside `sites.application` calling
  `sites.infrastructure`. `./mvnw -q -DskipTests compile` and `./mvnw -q -DskipTests test-compile`
  also pass. `SiteLocationControllerIT` not re-run: the change is purely additive (no existing
  method touched), so that IT is unaffected by construction; it will be exercised again once
  T-4/T-5 actually wire a caller through the new methods.
- Independent review (2026-09-09): confirmed no blocking findings; re-ran
  `./mvnw -q -Dtest=LocationServiceTest,ArchitectureTest test` (36 tests passed) and
  `git diff --check` (passed). Corrected two documentation errors above: the MANDATORY-vs-eager
  claim, and the test-count attribution. No source files changed by this review.

### T-2 — Resolve the identity/inventory cycle (R-3)

- Changed:
  - Added `identity/application/LastActorActivityPort.java` — a two-method interface
    (`Optional<OffsetDateTime> lastActivityFor(UUID actorId)`,
    `Map<UUID, OffsetDateTime> lastActivityByActor()`), declared in `identity.application` per the
    `InitialStockPort`/`InventoryCleanupPort` port-in-consumer precedent.
  - Added `inventory/application/LastActorActivityAdapter.java` — package-private `@Component`
    implementing the port, backed directly by `StockMovementRepository` (still in the legacy
    `repositories` package pending T-3); moved the `StockMovement -> OffsetDateTime` mapping and
    the `List<Object[]> -> Map<UUID, OffsetDateTime>` translation out of `UserService` into this
    adapter, matching where `InitialStockAdapter` already puts its translation logic.
  - `identity/application/UserService.java` — constructor now takes `LastActorActivityPort`
    instead of `StockMovementRepository`; `getLastAuditDate`/`getAllLastAuditDates` delegate to
    it. Dropped the now-unused `StockMovement` and `HashMap` imports.
  - `src/test/resources/module-dependency-edges-baseline.txt` — added `inventory -> identity`
    (with a justification block matching the file's existing convention for reviewed additions);
    pruned `identity -> models` and `identity -> repositories`, both now genuinely unused (grep
    confirmed `UserService.java` was their only source anywhere under `identity/**`) — the "prune
    on removal" discipline the file's own header requires.
- Design note: `LastActorActivityAdapter` uses `@Transactional(readOnly = true)` (default
  `REQUIRED` propagation), not `MANDATORY` — there's no cross-transaction managed-entity handoff
  here (unlike `CatalogEntityAccess`/R-4), just a read `UserService`'s own class-level
  `@Transactional` already covers.
- Tests:
  - `UserServiceTest` — replaced the `@Mock StockMovementRepository` field with
    `@Mock LastActorActivityPort`; added `getLastAuditDate_DelegatesToPort`,
    `getLastAuditDate_NoActivity_ReturnsEmpty`, `getAllLastAuditDates_DelegatesToPort` (this class
    had zero prior coverage for either method). Confirmed RED first (`./mvnw -q
    -Dtest=UserServiceTest test`: 3 `NullPointerException`s, `this.stockMovementRepository` still
    null in `UserService`, before the port was wired in), then GREEN (11 tests) after the change.
  - New `LastActorActivityAdapterTest` (4 cases: returns the movement's timestamp, returns empty
    with no movements, maps multi-row results to a `Map`, returns an empty map with no rows).
    Confirmed RED first (adapter class didn't exist — compile failure), then GREEN.
  - `ArchitectureTest.moduleDependencyEdgesMatchApprovedBaseline` — ran before editing the
    baseline file to see the actual diff first (per the file's own review discipline): exactly one
    new edge, `inventory -> identity`, nothing else — confirming the port/adapter split produced
    only the intended edge, not an accidental additional cross-module dependency.
- Result: pass. `./mvnw -q -Dtest=ArchitectureTest,LocationServiceTest,UserServiceTest,
  LastActorActivityAdapterTest test` — all green. `./mvnw -q -Dtest=InventoryServiceApplicationTests
  test` (full `@SpringBootTest` context, H2, no Docker needed) also passes, confirming Spring
  actually wires `LastActorActivityAdapter` as the `LastActorActivityPort` bean into `UserService`
  at runtime, not just at compile time. `./mvnw -q -DskipTests compile` and
  `./mvnw -q -DskipTests test-compile` pass.
- Independent review (2026-09-09): confirmed the adapter preserves the existing queries/mapping
  and Spring wires the port; re-ran `./mvnw -q -Dtest=UserServiceTest,LastActorActivityAdapterTest,
  ArchitectureTest,InventoryServiceApplicationTests test` (23 passed, including context boot) and
  `git diff --check` (passed). Flagged a spec-alignment gap: this record required the
  `docs/specs/spring-domain-modular-monolith.md` §6.2 target graph to be amended alongside T-2,
  but it hadn't been — fixed by adding `inventory -> identity` to that graph with the narrow-port-
  use-case explanation. Also corrected this log's `UserServiceTest` count above (was wrongly
  stated as 14; the actual green run is 11). No source files changed by this review.

### T-3 — Move inventory entities/repositories/exceptions out of legacy packages

Pure mechanical package move, no logic changes: `git mv` for each file, package-declaration and
internal-import fixes on the moved files themselves, then a repo-wide import-path update for every
caller. Kept deliberately free of behavioral edits per the plan's "keep mechanical movement
distinct from behavioral edits within the history" instruction — every method body is byte-for-byte
unchanged; only import/package lines moved.

- Moved (via `git mv`, git recorded all ten as renames):
  - `models/inventory/LocationInventory.java` -> `inventory/domain/LocationInventory.java`
  - `models/audit/StockMovement.java` -> `inventory/domain/StockMovement.java` (R-2; added an
    explicit `import ...models.audit.AuditLog;` since the two are no longer in the same package)
  - `exceptions/{InventoryNotFoundException,InsufficientInventoryException,
    InvalidInventoryOperationException}.java` -> `inventory/domain/`
  - `repositories/{LocationInventoryRepository,StockMovementRepository,
    StockMovementSpecifications,InventoryTotalsRepository}.java` -> `inventory/infrastructure/`
  - `repositories/projections/StockMovementHistoryView.java` -> `inventory/infrastructure/
    StockMovementHistoryView.java` (flattened; no other module uses a `projections` subpackage
    convention, so matched how the other repositories sit directly in `infrastructure`)
  - `ShipmentItemAllocationRepository` deliberately NOT moved — re-confirmed zero production
    callers via grep (only its own file references it), shipments' territory per the baseline
    inventory, decided explicitly rather than silently left or silently deleted.
- Import updates: repo-wide `perl` substitution of the ten old fully-qualified names across
  `src/main` and `src/test` (verified each replacement target count with grep before running).
  Five files had `import ...repositories.*;` or `...exceptions.*;` wildcard imports that stopped
  covering the moved classes once they left those packages — added explicit imports for the
  specific moved classes still needed in `ShipmentService`, `LocationInventoryService`,
  `StockMovementService`, and two test files (`InventoryAggregateControllerIT`,
  `LocationInventoryServiceTest`); removed two wildcard imports in `LocationInventoryService`
  entirely once nothing else in `repositories`/`exceptions` was left for them to cover (confirmed
  via IDE unused-import diagnostics, not guessed). `AuditLog.java` gained an explicit
  `import ...inventory.domain.StockMovement;` for its `movements` association (previously
  same-package, no import needed). `GlobalExceptionHandler.java` gained explicit imports for the
  three moved exceptions (previously same-package).
- ArchUnit baseline (`module-dependency-edges-baseline.txt`): ran `ArchitectureTest` before
  editing the file (per its own review discipline) to see the actual new-edge diff, not a guessed
  one. Nine new edges, added with a justification block: `analytics/controllers/dtos/exceptions/
  models/services -> inventory` (mechanical — same underlying dependency as the pre-move `->
  models`/`-> repositories`/`-> exceptions` edges, only the target package renamed);
  `inventory -> sites` and `inventory -> dtos`/`inventory -> utils` (genuinely new *directions*,
  from code that already lived inside the moved files, now recomputed against the new module
  boundary). Pruned `inventory -> repositories` in the same review — grep confirmed `inventory/**`
  no longer imports anything from the legacy `repositories` package. `models -> inventory` is the
  R-2 transitional-debt edge (`AuditLog.movements -> StockMovement`), documented as the SAME
  bidirectional JPA association that already existed entirely inside `models.audit` before the
  move (spec §6.1 rule 8 existing-relationship, not new). `inventory -> dtos` paired with
  `dtos -> inventory` is a second genuinely bidirectional pair (alongside `models <-> inventory`),
  same pre-existing-tangle character.
- Frozen ArchUnit stores: `legacyTechnicalLayerPackagesDoNotGrow`'s store only needed pruning (9
  obsolete violations removed, 0 new — moving classes OUT of legacy packages can only shrink that
  rule's violation set), and `allowStoreUpdate=true` alone handled it correctly on the first run.
  `repositoriesAreOnlyAccessedByServicesOrRepositories`'s store needed genuine regeneration: empirically confirmed
  that `allowStoreUpdate=true` alone only prunes obsolete entries and does NOT auto-accept new
  ones — a first run with just that flag still reported 32 failures (the same pre-existing debt,
  now under new class names). Treated this the same way Phase 5a's log describes handling a
  rule-text change: deleted the existing store file and its `stored.rules` entry, flipped
  `allowStoreCreation=true` too, ran once to recreate a fresh baseline (144 lines, matching the old
  store's 144 — same violation count, confirming this is the identical pre-existing debt renamed,
  not new debt), then reverted both flags to `false` and re-ran to confirm the finalized store
  passes. Diffed both stores against saved pre-change copies before touching them, per the "diff
  reviewed line by line" discipline: legacy-package store lost exactly the 9 lines for the moved
  classes and gained none; repository-access store's violation set is line-count-identical (144 to
  144) with every entry's class names updated to the new package, confirming no new violations and
  no silently-dropped ones.
- Tests: no new tests written — this is a pure mechanical move with no new behavior to specify.
  Relied on all test sources compiling and selected regression tests passing unchanged as the
  correctness proof (any accidental behavior change from a missed import or wrong target would
  show up as a compile error or a test failure, not silently).
- Result: pass, after the fix recorded in the entry below. `./mvnw -q clean test-compile` and
  `./mvnw -q clean compile` both clean on a from-scratch build. `./mvnw -q -Dtest=ArchitectureTest
  test` — all 7 rules pass against the finalized stores, confirmed stable across two independent
  clean rebuilds (see below). `./mvnw -q -Dtest=ArchitectureTest,LocationServiceTest,
  UserServiceTest,LastActorActivityAdapterTest,LocationInventoryServiceTest,
  StockMovementServiceActiveStatusDerivationTest,KujiBoxServiceTest,ShipmentServiceOverrideTest,
  StockMovementActorNameTest,InventoryServiceApplicationTests test` — all green, including the
  full `@SpringBootTest` context boot (H2). These are the actual existing unit test classes
  touching the moved code (`StockMovementServiceActiveStatusDerivationTest`/
  `ShipmentServiceOverrideTest`, not the nonexistent `StockMovementServiceTest`/
  `ShipmentServiceTest` this entry originally named) — each exercises one narrow behavior slice
  (active-status derivation; shipment override handling), not full-surface coverage of
  `StockMovementService`/`ShipmentService`. The actual correctness proof for a pure package move
  is the full clean compile plus the full `ArchitectureTest` suite; these targeted tests are
  supporting evidence for their specific slices, not a substitute for that. `git status` confirms
  all ten production moves and the one test-file move (see below) recorded as renames, no stray
  leftover files, no unintended deletions.

## Review-driven fix: T-3 build blocker (2026-09-09)

Independent review found T-3 did not actually build clean: `./mvnw -q test-compile` (no `clean`)
had been masking two real compile failures with stale incremental output. `./mvnw -q clean
test-compile` reproduced both immediately.

- **Root cause**: my import-path sweep for T-3 only handled explicit FQCN imports and wildcard
  (`import ...repositories.*;`/`...exceptions.*;`) imports. It missed a third case: a file in the
  *same legacy package* as a moved class, which needed no import before the move at all.
  `AuditLogSpecificationsIT.java` and `StockMovementRepositoryIT.java` both live in
  `src/test/java/.../repositories/` (test tree) and referenced `StockMovementRepository` with no
  import, since it used to be same-package. Fixed by adding
  `import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;` to both.
- **Second instance of the same gap**: `StockMovementActorNameTest.java` sat in
  `src/test/java/.../models/audit/` (same package as the pre-move `StockMovement`) and called
  `movement.prePersist()` directly — `prePersist()` is deliberately package-private (per its own
  Javadoc: "so the actor-name fallback can be unit tested without a persistence context"), so a
  same-package test is not just a missing-import case here but a genuine access-scope dependency.
  Moved the test file (`git mv`) into `inventory/domain/` alongside `StockMovement`, updated its
  package declaration, and added an explicit `import com.mirai.inventoryservice.models.audit.AuditLog;`
  (needed now since `AuditLog` stays in `models.audit` per R-2 while the test moved to
  `inventory.domain`).
- **A second, independent bug surfaced while fixing the first**: after fixing the two IT files'
  imports, `ArchitectureTest.repositoriesAreOnlyAccessedByServicesOrRepositories` started failing
  with `StoreUpdateFailedException` even though nothing in `inventory/**` production code had
  changed. Diffing the frozen store against a temporarily-enabled `allowStoreUpdate=true` run
  showed the mismatch was three `DevSeedController` violations recorded under synthetic
  `lambda$25`/`lambda$3`/`lambda$4` method names in the store, vs. the same three violations
  reported against their *enclosing* named methods (`ensureDevEmployee`/`ensureMachinesExist`/
  `seedAll`) on the fresh run — despite `DevSeedController.java`'s only diff since being import
  lines (confirmed via `git diff`, 4 lines changed, all imports). This is lambda synthetic-name
  numbering instability across separate javac invocations, not a real violation change. Root
  cause: my original T-3 store regeneration ran against an *incremental* compile
  (`test-compile` without `clean`), which captured one particular lambda-numbering snapshot that
  didn't reproduce on the next from-scratch compile — the exact class of frozen-text fragility
  `ArchitectureTest.java`'s own comments warn about for a different, now-removed rule. Confirmed
  the pre-T3 original store (saved copy) had zero `lambda$` references, so this instability was
  introduced by my T-3 regeneration procedure, not pre-existing.
  - **Fix**: deleted the contaminated store entry and its `stored.rules` line, flipped
    `allowStoreCreation=true`/`allowStoreUpdate=true`, ran `./mvnw -q clean test-compile` (clean,
    this time) followed by the ArchitectureTest run to recreate the store against a from-scratch
    compile, reverted both flags to `false`, then verified stability by running
    `./mvnw -q clean test-compile` + `./mvnw -q -Dtest=ArchitectureTest test` **twice more**,
    independently, both passing. New store: 144 lines (same count as before — same underlying
    debt), zero `lambda$` references.
  - **Process lesson recorded for T-4 onward**: any future frozen-store regeneration in this
    checkpoint must run against `mvn clean test-compile`, never an incremental compile, and must
    be verified stable across at least two independent clean rebuilds before being treated as
    final — not just the one run that happens to pass.
- Re-verified after both fixes: `./mvnw -q clean test-compile` clean; `./mvnw -q -Dtest=
  ArchitectureTest,LocationServiceTest,UserServiceTest,LastActorActivityAdapterTest,
  LocationInventoryServiceTest,StockMovementServiceActiveStatusDerivationTest,KujiBoxServiceTest,
  ShipmentServiceOverrideTest,StockMovementActorNameTest,InventoryServiceApplicationTests test`
  all green; `git diff --check` passed.

### T-4 — Move inventory services into `inventory.application`; R-8 consolidation; drop dead injection

Split into a behavior-preserving delta (R-8 consolidation + dead-injection removal, done first,
while the three services still lived in `services`) and the mechanical package move — kept
separate so a behavior review of the small delta isn't buried inside a 3-file, ~40-caller package
move, matching the plan's "keep mechanical movement distinct from behavioral edits" instruction.

**R-8 consolidation (behavior-preserving, done before the move):**

- Changed: `sites/application/LocationService.java` — `DEFAULT_SITE_CODE` and `getDefaultSiteId()`
  went from `private` to `public` (no logic change), giving `StockMovementService`/
  `LocationInventoryService` one shared, already-tested source of truth instead of each keeping
  its own private copy.
- `services/StockMovementService.java` (pre-move package): removed the `SiteRepository
  siteRepository` field/constructor param (confirmed its only use was the duplicated
  `getDefaultSiteId()`) and the private `DEFAULT_SITE_CODE`/`getDefaultSiteId()` pair; added a
  `LocationService locationService` constructor param. `getNotAssignedLocationId()` — a full
  structural duplicate of `LocationService.getNotAssignedLocation()` (T-1) — now delegates to it
  directly. A second, structurally different NOT_ASSIGNED lookup inline in
  `createInventoryWithTracking` (missing the storage-location existence check
  `getNotAssignedLocation()` has) was left structurally as-is and only had its site-id source
  swapped to `locationService.getDefaultSiteId()`, specifically to avoid changing which exception
  type that edge case throws (`LocationNotFoundException` today vs.
  `StorageLocationNotFoundException` from the facade) — a deliberate, narrower fix than fully
  reusing the facade there, to honor "without changing site-resolution behavior" literally, not
  just approximately. Also dropped the dead `KujiBoxTierRepository kujiBoxTierRepository`
  field/constructor param (flagged unused by the IDE since T-0; zero call sites) and the
  now-unused `SiteNotFoundException`/`StorageLocationNotFoundException` imports.
- `services/LocationInventoryService.java` (pre-move package): same `SiteRepository` removal and
  `LocationService` injection. `listInventoryByStorageLocationCode`/`getLocationByCode`'s
  `getDefaultSiteId()` calls now go through `locationService.getDefaultSiteId()`.
  `getStorageLocations()`/`getStorageLocationByCode(String)` (which used the bare
  `DEFAULT_SITE_CODE` string, not the UUID) now reference `LocationService.DEFAULT_SITE_CODE`
  instead of keeping their own copy — `storageLocationRepository` itself stays (still used at
  `listInventoryByStorageLocation`'s existence check, an unrelated purpose R-8 doesn't touch).
  Removed the now-unused `Site`/`SiteNotFoundException` imports.
- Tests: `StockMovementServiceActiveStatusDerivationTest` and `LocationInventoryServiceTest` are
  the only files constructing these two classes directly (`new StockMovementService(...)` /
  `@InjectMocks`) — updated their mocks (`@Mock SiteRepository`/`StorageLocationRepository`/
  `KujiBoxTierRepository` removed where now-unused, `@Mock LocationService` added) and the explicit
  constructor call in the first. Grepped both files first to confirm `siteRepository`/
  `storageLocationRepository`/`kujiBoxTierRepository` mocks were never stubbed in either test body
  (pure pass-through), so no test assertions needed to change, only wiring.
- Verified: `./mvnw -q clean test-compile` clean; `./mvnw -q -Dtest=
  StockMovementServiceActiveStatusDerivationTest,LocationInventoryServiceTest,LocationServiceTest,
  ArchitectureTest test` all green (module edges unaffected — `services -> sites` was already
  approved, and these classes hadn't moved packages yet at this point).

**Mechanical move:**

- Moved (via `git mv`): `services/{StockMovementService,LocationInventoryService,
  InventoryAggregateService}.java` -> `inventory/application/`.
- Caller inventory before touching imports (learned from the T-3 review finding — checked
  same-package-without-import explicitly this time, not just FQCN/wildcard imports): grepped every
  file matching `\bStockMovementService\b`/`\bLocationInventoryService\b`/
  `\bInventoryAggregateService\b` (23/4/4 files respectively), then cross-referenced against files
  literally inside `main/java/.../services/` and `test/java/.../services/` (same package as the
  pre-move classes) with no explicit import — found 11 such files (`EventOutboxService.java`,
  `KujiBoxService.java`, `ShipmentService.java` in main; 8 test files including the moved classes'
  own tests) needing a brand-new import they'd never needed before. Also verified zero
  `import ...services.*;` wildcards exist repo-wide (would have hidden the same gap), and checked
  every remaining comment/Javadoc/string-literal reference (`ProductStockStateWriter.java`,
  `LastActorActivityAdapter.java`, `LocationService.java`'s own T-1 comment,
  `ProductStockStateWriterTest.java`, `SiteProductLegacyCompatibilityIT.java`) to confirm they were
  prose, not code, and needed no change.
- One caller needed special confirmation, not just an import: `CatalogEntityAccessCallerSetTest`
  compares ArchUnit-reported origins by `getSimpleName()`, not fully-qualified name (traced through
  its `actualOrigins()` method) — confirmed the `LocationInventoryService`/`StockMovementService`
  move cannot break its expected-origin set, since a package move never changes a class's simple
  name. No change needed to that file.
- Fixed the 11 same-package gaps (12, counting `StockMovementServiceActiveStatusDerivationTest`
  testing its own now-moved subject, which the initial pass through the file list missed on first
  compile and required an added import for `StockMovementService` itself) with explicit imports.
  `StockMovementService.java`'s own imports also needed two additions
  (`services.EventOutboxService`, `services.SupabaseBroadcastService` — used via
  `EventOutboxService.StockEventContext`), since those two classes stay in the legacy `services`
  package while `StockMovementService` moved out.
- ArchUnit baseline: ran `ArchitectureTest` before editing the baseline file, per the established
  discipline. One new edge: `inventory -> repositories` (`StockMovementService` still reaches
  `AuditLogRepository` via the `repositories.*` wildcard — `AuditLogRepository` itself is `audit`
  module territory, not moved, out of this checkpoint's scope). Added with justification,
  explicitly confirming every other edge the move touches was already covered by existing
  `inventory -> {catalog,identity,models,services,sites,dtos,utils}` lines (only the *source*
  module of pre-existing dependencies changed, from `services` to `inventory`) and that no new
  `services -> inventory` edge was needed (EventOutboxService/KujiBoxService/ShipmentService
  already had that edge approved for the pre-move class).
- Frozen ArchUnit stores: applied the corrected procedure from the T-3 fix (clean build, verify
  stability across two independent clean rebuilds — not just one passing run). Deleted both store
  entries and `stored.rules` entirely, flipped both flags, ran `./mvnw -q clean test-compile`
  followed by `ArchitectureTest` to recreate fresh baselines against a from-scratch compile,
  reverted both flags to `false`, then re-ran `./mvnw -q clean test-compile` + `./mvnw -q
  -Dtest=ArchitectureTest test` **twice more**, independently, both passing. Confirmed zero
  `lambda$` references in either regenerated store (the T-3 fragility class). Diffed both new
  stores against pre-T4 saved copies: `legacyTechnicalLayerPackagesDoNotGrow`'s store lost exactly
  the 3 lines for the moved classes (377 -> 374 lines) and gained nothing;
  `repositoriesAreOnlyAccessedByServicesOrRepositories`'s store is line-count-identical (144 to
  144), with the single expected cosmetic diff — `InventoryAggregateController`'s constructor
  signature text now shows `InventoryAggregateService`'s new package — confirming no new debt and
  nothing silently dropped.
- Result: pass. `./mvnw -q clean test-compile` clean (verified 3 times total across the store
  regeneration). `./mvnw -q -Dtest=ArchitectureTest test` — all 7 rules pass, stable across
  repeated clean rebuilds. `./mvnw -q -Dtest=ArchitectureTest,LocationServiceTest,UserServiceTest,
  LastActorActivityAdapterTest,LocationInventoryServiceTest,
  StockMovementServiceActiveStatusDerivationTest,KujiBoxServiceTest,
  KujiBoxServiceCatalogFacadeMigrationTest,ShipmentServiceOverrideTest,StockMovementActorNameTest,
  EventOutboxServicePublishTest,EventOutboxServiceCreateEventTest,EventOutboxServiceDeadLetterTest,
  CatalogEntityAccessCallerSetTest,InventoryServiceApplicationTests test` — all green, including
  the full `@SpringBootTest` context boot. `git status` confirms the three production moves
  recorded as renames, no stray leftover files.
