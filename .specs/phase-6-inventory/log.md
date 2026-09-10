# Implementation log

## Current handoff

- Status: 6a baseline inventory complete and reviewed. T-0 done (ArchUnit `isRepositoryClass()`
  extended). T-1 done (`LocationService.getNotAssignedLocation()` overloads added). T-2 done
  (`LastActorActivityPort`/`LastActorActivityAdapter`, R-3 cycle resolved). T-3 done (moved
  `LocationInventory`, `StockMovement`, their repositories/specifications/projection, and the
  three inventory exceptions into `inventory.domain`/`inventory.infrastructure`). T-4 done (moved
  `StockMovementService`/`LocationInventoryService`/`InventoryAggregateService` into
  `inventory.application`; dropped the dead `KujiBoxTierRepository` injection; consolidated the
  R-8 `DEFAULT_SITE_CODE` duplication through `LocationService`). T-5 done: introduced
  `InventoryOperations`/`InventoryQueries` in `inventory.application` and migrated all eight named
  external callers (`ShipmentService`, `KujiBoxService`, `MachineDisplayService`,
  `ProductReportBundleService`, `ForecastService`, `AnalyticsService`, `AuditLogService`,
  `SalesRollupRecomputeService`) off direct `LocationInventoryRepository`/`StockMovementRepository`/
  `InventoryTotalsRepository` access. Independent review of T-5 found two P2s, both fixed in the
  same session: (1) `InventoryQueries.findHistoryByItemId` returned the infrastructure-layer
  `StockMovementHistoryView` projection to callers outside `inventory` — added an
  application-owned `StockMovementHistoryEntry` record and mapped to it, so `ForecastService`/
  `ProductReportBundleService` no longer import anything from `inventory.infrastructure`; (2) the
  6a task list's own test plan required a Testcontainers-or-equivalent IT proving `applyDelta`
  inside a caller-opened transaction rolls back inventory + movement + outbox together (AC-1)
  before 6a closes — this had been deferred to 6c, which the review correctly flagged as
  premature; added `InventoryOperationsCallerTransactionIT` (an `@SpringBootTest` against the H2
  `test` profile, same class of proof `StockMovementOutboxAtomicityIT` already uses — no actual
  Testcontainers/Postgres needed for a transaction-propagation proof) and it passes. A second
  independent review pass confirmed both fixes and flagged one more gap: the 6a task list's
  delete-on-zero/find-or-create IT (covering the now-shared `ShipmentService`/`KujiBoxService`
  path) was still outstanding, and the log's own summary still read "unit-level proof only" after
  the transaction-proof fix landed. Added `InventoryOperationsSharedCallerPathIT` — see the task
  record and second "Review-driven fix" entry below — and corrected the stale summary.
- T-6 done: moved `InventoryAggregateController`/`StockMovementController` and their DTOs/mappers
  into `inventory.api`; `InventoryAggregateController.getInventoryTotals` now goes through a new
  `InventoryQueries.findAllInventoryTotals`, retiring one of `ArchitectureTest`'s frozen
  `repositoriesAreOnlyAccessedByServicesOrRepositories` violations. `LocationAggregateController`/
  `Service`/`Repository`/`LocationWithCountsDTO` moved into `sites.api`/`sites.application`/
  `sites.infrastructure` per R-1. `packages/contracts/openapi.json` re-verified byte-identical
  after the move (regenerated via `OpenApiContractExportTest`, `diff` against the pre-move copy is
  empty). `LocationInventoryController`/`LocationInventoryMapper`/`LocationInventoryResponseDTO`/
  `InventoryRequestDTO` deliberately NOT moved — new R-9, see below. T-7 done: added the live
  (never frozen) `noProductionClassOutsideInventoryDependsOnInventoryInfrastructure` rule (exempts
  only `DevSeedController`/`AnalyticsSeedService` by FQN, the same two as catalog's version) and
  `InventoryOperationsCallerSetTest` pinning the exact eight-class origin set from T-5. No
  `identity -> inventory` edge exists (confirmed by grep against the baseline file). T-8 done:
  recorded R-1/R-2 and the `inventory -> identity` (R-4/R-3) edge in
  `docs/specs/spring-domain-modular-monolith.md` §5/§6.2/§11 and in `inventory`'s and `sites'`
  `package-info.java`.
- Next action: 6a's own checklist (T-0 through T-8) is now fully done. Request independent review
  of the completed 6a slice before moving to 6b. R-9 (LocationInventoryController's catalog.api
  coupling) is recorded as new open debt, not a 6a blocker — no acceptance criterion required
  moving it.
- Committed 2026-09-09: `dbc2c1d` "feat(inventory): establish inventory module boundary (Phase 6a,
  T-0-T-4)" — T-0 through T-4 as one commit. The reviewer asked for a three-way split (guard/facade
  prep; mechanical entity/service moves; R-8 consolidation/dead-injection removal); the user then
  asked to commit it all as one instead, so the split was not carried out. T-5 onward should start
  its own commit(s) rather than accumulate further into this one.
- Surviving decisions: one branch/PR and one Full-tier record; five logical commits/review
  checkpoints with fix commits allowed. A production prerequisite can require a separate PR.
- Last verified (post-review fixes, both rounds): `./mvnw -q clean test-compile` clean, then
  `./mvnw -q clean test` (full unrestricted suite) — exit 0, zero failures/errors across every
  surefire report. `./mvnw -q -Dtest=ArchitectureTest test` re-run standalone after a second
  independent clean `test-compile` — 7/7 pass, stable across two independent clean rebuilds, **no
  frozen-store regeneration needed** (T-5's new cross-module edges fall under the `-> inventory`
  edges already approved at T-3/T-4). `InventoryOperationsCallerTransactionIT` — 2/2 pass, proving
  `applyDelta` inside a caller-opened `@Transactional` harness both rolls back
  `LocationInventory`/`StockMovement`/`EventOutbox` together on a later failure and commits all
  three together on success. `InventoryOperationsSharedCallerPathIT` — 2/2 pass, proving the
  find-or-create/delete-on-zero path through the real `ShipmentService.receiveShipment` /
  `undoReceiveShipmentItem` round trip and the real `KujiBoxService.openBox` /
  `closeBox` round trip (source drained to exactly zero and deleted; a destination that never had
  inventory gets created). Doc-level `git diff --check` and local-link checks from the planning
  pass still hold. T-6/T-7/T-8: `./mvnw -q clean test-compile` + `./mvnw -q clean test` (full
  unrestricted suite) both clean/green; `ArchitectureTest` (now 8 rules, up from 7) stable across
  two independent clean rebuilds after regenerating the two frozen stores it needed (see T-6
  section below); `InventoryOperationsCallerSetTest` and `CatalogEntityAccessCallerSetTest` both
  pass; `OpenApiContractExportTest` confirms `packages/contracts/openapi.json` byte-identical.
- Open risks: actual migration mechanism and old-writer compatibility (6b); global quantity/activity
  semantics and downstream consumers (still to reconcile before 6c); event payload coverage and
  targeted-refresh baseline (6c/6e). R-4 through R-8 below remain open, not yet resolved. New for
  T-5: `KujiBoxService`'s `undoDraw` legacy-inventory-restore branch (pre-decoupling draws that
  decremented `LocationInventory` at the box location) and `reopenBox`'s legacy/new-model reversal
  branches were migrated by mechanical call-site substitution only and are covered by
  `KujiBoxServiceTest`/`KujiBoxServiceCatalogFacadeMigrationTest` (Mockito) plus the generic
  transaction-propagation proof in `InventoryOperationsCallerTransactionIT`, but not by their own
  real-database round trip the way `openBox`'s source-removal and `closeBox`'s leftover-transfer-out
  branches now are via `InventoryOperationsSharedCallerPathIT`. No open AC requires that specific
  additional coverage; noted here as available future hardening, not a closing blocker. New for
  T-6 (R-9): `LocationInventoryController`/`LocationInventoryMapper`/`LocationInventoryResponseDTO`/
  `InventoryRequestDTO` were not moved into `inventory.api` — `LocationInventoryResponseDTO.item` is
  `catalog.api.ProductSummaryDTO` and `LocationInventoryMapper` `uses` `catalog.api.ProductMapper`;
  moving them would create an `inventory.api -> catalog.api` edge, which
  `ArchitectureTest.modulesDoNotDependOnAnotherModulesApi` forbids unconditionally (no
  baseline/exemption mechanism for that specific rule, unlike the frozen-store or module-edge
  checks). Resolving it properly needs a catalog-owned `catalog.application`-level product-summary
  read contract `inventory.api` can consume instead of catalog's own transport DTO — real design
  work, not a mechanical move; left in the legacy `controllers`/`dtos` packages, out of 6a's scope,
  flagged for a later checkpoint.

## Assumptions and decisions

- 2026-09-10: user agreed to slice-level implementation and review cadence. Checkpoints 6a–6e
  retain their existing scope and acceptance gates; T-numbered tasks are internal checklists,
  not user handoffs or separate review/commit gates. Batch mechanical work, use focused checks,
  and review after each completed slice. Keep required proof at the slice gate and resolve
  material decisions/migration prerequisites before dependent work. Updated the shared workflow,
  Phase 6 spec, and both parent plans; no application behavior or acceptance criteria changed.

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
- **T-5** (done): introduced `InventoryOperations` (`applyDelta`, `adjustQuantity`, `recordMovement`
  in field and pre-built-`StockMovement` forms, `saveMovement`/`saveMovements`, `findInventory`/
  `saveInventory`/`deleteInventory`, `syncProductTotals`) and `InventoryQueries` in
  `inventory.application`; migrated all eight named external callers off direct
  `LocationInventoryRepository`/`StockMovementRepository`/`InventoryTotalsRepository` access, by
  mechanical call-site substitution (no business-logic changes). Unit tests, the real-caller
  transaction IT, and the delete-on-zero/find-or-create shared-path IT all pass (see both
  review-driven fix records below) — nothing from T-5's own scope remains on the 6a closing
  checklist.
- **T-6** (done): moved `InventoryAggregateController`/`StockMovementController` and their
  DTOs/mappers into `inventory.api`; `InventoryAggregateController.getInventoryTotals` now goes
  through the new `InventoryQueries.findAllInventoryTotals` instead of `InventoryTotalsRepository`
  directly. `packages/contracts/openapi.json` re-verified byte-identical via
  `OpenApiContractExportTest`. `LocationAggregateController`/`Service`/`Repository` moved into
  `sites.api`/`sites.application`/`sites.infrastructure` per R-1, documented in both classes'
  Javadoc and the `sites` `package-info.java` as an approved cross-module read projection — no
  ArchUnit exemption entry was mechanically required for the move itself, since the repository
  reaches its three modules' tables via raw native SQL string literals (no Java-level import
  ArchUnit can see), not a cross-module class dependency; the "named exemption" this task
  originally anticipated turned out to be documentation, not a rule-list entry. `LocationInventoryController`/
  `LocationInventoryMapper`/`LocationInventoryResponseDTO`/`InventoryRequestDTO` deliberately NOT
  moved — new R-9 (see "Open risks" above): they depend on `catalog.api.ProductSummaryDTO`/
  `ProductMapper`, and moving them into `inventory.api` would violate the live
  `modulesDoNotDependOnAnotherModulesApi` rule (no exemption mechanism for it). Two new module edges
  needed baseline approval (`inventory -> validation`, `sites -> models`/`sites -> utils`,
  `validation -> inventory` — all mechanical, same underlying dependency as before the move under a
  different source-package name); see the baseline file's sixth reviewed-addition entry.
- **T-7** (done): added the live `noProductionClassOutsideInventoryDependsOnInventoryInfrastructure`
  rule (exempts only `DevSeedController`/`AnalyticsSeedService` by FQN, confirmed by grep to be the
  only two remaining direct `inventory.infrastructure` callers outside `inventory` — matches the
  catalog precedent exactly, no frozen baseline needed since T-5 already retired every other direct
  caller). Added `InventoryOperationsCallerSetTest`, mirroring `CatalogEntityAccessCallerSetTest`'s
  role but at class (not per-method) granularity: pins the exact eight classes calling
  `InventoryOperations`/`InventoryQueries` from outside `inventory`, confirmed by grep to match
  T-5's list with zero drift. No `identity -> inventory` edge exists in the baseline file
  (confirmed by grep — R-3 still holds).
- **T-8** (done): recorded R-1 (sites' cross-module read projection), R-2 (`StockMovement`'s move
  and its transitional `AuditLog` association), and the `inventory -> identity` edge (R-4/R-3) in
  `docs/specs/spring-domain-modular-monolith.md` §5 (module ownership table), §6.2 (dependency
  graph), and §11 (persistence/table-ownership rules); also documented in `inventory`'s and
  `sites`'s `package-info.java`.

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

### T-5 — Introduce `InventoryOperations`/`InventoryQueries`; migrate external callers

**Design (recorded before implementation):** two new classes in `inventory.application`, both
thin over the existing `inventory.infrastructure` repositories — no new query behavior, only a
narrower documented entry point for callers outside the `inventory` module (AC-1). `applyDelta`/
`adjustQuantity`/`recordMovement` intentionally do **not** validate the resulting quantity (e.g.
reject a delta that would go negative); every existing caller already validates before calling its
inline add/remove helper with its own message/exception type, and centralizing that check here
would have silently changed several callers' error messages — a behavior change T-5's "mechanical
call-site substitution" scope does not license. `recordMovement` has two overloads: one taking
individual fields (mirrors `StockMovementService`'s own movement-building shape, used by
`ShipmentService`) and one taking an already-built `StockMovement` (used by `KujiBoxService`, which
already assembles the full builder inline with kuji-specific metadata — decomposing back into
fields would risk a transcription bug across ~10 builder properties per call site for no benefit).
`saveMovement`/`saveMovements` are separate, deliberately outbox-free methods: `MachineDisplayService`
writes zero-quantity ledger rows today with **no** `eventOutboxService.createStockMovementEvent`
call (confirmed by reading every call site before migrating), and two `KujiBoxService` "birth"
sites (auto-create mint rows) share that same no-outbox shape — using `recordMovement` there would
have added outbox publishing where none exists today, an unintended behavior change.

- **Scope decision**: the spec's own Delivery decisions note ("Kuji/lootbox site migration...
  remain in their later phases. Necessary inventory facade/event integration may update those
  callers without migrating their entire domain") governs `KujiBoxService`'s and
  `MachineDisplayService`'s inclusion here: T-5 rewires their `location_inventory`/
  `stock_movements` call sites onto the new facade, but does not touch their own domain logic,
  package location, or control flow otherwise — full migration into a `kuji`/`displays` module
  remains Phase 7. R-5's `aggregateKujiDailyPayouts` debt (the query's kuji-flavored shape living
  inside `inventory`'s own repository, not `kuji`'s) is preserved as-is; the call site was moved
  from `KujiBoxService` calling `StockMovementRepository` directly to calling it through
  `InventoryQueries.aggregateKujiDailyPayouts` — satisfying "off direct repository access"
  without resolving the underlying debt, exactly as R-5 anticipated.
- **`syncProductTotals` consolidation**: `ShipmentService` and `KujiBoxService` already called
  `StockMovementService.syncProductTotals(...)` directly (legitimate — `StockMovementService` is
  already `inventory.application`, not a repository). Routed both through
  `InventoryOperations.syncProductTotals` instead (which delegates to
  `StockMovementService.syncProductTotals`) so the two migrated callers depend only on the new
  narrow facade, not on `StockMovementService`'s full internal API surface — consistent with T-5's
  own task text naming `syncProductTotals` as one of `InventoryOperations`'s three methods.
- Changed (production):
  - New: `inventory/application/InventoryOperations.java`, `inventory/application/InventoryQueries.java`.
  - `services/ShipmentService.java`: `addToInventory`/`addToNotAssignedInventory` now call
    `inventoryOperations.adjustQuantity` + `.recordMovement(fields...)` (metadata needs the
    post-save inventory id, so these use the two-step form, not `applyDelta`).
    `removeFromInventory`/`removeFromNotAssignedInventory` use `inventoryOperations.findInventory`
    (preserving the existing `.orElseThrow(...)` custom messages exactly) +
    `.recordMovement(fields...)` + `.saveInventory`/`.deleteInventory`. Dropped the now-unused
    `StockMovementRepository`/`LocationInventoryRepository`/`EventOutboxService`/
    `StockMovementService` fields and constructor params.
  - `services/KujiBoxService.java`: all ~28 call sites across `executeKujiSourceRemoval`,
    `executeKujiCounterReturn`, `executeKujiTransfer`, `reopenBox`'s new/legacy-model reversal
    branches, `mintAutoCreatedAtBox`, `recordDraw`, `undoDraw`, `openBox`'s auto-create/source-removal
    branches, `closeBox`'s auto-remove branch, `addTier`'s auto-create branch, `patchTier`, and
    `getDailyPayouts` migrated to `inventoryOperations`/`inventoryQueries` equivalents, one-for-one,
    with no control-flow changes. Dropped the now-unused `LocationInventoryRepository`/
    `StockMovementRepository`/`EventOutboxService`/`StockMovementService` fields and constructor
    params.
  - `services/MachineDisplayService.java`: all `stockMovementRepository.save`/`.saveAll` sites
    (all no-outbox, zero-quantity display-lifecycle rows) now call
    `inventoryOperations.saveMovement`/`.saveMovements` — pure pass-through, confirmed no behavior
    change (no outbox call added or removed).
  - `services/ProductReportBundleService.java`, `services/ForecastService.java`,
    `services/AnalyticsService.java`, `services/AuditLogService.java`,
    `analytics/application/SalesRollupRecomputeService.java`: read-only repository fields replaced
    1:1 with `InventoryQueries` equivalents (`sumQuantityByProductId`, `findByProductId`,
    `findAllStockTotalsMap`, `findHistoryByItemId`, `findByAuditLogIdWithItem`,
    `aggregateSalesByItemAndDate`) — every call site's arguments are unchanged, only the receiver.
- **Out of scope for T-5** (not in the argument list, left as direct repository access for a later
  checkpoint): `ActivityFeedService`, `EventOutboxService`'s own internal repository use,
  `AuditLogMapper`/`AuditLogDTOMapper`/`StockMovementMapper`, and `AuditLogSpecifications`/
  `StockMovementSpecifications` (the `JpaSpecificationExecutor`-based `getAuditLog`/
  `findAll(spec, pageable)` pattern needs a genuinely scoped-repository redesign, which is 6c's
  "trusted context, scoped repositories" territory (AC-2/AC-3), not 6a's boundary work).
- Tests:
  - New `InventoryOperationsTest` (10 cases): `adjustQuantity` create/update/delete-on-zero;
    `recordMovement` both overloads save + publish outbox; `applyDelta` sign-based from/to-location
    assignment for both directions; `saveMovement`/`saveMovements` save without publishing outbox;
    `syncProductTotals` delegates to `StockMovementService`. Pins the exact sequence every migrated
    caller relied on inline.
  - Updated constructor wiring and mock types (repository mocks -> `InventoryOperations`/
    `InventoryQueries` mocks, with `verify`/`when` call sites remapped to the matching facade
    method) in `AuditLogServiceShipmentEventTest`, `MachineDisplayServiceBatchQueryGuardTest`
    (also fixed an accidental blanket-regex rename that briefly renamed the unrelated
    `machineDisplayRepository.saveAll` call — caught and reverted before running),
    `MachineDisplayServiceNotificationTest`, `ShipmentServiceOverrideTest`, `KujiBoxServiceTest`,
    `KujiBoxServiceCatalogFacadeMigrationTest`, `AnalyticsServiceTest`. `KujiBoxServiceTest`'s
    `toResponseDTO_batchesTierInventoryLookupIntoSingleQuery` test and its `addSlip` stub for
    `findByLocation_IdAndProduct_IdIn` were already dead against current production code (that
    repository method isn't called anywhere in `KujiBoxService` any more — kuji prize counts live
    on the tier, not `location_inventory`, per the test's own comment) — removed the stale stub,
    kept the `never()` assertions rewritten against `inventoryOperations`.
  - IT-level coverage for the task list's originally-scoped caller-transaction rollback proof was
    initially deferred to 6c; independent review correctly flagged that as premature (the 6a test
    plan itself requires it). Added in a follow-up fix — see "Review-driven fix: T-5 P2 findings"
    below.
- Result: pass. `./mvnw -q clean test` (full suite, unrestricted) — exit 0, zero failures/errors,
  run twice independently. `./mvnw -q clean test-compile` + `./mvnw -q -Dtest=ArchitectureTest
  test` — 7/7 rules pass, stable across two independent clean rebuilds, **no frozen-store
  regeneration needed** (new edges fall under already-approved `-> inventory` target-module
  coverage from T-3/T-4). Targeted suite (T-3/T-4's list plus every T-5-touched test class) also
  green standalone. `git status` confirms two new production files, one new test file, and the
  eight caller files plus seven test files modified in place — matches the expected T-5 footprint.

## Review-driven fix: T-5 P2 findings (2026-09-09)

Independent review of T-5 found two P2s. No business-behavior regression found; the limited
Kuji/display migration scope was confirmed supported by the spec. Both P2s fixed same-session.

- **P2-1, infrastructure leak**: `InventoryQueries.findHistoryByItemId` returned
  `inventory.infrastructure.StockMovementHistoryView` (a Spring Data projection interface) to
  callers outside `inventory` — `ForecastService` and `ProductReportBundleService` both imported
  it directly, which would fail T-7's planned `noProductionClassOutsideInventoryDependsOn
  InventoryInfrastructure` rule once added.
  - Fix: new `inventory/application/StockMovementHistoryEntry.java` — a record mirroring
    `StockMovementHistoryView` field-for-field (`id`, `at`, `reason`, `quantityChange`,
    `previousQuantity`, `currentQuantity`, `fromLocationId`, `toLocationId`), with a
    package-private `from(StockMovementHistoryView)` factory. `InventoryQueries
    .findHistoryByItemId` now maps the repository's result through it before returning.
    `ForecastService`/`ProductReportBundleService` updated to the new type and record-accessor
    call sites (`.getAt()` -> `.at()` etc. — mechanical, no logic change; verified every read-only
    call site the type touches, not just the ones that happened to compile).
  - Verified: `./mvnw -q -DskipTests compile` clean (production only, isolates this fix from the
    IT fix below). No test referenced `StockMovementHistoryView`/`StockMovementHistoryEntry`
    directly, so no test-side churn.
- **P2-2, missing 6a-closing IT proof**: the 6a task list's own test plan required a Testcontainers
  (or equivalent real-transaction) IT proving `applyDelta` inside a caller-opened transaction
  rolls back `LocationInventory`/`StockMovement`/`EventOutbox` together — Mockito unit tests
  cannot verify real transaction propagation. The original T-5 entry deferred this to 6c; review
  correctly identified that as premature since 6a's own plan requires it before closing, and
  `./mvnw clean test` doesn't select `*IT` classes so its absence wasn't caught by "all green."
  - Fix: new `inventory/application/InventoryOperationsCallerTransactionIT.java`. Mirrors
    `StockMovementOutboxAtomicityIT`'s shape (a real `@SpringBootTest` against the `test` profile's
    H2 database, deliberately not wrapped in an outer rolled-back test transaction so the
    harness's own commit/rollback is real) rather than `AdjustToKafkaIT`'s Kafka-focused one, since
    this proof is about transaction propagation, not Kafka delivery. A nested `@TestComponent`
    (`CallerTransactionHarness`, registered via `@Import` since Spring Boot's component scan
    doesn't pick up test-source classes automatically) stands in for a production caller: its own
    `@Transactional` method calls `inventoryOperations.applyDelta(...)` partway through, then
    either returns or throws. Two cases: (1) throws after `applyDelta` — asserts the
    `LocationInventory` row, `StockMovement` row and `EventOutbox` row are all absent afterward;
    (2) returns normally — asserts all three persisted together with the expected end-state
    (quantity 5, one movement with `quantityChange=5`, one `stock_movement`-typed outbox row).
  - Design note: confirmed no Testcontainers/Postgres container was actually needed — this class
    of proof (does `@Transactional`'s default `REQUIRED` propagation actually join the caller's
    transaction) only needs a real relational transaction, and the project's own `test` profile
    already runs against H2 for exactly this reason (`StockMovementOutboxAtomicityIT` does the
    same). Reserved the heavier Testcontainers-Postgres path for whatever 6c/6e work genuinely
    needs Postgres-specific behavior (e.g. native SQL, JSONB), not used here.
  - Verified: `./mvnw -q -Dtest=InventoryOperationsCallerTransactionIT test` — 2/2 pass. Then the
    full suite re-run (`./mvnw -q clean test-compile` + `./mvnw -q clean test`, exit 0) and
    `ArchitectureTest` re-checked stable across a second independent clean rebuild (7/7, no store
    regeneration needed — the new IT and its nested `@TestComponent` both live in the
    already-approved `inventory.application` package).
- Result: pass. Both fixes verified together in one final `./mvnw -q clean test` run (exit 0,
  zero failures across every surefire report) plus the standalone `ArchitectureTest`
  double-clean-rebuild check described above.

## Review-driven fix: T-5 delete-on-zero/find-or-create IT (2026-09-10)

A second independent review pass — after re-running the full targeted suite standalone with JDK
21 (27 tests, zero failures/skips, `git diff --check` clean) — confirmed both P2 fixes above and
found one more gap: the 6a task list's own test plan (see "Test plan" under "6a task list") called
for a delete-on-zero/find-or-create IT covering the now-shared `ShipmentService`/`KujiBoxService`
path specifically, distinct from the transaction-propagation proof
`InventoryOperationsCallerTransactionIT` already gave via a synthetic harness. That coverage was
still outstanding, and the "Current handoff" summary hadn't been updated to say so after the first
review round — it still read "Unit-level proof only," which was stale once the transaction IT
landed and inaccurate about what was still missing.

- Fix: new `inventory/application/InventoryOperationsSharedCallerPathIT.java`. Unlike
  `InventoryOperationsCallerTransactionIT`'s synthetic `@TestComponent` harness, this drives the
  real, already-production-reachable public API of both callers end to end against the H2 `test`
  profile:
  - `ShipmentService.createShipment` + `.receiveShipment` (with an explicit destination allocation
    to a location that has no existing `LocationInventory` row) proves `addToInventory`'s
    find-or-create; `.undoReceiveShipmentItem` reversing the full received quantity proves
    `removeFromInventory`'s delete-on-zero.
  - `KujiBoxService.openBox` (one linked-existing-product tier, `sourceLocationId` pointing at a
    source row pre-seeded to exactly the tier's total quantity) proves `executeKujiSourceRemoval`'s
    delete-on-zero; the subsequent `.closeBox` with a `transferOutTargets` entry pointing the
    leftover at a *different* location that never had inventory for that product proves
    `executeKujiCounterReturn`'s find-or-create.
  - Needed one fixture correction mid-write: `openBox` rejects a parent product whose `kujiType`
    isn't `CUSTOM` (`"Product is not a custom kuji"`) — the initial seed didn't set it; caught by
    the test's own first run, not by review, and fixed before the test was considered done.
  - Confirmed `KujiBoxService.mintAutoCreatedAtBox` (migrated mechanically in T-5, per the original
    task record) has zero production callers — `addTier`'s and `transferInMore`'s auto-create
    branches both bypass `location_inventory` entirely (kuji counters are the source of truth for
    auto-created prizes), so this pre-existing dead method was not a viable find-or-create proof
    target and isn't exercised here. Not a T-5 regression or a new finding to act on — recorded so
    a future reader doesn't re-derive the same dead-end.
- Also fixed: the stale "Unit-level proof only" sentence in the "Current handoff" section and the
  T-5 bullet under "6a task list" — both now reflect that the delete-on-zero/find-or-create IT
  exists and passes.
- Verified: `./mvnw -q clean test-compile` clean, `./mvnw -q -Dtest=
  InventoryOperationsSharedCallerPathIT test` — 2/2 pass. Full suite re-run
  (`./mvnw -q clean test`, exit 0, zero failures) and `ArchitectureTest` re-checked stable across
  a second independent clean rebuild (7/7, no store regeneration needed — the new IT lives in the
  already-approved `inventory.application` package and only autowires existing `services`-package
  beans, the same pattern `InventoryOperationsCallerTransactionIT` and
  `StockMovementOutboxAtomicityIT` already use).
- Result: pass. All three of T-5's test classes (`InventoryOperationsTest`,
  `InventoryOperationsCallerTransactionIT`, `InventoryOperationsSharedCallerPathIT`) plus the full
  unrestricted suite pass together in one final `./mvnw -q clean test` run.

### T-6 — Move inventory/location-aggregate controllers, DTOs, mappers into their owning modules

- Changed (production):
  - `git mv` into `inventory/api/`: `InventoryAggregateController.java`, `StockMovementController.java`,
    `InventoryTotalDTO.java`, `ProductInventoryResponseDTO.java`, `ProductInventoryEntryDTO.java`,
    `StockMovementMapper.java`, `StockMovementResponseDTO.java`, `BatchAdjustStockRequestDTO.java`,
    `BatchAdjustLineDTO.java`, `TransferInventoryRequestDTO.java`, `BatchTransferInventoryRequestDTO.java`.
  - `git mv` into `sites/api/`, `sites/application/`, `sites/infrastructure/` respectively:
    `LocationAggregateController.java`, `LocationAggregateService.java`, `LocationAggregateRepository.java`,
    plus `LocationWithCountsDTO.java` into `sites/api/`.
  - `InventoryQueries.java`: added `findAllInventoryTotals()`, a pass-through to
    `InventoryTotalsRepository.findAllInventoryTotals()`.
  - `InventoryAggregateController.getInventoryTotals()`: now calls `inventoryQueries
    .findAllInventoryTotals()` instead of holding an `InventoryTotalsRepository` field directly —
    retires the method's frozen `repositoriesAreOnlyAccessedByServicesOrRepositories` violation
    (confirmed by diffing the regenerated store against the saved pre-move copy: exactly the three
    lines for this one call site dropped, nothing else).
  - `InventoryTotalsRepository.java`: import fix only (`InventoryTotalDTO`'s new package) — no
    logic change. Left returning `InventoryTotalDTO` (an `inventory.api` type) directly from
    `inventory.infrastructure`, matching its pre-existing shape; not refactored further, out of
    T-6's mechanical scope.
  - `LocationAggregateController`/`Service`/`Repository`: package-declaration and import fixes
    only, plus a Javadoc note each recording the R-1 decision. No logic changed — the repository's
    three native SQL strings (`ALL_LOCATIONS_WITH_COUNTS_SQL` etc.) are untouched.
  - **Not moved (new R-9)**: `LocationInventoryController`, `LocationInventoryMapper`,
    `LocationInventoryResponseDTO`, `InventoryRequestDTO` — see "Open risks" above for the full
    reasoning (an `inventory.api -> catalog.api` edge `modulesDoNotDependOnAnotherModulesApi`
    forbids unconditionally).
  - `docs/specs/spring-domain-modular-monolith.md`: §5, §6.2, §11 updated (T-8, done in the same
    pass — see below).
- Same-package-without-import sweep: grepped every file matching each moved class's simple name,
  cross-referenced against files living in the pre-move legacy package with no explicit import
  (the T-3-established discipline) — found two: `RedactedFieldsAreOmittedFromJsonIT` (in
  `dtos.responses`, used `InventoryTotalDTO` bare) and
  `InventoryAggregateControllerCostVisibilityTest` (in `controllers`, constructed
  `InventoryAggregateController` directly, and needed its constructor-signature update from
  `InventoryTotalsRepository` to `InventoryQueries` regardless). Both fixed. `InventoryAggregateControllerIT`/
  `LocationAggregateControllerIT`/`StockMovementControllerSecurityIT`/`RBACAlignmentIT` only
  reference the moved class names in `@DisplayName`/Javadoc prose (verified by grep), not as Java
  symbols — no import needed, confirmed by a clean `./mvnw clean test-compile`.
- ArchUnit: `module-dependency-edges-baseline.txt` needed a sixth reviewed addition (four new
  edges: `inventory -> validation`, `validation -> inventory`, `sites -> models`, `sites -> utils`
  — all mechanical, the same underlying dependency as before the move under the old source-package
  name; see the file's own sixth-addition entry for the full reasoning, including why
  `sites -> models` paired with the pre-existing `models -> sites` isn't a new business-module
  cycle). Two frozen stores needed regeneration
  (`repositoriesAreOnlyAccessedByServicesOrRepositories`, `legacyTechnicalLayerPackagesDoNotGrow`):
  followed the established clean-rebuild-twice-independently discipline (delete store + `stored.rules`
  entry, `allowStoreCreation=true`/`allowStoreUpdate=true`, regenerate against a from-scratch
  compile, revert both flags, re-verify stable across two more independent clean rebuilds). Diffed
  both new stores against saved pre-T6 copies: the legacy-package store lost exactly the eleven
  lines for the moved classes and gained none; the repository-access store lost exactly the three
  lines for `InventoryAggregateController`'s retired direct-repository-access violation (144 -> 141
  lines) and gained none. Zero `lambda$` references in either regenerated store.
- Contract: regenerated `packages/contracts/openapi.json` via `./mvnw -q -Dtest=
  OpenApiContractExportTest test` and diffed it against a pre-move copy — byte-identical (`diff`
  exit 0), confirming the package move changed no endpoint shape. `tests/contracts` (the Python
  suite) covers the Kafka event-envelope schema, not the REST/OpenAPI contract, so it wasn't
  relevant to this task's "byte-identical" claim; confirmed by reading its test files first.
- Result: pass. `./mvnw -q clean test-compile` clean; `./mvnw -q clean test` (full unrestricted
  suite) exit 0, zero failures across every surefire report; `./mvnw -q -Dtest=ArchitectureTest
  test` 8/8 rules pass (T-7's new rule included), stable across two independent clean rebuilds.
  Targeted suite (`InventoryAggregateControllerCostVisibilityTest`, `RedactedFieldsAreOmittedFromJsonIT`,
  `StockMovementServiceActiveStatusDerivationTest`, `StockMovementOutboxAtomicityIT`, plus every
  `*IT`/`*Test` class named above) green. Also ran the broader `-Dtest='*IT'` sweep across the
  whole suite: zero actual failures/errors anywhere, though a pre-existing, broad sandbox quirk
  (affecting ~17 test classes this session never touched, e.g. `AnalyticsServiceTest`,
  `LocationServiceTest`, `AuditLogMapperTest` — confirmed via `git diff` that none of these files
  changed) reports "Tests run: 0" for some `@Nested`-only classes under that specific invocation
  pattern; reproduced identically on files with zero diff from HEAD, so this is pre-existing
  environment behavior, not a T-6 regression. `git diff --check` passed.

### T-7 — Live inventory-infrastructure boundary rule + caller-set pin

- Changed: `ArchitectureTest.java` — added `noProductionClassOutsideInventoryDependsOnInventoryInfrastructure`
  (mirrors `noProductionClassOutsideCatalogDependsOnCatalogInfrastructure`'s shape exactly:
  `outsideInventoryToInventoryInfrastructureRule()`, `INVENTORY_INFRASTRUCTURE_ACCESS_EXEMPTIONS`,
  `isExemptFromInventoryInfrastructureRule`). New `InventoryOperationsCallerSetTest.java` in
  `architecture/`, pinning the eight-class origin set (class-level granularity, not per-method
  like `CatalogEntityAccessCallerSetTest` — T-7's task text asked for "the origin set," and a
  method-level pin would have meant hand-enumerating every call site across eight classes for
  marginal extra precision T-5's own `InventoryOperationsTest`/the two new ITs already cover from
  the behavior side).
- Verified before writing the exemption list: grepped every `com.mirai.inventoryservice.inventory
  .infrastructure.` reference outside `inventory/**` — exactly `DevSeedController` and
  `AnalyticsSeedService`, matching the catalog precedent's two names exactly. Verified the caller
  set: grepped every `inventoryOperations.`/`inventoryQueries.` call site outside `inventory/**` —
  exactly the same eight classes T-5 named, zero drift.
- Tests: `./mvnw -q -Dtest=ArchitectureTest,InventoryOperationsCallerSetTest,
  CatalogEntityAccessCallerSetTest test` — `ArchitectureTest` now 8/8 (was 7/7), both caller-set
  tests pass. `moduleDependencyEdgesMatchApprovedBaseline` unaffected by this task on its own (no
  new edges from adding a live rule and a caller-set test, both of which only read the compiled
  graph, they don't add dependencies). Confirmed no `identity -> inventory` line exists in
  `module-dependency-edges-baseline.txt` (grep) — R-3 still holds after T-6/T-7.
- Result: pass. `./mvnw -q clean test-compile` + `./mvnw -q -Dtest=ArchitectureTest test` stable
  across two independent clean rebuilds (no frozen-store regeneration needed for this task — both
  new tests are live/unfrozen).

### T-8 — Record R-1/R-2/R-4 in the durable spec and package-info

- Changed: `docs/specs/spring-domain-modular-monolith.md` — §5's module-ownership table: `sites`
  row now notes its R-1 cross-module read projection; `inventory` row now notes `StockMovement`'s
  R-2 move and its transitional `AuditLog` association. §6.2: added a paragraph documenting R-1
  (`LocationAggregateController` as the one approved exception to "no cross-module repository
  reads," per §7.4). §11: added a bullet naming `inventory` as the owner of `location_inventory`/
  `stock_movements`, and `LocationAggregateController`'s query as the one documented read-only
  cross-module exception. `inventory/package-info.java` and `sites/package-info.java`: extended
  from one-line summaries to document each module's public facade/exception (matching the level of
  detail the doc's own module-layout section describes, not inventing a new documentation
  convention).
- Result: pass — documentation-only change, verified by `git diff --check` and a read-through
  confirming no factual drift from what T-2/T-3/T-6 actually did.

## Review-driven fix: T-6–T-8 P3 findings (2026-09-10)

Independent review of the completed T-6–T-8 slice found two P3s. No runtime regression found.

- **P3-1, caller guard could collide on simple name**: `InventoryOperationsCallerSetTest` compared
  origins by `getSimpleName()`, so a future same-named class in a different package (e.g. a
  `kuji.application.AnalyticsService`) would silently collapse into the existing
  `services.AnalyticsService` entry instead of failing as an unexpected new caller.
  - Fix: switched `actualOrigins()`/`expectedOrigins()` to fully qualified names
    (`getFullName()`, and the eight expected entries spelled out as FQNs), added a class-Javadoc
    note explaining why. Re-verified: `./mvnw -q -Dtest=InventoryOperationsCallerSetTest,
    ArchitectureTest test` — both pass (ArchitectureTest still 8/8).
- **P3-2, checkpoint evidence was planning-only**: `.specs/phase-6-inventory/validation.md` and
  `review.md` still held only the original planning-stage content (five-checkpoint structure,
  no runtime tests claimed) even though 6a's actual implementation and review had completed.
  - Fix: added a "6a — Inventory module boundary (AC-1)" section to each, recording the actual
    commands/results (validation.md) and the four independent-review rounds' findings/disposition
    (review.md), without altering the original planning-stage content above them.
- Verified: `./mvnw -q clean test-compile` clean; `./mvnw -q clean test` (full unrestricted suite)
  exit 0, zero failures across every surefire report; `git diff --check` passed.
- Result: pass. Both P3s fixed same session; no files committed.
