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
- 6a independent review completed and both P3 findings fixed (see "Review-driven fix" entry
  below). 6a committed as `dbc2c1d`/`2c94a20`.
- 6b done: worksheet finalized (user confirmed 5c's V56/V57 are already applied to production,
  resolving the worksheet's F-2 escalation), then V58/V59/V60 migrations, `StockMovement.site`,
  and every production writer updated to set it — see "6b implementation" section. `V61`
  (constrain) deliberately not written; split into its own PR/record per the worksheet.
- 6b independent review (mirai-spring-reviewer) returned **Block**: one blocker (V60's backfill
  silently assigned the wrong site to a transfer's withdrawal leg — latent today since every row is
  MAIN, but would freeze wrong data once Phase 7 inter-site transfers exist) plus three required
  findings (a factually-wrong comment blocking a one-line `DevSeedController` fix; no test could
  have caught the blocker; nothing failed fast on a null site before `V61` would). All four fixed
  same session — see "Review-driven fix: 6b findings" below. Re-verified independently: full suite
  and `*IT` sweep both green, `ArchitectureTest` stable with **zero** frozen-store changes needed
  (the `DevSeedController` fix was rerouted through an already-injected service instead of adding a
  constructor parameter, specifically to avoid the store-regeneration problem hit and abandoned
  mid-session — see that section for the mechanic worth remembering).
- 6b re-review completed: independent Standards and Spec passes found no remaining blocking
  findings. Focused JDK 21 verification passed 40 tests, with zero failures/errors/skips and no
  ArchUnit store changes; see review.md and validation.md for scope and actual command.
- 6c planning done and all five open questions resolved by the user (see "User decisions on
  Q-6c-1 through Q-6c-5" below Q-6c-5 in the "6c planning" section) — production is confirmed NOT
  yet deployed (Q-6c-1), quantity/active display stays as-is (Q-6c-2), idempotency is a durable
  site-qualified table with the concrete design recorded there (Q-6c-3), the Kafka partition key
  changes to `site_id:product_id` in 6c code but its production cutover requires a separate gated
  authorization (Q-6c-4), and null-site movement rows are surfaced labeled unknown-site rather
  than hidden (Q-6c-5). `DevSeedController.seedSalesData`'s no-site gap is now closed. R-9
  (LocationInventoryController's catalog.api coupling) still open, not a 6a/6b blocker.
- 6c implementation started this session: T-6c-0 (AC-8 baseline measurement), T-6c-1
  (site-qualified repository methods), T-6c-2 (site-scoped facade overloads) and T-6c-3 (`V62`
  index migration) are done and independently verified — see "6c implementation (T-6c-0..T-6c-3)"
  below for what changed, the tests that prove it, and actual command output. T-6c-4 through
  T-6c-17 are not started; P-5's ordering constraint (repository/facade work before any
  controller) is now satisfied, so T-6c-4 (same-site transfer preconditions) or T-6c-5 (slim
  totals projection, gated on T-6c-0's baseline already being captured, which it now is) are both
  valid next tasks.
- Independent review of T-6c-0..T-6c-3 returned three P2s, all in the `InventoryEgressBaselineIT`
  fixture (unisolated catalog hiding accumulation, two measurements over empty data, database-egress
  dimensions missing) and no production-code finding — fixed same session, see "Review-driven fix:
  T-6c-0 baseline P2 findings" below. A second review round on that fix found two more P2s
  (by-product DB egress undercounted; totals filtering measured a different workload than the real
  unfiltered production query) and one P3 (leaked JDBC connection/unfreed `Array`) — also fixed same
  session, see "Review-driven fix round 2" below. T-6c-0's recorded numbers changed twice as a
  result; T-6c-17 must diff against the round-2 numbers (the current, final ones in the task entry
  above), not either earlier set.
- T-6c-4 and T-6c-5 done and verified this session — see "6c implementation (T-6c-4..T-6c-5)"
  below for what changed, the tests that prove it, and actual command output.
  `StockMovementService.executeTransfer` (shared by `transferInventory`/`batchTransferInventory`)
  and `MachineDisplayService.batchSwapDisplay`'s machine-to-machine mode now reject a
  cross-site source/destination pair via the existing `InvalidInventoryOperationException`
  (→ 400), fail-fast before any write. `InventoryTotalsRepository` gained
  `findAllInventoryTotalsBySite`/`findInventoryTotalsBySiteAndProductIds` (slim
  `SiteInventoryTotalDTO`, two deliberately different and separately tested zero-stock contracts —
  full-catalog mode guarantees a row per product via a `LEFT JOIN ... ON` site predicate; batched
  mode reuses T-6c-1's existing absence-means-zero contract), with a documented, enforced
  `MAX_PRODUCT_IDS_BATCH_SIZE = 500` ceiling. `InventoryQueries` gained matching facade overloads.
  `INVENTORY_TOTALS_SQL`/`STOCK_TOTALS_SQL` (the AC-8 baseline's measured path) are untouched. No
  routes added, so no contract regeneration this pass. T-6c-6 (row locking) was not started —
  stopped cleanly per the "don't guess at a large remaining task" ground rule; it is next per
  P-5's ordering.
- Independent review of T-6c-4 returned one P1 (the swap guard checked the requested machine ids
  but never validated that a caller-supplied *display id* actually belongs to the named machine,
  so naming two same-site machines while supplying a foreign-site display id bypassed the guard
  entirely) and one P2 (the task's own required machine-swap IT was missing — only Mockito
  coverage existed). No finding in T-6c-5. Both fixed same session — see "Review-driven fix:
  T-6c-4 findings" below. The fix is a general ownership-mismatch validation (each moved display
  must actually belong to the machine the caller claims), not a narrowly site-specific patch.
- A second review round reproduced the same P1 bug class via a path round 1 didn't touch
  (`displayIdsToRemove`, which has no `requireSameSite` guard at all to partially rely on). Fixed
  the same way, and a proactive sweep of every other global-by-id display lookup in
  `MachineDisplayService` found and fixed the identical bug in `renewDisplays` and `swapDisplay`
  before a third review round could reproduce them independently; `batchClearDisplays` was checked
  and confirmed safe (it derives the expected machine from the displays themselves, not from a
  separately claimed caller field). See "Review-driven fix round 2: T-6c-4 P1 recurrence" below.
  `MachineDisplayServiceCrossSiteSwapIT` now covers all five bypass paths (5/5 pass).
- T-6c-6 done and verified this session — see "6c implementation (T-6c-6)" below for what
  changed, the concurrency test that proves it, and actual command output. `batchAdjustInventory`,
  `transferInventory` and `batchTransferInventory` now all acquire `PESSIMISTIC_WRITE` locks on
  every inventory id they are about to read-modify-write, via a new plain (no-join) id-ordered
  lock query, strictly before the first entity load of any of those ids in the transaction — per
  F-6c-5's recommended shape (a), not `@Version`. `LocationInventory` is unchanged (still no
  `@Version` column, as F-6c-5 recommended against). A real-Postgres concurrency IT
  (`StockMovementServiceConcurrentAdjustIT`) proves the fix: two genuinely concurrent
  `batchAdjustInventory` calls against the same `(location, product)` row, forced to overlap via
  an externally held `FOR UPDATE` lock (same technique as `SiteProductConcurrencyIT`), serialize
  correctly (final quantity is the combined delta, not a lost update), produce exactly two
  movement rows, and never observe a negative quantity. Confirmed the test actually catches the
  regression it targets: temporarily disabling the new lock call reproduced the exact lost-update
  failure (final quantity 80, i.e. only the second write survived, instead of the correct 50) —
  see "6c implementation (T-6c-6)" for the full before/after transcript.
- Review-driven fix (T-6c-6 P1) done and verified this session — see "Review-driven fix: T-6c-6
  P1 findings" below for what changed, why, the pre-fix-failure evidence, and actual command
  output. `resolveTransferLockIds` now guarantees every id it returns already names a real,
  about-to-be-locked row: for an implicit destination, it first ensures the row exists (new
  `LocationInventoryRepository.insertLocationInventoryIfAbsent`, a race-safe `INSERT ... ON
  CONFLICT DO NOTHING`) before resolving its id, instead of only locking a destination that
  happened to already exist at planning time. `executeTransfer`'s implicit-destination branch no
  longer has its own unlocked find-or-create fallback — it now asserts (via `IllegalStateException`
  on a miss) that the row `resolveTransferLockIds` guaranteed and `lockInventoryRowsForUpdate`
  already locked is the one it finds. New deterministic concurrency IT
  (`StockMovementServiceConcurrentTransferNewDestinationIT`) proves two concurrent transfers
  landing on the same not-yet-existing destination serialize correctly (combined quantity, no lost
  update, no duplicate row) using the same forced-overlap `pg_stat_activity`-polling technique as
  `StockMovementServiceConcurrentAdjustIT`. Confirmed the fix matters: temporarily reverting it
  reproduced a real `DataIntegrityViolationException` (unique-constraint violation) on the same
  test. The delete-and-recreate sub-case the reviewer also raised was reasoned through and found
  not independently reachable (subsumed by the `PESSIMISTIC_WRITE` lock hold — Postgres blocks any
  concurrent `DELETE` of a row this transaction holds locked). One non-material ordering nuance
  recorded as an assumption, not a `Q-6c-N` (no durable behavior or persisted-data change): the new
  ensure-step can now write before `executeTransfer`'s cross-site `requireSameSite` check runs,
  but both are inside the same `@Transactional`, so a rejected cross-site transfer still leaves no
  trace — see that section for the full reasoning.
- **Review-driven fix round 2 (T-6c-6 P1 findings, unified locking strategy) done and verified this
  session — see "Review-driven fix round 2: T-6c-6 P1 findings (unified locking strategy)" below.**
  Independent review reproduced two P1s in round 1's fix, both stemming from the same design flaw
  the reviewer named directly: destination creation, identity resolution, and locking used two
  inconsistent domains (an unlocked existence check racing a no-op `ON CONFLICT`; request-order
  processing racing the later id-sort). Both are closed by one redesign: every row a transfer or
  batch-transfer touches — source and destination, existing and new — is now resolved to a
  `(location, product)` key and locked/created through one routine
  (`StockMovementService.ensureAndLockInventoryRow`), processed strictly in one global
  `(location, product)`-sorted order (`planTransfers`/`lockPlannedRows`), replacing the old
  `resolveTransferLockIds`/`ensureDestinationInventoryExists`/id-based
  `lockInventoryRowsForUpdate` path for transfers (`lockInventoryRowsForUpdate` itself is kept,
  unchanged, for `batchAdjustInventory` only — its rows never need creating). New repository method
  `LocationInventoryRepository.findIdByLocation_IdAndProduct_IdForUpdate` (scalar,
  `PESSIMISTIC_WRITE`) makes "found" and "locked" the same act, closing bug 1; the removed
  `findProductIdById`/`findIdByLocation_IdAndProduct_Id` scalar methods were dead after the
  redesign and deleted. `executeTransfer` no longer has any find-or-create logic of its own — it
  takes an already-resolved, already-locked `destinationInventoryId` and asserts it exists.
- **Review-driven fix round 3 (T-6c-6 P1, adjustment/transfer lock-order conflict) done and
  verified this session — see "Review-driven fix round 3" below.** Round 2 unified the transfer
  paths onto one `(location, product)`-keyed lock order but left `batchAdjustInventory` locking by
  ascending row id — two internally-consistent but mutually-conflicting domains, since row id and
  product id are unrelated random UUIDs. A batch-adjust and a concurrent batch-transfer sharing two
  rows at one location could acquire them in opposite order and deadlock (reproduced by the
  reviewer on real Postgres). Fixed by moving `batchAdjustInventory` onto the exact same
  `LocationProductKey`/`ensureAndLockInventoryRow` routine transfers already use — there is now
  exactly one lock-ordering domain for every writer in `StockMovementService` capable of holding
  more than one `LocationInventory` row per transaction. The now fully-unused `lockAllByIdForUpdate`
  repository method was deleted. T-6c-6 is now considered closed pending any further review.
- T-6c-7 done and verified this session — see "6c implementation (T-6c-7)" below for what
  changed, the tests that prove it, and actual command output. `V63`/`V64` add the five nullable
  AC-4 envelope columns to `event_outbox` (predates Flyway, same as `stock_movements` before V59)
  and backfill them for unpublished rows; `EventOutbox` gained the matching nullable Java fields.
  `EventOutboxService` is unmodified (T-6c-8's job).
- T-6c-8 done and verified this session (new session, continuing 6c) — see "6c implementation
  (T-6c-8)" below. `EventOutboxService.createStockMovementEvent` now populates `siteId`/
  `eventVersion`/`correlationId`/`idempotencyKey` on every `EventOutbox` row (`causationId` stays
  null — no event-consuming producer exists yet); a new `IdempotencyKeyContext` (MDC-based,
  mirrors `CorrelationIdContext`) is wired but not yet set by anything (T-6c-10's job).
  `publishPendingEvents` adds the same four fields to the Kafka message and computes the
  partition key via `partitionKeyFor`, which stays the legacy `item_id` key unless
  `kafka.partitioning.site-scoped-key.enabled` (new property, default `false` everywhere) is true
  **and** the event carries a site — the Q-6c-4 cutover itself (verify partition count, drain,
  confirm consumer ordering tolerance) remains unauthorized and undone; only the gated code path
  and its tests exist. Building the required "re-run creation does not duplicate" proof surfaced
  and fixed a real pre-existing bug: catching `DataIntegrityViolationException` around
  `eventOutboxRepository.save()` did not actually protect the caller's transaction, because
  Hibernate's batching-deferred flush plus Spring Data's own `@Transactional` on `save` marks the
  *physical* transaction rollback-only before the exception reaches this method's catch block —
  confirmed by temporarily removing the fix and reproducing a real
  `UnexpectedRollbackException`. Fixed with a check-before-insert `existsByEntityId` guard (new
  repository method) plus switching to `saveAndFlush`; the V16 index and its catch remain as
  documented, accepted defense-in-depth for the now much narrower concurrent-race window.
- T-6c-9 done and verified this session — see "6c implementation (T-6c-9)" below.
  `tests/contracts/schemas/event_envelope.json` now declares `correlation_id`/`event_version`/
  `site_id`/`causation_id`/`idempotency_key` at the top level (also fixing the pre-existing
  F-6c-6 `correlation_id` drift). New `test_consumer_compatibility.py` proves both
  forecasting-service's and messaging-service's real `EventEnvelope` Pydantic models parse an
  envelope carrying all five new fields (via both `model_validate` and the real
  `model_validate_json` parse path) and still parse one that omits them. Two gaps explicitly not
  covered, per the task's own fallback allowance: a producer-payload built by the actual Java path
  (would need real cross-language plumbing; `AdjustToKafkaIT` already covers this from the Java
  side), and "duplicate event_id produces one effect" for these two consumers (no existing
  event-id-dedupe mechanism found in either service to test — pre-existing AC-4/§5 debt, not
  something this task's change touches).
- T-6c-10 done and verified this session — see "6c implementation (T-6c-10)" below. New
  `shared.idempotency` package: `V65` migration + `CommandIdempotency` entity/repository +
  `CommandIdempotencyService.executeIdempotent(...)` implementing Q-6c-3's full design (durable
  `(site_id, user_id, idempotency_key)`-unique table, fingerprint-conflict 409, no row survives a
  failed attempt, documented 7-day retention with a scheduled cleanup). Two reviewed ArchUnit
  changes: `exceptions -> shared` added to the module-edge baseline, and a narrow new allowance in
  `isAllowedRepositoryCaller` for `shared.idempotency` (shared has no business-module-style
  application/infrastructure split, so neither existing exemption covered it). Not yet wired to
  any HTTP route — that lands with T-6c-12.
- Review-driven fix (T-6c-8/T-6c-10 P2s) done and verified this session — see "Review-driven fix:
  T-6c-8/T-6c-10 P2 findings" below. `V66` adds a supporting `CONCURRENTLY` index on
  `event_outbox.entity_id` (T-6c-8's `existsByEntityId` guard had none, unlike V16's JSONB-
  expression index). `CommandIdempotencyService`'s replay check now compares `commandType` in
  addition to `requestFingerprint` before replaying a stored result. The T-6c-9 consumer-dedup gap
  remains open, reiterated as real/unfinished AC-4 work by the review, not attempted.
- **Superseded — see the "Current handoff (superseding the 'Next action' note above)" subsection
  under "6c implementation (T-6c-11..T-6c-17)" further down this file for the current status: 6c is
  now complete (T-6c-0 through T-6c-17, checkpoint review and validation all done).** This bullet is
  left in place as history of what was still pending at the point it was written, not as the
  current state.
- Last verified (T-6c-8, this session): `./mvnw -q clean test-compile` clean; `./mvnw -q clean
  test` — 355 tests summed across 61 surefire reports, 0 failures/errors (up from 348 by 7 new
  unit tests); `./mvnw -q test -Dtest='*IT'` — 431 tests (up from 419 by 3 new IT methods), 8
  failures, all the same pre-existing `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT`
  debt (confirmed not a regression by excluding those two classes, exit 0); `./mvnw -q
  -Dtest=ArchitectureTest test` after an independent clean `test-compile` — 8/8, no
  `archunit_store/` diff; `./mvnw -q -Dtest=OpenApiContractExportTest test` then `diff` against a
  pre-change copy of `packages/contracts/openapi.json` — byte-identical (no routes changed this
  task). See "6c implementation (T-6c-8)" below for the full list including the pre-fix-failure
  reproduction.
- Last verified (review-driven fix round 2, this session): `./mvnw -q clean test-compile` clean;
  new tests `StockMovementServiceConcurrentTransferExistingDestinationRaceIT` 1/1 pass (bug 1) and
  `StockMovementServiceConcurrentBatchTransferCrossedDestinationsIT` 2/2 pass (bug 2, both the
  service-level no-deadlock proof and the raw-SQL mechanism-reproduction proof); both bugs'
  pre-fix-failure evidence captured (a real `OptimisticLockException`/`StaleStateException` for bug
  1, a real `deadlock detected` Postgres error for bug 2's mechanism); previously-existing
  `StockMovementServiceConcurrentTransferNewDestinationIT` 1/1 still passes unmodified;
  `StockMovementServiceSameSiteTransferTest` rewritten for the new repository methods, 4/4 pass;
  `StockMovementServiceConcurrentAdjustIT`/`StockMovementOutboxAtomicityIT`/
  `InventoryOperationsCallerTransactionIT` all still pass; `ArchitectureTest` 8/8, no
  `archunit_store/` diff; `./mvnw -q clean test` — 348 tests, 0 failures/errors (unchanged, all new
  tests are `*IT`); `./mvnw -q test -Dtest='*IT'` — 419 tests (up from 416 by 3 new IT methods), 8
  failures, all the pre-existing `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT`
  debt, confirmed not a regression by excluding those two classes (exit 0); `git diff --check`
  clean; no `archunit_store/` diff. See "Review-driven fix round 2: T-6c-6 P1 findings (unified
  locking strategy)" below for the full verification list and bug-by-bug mechanism tracing.
- Last verified (T-6c-6 P1 review-fix round 1, prior session): `./mvnw -q clean test-compile` clean;
  `StockMovementServiceConcurrentTransferNewDestinationIT` 1/1 pass (real Testcontainers Postgres);
  pre-fix revert reproduced a real `DataIntegrityViolationException` on the same test, restored fix
  passes again; `StockMovementServiceConcurrentAdjustIT`/`StockMovementServiceSameSiteTransferTest`/
  `StockMovementOutboxAtomicityIT`/`InventoryOperationsCallerTransactionIT` all still pass;
  `ArchitectureTest` 8/8, no `archunit_store/` diff; `./mvnw -q clean test` — 348 tests, 0
  failures/errors (unchanged, new test is itself an `*IT`); `./mvnw -q test -Dtest='*IT'` — 416
  tests, 8 failures, all the same pre-existing `AnalyticsControllerSecurityIT`/
  `ForecastControllerSecurityIT` debt, confirmed not a regression by excluding those two classes
  (exit 0). See "Review-driven fix: T-6c-6 P1 findings" below for the full verification list.
- Last verified (T-6c-6, prior session): `./mvnw -q clean test-compile` clean. New concurrency IT
  alone: `./mvnw -q -Dtest=StockMovementServiceConcurrentAdjustIT test` — 1/1 pass (real
  Testcontainers Postgres). `./mvnw -q clean test` (full unrestricted suite, plain `test`,
  excludes `*IT.java` by design) — exit 0, 348 tests summed across every surefire report, zero
  failures/errors (unchanged from T-6c-4..T-6c-5 since the new class is an `*IT`). `./mvnw -q test
  -Dtest='*IT'` — `MojoFailureException`, 415 tests (+1 over the prior 414), 8 failures, all still
  exactly `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` (the pre-existing
  order-fragile debt, not a regression — confirmed again by `./mvnw -q test
  -Dtest='*IT,!AnalyticsControllerSecurityIT,!ForecastControllerSecurityIT'` exiting 0). `./mvnw -q
  -Dtest=ArchitectureTest test` after an independent clean `test-compile` — 8/8 pass,
  `git status --porcelain` on `archunit_store/` shows no diff (the new lock/scalar-lookup
  repository methods reference only already-approved `inventory`-internal types). Pre-existing
  tests exercising the changed methods under real persistence stayed green:
  `StockMovementOutboxAtomicityIT` 2/2, `InventoryOperationsCallerTransactionIT` 2/2,
  `StockMovementServiceSameSiteTransferTest` 4/4 (Mockito, unaffected by the new lock calls —
  Mockito's default `Optional`/`List` answers for the unstubbed new repository methods are empty,
  which is exactly what the production code expects for "nothing to additionally lock").
- Open risks/questions carried forward: everything in the prior 6c-0..T-6c-3 "Open items" entry
  below still applies unchanged (the `*IT` order-fragility debt; T-6c-7 through T-6c-17 unstarted).
  No new open question was raised by T-6c-6 — it was implementable within F-6c-5's already-recorded
  recommendation (option (a), `PESSIMISTIC_WRITE`). One residual, deliberately-accepted, narrower
  gap was recorded rather than silently left implicit: a transfer's find-or-create path for a
  brand-new destination row (never-before-used `(location, product)` pair) was not covered by the
  new lock, since there was no row to lock yet. **This gap was subsequently found by independent
  review to be a P1 (not merely the "safe, loud constraint error" this entry originally assumed —
  the actual hazard was a silent, unlocked read-modify-write, not just a create-time collision) and
  fixed the same phase — see "Review-driven fix: T-6c-6 P1 findings" below.**
- **New standing risk discovered this session, not yet fixed (deliberately, see below): the
  `*IT` Maven suite is order-fragile independent of 6c.** Adding *any* new `*IT` test class to the
  module — proven with a throwaway no-op `ZZZOrderProbeIT` containing a single empty `@Test`,
  added and then removed — perturbs JUnit 5's test-class execution order enough to make
  `AnalyticsControllerSecurityIT` (`demand-leaders`, `performance-metrics`, `inventory-by-category`)
  and `ForecastControllerSecurityIT` (`getAllForecasts`) fail with 500s. Root cause (from the
  actual stack traces): `AnalyticsService`/`ForecastService` issue native SQL that is not
  H2-compatible (a `year` column alias colliding with H2's reserved word, and a Postgres-only JSONB
  `->>'demand_segment'` operator) — these queries only execute, and only fail, once
  `analytics_daily_rollup`/`forecast_predictions` actually contain rows, which is itself a function
  of which other tests happened to run first in the shared, non-isolated, single H2 instance
  (`DB_CLOSE_DELAY=-1`) the whole `test` profile uses. This is pre-existing debt in
  `AnalyticsService`/`ForecastService` (not `inventory`, not touched by 6c) that has apparently
  been surviving on accidental test-ordering luck. Confirmed NOT a 6c regression: reproduced with
  the no-op probe class alone (`./mvnw clean test -Dtest='*IT'` fails the same 8 tests with zero
  inventory-related code present), and the full `*IT` sweep is green with only those two
  pre-existing classes excluded (`./mvnw test -Dtest='*IT,!AnalyticsControllerSecurityIT,
  !ForecastControllerSecurityIT'` — exit 0). Recorded here as an open risk per the ground rules
  (a genuine gap, not silently worked around) rather than fixed, since fixing
  `AnalyticsService`/`ForecastService`'s native SQL is out of `inventory`'s module boundary and
  out of 6c's scope; a future session/PR should either make those two native queries
  H2-compatible or give `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` their own
  isolated persistence context.
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
- Last verified (6b, independently re-run this session, not just as claimed in the "6b
  implementation" section): `./mvnw -q clean test-compile` clean. `./mvnw -q clean test` (full
  unrestricted suite, plain `test` — skips `*IT.java` by design) — exit 0, 332 tests, zero
  failures/errors/skips across every surefire report. `./mvnw -q test -Dtest='*IT'` — exit 0, 537
  tests, zero failures/errors/skips, including the three new ITs individually confirmed:
  `InventoryOperationsSiteIT` 4/4, `StockMovementSiteBackfillIT` 6/6, `StockMovementSiteMigrationIT`
  2/2 (the latter two run against a real `postgres:16-alpine` Testcontainers instance, not H2 —
  confirmed by reading both files' `@Container PostgreSQLContainer` setup, and by their
  sub-second-but-nonzero elapsed times matching container-backed execution, not an instant skip).
  `./mvnw -q -Dtest=ArchitectureTest test` re-run standalone after an independent clean
  `test-compile` — 8/8 pass; `git status --porcelain` on `archunit_store/`/`archunit.properties`
  shows no diff, confirming this pass added no new cross-module edge or repository-access
  violation requiring a frozen-store update.
- Open risks: global quantity/activity semantics and downstream consumers (still to reconcile
  before 6c, per 6b's worksheet Row 4 — `products.quantity`/`products.is_active`/
  `ProductStockStateWriter` deliberately untouched by 6b); `V61` (constrain `stock_movements.site_id`
  NOT NULL/FK) not yet written — split into its own PR/record per the worksheet's F-2/enforcement-
  timing analysis, gated on a writer-release deploy plus a full business day of zero-null
  production verification; a `V58` IT (nothing currently executes V58's SQL — advisory from the
  review, recorded debt not a blocker); a same-site precondition on `MachineDisplayService`'s
  cross-site swap path (advisory, belongs in 6c's trusted-context work); A-1 (confirm
  `location_inventory.site_id`'s
  NOT NULL/FK/trigger actually exist in Supabase, not just fresh-container `init-db`) and A-2/A-3
  (empirical backfill row counts and batching/lock-time consideration against real production
  `stock_movements` volume) remain unverified against production data. Event payload coverage and
  targeted-refresh baseline remain 6c/6e's job. R-4 through R-8 below remain open, not yet
  resolved. New for
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

## 6b planning — site-ownership rollout worksheet (2026-09-10)

Research-only pass (mirai-spring-architect), no code changed. Full findings, per-table worksheet,
migration file plan, and test plan below. This satisfies the spec's "finalize rollout worksheet
before editing schema" gate for 6b; implementation has not started.

### Findings that shape 6b's scope

- **F-1**: Flyway is not wired into the running service — no Flyway dependency in
  `services/inventory-service/pom.xml`, both prod profiles set `spring.jpa.hibernate.ddl-auto=none`.
  `docs/runbooks/ci-cd-pipeline.md:53-58,240-277` confirms: no migration gate in `deploy.yml`, no
  `flyway_schema_history`; V-files are a naming/ordering convention, applied by hand against
  Supabase. `docs/specs/multi-site-data-and-api.md:98`'s "Flyway is canonical in production" is
  aspirational, not current state. `.specs/phase-5c-site-products/log.md:147-150` records the same
  for 5c. Consequence: "when enforcement is safe" is an operator action, not something a commit
  sequence alone establishes; ITs that execute the actual `.sql` text are the only runtime proof
  these files work at all (5c's `SiteProductsMigrationIT`/`SiteProductBackfillIT` pattern, reused
  below).
- **F-2 (escalation, needs user decision before any 6b migration is scheduled)**: 5c's own
  production-apply task (V56/V57) is still outstanding —
  `.specs/phase-5c-site-products/log.md:5-11` records it as deferred pending explicit approval and
  a fresh Supabase backup. 6b's migrations would queue behind that unapplied pair. This is a
  production-data/operational decision, not an architecture one; no apply is authorized by this
  record either way.
- **F-3**: `location_inventory` is already fully site-owned — `site_id UUID NOT NULL REFERENCES
  sites(id)` plus a `BEFORE INSERT OR UPDATE` trigger (`sync_inventory_site_id()`) that re-derives
  it from the location's site (`infra/init-db/20-unified-locations.sql:106-169`); the entity and
  all seven production write sites already set it from `location.getStorageLocation().getSite()`.
  No expand/backfill needed here — 6b's job on this table is verify + index strengthening only.
- **F-4**: `stock_movements` is the one genuinely site-blind inventory table, and it predates
  Flyway entirely (Hibernate-created per `infra/init-db/07-audit-logs.sql:26-39`); its
  `from_location_id`/`to_location_id` columns carry no FK.
- **F-5**: a real share of `stock_movements` rows are not location-derivable at all — Kuji ledger
  rows deliberately set both location ids null (`KujiBoxService.java:318-319,402-403,760-761,
  942-943,1405-1406,1640-1641`). A backfill keyed only on `COALESCE(to_location_id, from_location_id)`
  leaves those rows null; needs an explicit MAIN-fallback pass (below).

### Worksheet

**Row 1 — `location_inventory` (owner `inventory`)**: no schema gap; add
`idx_location_inventory_site_product(site_id, product_id)` only (`V58`, `CREATE INDEX
CONCURRENTLY` with a paired `.conf`, matching the `V19`/`V44` precedent). Verify: zero rows where
the location's actual site (via `locations -> storage_locations.site_id`) disagrees with
`location_inventory.site_id`, zero null `site_id`. Rollback-safe at any point (index-only). Also
open: confirm in Supabase (not just fresh-container `init-db/*.sql`) that `site_id NOT NULL`, the
FK, and the trigger actually exist there — `docs/baseline/tenant-migration-worksheet.md:69-73`
already lists this as required and still open (A-1).

**Row 2 — `stock_movements` (owner `inventory`)**: target shape adds `site_id UUID NOT NULL
REFERENCES sites(id) ON DELETE RESTRICT` (RESTRICT matches `site_products`'s
`V56__create_site_products.sql:14` precedent) plus `idx_stock_movements_site_at(site_id, at DESC)`
and `idx_stock_movements_site_item_at(site_id, item_id, at DESC)`. No uniqueness change — append-only
ledger, surrogate key; stable event identity/idempotency is AC-4/6c's job, not this table's schema.
- Migration files (see "Migration file plan" below): `V59` (expand, nullable, no FK/default),
  `V60` (backfill, two passes), `V61` (constrain — **split into a separate PR/record**, see below).
- Backfill: pass 1, location-derived —
  `UPDATE stock_movements sm SET site_id = sl.site_id FROM locations l JOIN storage_locations sl
  ON sl.id = l.storage_location_id WHERE l.id = COALESCE(sm.to_location_id, sm.from_location_id)
  AND sm.site_id IS NULL;` pass 2, MAIN fallback for both-null/orphaned-location rows —
  `UPDATE stock_movements SET site_id = (SELECT id FROM sites WHERE code='MAIN') WHERE site_id IS
  NULL;`, guarded to fail loudly if MAIN is absent (matches `V53:12-19`/`V57:14-19`). Pass 2 is
  correct for *existing* data only because SECOND (`V54`) has no `locations` and therefore no
  movements yet — not a rule new writers may rely on. Record pass-1 vs pass-2 row counts as an
  observed number, not an assumption.
- Verify: zero null `site_id`; zero orphans against `sites`; zero conflicting-site rows
  (movement's derived site vs. its location's actual site); cross-check against sibling
  `location_inventory.site_id` for the same location/product.
- Writer compatibility: **all 25 production `StockMovement.builder()` call sites** (KujiBoxService
  ×12, MachineDisplayService ×6, `inventory.application.StockMovementService` ×5,
  `inventory.application.InventoryOperations` ×2, plus 4 dev-seed) currently set no site and would
  break under enforcement today. Because persistence already funnels through
  `InventoryOperations.recordMovement`/`saveMovement`/`saveMovements` (6a T-5/T-7, pinned by
  `InventoryOperationsCallerSetTest`), the fix is: add `site` to `StockMovement`, derive it inside
  `InventoryOperations` from the same `Location` callers already pass, and require an explicit site
  for the location-less Kuji/display ledger forms. Do not mirror `sync_inventory_site_id()` as a DB
  trigger here — it cannot derive a site for the both-null rows, and a MAIN-default trigger would
  silently mis-write once SECOND has real activity.
- Rollback compatibility: `V59`/`V60` alone are rollback-safe (old image ignores an unknown
  nullable column). `V61` is not — `deploy.yml` rollback reverts images only, never schema
  (`docs/runbooks/ci-cd-pipeline.md:271-275`); rolling back past the writer release once `site_id`
  is `NOT NULL` fails every stock movement in the system.
- Enforcement safe when: the writer release (StockMovement.site + InventoryOperations derivation)
  is deployed and healthy, the verify queries return zero against production data collected after
  at least one full business day on that release, and the operator accepts that a rollback past the
  writer release now also requires a schema step.

**Row 3 — `locations` (owner `sites`)**: deferred, not in 6b. `locations` has no `site_id` of its
own (reachable only via `storage_locations.site_id`); the only 6b-relevant payoff would be a
composite FK from `location_inventory`, which the existing trigger already makes redundant. Record
as an explicit, reasoned deferral per `docs/specs/multi-site-data-and-api.md:57-58`'s "owner and
tenant strategy MUST be recorded for every table," not a silent gap.

**Row 4 — `products.quantity`/`products.is_active`**: **out of 6b, deferred to 6c.** Recommendation
and full reasoning: this is a global-activity-semantics question
(`docs/specs/multi-site-data-and-api.md:23-43` already fixes `is_active` as "stock at *any* site,"
deliberately distinct from `site_products.is_stocked`), not a tenancy-column question — adding
`site_id` to these columns is meaningless. `StockMovementService.calculateTotalInventory` sums
`location_inventory` with no site predicate today and both Kuji tab semantics
(`KujiBoxService.java:266,418,641,1291,1372` via `ProductStockStateWriter`) and
`forecasting-service`'s item-selection/inventory-sum/reorder-write queries
(`supabase_repo.py:148,180-193,644-645`) depend on the current global meaning, with no Java test
covering the Python consumer. Writer set is also larger than `ProductStockStateWriter` alone:
`SiteProductService.java:74` dual-writes `is_active` on MAIN assortment change, and
`ProductService.java:136,328` write `quantity` directly for prize/child products, bypassing the
writer facade. `ProductStockStateWriter`'s own Javadoc already schedules its removal for "together
with the `quantity` column once `inventory` owns quantity" — i.e. gated on 6c's ownership move, not
6b's tenancy work. Matches the spec's own gating language (`spec.md:49-52`, 6c row `spec.md:99`)
and 6a's handoff note (`log.md:78-80`). 6b's only obligation here is to record this consumer
inventory as the durable list 6c inherits and leave `ProductStockStateWriter`/adapters untouched.

**Row 5 — `event_outbox`**: out of 6b (AC-4/6c) — no `site_id`, version, or idempotency key yet;
listed for worksheet completeness only, omission is deliberate.

### Migration file plan

| File | Step | Release | Rollback-safe |
| --- | --- | --- | --- |
| `V58__location_inventory_site_product_index.sql` | strengthen | 6b | yes |
| `V59__add_site_id_to_stock_movements.sql` | expand (nullable, no FK) | 6b | yes |
| `V60__backfill_stock_movements_site_main.sql` | backfill + guard | 6b | yes |
| `V61__constrain_stock_movements_site.sql` | constrain (NOT NULL, FK, indexes) | **separate PR/record** | no |

Writer code (`StockMovement.site` + `InventoryOperations` derivation, 25 call sites) ships with
`V59`/`V60`, writing `site_id` on every new row while the column is still nullable. `V61` is
explicitly the spec's "enforcement requires an earlier deployed release" case
(`spec.md:44-48`) — split into its own PR/record, not part of this one; no production apply
authorized by this record regardless.

### Test plan (reuses 5c's proven pattern — standalone Testcontainers Postgres executing the
actual `.sql` text, since Flyway never runs these files at runtime per F-1)

- `StockMovementSiteMigrationIT` — runs `V59` then `V61` against stub tables; asserts
  column/nullability/FK/index shape; asserts `V61` fails loudly against a table with a null
  `site_id` row (verify-before-constrain enforced by the SQL itself, not operator memory).
- `StockMovementSiteBackfillIT` — fixtures: MAIN location-derived row, SECOND location-derived row
  (proves pass 1 doesn't blanket-MAIN), both-locations-null Kuji row, dangling `to_location_id`,
  MAIN-absent (must raise), idempotent re-run.
- Extend/add an `InventoryOperations` IT proving every facade write path populates `site_id` —
  paired with the existing `InventoryOperationsCallerSetTest` pin against a future 26th builder
  site being added without one.
- Concurrency IT: the implemented test proves separate threads writing different locations and
  products retain their own movement sites. Same `(location, product)` contention with assertions
  on both movement and inventory rows remains for 6c's concurrency gate; it is not proven by 6b.
- Keep green: `StockMovementOutboxAtomicityIT`, `AdjustToKafkaIT`,
  `InventoryOperationsCallerTransactionIT`, `InventoryOperationsSharedCallerPathIT`,
  `ArchitectureTest` (8 rules).
- Reminder (memory + `.specs/phase-5c-site-products/log.md:12-20`): plain `./mvnw test` skips every
  `*IT.java` (no failsafe plugin) — run it and `./mvnw test -Dtest='*IT'`.

### Risks and open assumptions

- **A-1**: `location_inventory.site_id`'s `NOT NULL`/FK/trigger existing in Supabase itself is
  unverified — `infra/init-db/*.sql` only runs against a fresh container. Verify before `V58`.
- **A-2**: assumes all existing `stock_movements` rows belong to MAIN (SECOND has no `locations`
  yet per `V54`, even if `007-seed-standard-storage-locations.sql` seeded `storage_locations` for
  it). Confirm by recording pass-1/pass-2 backfill counts empirically rather than assuming.
- **A-3**: production `stock_movements` row count unknown to this pass — if large, batch `V60`'s
  updates by `at` range, and prefer a `NOT VALID` `CHECK (site_id IS NOT NULL)` validated separately
  before `V61`'s `SET NOT NULL` to avoid an uninterruptible full-table rewrite/scan.
- R-9 (LocationInventoryController's `catalog.api` coupling) carries over unaffected by 6b.

## 6b implementation (2026-09-10)

Implements the worksheet above: `location_inventory` index strengthening, `stock_movements`
expand + backfill, and the writer-release code change. `V61` (constrain) is explicitly not part
of this pass — split into its own PR/record per the worksheet.

- **V58/V59/V60 migration files**: added per the worksheet's exact shapes.
  `V58__location_inventory_site_product_index.sql` (+ paired `.conf`,
  `executeInTransaction=false`) — `CREATE INDEX CONCURRENTLY IF NOT EXISTS
  idx_location_inventory_site_product ON location_inventory(site_id, product_id)`, matching
  `V19`'s precedent. `V59__add_site_id_to_stock_movements.sql` — nullable `site_id UUID`, no
  FK/default. `V60__backfill_stock_movements_site_main.sql` — pass 1 location-derived
  (`COALESCE(to_location_id, from_location_id)` joined through `locations ->
  storage_locations.site_id`), pass 2 MAIN-fallback guarded to `RAISE EXCEPTION` if no MAIN site
  row exists, matching `V53`/`V57`'s precedent exactly.
- **`StockMovement.site`**: added as a `@ManyToOne(fetch = LAZY) @JoinColumn(name = "site_id")`
  `Site` field, nullable at the JPA level (matches V59's expand-phase nullability).
- **`InventoryOperations`**: the `recordMovement(AuditLog, Product, LocationType, fromLocationId,
  toLocationId, ..., Map)` field-form overload now takes a required `Site site` parameter and
  sets it on the built movement — every caller of this overload already has a resolvable
  `Location` (or, for `ShipmentService`'s undo paths, an existing `LocationInventory.getSite()`)
  in scope. `applyDelta` derives it from `location.getStorageLocation().getSite()` and passes it
  through. The other write paths (`recordMovement(StockMovement)`, `saveMovement`,
  `saveMovements`) take no new parameter — callers set `.site(...)` on the `StockMovement` they
  already build before calling these pass-throughs, so a caller with no `Location` at all (e.g. a
  KUJI ledger row) supplies the site explicitly rather than having one silently inferred.
- **Every production `StockMovement.builder()` call site updated** to set `.site(...)`:
  `KujiBoxService` (12 sites — each traced to the specific `Location`/`Site` already in scope at
  that call: `box.getLocation()` for box-scoped ledger rows, `destination`/`sourceLocation` for
  transfers, preferring the destination side to match `V60`'s `COALESCE` convention where a
  transfer touches two locations); `MachineDisplayService` (6 sites — `location`/
  `display.getLocation()`, plus a new `resolveMachineSite(UUID)` helper for the swap path's
  `DisplayChange` records, which carry only raw machine-id UUIDs — preferring
  `toMachineId`, falling back to `fromMachineId` for a pure-removal change with no destination);
  `StockMovementService` (5 sites — `inv.getSite()` / `location.getStorageLocation().getSite()` /
  `sourceInventory.getSite()` / `destinationInventory.getSite()`, all already-loaded entities with
  a `site` field, no extra query needed); `AnalyticsSeedService` (dev-only, new `SiteRepository`
  field — already exempt as a `services`-package caller — resolving MAIN once per seed run).
- **`DevSeedController`'s three `StockMovement.builder()` sites**: two (in
  `seedComprehensiveData`/the audit-log seed path) reuse an already-in-scope `site`/`toLoc`
  variable. The third, `seedSalesData`'s pure-synthetic sales-history seed (no real location at
  all), deliberately does **not** set a site — see "Open risks" below.
- **Result**: `./mvnw -q clean test-compile` clean. `./mvnw -q clean test` (full unrestricted
  suite) exit 0, zero failures/errors. `./mvnw -q test -Dtest='*IT'` exit 0 (plain `test` alone
  would have skipped every `*IT.java`, per the standing memory note — ran both). `ArchitectureTest`
  re-verified 8/8 across two independent clean rebuilds with **zero changes** to
  `archunit_store/`/`archunit.properties` (confirmed via `git status --porcelain` showing no diff
  on either) — this build pass adds no new cross-module dependency edges and no repository access
  from outside a service/application layer.
- **Tests added**: `StockMovementSiteMigrationIT` (V59's column shape/nullability/no-FK, against
  a standalone Testcontainers Postgres executing the real SQL — same pattern as
  `SiteProductsMigrationIT`, needed because Flyway never runs these files at runtime per the
  worksheet's F-1; V61 is out of scope so there is nothing to constrain yet in this file).
  `StockMovementSiteBackfillIT` (6 cases: fails loudly with no MAIN; location-derived resolves to
  the *actual* location's site, not blanket-MAIN; `to_location_id` preferred over
  `from_location_id` when both present; both-null falls back to MAIN; a dangling location
  reference falls back to MAIN; idempotent re-run). `InventoryOperationsSiteIT` (4 cases:
  `applyDelta` derives site from location; the field-form `recordMovement` persists the supplied
  site through a real DB round trip; a location-less `recordMovement(StockMovement)` persists an
  explicitly-supplied site; two concurrent `applyDelta` calls against different sites each derive
  their own correct, distinct site — proving the derivation isn't read-racy). Existing
  `InventoryOperationsTest` extended with site assertions on the three affected tests rather than
  just fixed for the new required parameter.
- **Fixed as a consequence, not scope creep**: `MachineDisplayServiceNotificationTest` and
  `MachineDisplayServiceBatchQueryGuardTest`'s `Location`/`MachineDisplay` mock fixtures had no
  `storageLocation`/`site` chain (they predate this field existing at all) — six tests NPE'd once
  `MachineDisplayService` started reading `location.getStorageLocation().getSite()`. Fixed by
  giving the fixtures a real `Site`/`StorageLocation` and wiring `display()`'s built
  `MachineDisplay` to the matching `Location`. Also fixed a real bug the tests caught: the swap
  path's `resolveMachineSite(change.toMachineId())` NPE'd/threw `LocationNotFound: null` for a
  pure-removal `DisplayChange` (`toMachineId` null, `fromMachineId` set) — changed to prefer
  `toMachineId`, fall back to `fromMachineId`, matching `V60`'s own `COALESCE` convention.

### Open risks / deferred decisions

- ~~`DevSeedController.seedSalesData`'s synthetic sales-history rows have no site.~~ **Resolved**
  in the review-driven fix pass below — routed through `AnalyticsSeedService.getDefaultSite()`
  (already an injected field) instead of adding a new constructor parameter, so no ArchUnit store
  regeneration was needed at all.
- Carries forward unresolved from the worksheet: A-1 (verify `location_inventory.site_id`'s
  NOT NULL/FK/trigger actually exist in Supabase, not just fresh-container `init-db`), A-2/A-3
  (empirical backfill row counts and batching/lock-time consideration once run against real
  production `stock_movements` volume — this pass only proves the SQL's correctness against
  synthetic Testcontainers fixtures, not production data shape). `V61` itself, and the F-2
  production-apply-ordering question, remain fully out of scope per the worksheet and the user's
  confirmation that V56/V57 are already applied.

## Review-driven fix: 6b findings (2026-09-10)

Independent review (mirai-spring-reviewer) of the 6b implementation above returned a **Block**
verdict with one blocker and three required findings; advisory items left as recorded open debt.
All four are fixed in this session; re-verified against real Postgres and the full suite, not just
compile.

- **Blocker — V60's backfill assigned the *destination* site to a transfer's withdrawal leg.**
  `COALESCE(sm.to_location_id, sm.from_location_id)` unconditionally preferred `to_location_id`,
  but a transfer pair carries *both* location ids on *both* rows (`StockMovementService`'s and
  `KujiBoxService`'s transfer writers independently set `.site(sourceLocation...)` on the
  withdrawal row and `.site(destinationLocation...)` on the deposit row). The migration comment's
  claim that "a transfer's destination is the more relevant site... when both are somehow present"
  was wrong — both being present is the transfer's *normal* shape, not an edge case, and every
  production row is currently MAIN so the bug was latent (invisible) until a real inter-site
  transfer exists. Reviewer proved it by executing V60's SQL verbatim against a throwaway Postgres
  with a synthetic transfer pair: both legs landed on the destination's site.
  - Fix: made pass 1 sign-aware, matching every writer exactly —
    `WHEN sm.quantity_change < 0 THEN COALESCE(from_location_id, to_location_id) ELSE
    COALESCE(to_location_id, from_location_id)`. Added `quantity_change` to
    `StockMovementSiteBackfillIT`'s stub table (it was missing, which is *why* this case couldn't
    be expressed as a test before) and replaced the old
    `toLocationIsPreferredOverFromLocationWhenBothArePresent` test (which had cemented the wrong
    behavior) with `transferPairResolvesEachLegToItsOwnSiteBySign`, asserting withdrawal→source,
    deposit→destination.
- **Required — `DevSeedController.seedSalesData` still wrote a null site**, and the in-code comment
  explaining why (adding a `SiteRepository`/`LocationService` dependency would trip
  `ArchitectureTest`'s frozen store) was factually wrong: `AnalyticsSeedService` was already an
  injected field on the class. Fix: added `AnalyticsSeedService.getDefaultSite()` (mirrors its
  existing `seedForecastPredictions`-style `siteRepository.findByCode("MAIN")` lookup, exposed as a
  method) and called it from `DevSeedController` — no constructor signature change, so the frozen
  ArchUnit store needed no regeneration. (An earlier attempt in this session routed through a new
  `LocationService` constructor parameter instead; that *did* require touching the frozen store,
  surfaced the real mechanics of `FreezingArchRule` — `allowStoreUpdate=true` only lets *resolved*
  violations shrink out of the store, it will not silently admit a new violation shape, so a
  changed constructor signature needs the store deleted and recreated from scratch, not just
  updated — and was abandoned once the no-new-dependency fix above made the whole problem moot.
  Recorded here so a future session doesn't have to rediscover that mechanic.)
- **Required — no test asserted the site chosen at 21 of the real call sites**, so a wrong
  derivation (like the blocker above) was undetectable. Specifically, `MachineDisplayServiceNotificationTest`
  wired `loc` and `targetLoc` to the *same* `StorageLocation`/`Site`, so a swap that picked the
  wrong machine's site would still pass. Fix: gave `targetLoc` its own distinct `Site`
  ("SECOND"), and added an assertion in `batchSwapDisplay_machineToMachine_emitsTwoMachineSnapshots`
  capturing `inventoryOperations.saveMovements(...)` and checking each `DisplayChange`'s movement
  landed on its own destination site (p1 → SECOND, p2 → MAIN) — this is exactly the site-selection
  logic the blocker's bug class lives in, just at the `MachineDisplayService.resolveMachineSite`
  call site rather than the migration SQL.
- **Required — nothing failed fast on a null site before V61 would.** `InventoryOperations
  .recordMovement(StockMovement)`, `saveMovement`, and `saveMovements` accepted and persisted a
  movement with a null site silently; H2's `ddl-auto=create-drop` test schema has no NOT NULL
  constraint to catch it either. Fix: added `Objects.requireNonNull(movement.getSite(), ...)` to
  all three funnel methods (the batch form checks every element). Required updating three existing
  `InventoryOperationsTest` fixtures to set `.site(site)` — they were exercising the pass-through
  methods with no site, which the new guard now correctly rejects.
- **Advisory items left as recorded debt, not fixed this pass** (per the review): a `V58` IT
  (nothing currently executes V58's SQL, since V59/V60's ITs only run those two files — matches the
  same F-1 "Flyway doesn't run at runtime" gap, just for the one file this pass didn't add explicit
  coverage for); `CREATE INDEX CONCURRENTLY IF NOT EXISTS` leaving a permanently invalid index on a
  failed build (matches `V19`'s existing precedent exactly, not a new defect); and a same-site
  precondition on `MachineDisplayService`'s cross-site swap path (zero-quantity display rows, low
  impact, belongs in 6c's trusted-context work per the reviewer).
- **Re-verified after all fixes**: `./mvnw -q clean test-compile` clean; `./mvnw -q clean test`
  (full unrestricted suite) exit 0, zero failures; `./mvnw -q test -Dtest='*IT'` exit 0, zero
  failures (confirmed via log grep for `[ERROR]`, not just exit code); `./mvnw -q clean test-compile`
  + `./mvnw -q test -Dtest=ArchitectureTest` stable on a second independent clean rebuild, **zero
  diff** on `archunit_store/`/`archunit.properties` (`git status --short` clean on both) — this
  fix pass, like the original implementation pass, adds no new module edges and needs no frozen-
  store change.

## 6c planning — scoped inventory backend worksheet (2026-09-11)

Research-only pass (mirai-spring-architect), no code changed. Findings, prerequisites, open
questions and the T-numbered task list below. This is the equivalent of 6b's rollout worksheet for
6c's scope (trusted context, scoped repositories/constraints, atomic writes, compatible event
context, v1 routes with slim/batched totals). Nothing here is implementation proof; every claim is
marked verified (read at the cited file/line) or assumed.

### Findings that shape 6c's scope

- **F-6c-1 (verified): the trusted-site-context mechanism already exists and 6c must not invent a
  second one.** `identity.infrastructure.SiteAccessAuthorizationFilter:31-96` matches
  `/api/v1/sites/{siteId}/**` (line 33), resolves through
  `identity.application.AuthorizedSiteContextFactory:37-72` (authenticated principal -> site exists
  -> active membership -> audited system-admin bypass), stashes
  `shared.web.AuthorizedSiteContext` on `AuthorizedSiteContextHolder` and always clears it in a
  `finally` (lines 91-95). Controller precedent to copy verbatim:
  `sites/api/SiteLocationController.java:39-98` and `catalog/api/SiteProductController.java:35-114`
  — declare `@PathVariable UUID siteId` on every handler so springdoc/the generated client expose
  the URL segment, but read the *trusted* site off `AuthorizedSiteContextHolder.require().siteId()`
  and never use the raw path variable. The filter authorizes *reachability* only; per-operation
  role checks stay `@PreAuthorize` on the handler (`SiteProductController:30-33`). 6c adds no new
  context class, no `HandlerMethodArgumentResolver`, no `@Transactional` site-resolution helper.
- **F-6c-2 (verified): inventory persistence is still almost entirely site-blind.**
  `LocationInventoryRepository` has exactly three site-qualified methods — `findBySite_Id` (72-73),
  `sumQuantityByProductIdAndSiteId` (81-82), `findByStorageLocationCodeAndSiteId` (91-92). The
  methods the write/read paths actually use — `findByLocation_Id` (24-33),
  `findByLocation_IdAndProduct_Id` (38-39), `findByLocation_IdAndProduct_IdIn` (41-42),
  `findAllByIdWithGraph` (50-58), `sumQuantitiesByProductIds` (64-70), `sumQuantityByProductId`
  (78-79) — carry no site predicate. `StockMovementRepository` has **zero** occurrences of "site"
  (grep, whole file), and neither does `StockMovementSpecifications` (the audit-log filter path).
  So AC-3's "site-qualified lookup and composite constraints reject foreign-site identifiers" is
  entirely 6c work, and it is a *repository-query* change, not a service-level post-load check
  (multi-site-data-and-api.md:108-110).
- **F-6c-3 (verified): the slim-projection target is `InventoryTotalsRepository`.**
  `INVENTORY_TOTALS_SQL` (`inventory/infrastructure/InventoryTotalsRepository.java:27-47`) returns
  12 columns per product — sku, name, image_url, category id/name, parent category id/name,
  unit_cost, is_active — for **every product in the catalog**, joining `categories` twice, with a
  `LEFT JOIN location_inventory` and no site predicate, on every `/api/inventory/totals` call
  (`inventory/api/InventoryAggregateController.java:43-55`, called by
  `apps/web/src/lib/api/inventory.ts:217`). AC-5's slim projection is
  `(product_id, total_quantity, last_updated_at)` scoped by site, optionally narrowed to known
  product ids. **Zero-stock hazard (verified by reading the SQL):** the `LEFT JOIN` is the only
  reason zero-stock products appear in the result at all; a site-scoped rewrite that inner-joins or
  filters on `li.site_id` in the `WHERE` clause silently drops every product with no row at that
  site, which AC-5 explicitly forbids ("zero-stock products and quantity semantics remain
  correct"). The site predicate must live in the join condition, or the projection must be defined
  as "rows that exist" with the client treating absence as zero — that choice has to be made
  deliberately and tested, not discovered in 6d.
  `STOCK_TOTALS_SQL` (73-77) is the already-slim sibling and is the better starting shape.
- **F-6c-4 (verified, Row 4 resolution input): global stock state must not become site-scoped by
  accident.** `catalog.application.ProductStockStateWriter` (3 methods, all
  `Propagation.MANDATORY`) is the documented adapter; its own Javadoc schedules deletion for
  "Phase 6 ... once `inventory` owns quantity". Its inventory-side callers are
  `StockMovementService.applyProductActiveStatusFromTotals` (352-368),
  `updateProductActiveStatus` (861-869) and `syncProductTotals` (870-901), all fed by
  `sumCurrentTotalsByProductIds` (330-344) → `LocationInventoryRepository.sumQuantitiesByProductIds`
  — a **global, un-scoped** sum. Non-facade writers `SiteProductService:74` and
  `ProductService:136,328` also write these columns (recorded in 6b Row 4, re-confirmed by grep
  this pass). Python `forecasting-service` reads `products.quantity`/`is_active` directly
  (`supabase_repo.py:148,180-193,644-645`, per 6b's worksheet; not re-read this pass) and Kuji's
  Active/Closed tabs depend on the same meaning. **Recommended rule for 6c, to be recorded as the
  Row 4 resolution:** the global sum stays global and un-scoped; `ProductStockStateWriter`,
  `products.quantity` and `products.is_active` are not modified, redefined or dropped in 6c; the
  site-scoped total is a *new, additional* query path used only by the new v1 read surface. The
  single concrete failure mode to guard with a test is a well-meaning edit that adds `AND
  li.site_id = :siteId` to `sumQuantitiesByProductIds`, which would silently turn
  `products.quantity` into "MAIN quantity" and under-forecast every non-MAIN item. The
  user-visible consequence of keeping them global is Q-6c-2 below.
- **F-6c-5 (verified): no concurrency control exists on the read-modify-write path.**
  `LocationInventory` (`inventory/domain/LocationInventory.java:45-78`) has no `@Version` column;
  `StockMovementService.batchAdjustInventory` (147-288) preloads rows (153-155), validates
  (158-183), mutates and saves (215-261) with no lock of any kind, and the transfer path
  (`executeTransfer`, 485-618) does the same. The only backstop is the database
  `CHECK (quantity >= 0)` (`infra/init-db/20-unified-locations.sql`, `chk_quantity_non_negative`),
  which turns a lost update into either silent over-sale or a 500, depending on interleaving. AC-3
  requires "concurrent writes ... are tested" and 6b's worksheet explicitly deferred same-`(location,
  product)` contention to 6c's gate. Two viable shapes: (a) `PESSIMISTIC_WRITE` on an id-ordered
  lock query before the graph fetch — no schema change, deterministic, no client retry contract;
  (b) `@Version` optimistic locking — needs a `version` column migration *and* a documented retry/409
  contract on the v1 endpoints. **Recommended: (a).** Implementation hazard to respect:
  PostgreSQL rejects `FOR UPDATE` applied to the nullable side of an outer join, so the lock query
  must be a plain `SELECT li ... WHERE li.id IN :ids ORDER BY li.id` and must not reuse
  `findAllByIdWithGraph`'s `LEFT JOIN FETCH p.parent` (line 55). Ordering by id is what prevents
  deadlock between two overlapping batches.
- **F-6c-6 (verified): the event envelope is missing every AC-4 field, and the contract schema has
  already drifted.** `models/audit/EventOutbox.java:24-64` has no `site_id`, no `event_version`, no
  `correlation_id`/`causation_id`/`idempotency_key` columns. `EventOutboxService` smuggles
  correlation into the JSON payload (`:139`) and re-reads it at publish (`:199`); stable event
  identity today is `entityId = UUID.nameUUIDFromBytes(movement.getId())` (`:145`) plus the unique
  index on `payload->>'stock_movement_id'` (`V16__fix_duplicate_notifications.sql:12-13`) — dedupe
  exists, but expressed through JSON rather than a column. The Kafka partition key is `item_id`
  alone (`:202`); events-and-replica-readiness.md:33-34 requires `site_id + product_id`.
  **Drift worth knowing before touching the schema:** `tests/contracts/schemas/event_envelope.json`
  sets `additionalProperties: false` at *both* the envelope (line 89) and payload (line 86) level
  and does **not** declare `correlation_id`, which the producer has been emitting at both levels
  for some time — i.e. the contract test validates hand-written `conftest.py` fixtures, not real
  producer output, so it never caught it. Consequence: every new envelope field is a *required*
  edit to that schema file, and the producer-contract test as currently written cannot prove the
  Java producer matches it.
- **F-6c-7 (verified): both Python consumers tolerate unknown fields.**
  `services/forecasting-service/src/events.py:14-48` and the messaging-service equivalent declare
  plain Pydantic `BaseModel`s with no `model_config`/`class Config`, so v2's default
  `extra='ignore'` applies — adding `site_id`/`event_version`/`causation_id` to the envelope is
  additive-compatible at the consumer (assumed only in that this reasons from Pydantic's documented
  default rather than from an executed test; T-6c-9 turns it into an executed one).
- **F-6c-8 (verified): there is no durable idempotency foundation for inventory commands.**
  `services/IdempotencyService.java` is a JVM-local Caffeine cache keyed `userId:key` with a 5-minute
  TTL (lines 19-44) — not durable across restarts, not site-qualified (multi-site-data-and-api.md:68
  requires site identity in idempotency keys), and used today only by lootbox, which additionally
  has a real `UNIQUE(user_id, idempotency_key)` column as its actual safety net. AC-3's "idempotent
  retries are tested" and AC-4's "idempotency context" therefore need a decision, not a reuse
  (Q-6c-3).
- **F-6c-9 (verified): R-9 does not block 6c, provided the new v1 DTOs are new.** R-9 exists
  because `LocationInventoryResponseDTO.item` is `catalog.api.ProductSummaryDTO` and
  `LocationInventoryMapper` `uses` `catalog.api.ProductMapper`, which would make
  `inventory.api -> catalog.api` and trip `modulesDoNotDependOnAnotherModulesApi`. 6c's v1 response
  DTOs are slim by AC-5 anyway (product id + quantity + timestamps, not embedded catalog metadata),
  so they can live in `inventory.api` without that edge. The legacy
  `controllers/LocationInventoryController` and its DTOs stay exactly where they are. If a 6c task
  ever reaches for `ProductSummaryDTO`, stop — that is R-9 becoming a blocker and needs the
  catalog-owned application-level read contract described in the 6a handoff, not a workaround.
- **F-6c-10 (verified): AC-8's "before" numbers are only capturable before T-6c-5 changes the
  totals query.** Once the slim projection exists there is no clean baseline left except by
  reverting. This forces measurement to be the *first* task, not the last.

### Prerequisites and ordering constraints

- **P-1 (blocking, operational — see Q-6c-1): V59/V60 must be applied to production and the 6b
  writer release deployed before any site-scoped `stock_movements` read is trusted in production.**
  A site-qualified movement query (`WHERE sm.site_id = :siteId`) silently excludes every row whose
  `site_id` is still null. Until the backfill has run and the writer release is live, `/api/v1/
  sites/{siteId}/inventory/movements` and the scoped audit log would return a *quietly incomplete*
  history — worse than an error. This does not block writing or testing 6c code (Testcontainers
  applies the SQL itself), it blocks relying on those endpoints in production and it interacts with
  Q-6c-5.
- **P-2 (not blocking, but reorders V61): the two site indexes are 6c's need, the NOT NULL/FK is
  not.** 6b's worksheet put `idx_stock_movements_site_at` and `idx_stock_movements_site_item_at`
  inside `V61` alongside `SET NOT NULL` + FK. Only the constrain half is the gated,
  rollback-unsafe, deploy-ordered step; `CREATE INDEX CONCURRENTLY` is rollback-safe at any point
  (same reasoning as `V58`). Recommendation: split the two indexes into a 6c index-only migration
  (`V62`, `CONCURRENTLY` + paired `.conf`, matching `V58`'s precedent) and leave `V61`'s
  `SET NOT NULL`/FK as the separate gated PR it already is. Without this, 6c's scoped movement
  reads seq-scan a ledger table.
- **P-3 (internal ordering): outbox expand migration + writer ship together, constrain is deferred.**
  Exactly 6b's pattern: `V63` expand (nullable `site_id`, `event_version`, `correlation_id`,
  `causation_id`, `idempotency_key`), `V64` backfill for unpublished rows, and no constrain step in
  this record. Old deployed images ignore unknown nullable columns, so expand alone stays
  rollback-safe.
- **P-4 (internal ordering): T-6c-0 (baseline measurement) precedes T-6c-5 (slim projection)** per
  F-6c-10.
- **P-5 (internal ordering): T-6c-1/T-6c-2 (site-qualified repository methods and facade overloads)
  precede every controller task**, so no controller ever has a reason to filter by site in Java
  after loading globally.
- **P-6**: no production apply and no deployment is authorized by this record, unchanged from 6a/6b.

### Open questions for the user (do not proceed past the dependent task without an answer)

- **Q-6c-1 (deployment/data-visibility, blocks P-1 and Q-6c-5): what is actually applied in
  production today?** 6b's record has explicit user confirmation for V56/V57 only; V58/V59/V60 were
  written in 6b but no apply was authorized, and the 6b writer release's deploy status is not
  recorded. 6c needs the real answer to decide whether the v1 movement endpoints can ship enabled,
  and whether the P-2 index split can be applied before `V61`.
- **Q-6c-2 (product): after 6d, the Products page shows a per-site quantity next to a global
  active status.** Keeping `products.is_active` global (F-6c-4, and multi-site-data-and-api.md:25-33
  fixes that meaning deliberately) while AC-6 restores quantities "only from scoped totals" means a
  product with 0 at MAIN and 5 at SECOND renders, at MAIN, as quantity 0 but still Active. That is
  correct per the durable spec and it is also a visible behavior change users will read as a bug.
  Confirm the intended presentation (accept as-is / show both numbers / surface an "in stock at
  another site" affordance) before 6c freezes the totals DTO shape, since the DTO is what 6d can
  render.
- **Q-6c-3 (contract + data): idempotency model for inventory mutations.** Three sub-decisions:
  (a) is `Idempotency-Key` **required** on the v1 mutation routes (a contract requirement the web
  client must satisfy) or optional-but-honored; (b) durable table keyed `(site_id, user_id, key)`
  with the response/effect recorded, or the existing JVM-local Caffeine cache (F-6c-8 — not durable,
  not site-qualified, contradicts multi-site-data-and-api.md:68); (c) does the key also become the
  event envelope's `idempotency_key` (AC-4) or are they separate concepts. (b) implies another
  migration in this record.
- **Q-6c-4 (deployment/ordering risk): change the Kafka partition key from `item_id` to
  `site_id:product_id` now, or defer?** events-and-replica-readiness.md:33-34 requires the composite
  key, but changing a partition key remaps existing keys across partitions, so in-flight events for
  one product can be processed out of order across the switch. Impact is likely nil today (single
  broker, topic partition count not verified this pass — `infra/docker-compose.yml:150,199` only set
  the topic name), but "likely nil" on ordering is exactly the kind of thing that should be
  confirmed rather than assumed, and the safe version needs a drain before the switch.
- **Q-6c-5 (audit completeness, depends on Q-6c-1): may the v1 movement/audit-log endpoints show
  rows whose `site_id` is still null?** Options: hide them (clean tenancy, incomplete audit trail
  until backfill), or treat null as MAIN in the query during the compatibility window (complete
  history, one documented compatibility clause to remove later). This is an audit-trail
  completeness decision, not a technical one.

Anything not listed above is a recorded assumption, resolved in the task list: same-site-only
transfers in 6c (audited inter-site transfers stay Phase 7), legacy routes keep MAIN-resolving
behavior and gain `Deprecation`/`Link` headers with no `Sunset` (matching
`LegacyCatalogDeprecationFilter:13-24`), and v1 role requirements mirror the legacy endpoints they
replace rather than tightening them mid-migration.

### User decisions on Q-6c-1 through Q-6c-5 (2026-09-11)

- **Q-6c-1 resolved: not yet deployed.** V58/V59/V60 and the 6b writer release are confirmed NOT
  live in production. A-6c-1 stands as written — every 6c claim about scoped movement reads stays
  proven against Testcontainers only; P-1 blocks relying on `/api/v1/sites/{siteId}/inventory/
  movements` in production until the user separately confirms deploy + backfill completion.
- **Q-6c-2 resolved: keep as-is.** Global `products.is_active` next to a site-scoped quantity
  (e.g. "quantity 0, Active" at a site with no local stock but stock elsewhere) is accepted as
  correct per spec; no 6c or 6d UI affordance required for the mismatch.
- **Q-6c-3 resolved: durable, site-qualified idempotency (T-6c-10 supersedes its "shape decided by
  Q-6c-3" placeholder with this concrete design):**
  - `Idempotency-Key` header is **required** on the v1 mutation routes (`POST .../adjustments`,
    `POST .../transfers`); legacy `/api/inventory/*`/`/api/stock-movements/*` are explicitly
    unaffected (no new requirement on routes T-6c-13 leaves untouched).
  - New durable table, unique on `(site_id, user_id, idempotency_key)`, using the trusted
    `AuthorizedSiteContext` site/user, not client-supplied values.
  - Stores command type, a canonical request fingerprint, and the original result. A replay with
    the same key and same fingerprint returns the stored result; the same key with a different
    fingerprint returns 409 Conflict.
  - The idempotency record commits in the **same transaction** as the inventory/movement/audit/
    outbox writes it guards — one committed effect per key, and a rolled-back command leaves the
    key retryable (no idempotency row survives a failed attempt).
  - Authorization is rechecked before serving a replayed result (a role/membership change between
    the original call and a retry must not bypass a now-invalid grant).
  - The command's idempotency key propagates into the event envelope's `idempotency_key` (AC-4);
    each emitted event still gets its own distinct event id — the command key identifies the
    command, not the event.
  - Retention policy must be defined explicitly (not left implicit) and tested: concurrent
    duplicate submissions, restart-then-replay, rollback-then-retry, payload-conflict (409), and
    site/user isolation (same key, different site or user, is not a replay).
  - This adds its own expand migration (a new table), consistent with 6b/6c's expand-only pattern
    for this record — no constrain step.
- **Q-6c-4 resolved: change the Kafka partition key to `site_id:product_id` in 6c, gated on an
  explicit cutover, not a bare code change.** T-6c-8 must additionally, before flipping the
  partition key in any environment beyond tests:
  - Verify the actual topic partition count in the target environment before reasoning about
    ordering risk (A-6c-2 — not yet established; `infra/docker-compose.yml:150,199` only names the
    topic).
  - Treat existing Kafka records as immutable: the new key governs routing for events published
    *after* the switch, including retries. It does not move already-published records, so a
    coordinated drain is required, not a flag flip: pause inventory mutations, drain the old
    publisher's outbox and any in-flight sends, let consumers finish processing through recorded
    partition offsets, then cut the publisher over and resume writes.
  - Test duplicate delivery, delayed retries and historical replay across the cutover boundary
    explicitly — event-id dedupe (T-6c-9) does not by itself prevent an older, previously-unseen
    event from overwriting newer state once ordering guarantees change.
  - Check every consumer that currently relies on shared partition ordering for one product across
    sites (global product-state consumers) and confirm each tolerates the loss of that shared
    ordering once site/product keys replace it.
  - Keep the partition count unchanged during the cutover; document rollback as its own equally
    coordinated switch, not an inverse flag flip.
  - Per P-6, no production apply/deployment is authorized by this record: T-6c-8 implements and
    tests the new key and the cutover mechanics against Testcontainers/local Kafka; the gated
    cutover itself is a deploy-time action requiring separate authorization, and AC-4 is not
    "complete" against production until that gate is actually passed and recorded, not merely
    coded.
- **Q-6c-5 resolved: surface null-`site_id` rows as unknown-site, not hidden.** T-6c-11's `GET
  .../inventory/movements` includes rows whose `site_id` is still null, tagged explicitly (e.g. a
  `siteAttribution: "UNKNOWN"` field or equivalent) rather than silently omitted, preserving audit
  completeness during the pre-backfill compatibility window. This is the inverse of what P-1 warned
  against for *scoped-only* reliance — the chosen behavior is now "include and label," not "filter
  out," so the earlier "quietly incomplete" risk is replaced by an explicit compatibility marker
  the client can render. Remove the marker path once Q-6c-1's backfill/deploy is separately
  confirmed complete (tracked as new debt, not closed by this record).

### 6c task list

Ordered; each is independently testable. Per the slice cadence these are internal checklist items,
not separate review/commit gates.

- **T-6c-0 — Capture the AC-8 "before" measurement.** Changes: a repeatable harness (Testcontainers
  or a `@SpringBootTest` with a seeded catalog of fixed size) that records, for `/api/inventory/
  totals`, `/api/inventory/by-product/{id}` and the audit-log page: SQL statement count, rows
  returned, and serialized response bytes. Why: F-6c-10 — after T-6c-5 the baseline is
  unrecoverable, and AC-8 explicitly rejects historical cumulative counters as proof. Test: the
  harness itself, with the recorded numbers written into this log (not just an assertion).
- **T-6c-1 — Site-qualified repository methods (no callers yet).** Changes:
  `LocationInventoryRepository` gains `findByIdAndSite_Id`, `findByLocation_IdAndProduct_IdAndSite_Id`,
  `findAllByIdInAndSite_Id(...WithGraph)`, `findByLocation_IdAndSite_Id`, and a site-scoped
  `sumQuantitiesByProductIdsAndSiteId`; `StockMovementRepository` gains site-qualified history/audit
  variants; `StockMovementSpecifications` gains a mandatory site predicate for the audit-log filter
  path. The existing global methods stay, untouched (F-6c-4). Why: AC-3 requires the site predicate
  at the query, not after load. Test: a Testcontainers IT that seeds the same `(location, product)`
  shape at MAIN and SECOND and asserts each new method returns only its own site's rows and
  `Optional.empty()`/empty list for a foreign-site id.
- **T-6c-2 — Site-scoped facade overloads on `InventoryQueries`/`InventoryOperations`.** Changes:
  `(UUID siteId, ...)`-first overloads mirroring `LocationService`'s existing
  `getLocationById(siteId, id)` shape; foreign-site ids raise `InventoryNotFoundException` (→ 404 per
  multi-site-data-and-api.md:63-64), not an authorization error. Existing global methods keep their
  current signatures so no 6a caller is disturbed. Why: AC-1's facade boundary plus AC-3. Test: unit
  tests for the mapping-to-404 behavior; the foreign-site rejection proven at the IT level in
  T-6c-1/T-6c-12 rather than duplicated here.
- **T-6c-3 — `stock_movements` site index migration (`V62`, `CONCURRENTLY` + paired `.conf`).**
  Changes: `idx_stock_movements_site_at(site_id, at DESC)` and
  `idx_stock_movements_site_item_at(site_id, item_id, at DESC)`, split out of `V61` per P-2; `V61`
  keeps only `SET NOT NULL` + FK and stays a separate gated PR. Why: 6c's scoped reads otherwise
  seq-scan the ledger, and the index half is rollback-safe while the constrain half is not. Test: a
  migration IT executing the real `.sql` against Testcontainers Postgres asserting index
  existence/shape (the `StockMovementSiteMigrationIT` pattern), plus an `EXPLAIN` assertion that the
  scoped history query uses the index — the cheapest available guard against the index being
  cosmetically present but unusable because of a column-order mismatch.
- **T-6c-4 — Same-site preconditions on existing multi-location writes.** Changes: `transferInventory`/
  `batchTransferInventory` (`StockMovementService:369-618`) reject a source/destination pair whose
  sites differ; `MachineDisplayService`'s swap path gains the same-site precondition flagged as
  advisory debt by 6b's review. Why: AC-3's tenant invariants and multi-site-data-and-api.md:67
  ("cross-site joins prohibited except ... transfer workflows") — audited inter-site transfers are
  Phase 7's, so until then the correct behavior is an explicit rejection, not a silent cross-site
  write. Test: unit tests asserting the rejection and its message/type; one IT covering the machine
  swap path, whose fixtures already carry two distinct sites after 6b's review fix
  (`MachineDisplayServiceNotificationTest`, MAIN/SECOND).
- **T-6c-5 — Slim, site-scoped, batched totals projection.** Changes: a new
  `InventoryTotalsRepository` query returning `(product_id, total_quantity, last_updated_at)` for one
  site, with an optional bounded `productIds` filter (bound the batch explicitly — a documented
  maximum, rejected with 400 above it — so AC-7's "neither full-catalog refreshes nor one request
  per product" has a real ceiling); a matching slim `inventory.api` DTO that embeds no catalog type
  (F-6c-9). `INVENTORY_TOTALS_SQL` stays for the legacy endpoint. Why: AC-5, and the largest single
  AC-8 lever (F-6c-3). Test: an IT covering (a) a zero-stock product still appearing with quantity 0
  — or its documented absence-means-zero contract, whichever is chosen — per F-6c-3's hazard; (b)
  the same product with different quantities at MAIN and SECOND resolving independently; (c) the
  batched form returning exactly the requested ids; (d) the over-limit rejection.
- **T-6c-6 — Row locking on the read-modify-write paths.** Changes: `PESSIMISTIC_WRITE`, id-ordered
  lock query ahead of the graph fetch in `batchAdjustInventory` and `executeTransfer`, per F-6c-5's
  recommendation and its `FOR UPDATE`/outer-join hazard. Why: AC-3's concurrent-write requirement;
  today a concurrent double-subtract is a lost update or a CHECK-constraint 500. Test: a concurrency
  IT (real Postgres, two threads, same `(location, product)`) asserting the final quantity equals the
  serialized result, exactly two movement rows exist, and neither thread observes a negative
  quantity — this is the "proves a real invariant" case, and it is the one 6b explicitly deferred to
  6c.
- **T-6c-7 — Outbox envelope expand migration (`V63`) + `EventOutbox` entity fields.** Changes:
  nullable `site_id`, `event_version`, `correlation_id`, `causation_id`, `idempotency_key` columns
  and the matching entity fields; `V64` backfills unpublished rows (site from the referenced
  movement, `event_version` to the initial version, correlation from the existing payload key). No
  constrain step in this record (P-3). Why: AC-4's durable envelope. Test: migration + backfill ITs
  executing the real SQL (the `StockMovementSiteBackfillIT` pattern), including a guard case for an
  outbox row whose movement no longer exists.
- **T-6c-8 — `EventOutboxService` writes the envelope and the composite partition key.** Changes:
  populate the new columns at creation time from the movement (`site`, actor, correlation from
  `CorrelationIdContext`, idempotency per Q-6c-3) instead of/in addition to the JSON payload; publish
  them at the envelope level; partition key becomes `site_id:product_id` **subject to Q-6c-4**. Why:
  AC-4 and events-and-replica-readiness.md:19-34. Test: an IT asserting a movement's outbox row
  carries the movement's site and a stable event identity, that re-running creation for the same
  movement does not create a second logical event (the `V16` dedupe index, now exercised
  deliberately), and a key-shape assertion on the produced record.
- **T-6c-9 — Contract schema and producer/consumer compatibility.** Changes:
  `tests/contracts/schemas/event_envelope.json` gains the new envelope fields **and** the
  long-missing `correlation_id` (F-6c-6 drift); fixtures updated; a producer-side test that
  validates a payload built by the real Java producer path rather than a hand-written fixture, if
  that is achievable without cross-language plumbing — otherwise record the gap explicitly rather
  than letting the current false-confidence stand. Why: AC-4's "affected producers and consumers
  pass compatibility, claim, duplicate, reorder and crash/retry tests". Test: the Python contract
  suite; plus consumer-side tests proving both services still parse an envelope carrying the new
  fields (F-6c-7's `extra='ignore'` reasoning turned into an executed assertion) and that duplicate
  delivery of the same `event_id` produces one effect.
- **T-6c-10 — Command idempotency (shape decided by Q-6c-3).** Changes: whichever of the three
  sub-decisions the user takes; if durable, a table keyed `(site_id, user_id, idempotency_key)` with
  a unique constraint and its own expand migration. Why: AC-3's "idempotent retries are tested" and
  AC-4's idempotency context; F-6c-8 shows nothing reusable exists. Test: an IT replaying the same
  batch-adjust twice with one key and asserting exactly one set of inventory/movement/outbox effects,
  and that the same key at a different site is *not* treated as a replay.
- **T-6c-11 — v1 read routes.** Changes: `GET /api/v1/sites/{siteId}/inventory/totals`
  (optional `productIds`), `GET /api/v1/sites/{siteId}/inventory/products/{productId}`,
  `GET /api/v1/sites/{siteId}/inventory/locations/{locationId}`,
  `GET /api/v1/sites/{siteId}/inventory/movements` (history + audit-log filters), in a new
  `inventory.api` controller reading the site off `AuthorizedSiteContextHolder` and declaring the
  `siteId` path variable for springdoc, per F-6c-1. Distinct handler/DTO names from the legacy
  controllers so springdoc does not collide operation IDs and silently reassign the legacy ones
  (`SiteLocationController:27-38` records that trap). Why: AC-5. Test: a security/authorization IT
  in the `LocationInventoryControllerSecurityIT` family covering the role matrix, a foreign-site
  `siteId` (403), an unknown `siteId` (404), a valid site with a foreign-site entity id (404), and
  no-JWT (401, which the filter deliberately leaves to Spring Security —
  `SiteAccessAuthorizationFilter:65-75`).
- **T-6c-12 — v1 mutation routes.** Changes: `POST /api/v1/sites/{siteId}/inventory/adjustments`,
  `POST /api/v1/sites/{siteId}/inventory/transfers` (same-site only per T-6c-4), each through the
  scoped facade, each with `@PreAuthorize` mirroring the legacy endpoint's role set. Why: AC-3/AC-5.
  Test: authorization matrix IT; an atomicity IT proving inventory + movement + audit + outbox commit
  or roll back together through the *controller* path (extending, not duplicating,
  `InventoryOperationsCallerTransactionIT`/`StockMovementOutboxAtomicityIT`); and a test that a
  request naming a location belonging to another site is rejected before any write.
- **T-6c-13 — Legacy compatibility and deprecation.** Changes: legacy `/api/inventory/*` and
  `/api/stock-movements/*` keep their current MAIN-resolving behavior untouched and gain
  `Deprecation` + `Link` headers via a filter registered exactly like
  `LegacyCatalogDeprecationConfig:14-21`, no `Sunset`. `/api/locations/{id}/inventory` is
  `sites`-shaped legacy routing and is left alone this pass. Why: multi-site-data-and-api.md:126-128
  and AC-5's "prove legacy compatibility and no unintended contract break". Test: an IT asserting the
  headers on legacy inventory routes and their **absence** on `/api/v1/**`; existing legacy endpoint
  tests stay green unmodified, which is the actual compatibility proof.
- **T-6c-14 — Regenerate the contract and client.** Changes: `packages/contracts/openapi.json` and
  `packages/api-client/src/schema.d.ts` regenerated via `OpenApiContractExportTest`. Why: AGENTS.md
  requires both with any endpoint change. Test: `OpenApiContractExportTest`; a reviewed diff showing
  only additions (new v1 paths/schemas) and no modification to existing legacy operation IDs,
  parameters or response shapes — the 6a precedent used byte-identity, which no longer applies now
  that endpoints are genuinely being added.
- **T-6c-15 — Stock-state compatibility guard.** Changes: none to production code by design; a test
  that pins `sumQuantitiesByProductIds`/`syncProductTotals`/`calculateTotalInventory` as **global**
  (a product stocked at two sites yields the sum of both in `products.quantity`), plus a
  `ProductStockStateWriter` caller-set pin in the style of `InventoryOperationsCallerSetTest`. Why:
  F-6c-4 — this is the Row 4 resolution's enforcement, and the failure mode it guards is silent.
  Test: the above, run against real Postgres with two sites.
- **T-6c-16 — Boundary and documentation updates.** Changes: ArchUnit stays at its current 8 rules
  unless a new edge appears (the v1 controllers depend only on `inventory.application`,
  `shared.web` and `identity.domain` — all already-approved edges, so **expect no frozen-store
  regeneration**; if one is demanded, that is a signal a DTO reached into another module's `api`,
  i.e. F-6c-9); record the Row 4 resolution, the same-site transfer precondition and the new v1
  routes in `docs/specs/spring-domain-modular-monolith.md` and
  `docs/baseline/api-v1-map.md` (the doc the deprecation `Link` header already points at). Test:
  `ArchitectureTest` stable across two independent clean rebuilds with a clean `git status` on
  `archunit_store/`; doc link check.
- **T-6c-17 — Capture the AC-8 "after" measurement and record the delta.** Changes: rerun T-6c-0's
  harness against the v1 slim/batched path at equal catalog size. Why: AC-8, and 6e regresses
  against these numbers rather than re-deriving them. Test: recorded rows/bytes/query counts for
  full-catalog and known-ID cases, with the explicit note that the browser/realtime half of AC-8
  remains 6e's.

### Test plan summary

New proof concentrated in five ITs against real Postgres (Testcontainers), following 5c/6b's proven
pattern: tenant isolation (T-6c-1), concurrency on the same `(location, product)` (T-6c-6),
atomicity through the v1 controller path (T-6c-12), idempotent replay (T-6c-10) and the migration/
backfill pair (T-6c-3/T-6c-7). Authorization-matrix ITs extend the existing
`*ControllerSecurityIT`/`RBACAlignmentIT` family rather than starting a new one. Keep green:
`StockMovementOutboxAtomicityIT`, `AdjustToKafkaIT`, `InventoryOperationsCallerTransactionIT`,
`InventoryOperationsSharedCallerPathIT`, `InventoryOperationsSiteIT`, `StockMovementSiteBackfillIT`,
`StockMovementSiteMigrationIT`, `ArchitectureTest` (8 rules), `OpenApiContractExportTest`.
Standing reminder (memory + `.specs/phase-5c-site-products/log.md:12-20`): plain `./mvnw test` skips
every `*IT.java` — run it and `./mvnw test -Dtest='*IT'`.

### Risks and open assumptions carried into 6c

- A-6c-1: P-1's production state is unverified (Q-6c-1). Every 6c claim about scoped movement reads
  is proven against Testcontainers fixtures only, exactly as 6b's backfill was.
- A-6c-2: the Kafka topic's partition count was not established this pass (only the topic name, at
  `infra/docker-compose.yml:150,199`); Q-6c-4's ordering risk assessment depends on it.
- A-6c-3: `forecasting-service`'s dependence on global `products.quantity`/`is_active` is carried
  forward from 6b's worksheet reading of `supabase_repo.py:148,180-193,644-645` and was not re-read
  against the Python source this pass; no Java test covers that consumer, which is precisely why
  T-6c-15 pins the Java side instead.
- A-6c-4: F-6c-7's "consumers ignore unknown fields" reasons from Pydantic v2's documented default,
  not from an executed test, until T-6c-9.
- A-6c-5: carried forward unresolved — A-1 (`location_inventory.site_id`'s NOT NULL/FK/trigger in
  Supabase itself), A-2/A-3 (empirical backfill counts and lock time at production volume), the
  missing `V58` IT, and R-4 through R-8. R-9 is not a 6c blocker under F-6c-9's condition.

## 6c implementation (T-6c-0..T-6c-3) (2026-09-11)

Implements T-6c-0 through T-6c-3 from the task list above, in order (P-4/P-5 respected: baseline
before the totals query changes, which hasn't happened yet either — T-6c-5 is still open;
repository/facade work before any controller — no controller exists yet). Stopped at this clean
boundary per the "stop when the remaining tasks are large" ground rule, with the rest of the task
list (T-6c-4 through T-6c-17) precisely scoped and unstarted, not partially guessed at.

- **T-6c-0 — AC-8 baseline measurement.** Added
  `InventoryEgressBaselineIT` (extends `BaseKafkaIntegrationTest`, real Postgres — H2 was tried
  first and rejected: `InventoryTotalsRepository.findAllInventoryTotals()`'s native query casts
  `p.id` straight to `UUID`, which H2 returns as `byte[]` for a UUID column and throws
  `ClassCastException` on, confirmed by an actual failed run before switching). Seeds a fixed,
  deterministic catalog (25 products across 3 MAIN locations, every third product deliberately
  zero-stock with no `LocationInventory` row at all, matching F-6c-3's zero-stock hazard) and
  measures, via Hibernate `Statistics.getPrepareStatementCount()` (every JDBC statement, matching
  `CatalogQueriesEgressIT`'s established reasoning over `getQueryExecutionCount()`) plus
  `ObjectMapper.writeValueAsBytes()` for actual serialized response size:
  - `GET /api/inventory/totals` (`InventoryQueries.findAllInventoryTotals()`): **1 statement, 25
    rows, 9446 bytes** for the fixed 25-product catalog.
  - `GET /api/inventory/by-product/{id}` (`InventoryAggregateService.getInventoryByProduct`, one
    zero-stock product): **2 statements, 0 entry rows, 181 bytes**.
  - `GET /api/audit-logs` page 0/size 20 (`AuditLogService.getAuditLogs`, no `StockMovement` rows
    carry an `AuditLog` in this fixture — an honest data point, not a synthetic one): **1
    statement, 0 rows, 317 bytes**.
  - These are the numbers T-6c-17 diffs against once the slim/batched v1 path exists. Recorded
    from an actual run, not assumed — see "Verification" below for the exact command.
  - **Review-driven fix (P2 x3, 2026-09-11, superseding the three numbers above):** independent
    review found (1) the fixture committed 25 products per test with no cleanup, so catalog size
    depended on test order and the `>= PRODUCT_COUNT` assertion would hide accumulation across
    runs; (2) `firstProductId` — used for the by-product baseline — is always the *first* seeded
    product, and `i == 0` always hits the `i % 3 == 0` zero-stock branch, so that baseline measured
    an empty response every run; (3) the audit-log baseline's fixture created no `AuditLog` rows at
    all, so it also measured an empty page, and none of the three measurements captured AC-8's
    "database rows"/"projected bytes" dimensions independently of the mapped API response — they
    only measured response-DTO counts and serialized JSON bytes. Fixed, all in
    `InventoryEgressBaselineIT`:
    - Added `@AfterEach cleanup()` that deletes exactly the rows this fixture's own tracked ids
      created (products, categories, locations, storage locations, location-inventory, stock
      movements, the one audit log row) — not a blanket `deleteAll()` on any shared table, so this
      IT never touches state left by other IT classes sharing the same JVM-wide Testcontainers
      Postgres instance. The full-catalog assertion changed from `isGreaterThanOrEqualTo` to
      `isEqualTo(PRODUCT_COUNT)`, further hardened by filtering both the API result and the raw SQL
      (`WHERE p.id = ANY(?)`) to this run's own tracked product ids, so the exact-25 assertion holds
      even if another class's residue is ever present in the shared container.
    - Added a separate `stockedProductId` (the first product actually seeded with a
      `LocationInventory`/`StockMovement` row, distinct from the deliberately-zero-stock
      `firstProductId`) and renamed the test to `baseline_inventoryByProduct_stockedProduct`.
    - The fixture now calls `auditLogService.createAuditLog(...)` once during setup to seed one
      real `audit_logs` row, tracked by id and deleted in cleanup.
    - Added a `measureDbEgress` helper: a raw `JdbcTemplate` pass over the same query the
      production code runs (the totals query's SQL duplicated verbatim for an independent
      measurement; `SELECT * FROM location_inventory WHERE product_id = ?` for by-product;
      `SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT 20 OFFSET 0` for the audit page),
      summing a per-column-value byte-length estimate per row (UUID=16, boolean=1, numeric=8,
      strings/other=UTF-8 byte length, null=1) — an explicit label estimate per AC-8's own
      allowance, kept in a separate `dbRowCount`/`projectedDbBytesEstimate` field from
      `apiRowCount`/`apiBytes` so the two are never conflated again.
  - **Corrected numbers from the fixed, re-run test** (see "Verification" below):
    - `GET /api/inventory/totals`, full 25-product catalog: **1 statement, apiRows=25,
      apiBytes=9447, dbRows=25, projectedDbBytesEstimate=4080**.
    - `GET /api/inventory/by-product/{id}`, one stocked product: **2 statements, apiRows=1,
      apiBytes=523, dbRows=1, projectedDbBytesEstimate=124**.
    - `GET /api/audit-logs` page 0/size 20, one seeded audit row: **1 statement, apiRows=1,
      apiBytes=825, dbRows=1, projectedDbBytesEstimate=198**.
    - These superseded numbers, not the original three, are what T-6c-17 must diff against.
  - **Review-driven fix round 2 (P2 x2 + P3, 2026-09-12, superseding the numbers immediately
    above):** independent review found (1) the totals measurement's post-fetch application-side
    filter (`.filter(t -> createdProductIds.contains(...))`) and the JDBC side's in-SQL filter
    (`WHERE p.id = ANY(?)`) measure two different workloads whenever any other fixture's rows are
    also present in the shared Testcontainers Postgres instance — the real production query
    (`InventoryQueries.findAllInventoryTotals()`) has no id filter at all, so its actual executed
    cost scales with the whole table, not with this fixture's 25 rows; filtering the *response*
    down to 25 afterward doesn't change what the database actually did; (2) the by-product DB-egress
    measurement only queried `location_inventory`, undercounting the real read — `
    InventoryAggregateService.getInventoryByProduct` also fetches the product row (`CatalogQueries
    .getById` -> a second, distinct SQL statement) and `LocationInventoryRepository
    .findByProduct_Id`'s own query is a `JOIN FETCH` across `location_inventory`, `locations` and
    `storage_locations`, not `location_inventory` alone; one P3, the raw `Connection`/`java.sql
    .Array` acquired to build the `WHERE p.id = ANY(?)` bind parameter was never closed/freed. Fixed,
    all in `InventoryEgressBaselineIT`:
    - Replaced the tracked-id filtering approach with `clearCatalogTables()` — `deleteAllInBatch()`
      on `stock_movements`, `location_inventory`, `products`, `categories` (FK-safe, children
      before parents) — called in both `@BeforeEach` (before seeding, so every test starts from a
      genuinely empty catalog regardless of what ran before it in the shared container) and
      `@AfterEach` (leaving the DB clean for whatever runs after). Locations/storage
      locations/the seeded audit log stay on the existing targeted, tracked-id cleanup — they
      aren't part of the totals query's join and don't need blanket clearing. With the catalog
      genuinely isolated, the totals test now calls `inventoryQueries.findAllInventoryTotals()`
      unfiltered and runs its SQL counterpart unfiltered too — both sides measure the exact same,
      real workload, and the `WHERE p.id = ANY(?)`/raw-`Connection`/`Array` code path is gone
      entirely (removing the P3 leak by removing the code that caused it, not by adding a
      `finally`).
    - `measureDbEgress` now takes a `List<DbQuery>` (a new `record DbQuery(String sql, Object...
      args)`) and sums rows/bytes across every statement a production call actually issues, instead
      of one hardcoded query. The by-product test now supplies both `SELECT * FROM products WHERE
      id = ?` and the three-table join `location_inventory JOIN locations JOIN storage_locations`
      query, matching the real two-statement read and asserting `statementCount() == 2L` (tightened
      from `isGreaterThan(0L)` now that the exact count is known and stable).
  - **Corrected numbers from the round-2 fix, re-run test** (superseding the "corrected numbers"
    block above; see "Review-driven fix round 2" verification below):
    - `GET /api/inventory/totals`, full 25-product catalog (now genuinely the entire `products`
      table, not a filtered subset): **1 statement, apiRows=25, apiBytes=9445, dbRows=25,
      projectedDbBytesEstimate=4078**.
    - `GET /api/inventory/by-product/{id}`, one stocked product, now covering both the product
      fetch and the location_inventory/locations/storage_locations join: **2 statements, apiRows=1,
      apiBytes=523, dbRows=2, projectedDbBytesEstimate=595**.
    - `GET /api/audit-logs` page 0/size 20, one seeded audit row (unchanged by this round): **1
      statement, apiRows=1, apiBytes=825, dbRows=1, projectedDbBytesEstimate=198**.
    - These are the numbers T-6c-17 must diff against; the round-1 "corrected numbers" block above
      is now superseded in turn.
- **T-6c-1 — Site-qualified repository methods, no callers yet.** Added to
  `LocationInventoryRepository`: `findByIdAndSite_Id`, `findByLocation_IdAndProduct_IdAndSite_Id`,
  `findAllByIdInAndSite_IdWithGraph` (same eager-loaded graph as `findAllByIdWithGraph`, foreign-
  site ids silently excluded from the batch result — callers must check returned size against
  requested count), `findByLocation_IdAndSite_Id` (same child/CUSTOM-kuji exclusion filter as the
  un-scoped `findByLocation_Id`), `sumQuantitiesByProductIdsAndSiteId`. Added to
  `StockMovementRepository`: `findByItem_IdAndSite_IdOrderByAtDesc` (paginated). Added
  `StockMovementSpecifications.withSiteFilter(filters, siteId)`, composing the existing
  `withFilters` with a mandatory site predicate — implemented as an explicit `LEFT JOIN` on `site`
  (not implicit path navigation via `root.get("site")`, which would silently inner-join and drop
  null-site rows) so a movement with `site IS NULL` still matches, per the resolved Q-6c-5
  decision ("include and label", not "filter out", during the pre-backfill compatibility window).
  Every existing un-scoped method is untouched (F-6c-4's constraint against accidentally scoping
  the global sum).
  - Test: `LocationInventorySiteScopedQueriesIT` (H2 `test` profile — none of these queries are
    native/Postgres-specific, matching the reasoning already recorded for
    `InventoryOperationsSiteIT` choosing H2 over Testcontainers) — 7 cases, seeding the same
    `(location, product)` shape at MAIN and SECOND: each new method returns only its own site's
    rows, `Optional.empty()`/an empty list for a foreign-site id, and `withSiteFilter` matches its
    own site plus null-site rows while excluding the foreign site's row. 7/7 pass.
- **T-6c-2 — Site-scoped facade overloads on `InventoryQueries`/`InventoryOperations`.** Added to
  `InventoryQueries`: `findInventoryBySite(siteId, inventoryId)`,
  `findInventoryBySite(siteId, locationId, productId)` (both throw
  `InventoryNotFoundException` on a foreign-site/missing row, matching
  `LocationService.getLocationById(siteId, id)`'s -> 404 shape per
  multi-site-data-and-api.md:63-64, not an authorization error — the site boundary itself belongs
  to the trusted `AuthorizedSiteContext` at the controller, not here), `findByLocationIdAndSite`,
  `sumQuantityByProductIdAndSite`, `sumQuantitiesByProductIdsAndSite`, `findMovementHistoryBySite`,
  `findAuditLogPageBySite` (composes `withSiteFilter`). Added to `InventoryOperations`:
  `findInventory(siteId, locationId, productId)` — read-only; the site-scoped *write* path
  (adjust/transfer with row locking and the same-site transfer precondition) is deliberately left
  to T-6c-6/T-6c-12 rather than duplicated here ahead of the locking design F-6c-5 specifies.
  Existing global methods on both facades are unchanged, so no 6a caller is disturbed.
  - Test: `InventoryQueriesSiteScopedTest` (new, Mockito unit test, 7 cases) proves the 404 mapping
    and that the site id reaches the underlying site-qualified repository method unchanged.
    `InventoryOperationsTest` extended with 2 cases for the new `findInventory` overload. The
    repository-level tenant-isolation proof (a foreign-site id genuinely returning nothing at the
    database) stays at T-6c-1's IT, per the task list's own split between unit-level facade proof
    and IT-level repository proof. 7/7 and 12/12 (whole class) pass respectively.
- **T-6c-3 — `stock_movements` site index migration (`V62`).** Added
  `V62__stock_movements_site_indexes.sql` (+ paired `.conf`, `executeInTransaction=false`,
  matching `V58`'s precedent): `idx_stock_movements_site_at(site_id, at DESC)` and
  `idx_stock_movements_site_item_at(site_id, item_id, at DESC)`, split out of what the 6b
  worksheet originally scoped as part of `V61` per P-2 — only the index half is rollback-safe at
  any point; `V61`'s `SET NOT NULL` + FK stays the separate gated PR/record it already was.
  - Test: `StockMovementSiteIndexMigrationIT` (new, Testcontainers Postgres, executing the real
    `V59` then `V62` `.sql` text verbatim — Flyway never runs these files at runtime per F-1).
    Asserts both indexes exist with the documented column order via `pg_indexes`, and — the "index
    cosmetically present but unusable" guard the task explicitly calls for — that
    `EXPLAIN` on the scoped-history query shape (`site_id = ? AND item_id = ? ORDER BY at DESC`)
    actually plans through `idx_stock_movements_site_item_at`, using `SET enable_seqscan = off`
    around the `EXPLAIN` since the ~500 seeded rows are not by themselves enough for a real
    planner to prefer an index scan over a sequential scan at that size — a measurement technique
    note, not a claim about production planner behavior. 2/2 pass.

### Verification (T-6c-0..T-6c-3, actual commands and results, not paraphrased)

- `./mvnw -q clean test-compile` — clean, zero errors.
- `./mvnw -q clean test` (full unrestricted suite, plain `test` — skips `*IT.java` by design) —
  exit 0.
- New/changed classes run individually, all green:
  - `./mvnw -q test -Dtest='InventoryEgressBaselineIT'` — 3/3 pass (baseline numbers above logged
    at INFO and copied from this run's actual output).
  - `./mvnw -q test -Dtest='LocationInventorySiteScopedQueriesIT'` — 7/7 pass.
  - `./mvnw -q test -Dtest='InventoryQueriesSiteScopedTest,InventoryOperationsTest'` — 7/7 and
    12/12 pass.
  - `./mvnw -q test -Dtest='StockMovementSiteIndexMigrationIT'` — 2/2 pass.
- `./mvnw -q clean test -Dtest='*IT'` — **8 failures**, all in `AnalyticsControllerSecurityIT`/
  `ForecastControllerSecurityIT`, proven pre-existing and order-dependent, not a 6c regression —
  see the "Current handoff" entry above for the full reproduction (a throwaway no-op `*IT` class
  alone reproduces the identical 8 failures; excluding just those two classes gives a clean sweep):
  `./mvnw -q test -Dtest='*IT,!AnalyticsControllerSecurityIT,!ForecastControllerSecurityIT'` —
  exit 0.
- `./mvnw -q -Dtest=ArchitectureTest test` (after an independent clean `test-compile`) — 8/8 pass,
  `git status --porcelain` on `archunit_store/` shows no diff — **zero frozen-store regeneration
  needed**, matching P-5's prediction that repository/facade-only changes add no new cross-module
  edge.
- `packages/contracts/openapi.json`/`packages/api-client/src/schema.d.ts` — not touched this pass
  (no endpoint changed; T-6c-1/T-6c-2/T-6c-3 are repository/facade/migration-only, T-6c-11/T-6c-12
  are the first tasks that add routes). `OpenApiContractExportTest` not re-run for this reason —
  nothing for it to prove yet.

### Review-driven fix: T-6c-0 baseline P2 findings (2026-09-11)

Independent review of T-6c-0 returned three P2s (fixture isolation/exact-count assertion, two
measurements over empty data, database-egress dimensions missing) — see the corrected task entry
above for the full description and the fixed `InventoryEgressBaselineIT`. No production-code
finding in the scoped repository/facade methods (T-6c-1/T-6c-2) or the `V62` migration (T-6c-3).

- Verified independently, in order:
  - `./mvnw -q -Dtest=InventoryEgressBaselineIT test-compile` — clean.
  - `./mvnw -q -Dtest=InventoryEgressBaselineIT test` — 3/3 pass. Corrected numbers logged at INFO
    and copied above, superseding the original three.
  - `./mvnw -q -Dtest=InventoryQueriesSiteScopedTest,InventoryOperationsTest,
    LocationInventorySiteScopedQueriesIT,StockMovementSiteIndexMigrationIT,
    InventoryEgressBaselineIT,ArchitectureTest test` — 39/39 pass (matching the reviewer's own
    re-run count exactly).
  - `git diff --check` — clean; `git status --porcelain` on `archunit_store/` — no diff.
  - `./mvnw -q clean test-compile` — clean.
  - `./mvnw -q clean test` (full unrestricted suite, plain `test`) — all 60 surefire reports show
    `Failures: 0, Errors: 0` (558 tests summed across every report).
  - `./mvnw -q test -Dtest='*IT'` — Maven exits 1 (`MojoFailureException`), but every `*IT.txt`
    surefire report shows `Failures: 0, Errors: 0` across all 558 tests; the only anomaly is
    `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` reporting `Tests run: 0` each —
    the exact pre-existing "some `@Nested`-only classes report zero tests under this invocation
    pattern" quirk already recorded at 6a's T-6 section and re-confirmed (not newly discovered) by
    this session, unrelated to this fix. Not a regression from the `InventoryEgressBaselineIT`
    changes: neither class was touched, and the same two classes/same symptom were already flagged
    before this fix existed.
- Result: pass. All three P2s fixed same session; no files staged/committed (per this record's
  "no production apply, implementation/local verification only" rule — the tree is left for
  review).

### Review-driven fix round 2: T-6c-0 baseline P2/P3 findings (2026-09-12)

Independent review of the round-1 fix returned two more P2s (by-product DB egress undercounted;
totals filtering measured a different workload than the real production query) and one P3 (leaked
JDBC connection/unfreed `Array`) — see the corrected task entry above for the full description and
the fixed `InventoryEgressBaselineIT`. The empty-response P2 from round 1 was confirmed fixed. No
production-code finding.

- Verified independently, in order:
  - `./mvnw -q -Dtest=InventoryEgressBaselineIT test-compile` — clean.
  - `./mvnw -q clean test-compile` — clean (a stale-classpath `NoClassDefFoundError:
    InventoryQueries` on the first single-class run turned out to be a partial-compile artifact,
    not a real error — a full `clean test-compile` resolved it and the class runs fine).
  - `./mvnw -q -Dtest=InventoryEgressBaselineIT test` — 3/3 pass. Corrected numbers logged at INFO
    and copied above, superseding round 1's numbers.
  - `./mvnw -q -Dtest=InventoryQueriesSiteScopedTest,InventoryOperationsTest,
    LocationInventorySiteScopedQueriesIT,StockMovementSiteIndexMigrationIT,
    InventoryEgressBaselineIT,ArchitectureTest test` — 39/39 pass.
  - `git diff --check` — clean; `git status --porcelain` on `archunit_store/` — no diff.
  - `./mvnw -q clean test` (full unrestricted suite, plain `test`) — all 61 surefire reports show
    `Failures: 0, Errors: 0` (341 tests summed across every report).
  - `./mvnw -q test -Dtest='*IT'` — exit 0 this run (unlike round 1's `MojoFailureException`); every
    `*IT.txt` surefire report shows `Failures: 0, Errors: 0` across all 558 tests.
    `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` still report `Tests run: 0` each
    — the same pre-existing quirk, evidently order/timing-sensitive as to whether it trips Maven's
    `MojoFailureException` on a given run, but never a real test failure in either run. Confirms
    round 1's read: not a regression from this fix, still open debt outside `inventory`'s boundary.
- Result: pass. Both P2s and the P3 fixed same session; no files staged/committed.

### Open items carried forward from this pass

- The `*IT` suite order-fragility above (`AnalyticsControllerSecurityIT`/
  `ForecastControllerSecurityIT` vs. H2-incompatible native SQL in `AnalyticsService`/
  `ForecastService`) is new-found debt, recorded but not fixed — out of `inventory`'s module
  boundary and out of 6c's scope. Any future session adding `*IT` classes to this module should
  expect to see it and know it is not their regression.
- T-6c-4 through T-6c-17 are unstarted. T-6c-4 (same-site transfer preconditions) and T-6c-5
  (slim site-scoped totals projection, now unblocked by T-6c-0) are both valid next tasks per the
  task list's stated ordering; T-6c-6 (row locking) is a prerequisite for the site-scoped
  *write* overloads on `InventoryOperations` this pass deliberately deferred.

## 6c implementation (T-6c-4..T-6c-5) (2026-09-12)

Implements T-6c-4 and T-6c-5 from the task list above. Stopped at this clean boundary per the
"stop when the remaining tasks are large" ground rule; T-6c-6 (row locking) is the next task per
P-5's ordering but was not started this pass, deliberately, since it is a real design task
(`FOR UPDATE`/outer-join hazard, id-ordered lock query) rather than a small follow-on to either
task done here.

- **T-6c-4 — Same-site preconditions on existing multi-location writes.**
  `StockMovementService.executeTransfer` (shared by `transferInventory` and
  `batchTransferInventory`) now calls a new `requireSameSite(Site, Site)` helper immediately after
  resolving the destination — for the `destinationInventoryId` branch, right after the
  `findById`; for the `destinationLocationId` branch, right after resolving `destLocation` and
  *before* the find-or-create call that would otherwise persist a new, orphaned destination
  `LocationInventory` row for a transfer that is about to be rejected. Throws the existing
  `InvalidInventoryOperationException` (already mapped to 400 by `GlobalExceptionHandler`, so no
  new exception type or handler wiring was needed) with a message naming both site codes. Every
  line of a batch transfer is checked independently (each call to `executeTransfer` inside
  `batchTransferInventory`'s loop resolves and checks its own source/destination pair), and the
  whole method is `@Transactional`, so a rejection on any line rolls back every line already
  applied earlier in the same batch — no new transactional wiring needed, this was already true of
  the existing `@Transactional` boundary.
  - `MachineDisplayService.batchSwapDisplay`'s machine-to-machine mode (Mode 2, `targetMachineId`/
    `targetLocationType`) gains the same precondition, checked at the very top of the method —
    before Mode 1's display removals/additions, before any `MachineDisplay`/`AuditLog`/
    `StockMovement` write, and before any notification is enqueued — via a new
    `requireSameSite(UUID, UUID)` helper that resolves both machines' sites through the existing
    `resolveMachineSite` and compares site ids. This was flagged as advisory debt by 6b's review
    (log.md's 6b "Review-driven fix" section, "a same-site precondition on `MachineDisplayService`'s
    cross-site swap path") and is now due.
  - **Existing test needed a compatible fixture change, not just new tests.**
    `MachineDisplayServiceNotificationTest`'s `setUp()` deliberately wires `loc` (MAIN) and
    `targetLoc` (SECOND) to distinct sites (6b's review fix, to prove `resolveMachineSite` picks
    each movement's own destination site rather than always the source's). That fixture was reused
    by `batchSwapDisplay_machineToMachine_emitsTwoMachineSnapshots` to exercise a *successful*
    cross-site swap — which T-6c-4 makes impossible. Fixed by adding a third, MAIN-site machine
    (`sameSiteTargetMachineId`/`sameSiteTargetLoc`) for that test's "successful swap" scenario
    (updated site assertions from SECOND/MAIN to MAIN/MAIN, with a comment recording that the
    distinct-site regression this test used to catch can no longer manifest once cross-site swaps
    are rejected), and adding a new test,
    `batchSwapDisplay_crossSiteTargetMachine_rejectsBeforeAnyMutation`, that reuses the original
    MAIN/SECOND `targetLoc` fixture to prove the rejection and assert zero mutation (`saveAll`/
    `save`/`createAuditLog`/`saveMovements`/`createNotification` all `never()` called).
  - Tests: new `StockMovementServiceSameSiteTransferTest` (Mockito unit test, 4 cases) —
    `transferInventory` rejects a cross-site pair for both destination shapes (existing
    `destinationInventoryId`, and a new `destinationLocationId` with no existing row, asserting no
    orphan row is created via `verify(locationInventoryRepository, never())
    .findByLocation_IdAndProduct_Id(...)`), a same-site `transferInventory` still succeeds, and
    `batchTransferInventory` rejects the whole batch when any one line is cross-site. Extended
    `MachineDisplayServiceNotificationTest` as described above (7 cases total, was 6).
- **T-6c-5 — Slim, site-scoped, batched totals projection.** Added to `InventoryTotalsRepository`:
  `findAllInventoryTotalsBySite(UUID siteId)` (full-catalog mode) and
  `findInventoryTotalsBySiteAndProductIds(UUID siteId, Collection<UUID> productIds)` (batched
  mode), both returning the new slim `inventory.api.SiteInventoryTotalDTO`
  (`productId`/`totalQuantity`/`lastUpdatedAt` — no catalog fields, per F-6c-9). Both are JPQL via
  `EntityManager.createQuery`, not native SQL — deliberately different from
  `INVENTORY_TOTALS_SQL`/`STOCK_TOTALS_SQL` above them, which are untouched and stay the AC-8
  baseline's measured path. JPQL was chosen specifically to reuse Hibernate's own UUID/collection
  parameter binding (`setParameter("productIds", collection)` for the `IN` clause) rather than
  hand-rolling native-SQL array binding, and it sidesteps T-6c-0's H2-UUID-cast problem entirely
  (JPQL, unlike a native query casting a raw column to `UUID`, lets Hibernate map the type) — the
  new IT below runs on the H2 `test` profile, not Testcontainers.
  - **Two deliberately different, explicitly documented zero-stock contracts** (F-6c-3's hazard,
    resolved as two separate, tested choices rather than one blanket rule):
    - Full-catalog mode: `FROM Product p LEFT JOIN LocationInventory li ON li.product = p AND
      li.site.id = :siteId GROUP BY p.id` — the site predicate lives in the `ON` clause, not a
      `WHERE` filter, so every product in the catalog gets exactly one row, `totalQuantity = 0`
      and `lastUpdatedAt = null` when it has no inventory at that site. Mirrors the legacy
      `INVENTORY_TOTALS_SQL`'s row-per-product guarantee, scoped by site.
    - Batched mode: `FROM LocationInventory li WHERE li.product.id IN :productIds AND li.site.id =
      :siteId GROUP BY li.product.id` — a requested id with no matching row is simply absent from
      the result. This reuses the contract T-6c-1 already established (undocumented as a
      deliberate "choice" at the time, but already the de facto behavior) for
      `LocationInventoryRepository.sumQuantitiesByProductIdsAndSiteId`'s Javadoc ("Missing products
      … mean total = 0 — callers must treat absence as zero, not as 'not found'"). Chosen over
      duplicating the full-catalog mode's LEFT JOIN shape because the batched mode's caller already
      knows every id is a real product (a targeted refresh of known ids, AC-7), not discovering the
      catalog, so the row-per-id guarantee has no value there and would only add a second `WHERE`
      variant that returns a different row for zero-stock than one-line-away callers might expect
      from the other mode — kept them visibly different in both code and doc instead.
  - **Documented, enforced batch ceiling (AC-7's "real ceiling").**
    `InventoryTotalsRepository.MAX_PRODUCT_IDS_BATCH_SIZE = 500` (a public constant, referenced by
    both the repository's own guard and `InventoryQueries`'s Javadoc). A batch over the limit
    throws `InvalidInventoryOperationException` (→ 400, no new handler wiring, same reasoning as
    T-6c-4) before the query runs. 500 was chosen as generously above any realistic coalesced-
    refresh batch (6e's targeted-refresh work is what will actually call this in bulk) while still
    meaningfully rejecting a full-catalog-sized id list sent through the wrong (batched) mode
    instead of the full-catalog method. A null or empty `productIds` returns an empty list rather
    than throwing or falling back to the full-catalog query — callers that want "all products" call
    the other method explicitly; this method never silently does a full-catalog read.
  - `InventoryQueries` gained two thin pass-through overloads,
    `findInventoryTotalsBySite(UUID siteId)` and `findInventoryTotalsBySite(UUID siteId,
    Collection<UUID> productIds)`, matching T-6c-2's facade-overload convention. No controller
    calls either yet (T-6c-11 is that task); `INVENTORY_TOTALS_SQL`'s existing
    `findAllInventoryTotals()` facade method is untouched.
  - Tests:
    - New `InventoryTotalsRepositorySiteScopedIT` (`@SpringBootTest`, H2 `test` profile — 6
      cases), covering exactly the task's four required cases plus two extra boundary cases: (a)
      a zero-stock product still appears with quantity 0 in the full-catalog mode; (b) the same
      product at MAIN and SECOND resolves to independent quantities; (c) the batched form returns
      exactly the requested ids (and separately confirms an id with no site inventory is *absent*,
      documenting the batched-mode contract explicitly rather than leaving it implicit); (d) a
      batch of `MAX_PRODUCT_IDS_BATCH_SIZE + 1` random ids is rejected with
      `InvalidInventoryOperationException` naming the limit in its message; plus a batch of
      exactly `MAX_PRODUCT_IDS_BATCH_SIZE` ids is accepted (boundary-inclusive), and
      null/empty `productIds` both return an empty list without throwing.
    - Extended `InventoryQueriesSiteScopedTest` with 2 cases proving both new facade methods pass
      the site id (and, for the batched overload, the id collection) through to the repository
      unchanged.

### Verification (T-6c-4..T-6c-5, actual commands and results, not paraphrased)

- `./mvnw -q clean test-compile` — clean, zero errors.
- New/changed classes run individually, all green:
  - `./mvnw -q -Dtest='StockMovementServiceSameSiteTransferTest,MachineDisplayServiceNotificationTest,
    StockMovementServiceActiveStatusDerivationTest' test` — 4/4, 7/7 (was 6, +1 new rejection
    test), 8/8 pass respectively.
  - `./mvnw -q -Dtest='InventoryQueriesSiteScopedTest,InventoryTotalsRepositorySiteScopedIT,
    StockMovementServiceSameSiteTransferTest,MachineDisplayServiceNotificationTest' test` — 9/9
    (was 7, +2 new pass-through tests), 6/6, 4/4, 7/7 pass.
- `./mvnw -q clean test` (full unrestricted suite, plain `test` — skips `*IT.java` by design) —
  exit 0; summed across every surefire report: **348 tests, 0 failures, 0 errors**.
- `./mvnw -q test -Dtest='*IT'` — Maven exits with `MojoFailureException`, **8 failures**, all in
  `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` (409 tests total, 8 failures, 0
  errors) — the exact pre-existing, order-fragile, out-of-`inventory`'s-boundary debt already
  recorded in this log's "Current handoff" and T-6c-0's verification section, reproduced again
  here without any new inventory-related failure. Confirmed not a regression from this pass:
  `./mvnw -q test -Dtest='*IT,!AnalyticsControllerSecurityIT,!ForecastControllerSecurityIT'` —
  exit 0.
- `./mvnw -q clean test-compile` then `./mvnw -q -Dtest=ArchitectureTest test` — 8/8 pass (still 8
  rules, unchanged from T-6c-0..T-6c-3), `git status --porcelain` on `archunit_store/` — no diff.
  Matches the expectation that repository/facade-only changes referencing entities `inventory`
  already depends on (`catalog.domain.Product`, already imported by `StockMovementService`) add no
  new cross-module edge.
- `packages/contracts/openapi.json`/`packages/api-client/src/schema.d.ts` — not touched this pass
  (no endpoint changed; T-6c-4 is service-layer-only, T-6c-5 is repository/facade-only).
  `OpenApiContractExportTest` not re-run for this reason — nothing for it to prove yet. T-6c-11/
  T-6c-12 are the first tasks that add routes and will trigger regeneration.

### Review-driven fix: T-6c-4 findings (2026-09-12)

Independent review of T-6c-4 returned one P1 and one P2. No finding in T-6c-5.

- **P1 — the swap guard could be bypassed with a foreign display id.**
  `MachineDisplayService.batchSwapDisplay`'s `requireSameSite` check only compares the two
  *requested machine ids* — it says nothing about which machine a *display id* the caller supplies
  in `displayIdsFromTarget`/`displayIdsToTarget` actually belongs to, because
  `findAllByIdInWithProduct` looks displays up globally by id with no site or machine filter.
  Naming two same-site (e.g. both MAIN) machines while supplying a foreign-site display's id passed
  the guard, then ended that foreign display and recreated it at the (wrong) requested machine —
  the guard checked the right thing about the wrong pair of ids. Fixed: after the existing
  "already ended" check in both loops (around `fromDisplays`/`toDisplays`), added a check that each
  display's actual `machineId`/`locationType` matches the machine the caller claims it's moving
  from — `displayIdsFromTarget` entries must belong to `targetMachineId`/`targetLocationType`,
  `displayIdsToTarget` entries must belong to `machineId`/`locationType` — throwing
  `IllegalArgumentException("Display does not belong to target/source machine: " + id)` before any
  mutation, matching this method's existing validation style ("Display not found"/"Display is
  already ended"). This is a general ownership-mismatch fix, not a site-specific patch — it closes
  the bypass regardless of whether the foreign display happens to be same-site or cross-site,
  which is what actually makes `requireSameSite`'s machine-level check meaningful again (the
  displays being moved are now guaranteed to really belong to the named machines).
- **P2 — the required machine-swap IT was missing.** T-6c-4's own task list explicitly calls for
  "one IT covering the machine swap path" (log.md's 6c planning section), but only
  `MachineDisplayServiceNotificationTest` (Mockito, no persistence) existed. Added
  `MachineDisplayServiceCrossSiteSwapIT` (`@SpringBootTest(webEnvironment = NONE)
  @ActiveProfiles("test")`, real H2-backed beans — no native SQL is involved in this path, matching
  `InventoryOperationsSiteIT`'s reasoning for not needing Testcontainers Postgres): seeds a real
  MAIN/MAIN/SECOND three-machine fixture and a display persisted at the SECOND machine, attempts
  the exact bypass (`machineId`/`targetMachineId` both MAIN, `displayIdsFromTarget` naming the
  SECOND-site display), asserts the call throws the new `IllegalArgumentException`, and asserts
  *nothing changed*: the display is reloaded from the repository with `endedAt` still null and its
  original `machineId`; no display was created at either MAIN machine
  (`findActiveByLocationTypeAndMachineId`); and `StockMovementRepository`/`AuditLogRepository`/
  `NotificationRepository` counts are all unchanged before/after — closing the "unchanged displays,
  movements, audit records, and notifications" proof the finding asked for. 1/1 pass.
- Verified independently, in order:
  - `./mvnw -q clean test-compile` — clean.
  - `./mvnw -q -Dtest=MachineDisplayServiceCrossSiteSwapIT test` — 1/1 pass.
  - `./mvnw -q -Dtest=StockMovementServiceSameSiteTransferTest,MachineDisplayServiceNotificationTest,
    MachineDisplayServiceCrossSiteSwapIT,MachineDisplayServiceBatchQueryGuardTest,
    InventoryTotalsRepositorySiteScopedIT,InventoryQueriesSiteScopedTest,ArchitectureTest test` —
    39/39 pass (4, 7, 1, 4, 6, 9, 8).
  - `git diff --check` — clean; `git status --porcelain` on `archunit_store/` — no diff.
  - `./mvnw -q clean test-compile` then `./mvnw -q clean test` (full unrestricted suite, plain
    `test`) — all 61 surefire reports show `Failures: 0, Errors: 0` (348 tests summed).
  - `./mvnw -q test -Dtest='*IT'` — exit 0, all 572 tests across every `*IT.txt` report show
    `Failures: 0, Errors: 0`. `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` still
    report `Tests run: 0` each — the same pre-existing, order/timing-sensitive quirk already
    recorded (sometimes trips `MojoFailureException`, sometimes doesn't; never a real failure in
    any run so far), unrelated to this fix.
- Result: pass. Both findings fixed same session; no files staged/committed.

### Review-driven fix round 2: T-6c-4 P1 recurrence (2026-09-12)

Independent review of the round-1 fix reproduced the identical bug class in a path round 1 didn't
touch: `displayIdsToRemove` (`batchSwapDisplay`'s Mode 1) still loaded removal displays globally
via `findAllByIdInWithProduct` with no check against `request.getMachineId()`/`locationType` —
naming only MAIN machines while supplying a foreign-site display id in `displayIdsToRemove` ended
and attributed the change to MAIN. Unlike the round-1 paths, `displayIdsToRemove` has no
`requireSameSite` guard to even partially rely on, since removal-only requests never populate
`targetMachineId` — the per-display ownership check is the *only* guard for this branch. Fixed the
same way as round 1 (ownership check before mutation, `IllegalArgumentException` before any
write).

Given the same bug had now appeared in two independent places sharing one root cause (a display id
looked up globally, then mutated on the strength of a caller-claimed machine, with no check that
the two agree), swept every other `machineDisplayRepository.findById*`/`findAllByIdIn*` call site
in the class before it could recur a third time via review. Found and fixed two more instances
proactively, same session, same fix shape:
- `renewDisplays` — `existing` (found by id via `findByIdWithProduct`) was ended and a replacement
  created at `request.getMachineId()` with no check that `existing` actually lived there.
- `swapDisplay` — `outgoing` (found by id via `findByIdWithProduct`) was ended with no check that
  it actually lived at `request.getMachineId()`/`locationType` before a new display was created
  there.

Checked `batchClearDisplays` and confirmed it does NOT have this bug: it derives the expected
machine from the displays themselves (`first.getMachineId()`/`first.getLocationType()`, checked
against every other display in the batch) rather than accepting a separately claimed machine id
from the caller — there is no caller-supplied "expected machine" for a mismatch to slip past, so no
fix was needed there.

- Extended `MachineDisplayServiceCrossSiteSwapIT` (renamed test methods from the original
  single-test file to one-per-bypass-path) to 5 cases: the two round-1 paths
  (`displayIdsFromTarget`/`displayIdsToTarget`, both now asserted explicitly rather than only one),
  plus new cases for `displayIdsToRemove`, `renewDisplays`, and `swapDisplay` — each seeding a
  foreign-site display, attempting the exact bypass, and asserting rejection plus zero side effects
  (display unchanged, no display created at the target machine, stock movement/audit log/
  notification counts unchanged). Refactored the fixture into a shared `Fixture` record/
  `newFixture()`/`saveDisplay()` helpers to support five cases without duplication.
- Verified independently, in order:
  - `./mvnw -q clean test-compile` — clean.
  - `./mvnw -q -Dtest=MachineDisplayServiceCrossSiteSwapIT test` — 5/5 pass (was 1).
  - `./mvnw -q -Dtest=StockMovementServiceSameSiteTransferTest,MachineDisplayServiceNotificationTest,
    MachineDisplayServiceCrossSiteSwapIT,MachineDisplayServiceBatchQueryGuardTest,
    InventoryTotalsRepositorySiteScopedIT,InventoryQueriesSiteScopedTest,ArchitectureTest test` —
    43/43 pass (4, 7, 5, 4, 6, 9, 8).
  - `git diff --check` — clean; `git status --porcelain` on `archunit_store/` — no diff.
  - `./mvnw -q clean test-compile` then `./mvnw -q clean test` (full unrestricted suite, plain
    `test`) — all 61 surefire reports show `Failures: 0, Errors: 0` (348 tests summed; unchanged
    from round 1 since `MachineDisplayServiceCrossSiteSwapIT` is an `*IT` class, excluded from
    plain `test` by design).
  - `./mvnw -q test -Dtest='*IT'` — exit 0, all 576 tests across every `*IT.txt` report show
    `Failures: 0, Errors: 0` (up from 572, the four new `MachineDisplayServiceCrossSiteSwapIT`
    cases). `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` still report `Tests run:
    0` each — the same pre-existing quirk, still unrelated.
- Result: pass. The reproduced finding and both proactively-found instances fixed same session; no
  files staged/committed.

## 6c implementation (T-6c-6) (2026-09-12)

Implements T-6c-6 from the task list above: row locking on the read-modify-write paths, per
F-6c-5's recommendation. No routes changed; repository/service-layer only, so no contract
regeneration.

- **Repository (`LocationInventoryRepository`).** Added three new methods, all scoped to "row
  locking" and documented as a group:
  - `lockAllByIdForUpdate(Collection<UUID> ids)` — `@Lock(LockModeType.PESSIMISTIC_WRITE)` on
    `SELECT li FROM LocationInventory li WHERE li.id IN :ids ORDER BY li.id`. Deliberately a plain
    query with no `JOIN FETCH` at all (not even `findByLocation_IdAndProduct_Id`'s inner joins) —
    the simplest query that cannot ever hit F-6c-5's `FOR UPDATE`-on-outer-join hazard, and the
    smallest possible SQL surface for a query whose only job is acquiring locks in a specific
    order. The `ORDER BY li.id` (combined with callers always sorting their input ids ascending
    first) is what prevents deadlock: two overlapping writers that need the same set of rows will
    always request their locks in the same relative order, so neither can hold what the other
    waits for.
  - `findProductIdById(UUID id)` / `findIdByLocation_IdAndProduct_Id(UUID locationId, UUID
    productId)` — scalar-only projections (`Optional<UUID>`, not `Optional<LocationInventory>`)
    used only to plan which ids a transfer needs to lock, before loading anything. Deliberately
    scalar: an entity-returning query would populate the persistence context, and Hibernate does
    not overwrite an already-managed entity's scalar state (e.g. `quantity`) from a later query —
    so if the *planning* step accidentally loaded a full entity here, and the *lock* query later
    tried to lock the same id, the entity's quantity would still be whatever this early, unlocked
    read saw, not the fresh value the lock is supposed to guarantee. Reading `product_id` this way
    without a lock is safe because it is immutable once a `location_inventory` row exists.
- **`StockMovementService`.** Added two private helpers and wired them into the three
  read-modify-write entry points:
  - `lockInventoryRowsForUpdate(Collection<UUID> ids)` — dedupes, sorts ascending, and calls
    `lockAllByIdForUpdate` (no-op on an empty set). This is the one and only place a lock is
    acquired; every caller below goes through it.
  - `resolveTransferLockIds(TransferInventoryRequestDTO)` — resolves the id set a transfer needs
    locked, using only the scalar lookups above, before any entity is loaded: always the source
    id; the destination's *existing* row id too, if one already exists (an explicit
    `destinationInventoryId` is used directly; an implicit `destinationLocationId` is resolved via
    `findProductIdById` then `findIdByLocation_IdAndProduct_Id`). A destination that does not yet
    exist has no row to lock — it is created fresh afterward exactly as before, and Javadoc records
    the one accepted residual gap this leaves (see the "Current handoff" entry above).
  - `batchAdjustInventory`: locks every line's `inventoryId` before the existing
    `preloadInventories` (join-fetch) call. Since these ids were not read anywhere earlier in the
    method, the lock query is genuinely the first touch, so the subsequent join-fetch query
    populates the same already-locked, already-fresh managed entities with their associations
    (Hibernate identity-map behavior — no double SELECT of scalar state, no staleness).
  - `transferInventory`: calls `lockInventoryRowsForUpdate(resolveTransferLockIds(request))`
    immediately, before its existing (unchanged) `findById(sourceInventoryId)` call — which is now
    genuinely the first load of that row in the transaction, same reasoning as above.
  - `batchTransferInventory`: computes `resolveTransferLockIds` for *every* line in the batch
    first, unions and locks them all in one call, then proceeds with the existing
    `preloadInventories` call for sources unchanged. Locking the whole batch's id set up front
    (not per-line, inside the loop) is what keeps a same-process batch internally deadlock-free
    too, not just deadlock-free against other batches/transfers.
  - `executeTransfer` itself required no changes beyond what T-6c-4 already added
    (`requireSameSite`): its existing `findById(destinationInventoryId)` /
    `findByLocation_IdAndProduct_Id(...).orElseGet(create)` calls now simply hit the persistence
    context's identity map for ids the caller already locked (a cache hit, not a new unlocked
    read), or proceed to create a genuinely new row when `resolveTransferLockIds` correctly found
    none to lock.
- **No `@Version` column added to `LocationInventory`** — per F-6c-5's explicit recommendation
  against option (b): a `PESSIMISTIC_WRITE` lock query needs no schema change and no client-facing
  retry/409 contract on the v1 endpoints (which don't exist yet — T-6c-11/T-6c-12).
- **Test — `StockMovementServiceConcurrentAdjustIT`** (new,
  `inventory/application/StockMovementServiceConcurrentAdjustIT.java`, extends
  `BaseKafkaIntegrationTest` for real Testcontainers Postgres, not H2 — F-6c-5 and the task text
  both call for real row-locking semantics). Seeds one `LocationInventory` row at quantity 100,
  then reuses `SiteProductConcurrencyIT`'s proven technique: a third, independently managed
  transaction takes `SELECT id FROM location_inventory WHERE id = ? FOR UPDATE` and holds it via a
  `CountDownLatch`, forcing both racing `batchAdjustInventory` calls (deltas -30 and -20 against
  the same row) to actually block at the same instant — confirmed via
  `pg_stat_activity`-polling for 2 backends in `wait_event_type = 'Lock'` before releasing. This is
  what makes the test prove genuine concurrent, overlapping transactions rather than two
  sequential calls that happen to look concurrent because of JVM thread scheduling. Assertions:
  final quantity equals the fully serialized result (50, not a lost update); exactly two
  `StockMovement` rows exist for the product; neither movement's `previousQuantity` nor
  `currentQuantity` is ever negative; and the two movements form one unbroken chain from 100 down
  to 50 in whichever order Postgres actually serialized them (the second movement's
  `previousQuantity` equals the first's `currentQuantity`), proving the second writer really did
  see the first writer's committed result rather than a stale pre-lock value.
- **Confirmed the test actually catches the regression it targets (not just "asserted, hoped it
  works").** Temporarily commented out the new `lockInventoryRowsForUpdate(requestedInventoryIds)`
  call in `batchAdjustInventory` (nothing else changed) and reran the same test:
  `./mvnw -q -Dtest=StockMovementServiceConcurrentAdjustIT test` failed with
  `AssertionFailedError: ... expected: 50 but was: 80` — i.e. only the `-20` delta survived; the
  `-30` write was silently lost, exactly the classic lost-update F-6c-5 predicted (both threads'
  unlocked reads saw quantity 100 under MVCC even while the external holder's `FOR UPDATE` lock
  was held, since plain reads never block on a writer's lock in Postgres; the actual `UPDATE`
  statements were what blocked on the held lock, and whichever `UPDATE` committed last won,
  discarding the other transaction's write). Restored the lock call and reran: 1/1 pass again. The
  `.bak` copy used for this A/B was not left in the tree.

### Verification (T-6c-6, actual commands and results, not paraphrased)

- `./mvnw -q clean test-compile` — clean, zero errors.
- `./mvnw -q -Dtest=StockMovementServiceConcurrentAdjustIT test` — 1/1 pass (real Testcontainers
  Postgres; ~9-10s including container reuse).
- Regression A/B (see above): with the lock call disabled, the same test failed
  (`expected: 50 but was: 80`); with it restored, 1/1 pass again.
- `./mvnw -q -Dtest='StockMovementOutboxAtomicityIT,InventoryOperationsCallerTransactionIT,
  StockMovementServiceSameSiteTransferTest,StockMovementServiceActiveStatusDerivationTest,
  StockMovementControllerSecurityIT' test` — 2/2, 2/2, 4/4, 8/8 pass; `StockMovementControllerSecurityIT`
  reports `Tests run: 0` in isolation (uses only `@Nested` classes — Maven Surefire's `-Dtest=`
  filter does not run nested classes for a bare class-name selector; this is a Surefire selection
  quirk, not a test failure — confirmed by running the full `*IT` sweep below, where its nested
  tests execute and pass normally).
- `./mvnw -q clean test` (full unrestricted suite, plain `test` — skips `*IT.java` by design) —
  exit 0; 348 tests summed across every surefire report, 0 failures, 0 errors (unchanged from
  T-6c-4..T-6c-5, since the new test class is itself an `*IT`).
- `./mvnw -q test -Dtest='*IT'` — `MojoFailureException`, 415 tests (up from 414, the one new
  `StockMovementServiceConcurrentAdjustIT` case), 8 failures, all still exactly
  `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` — the same pre-existing,
  order-fragile, out-of-`inventory`'s-boundary debt already recorded in this log, reproduced again
  here without any new inventory-related failure. Confirmed not a regression:
  `./mvnw -q test -Dtest='*IT,!AnalyticsControllerSecurityIT,!ForecastControllerSecurityIT'` —
  exit 0.
- `./mvnw -q clean test-compile` then `./mvnw -q -Dtest=ArchitectureTest test` — 8/8 pass (still 8
  rules, unchanged), `git status --porcelain` on `archunit_store/` — no diff. Expected: the new
  repository methods and service-layer helpers reference only types `inventory` already depends
  on internally (its own domain/infrastructure classes), adding no new cross-module edge.
- `packages/contracts/openapi.json`/`packages/api-client/src/schema.d.ts` — not touched this pass
  (no endpoint changed; T-6c-6 is repository/service-layer only). `OpenApiContractExportTest` not
  re-run for this reason.

## Review-driven fix: T-6c-6 P1 findings (2026-09-12)

Independent review of T-6c-6 found one P1, confirmed reproducible: `resolveTransferLockIds` only
planned a lock for a transfer's destination when a `location_inventory` row already existed at
that `(location, product)` pair **at planning time** (a scalar `findIdByLocation_IdAndProduct_Id`
check, no lock). A not-yet-existing destination had nothing planned to lock, and
`executeTransfer`'s own later, separate `findByLocation_IdAndProduct_Id(...).orElseGet(create)`
call ran a completely fresh, unlocked query — so if another transaction created a row at that same
key between planning and this later query, `executeTransfer` could find (or race to create) it and
mutate its quantity with no lock ever held. The stale doc comment on `resolveTransferLockIds`
claimed this was "protected by the unique constraint," which only covers the simultaneous-*insert*
case, not a later *unlocked read-then-update* of a row that already exists.

### Fix

Re-derived `resolveTransferLockIds`'s contract per the reviewer's own framing: every id it returns
must now already be real and about to be locked — there is no more "nothing to lock yet" case.

- **`LocationInventoryRepository.insertLocationInventoryIfAbsent(id, locationId, siteId,
  productId)`** (new): a native `INSERT ... ON CONFLICT (location_id, product_id) DO NOTHING`.
  Chosen over both alternatives weighed in the task: a plain JPA save-if-absent can't be made safe
  without a savepoint (Postgres aborts the whole transaction on the first unique-constraint error,
  and this codebase takes no savepoints), and a `REQUIRES_NEW` helper transaction would let a
  destination-row creation escape rollback if the enclosing transfer later failed — a real,
  avoidable behavior change. `INSERT ... ON CONFLICT DO NOTHING` sidesteps both problems: it never
  raises on a duplicate key (Postgres's speculative-insertion protocol makes a second, concurrent
  inserter of the same key *wait* for the first inserter's transaction to finish, then either see
  the committed row or, if the first inserter rolled back, insert for real — no error, no
  deadlock), and because it runs inside the same `@Transactional` as the rest of the transfer, a
  later failure (e.g. `InsufficientInventoryException`) rolls the insert back too, so the "a
  transfer that fails leaves no trace" property this module already had is fully preserved — no
  `Q-6c-N` decision needed on that point.
  - `id`, `site_id`, `created_at`, `updated_at` are all supplied explicitly by the caller rather
    than left to production's DB defaults/triggers (`trg_sync_inventory_site_id`,
    `id`/`created_at`/`updated_at` column defaults — infra/init-db/20-unified-locations.sql).
    Discovered why this matters the hard way: this module's own Testcontainers-backed integration
    tests run against a schema Hibernate generates from the entity mapping
    (`spring.jpa.hibernate.ddl-auto=create-drop`, `src/test/resources/application-integration.
    properties`), which has none of those triggers or defaults. The first version of this method
    (relying on the trigger/defaults) failed in-test with `null value in column "id" ... violates
    not-null constraint` — supplying every value explicitly makes the method correct in both
    environments instead of silently depending on production-only schema objects this module
    cannot verify from Java. `LocationRepository.findSiteIdById(locationId)` (new, scalar,
    unlocked — a location's site is immutable once set) resolves the site id needed for this.
- **`StockMovementService.resolveTransferLockIds`**: for an implicit destination, now calls a new
  `ensureDestinationInventoryExists(destLocationId, productId)` helper (guarantees the row exists
  via the insert above, then re-reads its id with the existing scalar
  `findIdByLocation_IdAndProduct_Id`) instead of only locking an already-existing row. Unchanged:
  if the source id doesn't exist, no destination row is created and only the source id is
  returned — the existing `findById(sourceInventoryId)` call downstream still raises
  `InventoryNotFoundException` with its established message, exactly as before this fix.
- **`StockMovementService.executeTransfer`**'s implicit-destination branch: the old
  `findByLocation_IdAndProduct_Id(...).orElseGet(create)` fallback is now
  `.orElseThrow(IllegalStateException)`. By the time `executeTransfer` runs, `resolveTransferLockIds`
  has already guaranteed the row exists and `lockInventoryRowsForUpdate` has already locked it, so
  this lookup can only ever hit the persistence context's identity map for the already-locked row
  (a cache hit, not a fresh unlocked read) — a miss here would mean the locking invariant was
  violated upstream, which is exactly the class of bug this fix exists to make loud instead of
  silent.

### Requirement 1 (mutated row is always the locked row)

Whatever destination row `executeTransfer` mutates is now structurally guaranteed to be one this
transaction holds `PESSIMISTIC_WRITE` on: `resolveTransferLockIds` creates-or-finds the row and
returns its real id *before* `lockInventoryRowsForUpdate` runs, so the id is in the locked set by
construction, and the unique `(location_id, product_id)` index guarantees at most one row can ever
exist at that key — the row `executeTransfer`'s natural-key lookup finds is necessarily the one
just locked.

### Requirement 2 (lock ordering / deadlock safety preserved)

`lockInventoryRowsForUpdate`'s existing sort-then-lock-once discipline is untouched — this fix only
changes what set of ids gets fed into it, not how that set is locked. The new
`ensureDestinationInventoryExists` step itself runs strictly *before* any lock is taken in the same
transaction (same position `resolveTransferLockIds` always occupied), so it cannot itself
contribute to a lock-ordering cycle. Two scenarios called out in the task, reasoned through:

- **Two concurrent transfers both need to create the same new destination.** Neither has taken any
  `PESSIMISTIC_WRITE` lock yet at this point in either transaction (the ensure-step precedes
  `lockInventoryRowsForUpdate`). The later inserter's `INSERT ... ON CONFLICT` blocks on Postgres's
  speculative-insertion wait for the earlier inserter's transaction outcome — a one-directional
  wait (the blocked side isn't holding anything the other side needs), so it cannot form a deadlock
  cycle by itself. Once both sides do reach `lockInventoryRowsForUpdate`, they lock the *same*
  resolved id in the same ascending-id order as always.
- **One transfer's source is another transfer's destination.** A transfer's `sourceInventoryId`
  always names an already-existing row (it's a caller-supplied reference to inventory that exists);
  a row created fresh by `ensureDestinationInventoryExists` can therefore never simultaneously be
  in use as some *other* transfer's source before it exists — there is no window where a
  not-yet-created row is referenced as a source. Once a destination row does exist (created or
  found), both transfers still resolve their full id sets and call
  `lockInventoryRowsForUpdate` with everything sorted ascending — identical to the pre-existing
  same-row-pair deadlock argument for two existing rows, unaffected by this fix.
- **Within one `batchTransferInventory` batch**, two lines targeting the same new destination:
  `resolveTransferLockIds` runs per line, sequentially, on the same thread/transaction — the second
  line's `INSERT ... ON CONFLICT` sees its *own* transaction's uncommitted insert from the first
  line (Postgres always lets a transaction see its own uncommitted writes) and no-ops immediately;
  no wait, no risk.

### Delete-and-recreate sub-case (reviewer's second concern)

Reasoned conclusion: **not independently reachable — subsumed by the lock hold, not a separate
risk.** Once `lockInventoryRowsForUpdate` acquires `PESSIMISTIC_WRITE` on a row, Postgres requires
any other transaction's `DELETE` of that same row to wait until this transaction commits or rolls
back (a `DELETE` needs the same row-level lock a `SELECT ... FOR UPDATE` holds). So no *other*
transaction can delete a row this transaction is holding locked, mid-transaction. The only entity
that deletes an inventory row inside `executeTransfer` is this same transaction's own drained-to-
zero *source* deletion (`if (newSourceQuantity == 0) { delete(sourceInventory); }`) — never the
destination — so within a single transfer's transaction, the destination row it locked is never
deleted before it's read. There is therefore no window, self-inflicted or external, in which the
specific locked destination row disappears and gets replaced by a new id before `executeTransfer`
reads it.

### One recorded, non-material ordering nuance (not a Q-6c-N — no durable behavior/persisted-data
change, transactionally invisible on rejection)

`ensureDestinationInventoryExists` (inside `resolveTransferLockIds`) now runs, and can write, before
`executeTransfer`'s `requireSameSite` cross-site check — whereas T-6c-4 originally fail-fast-ed the
site check *before any write* specifically to avoid creating an orphan destination row for a
request that's about to be rejected. Because the insert and the later rejection both happen inside
the same enclosing `@Transactional`, a cross-site transfer that creates a destination row via the
ensure-step and is then rejected by `requireSameSite` still rolls the insert back with everything
else — no orphan row is ever observable in the database, and the client-visible outcome (exception
type, message, final DB state) is unchanged from before this fix. The only externally-observable
difference is that another transaction racing to insert at that same new key would now briefly wait
on this doomed-to-rollback transaction's speculative insert before proceeding, once it rolls back —
a minor, correctness-neutral blocking delay, not a data-loss or security concern, so recorded here
as an assumption rather than raised as a new open question.

### Test — `StockMovementServiceConcurrentTransferNewDestinationIT` (new,
`inventory/application/StockMovementServiceConcurrentTransferNewDestinationIT.java`, extends
`BaseKafkaIntegrationTest`, real Testcontainers Postgres)

Two different source rows (`sourceA`, `sourceB`, quantity 100 each), each transferred concurrently
to the *same* destination `(location, product)` pair, confirmed absent from `location_inventory`
before the race starts. Uses the same forced-overlap technique as
`StockMovementServiceConcurrentAdjustIT`/`SiteProductConcurrencyIT`, adapted for a row that doesn't
exist yet: a third, independently managed transaction runs the *same-shaped* plain `INSERT` at the
destination key (no `ON CONFLICT` needed — it's the first attempt) and holds the transaction open
without committing, forcing both racing `transferInventory` calls' own `INSERT ... ON CONFLICT DO
NOTHING` destination-creation step to genuinely block on it — confirmed via `pg_stat_activity`
polling for 2 backends in `wait_event_type = 'Lock'` — before the holder rolls back (never commits,
so from the database's perspective no row ever really existed at this key before the two real
transfers raced to create/use it themselves). Assertions: exactly one `location_inventory` row
exists at the destination key (never two from a lost creation race); its final quantity equals the
sum of both transfers' quantities (30 + 20 = 50), not a lost update; both sources debited correctly;
exactly 4 `StockMovement` rows total (one withdrawal + one deposit per transfer); exactly 2 deposit
movements at the destination, forming one unbroken chain from 0 up to 50 in whichever order Postgres
actually serialized the two transactions (the second deposit's `previousQuantity` equals the first
deposit's `currentQuantity`), proving the second writer really did see the first writer's committed
result rather than a stale pre-lock value of zero.

### Confirmed the test actually catches the regression it targets (pre-fix failure, actual
transcript, not paraphrased)

Temporarily reverted the fix: `resolveTransferLockIds` back to only locking an already-existing
destination row (old `flatMap`/scalar-check version, `ensureDestinationInventoryExists` left
unused), and `executeTransfer`'s implicit branch back to the old unlocked
`findByLocation_IdAndProduct_Id(...).orElseGet(create)`. Reran the new test:
`./mvnw -q -Dtest=StockMovementServiceConcurrentTransferNewDestinationIT test` — **failed**, one of
the two racing `transferInventory` calls threw
`org.springframework.dao.DataIntegrityViolationException: ... duplicate key value violates unique
constraint "location_inventory_location_id_product_id_key"` from the old `orElseGet`'s plain JPA
`save(newInv)`, surfaced through `ExecutionException` in the test. This is precisely the
"genuine concurrent double-create... fails on the unique constraint" outcome the pre-fix
`resolveTransferLockIds` javadoc predicted for this exact scenario (both threads' unlocked
`findByLocation_IdAndProduct_Id` reads missed each other's uncommitted work and both attempted an
unprotected `INSERT`) — a real, reproducible failure caused specifically by the gap this fix closes,
not a flaky or unrelated error. Restored the fix (`resolveTransferLockIds`/`executeTransfer` back to
the versions above) and reran: passed again, 1/1.

### Verification (review-driven fix, actual commands and results, not paraphrased)

- `./mvnw -q clean test-compile` — clean, zero errors (both after the fix and, separately, after
  the temporary revert used for the pre-fix-failure confirmation above).
- `./mvnw -q -Dtest=StockMovementServiceConcurrentTransferNewDestinationIT test` — 1/1 pass (real
  Testcontainers Postgres).
- Pre-fix regression check (see above): temporarily reverted, same test failed with
  `DataIntegrityViolationException` / unique constraint violation; restored, 1/1 pass again.
- `./mvnw -q -Dtest='StockMovementServiceConcurrentTransferNewDestinationIT,
  StockMovementServiceConcurrentAdjustIT,StockMovementServiceSameSiteTransferTest,
  StockMovementOutboxAtomicityIT,InventoryOperationsCallerTransactionIT' test` — all pass (1/1, 1/1,
  4/4, 2/2, 2/2).
- `./mvnw -q clean test-compile` then `./mvnw -q -Dtest=ArchitectureTest test` — 8/8 pass (still 8
  rules, unchanged); `git status --porcelain` on `archunit_store/` — no diff. Expected: the new
  repository methods (`insertLocationInventoryIfAbsent`, `findSiteIdById`) and service-layer helper
  reference only types `inventory`/`sites` already depend on internally, adding no new cross-module
  edge.
- `./mvnw -q clean test` (full unrestricted suite, plain `test` — skips `*IT.java` by design) —
  exit 0; surefire reports sum to 348 tests, 0 failures, 0 errors, 0 skipped — unchanged from
  T-6c-6 (the new test class is itself an `*IT`, so it doesn't run here).
- `./mvnw -q test -Dtest='*IT'` — exit 1, 416 tests (up from 415, the one new
  `StockMovementServiceConcurrentTransferNewDestinationIT` case), 8 failures, all still exactly
  `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` — the same pre-existing,
  order-fragile, out-of-`inventory`'s-boundary debt already recorded in this log, reproduced again
  here with no new inventory-related failure. Confirmed not a regression:
  `./mvnw -q test -Dtest='*IT,!AnalyticsControllerSecurityIT,!ForecastControllerSecurityIT'` —
  exit 0.
- `git diff --check` — clean, no whitespace errors. `git status --porcelain` on `archunit_store/` —
  no diff (repeated from above for completeness of the checklist).
- `packages/contracts/openapi.json`/`packages/api-client/src/schema.d.ts` — not touched (no
  endpoint changed; this fix is repository/service-layer only). `OpenApiContractExportTest` not
  re-run for this reason.

## Review-driven fix round 2: T-6c-6 P1 findings (unified locking strategy) (2026-09-12)

Independent review of round 1's fix reproduced two P1s with real Postgres tests, both traced to
one root cause the reviewer named directly: "destination creation, identity resolution, and
locking need one consistent concurrency strategy; sorting afterward is insufficient." Round 1
introduced a second, inconsistent ordering domain (`(location, product)` for destination creation)
alongside the original one (row id, for locking) instead of unifying them, and that inconsistency
is exactly what both bugs exploited.

### Bug 1 — existing destination could disappear between "found" and "locked"

`ensureDestinationInventoryExists` read an existing destination's id with a scalar,
**unlocked** `findIdByLocation_IdAndProduct_Id` after its `INSERT ... ON CONFLICT DO NOTHING`
no-op'd against the already-existing row. `ON CONFLICT DO NOTHING` never touches or locks the row
it conflicts against — it is a true no-op for that case — so there was a real, unguarded window
between "read this id" and "this id is now locked" in which another transaction could delete the
row (e.g. it was drained to zero as a *different* transfer's source) before the read's id was ever
locked.

### Bug 2 — batch transfers could deadlock via crossed speculative inserts

`resolveTransferLockIds`/`ensureDestinationInventoryExists` ran in **request order** — whatever
order a batch's `transfers` list happened to name its lines — and ran *before*
`lockInventoryRowsForUpdate`'s id-sort ever applied. Postgres's `ON CONFLICT` speculative-insertion
protocol makes a second, concurrent inserter of the same brand-new key *wait* for the first
inserter's transaction to resolve (commit or abort) before deciding whether it's really a
conflict. Two batches naming the same two new destination keys X and Y in opposite request order
(batch 1: X then Y; batch 2: Y then X) could each successfully insert their first key and then
both block waiting for the other's speculative insert on the *second* key — a genuine circular
wait, independently reproduced by the reviewer as a real `PostgreSQL deadlock detected` error.
Sorting only the later `lockInventoryRowsForUpdate` call did not help, because the deadlock had
already happened during the earlier, unsorted `ensureDestinationInventoryExists` calls.

### Fix: one (location, product)-keyed routine, one global sort domain

Per the reviewer's own framing, this is a redesign, not two more independent patches. Every row a
transfer or batch-transfer will touch — source and destination, whether it already exists or must
be created — is now resolved to a `LocationProductKey(locationId, productId)` and locked through
exactly one routine, processed strictly in one global, deterministic order
(`LOCATION_PRODUCT_KEY_ORDER`: by location id, then product id). Row id is not used as a lock-order
key anywhere in the transfer path any more, because a not-yet-created destination has no id to
sort by until after it exists — mixing "sort by id" for some rows with "sort by
`(location, product)`" for others was exactly the two-domain inconsistency that caused bug 2.

- **`LocationInventoryRepository.findIdByLocation_IdAndProduct_IdForUpdate(locationId, productId)`**
  (new): a scalar, `@Lock(PESSIMISTIC_WRITE)` query — `SELECT li.id FROM LocationInventory li WHERE
  li.location.id = :locationId AND li.product.id = :productId`. Scalar-only (no joins), for the
  same F-6c-5 reason `lockAllByIdForUpdate` is scalar/join-free: `FOR UPDATE` must only ever lock
  the `location_inventory` row itself, never an outer-joined `locations`/`products`/
  `storage_locations` row. This is the method that closes bug 1: finding a row's id here *is*
  locking it — there is no separate unlocked read step for a concurrent delete to race through.
- **`LocationInventoryRepository.findLocationAndProductIdById(id)`** (new, replaces the now-dead
  `findProductIdById`): a scalar constructor-expression projection
  (`LocationInventoryRepository.LocationProductIds`) returning a row's `(location, product)` key,
  unlocked. Used during planning, before any lock is taken, for both a transfer's source (must
  already exist) and an explicit `destinationInventoryId` (the caller's claim that it exists) — an
  explicit destination id is resolved through the same scalar lookup as everything else rather
  than special-cased out of the scheme, and its absence now surfaces the same
  `InventoryNotFoundException` it always has ("Destination inventory not found: …"), just earlier
  (during planning) instead of inside the old `executeTransfer` fallback.
- **Removed** `findProductIdById` and the old unlocked `findIdByLocation_IdAndProduct_Id` scalar
  method — both dead once the routine above replaced their only caller (`ensureDestinationInventoryExists`,
  also removed). `findByLocation_IdAndProduct_Id` (the join-fetch, entity-returning method) is kept
  unchanged — it is still used by `LocationInventoryService`/`InventoryOperations` outside this
  fix's scope. `insertLocationInventoryIfAbsent` is kept unchanged.
- **`StockMovementService.ensureAndLockInventoryRow(locationId, productId)`** (new, replaces
  `ensureDestinationInventoryExists`): the single per-key find-or-create-and-lock routine.
  ```
  while (true) {
      Optional<UUID> lockedId = locationInventoryRepository
              .findIdByLocation_IdAndProduct_IdForUpdate(locationId, productId);
      if (lockedId.isPresent()) return lockedId.get();
      UUID siteId = locationRepository.findSiteIdById(locationId).orElseThrow(...);
      locationInventoryRepository.insertLocationInventoryIfAbsent(UUID.randomUUID(), locationId, siteId, productId);
  }
  ```
  Terminates in practice within 1-2 iterations: the first pass either finds an existing row (fast
  path) or finds nothing and issues `INSERT ... ON CONFLICT DO NOTHING`. That insert either commits
  our own new row (visible to our own transaction immediately, read-your-own-writes, so the next
  loop iteration's locked find succeeds) or no-ops because a concurrent inserter's row is in
  flight — in which case Postgres's speculative-insertion wait means our `INSERT` statement itself
  blocks until that concurrent transaction resolves, so by the time we loop back it has already
  committed (next find succeeds) or aborted (our own next insert succeeds). It can never return a
  "found but then vanished" id, because finding IS locking here.
- **`StockMovementService.planTransfers(transfers)`** (new, replaces `resolveTransferLockIds`):
  resolves every transfer's source and destination to a `LocationProductKey` up front, failing fast
  with the established not-found exceptions, and returns a list of `TransferPlan(request,
  sourceKey, destinationKey)` in the same order as the input.
- **`StockMovementService.lockPlannedRows(plans)`** (new): collects every plan's source key and
  destination key into one `TreeSet<LocationProductKey>` (dedup + sort in one step, via
  `LOCATION_PRODUCT_KEY_ORDER`), then calls `ensureAndLockInventoryRow` for each key **strictly in
  that sorted order, one key at a time** — never in parallel, never batched into one query — and
  returns a `Map<LocationProductKey, UUID>` of the resulting locked ids. Processing strictly in
  sorted order, across the *whole* set of rows a whole batch will touch (not per-line, inside a
  loop), is what makes the global-ordering property hold across concurrent transactions and
  batches: two overlapping writers that need overlapping keys always request them in the same
  relative order, so neither can hold what the other waits for.
- **`transferInventory`/`batchTransferInventory`**: both now call `planTransfers` then
  `lockPlannedRows` as one combined planning phase, before loading any entity — replacing the old
  `resolveTransferLockIds`/`lockInventoryRowsForUpdate` call sites. `batchTransferInventory` builds
  its plan list from the *whole* batch's transfers before locking anything, same as round 1's
  design, just re-keyed.
- **`executeTransfer`**: no longer takes `request.getDestinationInventoryId()` and branches on it.
  It now takes an already-resolved `UUID destinationInventoryId` (looked up from the locked-id map
  by the caller, using the plan's `destinationKey`) and does one `findById(destinationInventoryId)`
  — guaranteed to hit an already-locked, already-existing row — `.orElseThrow(IllegalStateException)`
  otherwise. All of the old explicit-id-vs-implicit-location branching, and the old
  `findByLocation_IdAndProduct_Id(...).orElseThrow` "should already exist" check, are gone: there is
  exactly one code path for resolving the destination now, for both shapes of request.
- **`lockInventoryRowsForUpdate`/`lockAllByIdForUpdate`** (id-ordered, unchanged): kept, but now
  used *only* by `batchAdjustInventory`. Confirmed by re-reading the method and grepping for
  `orElseGet`/`save(new`/`save(LocationInventory` in `StockMovementService.java`: `batchAdjustInventory`
  has no create-if-absent path anywhere — every id it locks is a caller-supplied reference to a row
  the earlier `preloadInventories`/ownership-validation loop already requires to exist — so an
  id-ordered lock remains sufficient and correct for it; it did not need this fix. Doc comments on
  both were updated to say so explicitly, so a future reader doesn't wonder why two different
  ordering strategies coexist in the same file.

### Why bug 1 cannot recur

Whatever destination (or source) row this transaction ends up mutating is now structurally
guaranteed to be one this transaction holds `PESSIMISTIC_WRITE` on, because *finding* the row's id
via `findIdByLocation_IdAndProduct_IdForUpdate` **is** the act of locking it — there is no
intermediate unlocked read whose result could go stale before a separate lock call runs. The old
bug required exactly that intermediate: read an id, then (implicitly) trust it while a completely
separate, later lock/load happened. That structure no longer exists anywhere in the transfer path.

### Why bug 2 cannot recur

A deadlock over two shared resources requires two transactions to acquire them in *opposite*
relative order. Every row this module ever locks for a transfer or batch-transfer — source or
destination, existing or brand-new — now passes through `lockPlannedRows`, which always visits its
full, deduplicated key set in one ascending `(location, product)` order. Two batches that name the
same two keys X and Y in opposite *request* order still process them in the same *relative* order
once each batch's own set is independently sorted (both visit "the smaller key" before "the larger
key," whichever concrete key that is) — so the crossed-wait shape (A holds X, wants Y; B holds Y,
wants X) can no longer arise. This is the same argument round 1's `lockInventoryRowsForUpdate`
already relied on for existing rows (sorted by id); the fix's contribution is putting *every* row,
including ones that don't exist yet, into that same one sort domain instead of a second, competing
one.

### Tests

- **`StockMovementServiceConcurrentTransferExistingDestinationRaceIT`** (new, bug 1). An existing
  destination row is held under an externally managed, uncommitted `SELECT ... FOR UPDATE` (same
  `pg_stat_activity`-polling forced-overlap technique as `StockMovementServiceConcurrentAdjustIT`)
  while two concurrent `transferInventory` calls target it via the *implicit* destination path (by
  location, so they re-derive the row's current id rather than pinning one up front). Once both
  racing transfers are confirmed genuinely blocked on the external lock, the external holder
  **deletes the row and inserts a brand-new row at the exact same `(location, product)` key** (a
  real, committed delete-and-recreate, not a placeholder that rolls back) before releasing.
  Assertions: exactly one row survives at the destination key; its id is *not* the original row's
  id (proving the recreated row was genuinely found and locked, not a stale reference to the
  deleted one); its final quantity equals the recreated row's baseline plus both transfers' deltas
  (no lost update); both sources debited correctly; exactly 4 movements (2 transfers × withdrawal +
  deposit).
  - **Pre-fix-failure evidence (actual transcript):** temporarily removed
    `@Lock(LockModeType.PESSIMISTIC_WRITE)` from `findIdByLocation_IdAndProduct_IdForUpdate` only
    (reintroducing bug 1's exact defect — finding the id is no longer the same act as locking it) and
    reran the same test: **failed** with
    `jakarta.persistence.OptimisticLockException: Batch update returned unexpected row count from
    update [1]; actual row count: 0; expected: 1` (wrapping a Hibernate `StaleStateException`) — one
    of the two racing transfers had read the *original* row's id unlocked, loaded and mutated it in
    memory, and then tried to `UPDATE ... WHERE id = <original id>` after the external holder had
    already deleted that exact row — a real, reproducible silent-write-to-a-vanished-row failure,
    exactly bug 1's mechanism. Restored the `@Lock` annotation and reran: 1/1 pass again.
- **`StockMovementServiceConcurrentBatchTransferCrossedDestinationsIT`** (new, bug 2), two tests:
  1. `concurrentBatchTransfersNamingSameTwoNewDestinationsInOppositeOrderDoNotDeadlock` — two
     concurrent `batchTransferInventory` calls, batch 1 naming brand-new destinations X then Y in
     its `transfers` list, batch 2 naming the same two Y then X, both started at the same instant
     via a `CyclicBarrier` to force genuine concurrent contention on the shared keys. Assertions:
     both calls complete without throwing; exactly one row exists at each of X and Y (never a
     duplicate from a lost creation race); each destination's final quantity equals the sum of
     *both* batches' contributions to it (18 at X = 10 + 8, 20 at Y = 15 + 5); exactly 8
     `StockMovement` rows (4 transfer lines × withdrawal + deposit) — it does not matter which
     batch's rows are checked, both applied cleanly, per the task's framing.
  2. `crossedOrderSpeculativeInsertsOnTwoNewKeysGenuinelyDeadlockInPostgres` — a mechanism-level
     proof independent of `StockMovementService`: two raw JDBC connections, autocommit off, each
     speculatively inserting the same two brand-new `(location, product)` keys in opposite order
     (side A: X then Y; side B: Y then X), with `CountDownLatch`es forcing each side to complete its
     *first* insert before either attempts its *second* — the exact interleaving needed to make each
     side's second insert block on the other's still-uncommitted first. This is the literal SQL
     pattern the pre-fix, request-ordered `resolveTransferLockIds`/`ensureDestinationInventoryExists`
     could produce across two batches with crossed line orders.
     - **Actual pre-fix mechanism evidence (real Postgres, this session, not paraphrased):** one
       side's connection returned `ERROR: deadlock detected` (captured verbatim via a temporary
       diagnostic print, then removed) while the other completed; the test asserts at least one side
       reports a deadlock. This is the same class of error the independent reviewer reported
       reproducing against the pre-fix code, confirming the crossed speculative-insert pattern is a
       genuine Postgres hazard, not a schedule-dependent artifact of a particular JVM thread
       interleaving.
- **`StockMovementServiceConcurrentTransferNewDestinationIT`** (prior round's test, unmodified):
  re-ran unchanged against the new design — still 1/1 pass. Its assertions (two concurrent transfers
  to the same not-yet-existing destination sum correctly, no lost update, no duplicate row) hold
  exactly as before; no internal method names it referenced needed updating (it only calls the
  public `transferInventory` and repository read methods, never the private planning helpers that
  were renamed).
- **`StockMovementServiceSameSiteTransferTest`** (existing Mockito unit test, updated — not just
  confirmed): rewritten to stub the new repository methods
  (`findLocationAndProductIdById`/`findIdByLocation_IdAndProduct_IdForUpdate`/`findSiteIdById`)
  instead of the removed ones, since this test never touches a real database and the old stubs
  (`findByLocation_IdAndProduct_Id`) are no longer called anywhere in the transfer path. Two
  fixtures that had reused the *same* location and product for both source and destination — valid
  under round 1's id-based locking but not realistic under the new `(location, product)`-unique-key
  scheme — were fixed to give the destination a distinct location, matching what the real unique
  constraint requires. 4/4 pass.

### Verification (actual commands and results, not paraphrased)

- `./mvnw -q clean test-compile` — clean, zero errors.
- `./mvnw -q -Dtest=StockMovementServiceConcurrentTransferExistingDestinationRaceIT test` — 1/1
  pass (real Testcontainers Postgres).
- Pre-fix regression check (bug 1, see above): `@Lock` temporarily removed from
  `findIdByLocation_IdAndProduct_IdForUpdate` only, same test failed with
  `OptimisticLockException`/`StaleStateException` ("actual row count: 0; expected: 1"); restored,
  1/1 pass again.
- `./mvnw -q -Dtest=StockMovementServiceConcurrentBatchTransferCrossedDestinationsIT test` — 2/2
  pass (the no-deadlock service-level proof and the raw-SQL mechanism proof).
- Pre-fix mechanism check (bug 2, see above): the mechanism-proof test's crossed-order raw-SQL
  pattern produced a real `ERROR: deadlock detected` from one of the two sides, captured via a
  temporary diagnostic print then removed; this is a property of the SQL pattern itself
  (independent of `StockMovementService`), confirming the hazard the fix's global sort order
  eliminates.
- `./mvnw -q -Dtest='StockMovementServiceConcurrentTransferExistingDestinationRaceIT,
  StockMovementServiceConcurrentBatchTransferCrossedDestinationsIT,
  StockMovementServiceConcurrentTransferNewDestinationIT,StockMovementServiceConcurrentAdjustIT,
  StockMovementServiceSameSiteTransferTest,StockMovementOutboxAtomicityIT,
  InventoryOperationsCallerTransactionIT' test` — all pass: 1/1, 2/2, 1/1, 1/1, 4/4, 2/2, 2/2.
- `./mvnw -q clean test-compile` then `./mvnw -q -Dtest=ArchitectureTest test` — 8/8 pass (still 8
  rules, unchanged); `git status --porcelain` on `archunit_store/` — no diff. Expected: the new
  repository methods and service-layer helpers reference only types `inventory`/`sites` already
  depend on internally, adding no new cross-module edge.
- `./mvnw -q clean test` (full unrestricted suite, plain `test` — skips `*IT.java` by design) —
  exit 0; surefire reports sum to 348 tests, 0 failures, 0 errors, 0 skipped — unchanged from
  before this fix (every new/changed test class here is itself an `*IT` or was already counted).
- `./mvnw -q test -Dtest='*IT'` — exit 1, 419 tests (up from 416: 3 new `*IT` test methods across
  the two new classes), 8 failures, all still exactly `AnalyticsControllerSecurityIT`/
  `ForecastControllerSecurityIT` — the same pre-existing, order-fragile, out-of-`inventory`'s-
  boundary debt already recorded in this log, reproduced again here with no new inventory-related
  failure. Confirmed not a regression:
  `./mvnw -q test -Dtest='*IT,!AnalyticsControllerSecurityIT,!ForecastControllerSecurityIT'` —
  exit 0.
- `git diff --check` — clean, no whitespace errors. `git status --porcelain` on `archunit_store/` —
  no diff.
- `packages/contracts/openapi.json`/`packages/api-client/src/schema.d.ts` — not touched (no
  endpoint changed; this fix is repository/service-layer only). `OpenApiContractExportTest` not
  re-run for this reason.

### No new Q-6c-N

This is a pure concurrency-correctness redesign, as anticipated: no new product, security, or
data-loss decision was required. The one nuance round 1 already recorded (the destination-ensure
step can now write, and roll back, before `executeTransfer`'s cross-site `requireSameSite` check —
transactionally invisible on rejection) still applies, unchanged in kind, just earlier in the
overall flow now that all rows are planned before any check runs.

## Review-driven fix round 3: T-6c-6 P1 finding, adjustment/transfer lock-order conflict (2026-09-12)

Independent review of round 2's fix found one more P1, reproduced on real Postgres with the
production repository lock methods: round 2 unified the *transfer* paths onto a single
`(location, product)`-keyed lock order, but left `batchAdjustInventory` locking by its original,
still-round-1-shaped ascending **row id** order (`lockInventoryRowsForUpdate` ->
`lockAllByIdForUpdate`). Both orderings are internally consistent on their own, but they are two
different domains that can disagree with each other: for two products A and B at one location, row
id and product id are unrelated random UUIDs, so whichever of A/B's row ids sorts first can easily
be the opposite of which sorts first by `(location, product)`. A batch-adjust touching both rows
(locking by id) and a concurrent batch-transfer whose destinations are those same two rows
(locking by key) could then acquire them in opposite relative order — each holding one row and
waiting for the other — a real crossed-wait deadlock, exactly the shape every prior round's fix
was meant to eliminate, just recurring at a boundary between two methods instead of within one.

### Fix: batchAdjustInventory joins the same single ordering domain

`lockInventoryRowsForUpdate` (used only by `batchAdjustInventory`) no longer sorts by id or calls
`lockAllByIdForUpdate` at all. It now resolves every requested inventory id to its
`LocationProductKey` (via the same unlocked scalar `findLocationAndProductIdById` the transfer
paths already use for planning), collects them into the same `TreeSet<LocationProductKey>` /
`LOCATION_PRODUCT_KEY_ORDER` ordering, and locks each key through the exact same
`ensureAndLockInventoryRow` routine transfers use. Ids that don't resolve to an existing row are
silently skipped at this step; the existing "Inventory not found" check immediately after (via
`preloadInventories`) still catches that case with its established message, unchanged. The now
fully-unused `lockAllByIdForUpdate` repository method was deleted (dead code), along with the
comment block that used to describe it as `batchAdjustInventory`'s intentionally-different, "also
correct" locking strategy — that framing was the bug: there is now exactly **one** lock-ordering
domain, `(location, product)`, shared by every writer in this class capable of holding more than
one `LocationInventory` row in a single transaction. No `@Version` column exists on
`LocationInventory` (F-6c-5, unchanged); this remains `PESSIMISTIC_WRITE`-only.

### Why this cannot recur the way rounds 1-2's bugs did

Both `batchAdjustInventory` and every transfer path now resolve every row they will touch to a
`LocationProductKey` and lock strictly via `ensureAndLockInventoryRow`, in `LOCATION_PRODUCT_KEY_ORDER`.
There is no second method left in `StockMovementService` that locks more than one `LocationInventory`
row through any other query or ordering. Two callers sharing overlapping rows — regardless of
whether one calls a row "the thing being adjusted" and the other calls the same row "a transfer
destination" — always attempt to acquire the smallest shared key first; whichever call reaches it
first proceeds through the rest of its own sorted list, and the other blocks on that one key until
the first finishes, with no possibility of a "you hold what I need while I hold what you need"
cycle. A future third method that needs to hold more than one row must reuse
`ensureAndLockInventoryRow`/the same key ordering rather than inventing a new one, exactly what
this round's fix itself had to do after round 2 left one method out.

### Test: deterministic mixed adjustment/transfer deadlock reproduction

New `StockMovementServiceMixedAdjustTransferLockOrderIT`
(`concurrentBatchAdjustAndBatchTransferOnSameTwoRowsDoNotDeadlock`). Relying on two independently-random
UUIDs (row id vs. product id) to disagree by chance would make the pre-fix reproduction flaky, so
the test deliberately inserts the two destination `location_inventory` rows with explicitly chosen
ids (via raw `JdbcTemplate`, bypassing Hibernate's `GenerationType.UUID` generator) such that
row-id order is the *exact reverse* of `(location, product)` key order, guaranteed regardless of
the actual random product ids. It then forces the precise interleaving a natural race cannot
reliably produce (same technique as `StockMovementServiceConcurrentAdjustIT`, extended to a staged
two-step handoff): an externally held `SELECT ... FOR UPDATE` on the key-order-second /
id-order-first row, released only after (a) the adjust batch is confirmed blocked on it and then
(b) the transfer batch is confirmed blocked too (having already acquired the other row first,
uncontended). Releasing with the adjust batch queued first reproduces the classic crossed-wait
deterministically.
- **Pre-fix reproduction (confirmed, not assumed)**: temporarily restored the old
  `lockAllByIdForUpdate`-based id-ordered body of `lockInventoryRowsForUpdate` and reran — the test
  failed with a real `org.springframework.dao.CannotAcquireLockException` wrapping
  `org.postgresql.util.PSQLException: ERROR: deadlock detected` ("Process 64 waits for ShareLock on
  transaction 835; blocked by process 62. Process 62 waits for ShareLock on transaction 834;
  blocked by process 64."). Reverted back to the fix; the test passes cleanly.
- Also confirms the correct combined final state when both do complete: each row's quantity equals
  100 plus its own adjustment delta plus its own transfer-in quantity (no lost update).

### Verification (actual commands and results, not paraphrased)

- `./mvnw -q clean test-compile` — clean, both during the temporary pre-fix revert and after
  restoring the fix.
- `./mvnw -q -Dtest=StockMovementServiceMixedAdjustTransferLockOrderIT test` — 1/1 pass (post-fix);
  1/1 **fail** with the deadlock exception above (pre-fix, temporary revert, not committed).
- `./mvnw -q -Dtest=StockMovementServiceMixedAdjustTransferLockOrderIT,StockMovementServiceConcurrentAdjustIT,
  StockMovementServiceConcurrentTransferNewDestinationIT,StockMovementServiceConcurrentBatchTransferCrossedDestinationsIT,
  StockMovementServiceSameSiteTransferTest,StockMovementOutboxAtomicityIT,InventoryOperationsCallerTransactionIT,
  ArchitectureTest test` — 21/21 pass (1, 1, 1, 2, 4, 2, 2, 8).
- `git diff --check` — clean. `git status --porcelain` on `archunit_store/` — no diff.
- `./mvnw -q clean test` (full unrestricted suite, plain `test`) — all 61 surefire reports show
  `Failures: 0, Errors: 0` (348 tests summed; unchanged from round 2, the new class is an `*IT`).
- `./mvnw -q test -Dtest='*IT'` — exit 0, all 582 tests (up from 576) across every `*IT.txt` report
  show `Failures: 0, Errors: 0`.
- `packages/contracts/openapi.json`/`packages/api-client/src/schema.d.ts` — not touched (no
  endpoint changed; repository/service-layer only).

### No new Q-6c-N

Pure concurrency-correctness fix, same as round 2. T-6c-6 is now considered closed pending any
further review.

## 6c implementation (T-6c-7) (2026-09-12)

Implements T-6c-7 (outbox envelope expand migration + `EventOutbox` entity fields) per F-6c-6 and
AC-4. Schema/entity only — `EventOutboxService` still writes correlation into the JSON payload as
before; nothing populates the new columns at creation time yet (T-6c-8's job), and the durable
idempotency command table (T-6c-10) is a separate, later thing from the `idempotency_key` column
added here (this column is only where a command's key gets copied *into* the outbox event once
T-6c-8/T-6c-10 exist).

- **`V63__add_envelope_context_to_event_outbox.sql`**: `event_outbox` predates Flyway (created by
  Hibernate before Flyway adoption, same as `stock_movements` before `V59`). Adds five nullable
  columns, no FK, matching 6b's expand-only precedent: `site_id UUID`, `event_version INTEGER
  DEFAULT 1` (so any row inserted after this migration, including by a writer that omits the
  column entirely, starts versioned), `correlation_id TEXT` (matches the existing JSON payload
  value's real type — a caller-supplied or generated string via `CorrelationIdFilter`, not
  guaranteed UUID-parseable), `causation_id TEXT` (same shape), `idempotency_key TEXT`
  (client-supplied header value per Q-6c-3, not a generated UUID). No constrain step in this
  record (P-3).
- **`V64__backfill_event_outbox_envelope_context.sql`**: scoped to unpublished rows only
  (`published_at IS NULL`) — a published event already went out over Kafka without this context,
  so backfilling it here wouldn't change what a consumer already received. Two passes: (1)
  movement-derived (authoritative) — for a row whose `payload->>'stock_movement_id'` still
  resolves to a real `stock_movements` row, takes that movement's `site_id` (populated for every
  existing row by `V60`'s backfill) and sets `event_version`/`correlation_id` from the payload; (2)
  guard case for a dangling movement reference (id doesn't parse or no matching row) — still sets
  `event_version`/`correlation_id`, leaves `site_id` NULL (the same "unknown-site" posture Q-6c-5
  already accepts for `stock_movements` during the compatibility window) rather than blocking or
  throwing. Both passes are `COALESCE`-guarded (idempotent on rerun), deliberately not guarded on
  `event_version` alone — `V63`'s `DEFAULT 1` means an old-writer row already reads back
  `event_version = 1` before this script runs, so an `event_version`-only guard would wrongly skip
  its still-unresolved `site_id`/`correlation_id`; `site_id IS NULL` is pass 1's real "not yet
  touched" signal, and pass 2 checks `event_version`/`correlation_id` individually.
- **`EventOutbox.java`**: added the five matching nullable Java fields (`siteId`, `eventVersion`,
  `correlationId`, `causationId`, `idempotencyKey`) via `@Column`. No behavior change — existing
  `@Data`/`@Builder` entity, `UUID` already imported.
- **`EventOutboxEnvelopeMigrationIT`** (2 cases): runs the real `V63` SQL against Testcontainers
  Postgres and asserts (a) all five columns exist, nullable, with the expected Postgres types
  (`uuid`, `integer`, `text` x3); (b) an old-writer-style insert that omits the new columns
  entirely still succeeds and reads back `event_version = 1` (the `DEFAULT`), the other four NULL.
- **`EventOutboxEnvelopeBackfillIT`** (6 cases): runs the real `V64` SQL and covers: a resolvable
  movement backfills site/version/correlation correctly; a payload with no `correlation_id` key
  backfills a NULL correlation (not an error); a dangling movement reference gets NULL `site_id`
  but still gets version/correlation backfilled (T-6c-7's explicit guard-case requirement); an
  old-writer row whose `event_version` is already defaulted to 1 (by `V63`, not yet backfilled)
  still gets its site/correlation backfilled — the specific case the dual-guard reasoning above
  exists to get right; already-published rows are left untouched; rerunning the whole backfill
  changes nothing (idempotent).

### Verification (actual commands and results, not paraphrased)

- `./mvnw -q clean test-compile` — clean.
- `./mvnw -q -Dtest=EventOutboxEnvelopeMigrationIT,EventOutboxEnvelopeBackfillIT test` — 8/8 pass
  (2 + 6).
- `git diff --check` — clean. `git status --porcelain` on `archunit_store/` — no diff.
- `./mvnw -q -Dtest=ArchitectureTest test` — 8/8 pass (unchanged rule count; schema/entity-only
  change adds no new cross-module edge).
- `./mvnw -q clean test` (full unrestricted suite, plain `test`) — all 61 surefire reports show
  `Failures: 0, Errors: 0` (348 tests summed; unchanged from T-6c-6 round 3, both new classes are
  `*IT`).
- `./mvnw -q test -Dtest='*IT'` — exit 0, all 590 tests (up from 582 by the 8 new test methods)
  across every `*IT.txt` report show `Failures: 0, Errors: 0`.
- `packages/contracts/openapi.json`/`packages/api-client/src/schema.d.ts` — not touched (no
  endpoint changed; this is a schema/entity-only pass).

### No new Q-6c-N

Schema/entity work only, fully within F-6c-6/AC-4's already-scoped requirements. Note for
whichever session picks up T-6c-8 next: T-6c-9 already flagged that
`tests/contracts/schemas/event_outbox.json` sets `additionalProperties: false` and doesn't declare
`correlation_id` even though the producer already emits it in the JSON payload today (F-6c-6) —
this drift is unrelated to and unaffected by T-6c-7's new *columns* (which aren't in the Kafka
payload yet), but T-6c-9 will need to add all five envelope fields to that schema once T-6c-8
starts publishing them at the envelope level, not just fix the pre-existing `correlation_id` gap.

## 6c implementation (T-6c-8) (2026-09-12)

Implements T-6c-8: `EventOutboxService` populates the AC-4 envelope columns at creation time and
gates the `site_id:product_id` Kafka partition key behind an explicit, currently-off cutover flag
per Q-6c-4. Causation ID stays null throughout — reserved for movements caused by *consuming*
another event (events-and-replica-readiness.md §2), and no such consumer exists yet; every
movement today is directly command-initiated.

- **New `IdempotencyKeyContext`** (`shared.correlation`, mirrors `CorrelationIdContext`'s MDC
  pattern exactly): `current()` reads MDC key `idempotencyKey`. Nothing sets it yet — T-6c-10's
  command layer is the intended writer — so it returns null everywhere today, the same pre-filter
  posture `CorrelationIdContext` had before `CorrelationIdFilter` existed. This is deliberate
  ordering, not a gap: T-6c-8 wires the outbox *column*, T-6c-10 wires who populates the *context*.
- **`EventOutboxService.createStockMovementEvent`**: `EventOutbox.builder()` now sets `siteId`
  (from `movement.getSite()`, null if the movement carries none), `eventVersion` (a fixed
  `ENVELOPE_VERSION = 1` constant — the envelope *shape* version, bumped only if the envelope
  itself changes, not per business event), `correlationId` (existing `CorrelationIdContext`,
  unchanged source), `causationId` (always null, see above), `idempotencyKey`
  (`IdempotencyKeyContext.current()`).
- **T-6c-8 found and fixed a pre-existing latent bug while building the "re-run creation does not
  duplicate" proof the task list requires**: the original code called
  `eventOutboxRepository.save(event)` inside a `try { } catch (DataIntegrityViolationException)`,
  relying on the V16 JSONB unique index to reject a second row for the same
  `stock_movement_id`. With Hibernate JDBC batching (`hibernate.jdbc.batch_size=50`), `save()`
  defers the actual INSERT past the method's `try/catch` scope, so the violation surfaces at the
  enclosing `@Transactional` method's flush/commit instead — and because `EventOutboxRepository`'s
  own `save`/`saveAndFlush` methods are themselves `@Transactional` (`SimpleJpaRepository`
  joining the same physical transaction), Spring marks that physical transaction rollback-only the
  moment the exception is thrown, regardless of whether the caller catches it — a well-known
  Spring/Hibernate trap, not something a `try/catch` at this call site can work around. Left as-is,
  a "retry outbox creation for an already-recorded movement" call would silently roll back the
  *entire* enclosing business transaction instead of being swallowed as the graceful duplicate the
  catch block's comment claims. Fixed with a check-before-insert guard: `entityId` is already a
  deterministic UUID derived from the movement id, so
  `eventOutboxRepository.existsByEntityId(entityId)` (new repository method) is checked first and
  the method returns early on a hit, before any location/total lookups or the insert attempt. The
  `saveAndFlush` (switched from `save`, so any *other* real DB error still surfaces synchronously
  inside this method's own try/catch rather than at some later, harder-to-attribute flush point)
  plus the V16 index and its catch block remain as defense-in-depth for the residual concurrent
  race (two overlapping transactions both passing the `existsByEntityId` check before either
  inserts) — recorded as an accepted assumption below, not a further fix: losing that rare race and
  rolling back the whole transaction is the textbook-correct behavior for a transactional outbox
  (the caller's whole idempotent command is expected to be retried), not a bug to route around.
- **`EventOutboxService.publishPendingEvents`**: message now also carries `event_version`,
  `site_id` (stringified, null-safe), `causation_id`, `idempotency_key` at the envelope level
  (T-6c-9 is the contract-schema side of this). New `partitionKeyFor(EventOutbox)`: returns the
  legacy `item_id`-only key unless `kafka.partitioning.site-scoped-key.enabled` is true **and**
  the event actually carries a `site_id` — falling back to the legacy key even when the flag is on
  if the event predates the site backfill, so flipping the flag in an environment with any
  unbackfilled row cannot silently drop site-scoped partitioning for only some events unnoticed.
- **New property `kafka.partitioning.site-scoped-key.enabled`** (`application.properties`,
  `application-dev.properties`), default `false` in both. Per Q-6c-4, flipping this is a
  coordinated production cutover (verify actual topic partition count, drain the old publisher,
  confirm every consumer tolerates losing shared per-product ordering across sites) that this
  record does not authorize — the flag exists so the code path and its tests can exist now without
  that cutover happening as a side effect of deploying this commit.

### Tests

- `EventOutboxServiceCreateEventTest` (+4): envelope population from a movement's site;
  null `siteId` when the movement has none; `idempotencyKey` carried from MDC when present;
  null `idempotencyKey` when not set (today's default, until T-6c-10). All existing
  `save`/`verify` calls in this class switched to `saveAndFlush` to match the production change.
- `EventOutboxServicePublishTest` (+3): legacy `item_id` key when the flag is off even if the
  event carries a site; `site_id:product_id` key when the flag is on and a site is present;
  fallback to the legacy key when the flag is on but the event has no site (pre-backfill row).
- `AdjustToKafkaIT` (+3, real Postgres/Kafka via Testcontainers): outbox record carries the
  movement's `site_id`/`event_version=1` through the real batch-adjust endpoint; re-creating an
  outbox event for an already-recorded movement (fetched via `findByReasonAndAtAfterWithItem` so
  the retry path is exercised on a detached-safe, non-lazy-proxy movement, matching how a real
  retried caller would load one) leaves exactly one outbox row — this test required manually
  creating the V16 index via `JdbcTemplate` first, since this profile's
  `spring.jpa.hibernate.ddl-auto=create-drop` means Flyway (and therefore V16) never runs against
  it, only Hibernate's entity-derived schema; a cutover-flag-enabled end-to-end test asserting the
  real Kafka record's key is `site_id:product_id`, searching every record the shared topic
  delivers (not just the first) since the topic is a static Testcontainer shared across every test
  in the class and an earliest-offset consumer also sees earlier tests' messages.
- Confirmed the dedupe fix actually matters: temporarily removed the `existsByEntityId` guard and
  reran `recreatingOutboxEventForSameMovement_isDeduplicated` alone — it failed with
  `UnexpectedRollbackException: Transaction silently rolled back because it has been marked as
  rollback-only`, the exact failure mode described above, not a generic assertion mismatch;
  restored the fix and reran to confirm it passes again.

### Verification (actual commands and results, not paraphrased)

- `./mvnw -q clean test-compile` — clean.
- `./mvnw -q -Dtest=EventOutboxServiceCreateEventTest,EventOutboxServicePublishTest test` — 11/11
  and 4/4 pass.
- `./mvnw -q -Dtest=AdjustToKafkaIT test` — 9/9 pass (6 pre-existing + 3 new), real Testcontainers
  Postgres/Kafka.
- Pre-fix-failure reproduction: `./mvnw -q -Dtest=AdjustToKafkaIT#recreatingOutboxEventForSameMovement_isDeduplicated test`
  with the guard removed — 1 test, 1 error, `UnexpectedRollbackException` as described; restored,
  reran the same single test — 1/1 pass.
- `./mvnw -q clean test` (full unrestricted suite) — 355 tests summed across all 61 surefire
  reports (up from 348 by 7 new unit-test methods), `Failures: 0, Errors: 0`.
- `./mvnw -q test -Dtest='*IT'` — 431 tests (up from 419 by 3 new IT methods), 8 failures, all the
  same pre-existing `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` order-fragility
  debt (unrelated native-SQL/H2 issue, documented in this log's "Current handoff"); confirmed not a
  regression: `./mvnw -q test -Dtest='*IT,!AnalyticsControllerSecurityIT,!ForecastControllerSecurityIT'`
  exits 0.
- `./mvnw -q -Dtest=ArchitectureTest test` after an independent clean `test-compile` — 8/8 pass;
  `git status --porcelain` on `archunit_store/`/`archunit.properties` shows no diff (no new
  cross-module edge — `IdempotencyKeyContext` lives in the already-approved `shared.correlation`
  package next to `CorrelationIdContext`).
- `./mvnw -q -Dtest=OpenApiContractExportTest test`, then `diff` against a pre-change copy of
  `packages/contracts/openapi.json` — byte-identical (no route added/changed this task; expected,
  T-6c-11/T-6c-12 are the route-adding tasks). `packages/api-client` not regenerated for the same
  reason.
- `git diff --check` — clean.

### Assumption carried forward (not a Q-6c-N)

The residual TOCTOU race left after the `existsByEntityId` guard (two transactions both observing
"not yet recorded" before either inserts) still hits the same rollback-only failure mode for
whichever transaction loses the race at the database level. This is accepted, not fixed: per the
transactional-outbox pattern, a transaction that cannot be sure its event was durably recorded
should roll back its business effect entirely and let the caller's idempotent command (Q-6c-3,
T-6c-10) be retried, rather than the outbox layer silently absorbing the failure and leaving the
business mutation committed with an uncertain event state. No durable behavior or persisted-data
change is implied, so this does not get a `Q-6c-N` — recorded here so a future session does not
mistake the remaining race window for an oversight.

## 6c implementation (T-6c-9) (2026-09-12)

Implements T-6c-9: contract schema and producer/consumer compatibility for the AC-4 envelope
fields T-6c-8 started publishing.

- **`tests/contracts/schemas/event_envelope.json`**: added `correlation_id`, `event_version`,
  `site_id`, `causation_id`, `idempotency_key` at the top-level `properties` (all nullable, typed
  to match the Java source: `event_version` integer, `site_id` uuid-formatted string, the rest
  plain nullable strings). Also fixes the pre-existing F-6c-6 drift this task flagged:
  `correlation_id` had been emitted by the producer since before this record but was never
  declared in the schema at all (the top-level object's `additionalProperties: false` was
  silently tolerating it only because no existing fixture/test ever included it).
- **`tests/contracts/conftest.py`**: `sample_full_payload` gains all five fields (non-null, proving
  the happy path); `sample_null_optionals_payload` gains all five as explicit `None`.
  `sample_minimal_payload` deliberately left untouched (still omits them entirely), so it doubles
  as the "old producer/pre-T-6c-8 row" compatibility case. Also loads messaging-service's
  `src/events.py` directly as `messaging_events` (no relative imports there, so it needs none of
  forecasting-service's package-shim machinery above it) so both real consumer models are
  reachable from the same test session.
- **`test_producer_contract.py`** (+2): `test_all_envelope_fields_present_in_schema` — the
  top-level analogue of the existing payload-level drift guard, asserting the exact field set
  `EventOutboxService.publishPendingEvents`'s `message.put(...)` calls produce (verified by
  grepping the Java source directly, not by copying the schema back at it) matches the schema's
  declared top-level properties; `test_envelope_fields_may_be_null_or_absent` pins that
  `sample_minimal_payload` (no AC-4 fields at all) still validates.
- **New `test_consumer_compatibility.py`** (+7): both real `EventEnvelope` Pydantic models
  (forecasting-service's and messaging-service's, imported directly, not re-implemented) parse
  `sample_full_payload` (all five new fields present) via both `model_validate` and
  `model_validate_json` — the latter matters because `_parse_line`'s real Kafka-message parse path
  uses `model_validate_json`, not `model_validate`. Also asserts directly (`"site_id" not in
  EventEnvelope.model_fields`) that neither service's model declares the fields it doesn't use,
  so "ignores unknown fields" is proven against the models' actual declared shape, not asserted
  past it. Forecasting-service's model additionally confirmed to still parse
  `sample_minimal_payload` (fields absent, not just null) since it is the consumer that actually
  runs this path today (messaging-service's model requires `topic`/`event_type`/`entity_type`/
  `entity_id`/`created_at`, so `sample_minimal_payload` was never a valid input for it and no
  compatibility claim is made there).

### Scope explicitly not covered (recorded per the task's own fallback clause)

- **Producer-payload-built-by-the-real-Java-path test**: not attempted. `EventOutboxService` is a
  Spring-managed, `@Transactional`, Postgres/Kafka-backed method — reaching it from this Python
  test session would mean either running the JVM test suite and shelling out to capture its
  produced JSON (real cross-language plumbing the task text explicitly allows skipping) or
  duplicating its field-construction logic in Python (which is what the existing hand-written
  fixtures already are, and wouldn't add independent proof). `AdjustToKafkaIT` (Java side, T-6c-8)
  already asserts the real producer's message shape against a live Kafka consumer; that is the
  actual "built by the real producer path" proof for this cycle, just not runnable from here.
- **"Duplicate delivery of the same event_id produces one effect" for these two consumers**: no
  existing event-id-dedupe mechanism was found in either `forecasting-service/src` or
  `messaging-service/src` (grepped for `event_id` usage in both; neither stores/checks processed
  event ids before generating an effect). This is a consumer-idempotency gap AC-4/§5 of
  events-and-replica-readiness.md already requires in general, not something T-6c-8/T-6c-9
  introduced or changed — the new envelope fields don't touch it either way. Recording as carried-
  forward debt rather than fabricating a test against a mechanism that does not exist.

### Verification (actual commands and results, not paraphrased)

- `services/forecasting-service/.venv/bin/python -m pytest tests/contracts -v` — 31 passed (22
  pre-existing + 9 new: 2 in `test_producer_contract.py`, 7 in `test_consumer_compatibility.py`).
  Uses forecasting-service's existing `.venv` (has `pytest`/`pydantic`/`jsonschema`/`pandas`
  already installed) since the system `python3`/`python3.11` had none of them.
- `services/messaging-service/.venv/bin/python -m pytest services/messaging-service/tests -q` —
  71 passed, confirming the `conftest.py` change (loading messaging-service's `events.py`
  alongside forecasting-service's) didn't disturb messaging-service's own suite.
- No Java files changed this task; inventory-service's suites not rerun for this task on their own
  (T-6c-8's verification already covers the Java producer side these Python tests validate
  against; T-6c-10 is the next Java-touching task).

### No new Q-6c-N

Schema/test-only work within F-6c-6/F-6c-7/AC-4's already-scoped requirements.

## 6c implementation (T-6c-10) (2026-09-12)

Implements T-6c-10: durable, site/user-scoped command idempotency per Q-6c-3's concrete design.
Scoped as the durable-table/service layer per the "Current handoff" note above this session
opened with — the v1 mutation routes that actually extract the `Idempotency-Key` header and call
this service are T-6c-12's job, not this one's; this task makes the mechanism itself real and
independently tested.

- **`V65__create_command_idempotency.sql`**: new `command_idempotency` table (`site_id`, `user_id`,
  `idempotency_key`, `command_type`, `request_fingerprint`, `result_status`, `result_body`,
  `created_at`), no FK (same no-FK precedent as `event_outbox`'s V16/V63 — this is an operational
  dedup log, not a durable business record), a unique index on `(site_id, user_id,
  idempotency_key)`, and an index on `created_at` for the retention cleanup's range scan. Explicit
  7-day retention policy documented in the migration header (T-6c-10's task text requires this be
  explicit, not left implicit).
- **New `shared.idempotency` package** (`CommandIdempotency` entity, `CommandIdempotencyRepository`,
  `IdempotencyConflictException`, `CommandIdempotencyService`). Placed under `shared`, not
  `inventory`, because Q-6c-3's design is not inventory-specific — any future v1 mutation route in
  any module can reuse it. `CommandIdempotency` also declares the same unique constraint at the
  JPA level (`@Table(uniqueConstraints = ...)`), matching the codebase's existing dual-declaration
  pattern (migration SQL + entity annotation) for every other unique index — the entity Building
  step below (T-6c-10's own concurrency test) demonstrated this is load-bearing, not decorative:
  the test profile's `ddl-auto=create-drop` schema is Hibernate-generated, not Flyway-migrated, so
  without the JPA-level declaration the concurrent-race test's schema had no constraint at all and
  both racing inserts silently succeeded.
- **`CommandIdempotencyService.executeIdempotent(siteId, userId, idempotencyKey, commandType,
  requestFingerprint, resultType, command)`**: reads any existing record for the (site, user, key)
  triple first. A hit with a matching fingerprint returns the stored `CommandResult` (status +
  Jackson-deserialized body) without invoking `command`; a hit with a different fingerprint throws
  `IdempotencyConflictException` (409, wired into `GlobalExceptionHandler` next to
  `SiteProductVersionConflictException`) without invoking `command` either. A miss invokes
  `command`, then `saveAndFlush`s the result row — deliberately not caught: per Q-6c-3, "no
  idempotency row survives a failed attempt," so letting a unique-constraint violation from the
  residual concurrent-race window (two calls both missing the initial read) propagate rolls back
  this whole `@Transactional` method, including whatever `command` already did in the same
  transaction, cleanly failing the race's loser rather than leaving an uncertain half-committed
  state. Authorization recheck on replay (Q-6c-3) is structural, not code in this class: a v1
  controller's `@PreAuthorize` runs before its method body regardless of whether the call turns
  out to be a first attempt or a replay, so a role/membership change between attempts is never
  bypassed — documented in the class Javadoc rather than re-implemented here.
- **`cleanupExpiredRecords()`**: `@Scheduled(cron = "0 0 3 * * *")`, deletes rows older than the
  documented 7-day retention window via a new `deleteByCreatedAtBefore` repository method.
- **ArchUnit**: two changes, both reviewed. (1) `module-dependency-edges-baseline.txt` gains
  `exceptions -> shared` (seventh reviewed addition, documented inline) from
  `GlobalExceptionHandler` importing `IdempotencyConflictException` — verified `shared` still has
  zero outgoing edges (grepped every import under `shared/**`), so this cannot create a cycle. (2)
  `ArchitectureTest.isAllowedRepositoryCaller` gains an explicit, narrow allowance for
  `shared.idempotency` (mirroring the legacy `services`/`repositories` top-level exemption): a
  business module's repository-owning code is recognized by its `.application`/`.infrastructure`
  subpackage, but `shared` is deliberately excluded from `BUSINESS_MODULES` (it must not depend on
  a business module) and its existing subpackages (`correlation`, `web`) are flat, not
  application/infrastructure-split — so `shared.idempotency`, its first entity/repository-owning
  subpackage, needed its own explicit line rather than either existing mechanism recognizing it.
  No frozen-store regeneration needed: the rule change only *removes* violations it previously
  would have flagged, and confirmed stable (8/8, no `archunit_store`/`archunit.properties` diff)
  across two independent clean rebuilds.

### Tests

- **`CommandIdempotencyServiceTest`** (Mockito, 6 cases): new key invokes the command and stores
  status/commandType/fingerprint/serialized body; replay with the same fingerprint returns the
  stored result without invoking the command; replay with a different fingerprint throws
  `IdempotencyConflictException` without invoking the command; same key at a different site is not
  a replay; same key for a different user is not a replay; `cleanupExpiredRecords` deletes with the
  documented 7-day cutoff.
- **`CommandIdempotencyServiceIT`** (real Postgres/Kafka via `BaseKafkaIntegrationTest`, 3 cases):
  concurrent duplicate submissions (two threads, same site/user/key, the command sleeps to widen
  the race window and writes to a scratch table participating in the same ambient transaction) —
  both threads race past the initial read (proven: the command is invoked exactly twice), exactly
  one succeeds, the loser's business effect (the scratch-table insert) rolls back with its failed
  idempotency insert, and exactly one `CommandIdempotency` row survives; rollback-then-retry — a
  command that throws leaves no row, and the same key can be retried successfully afterward;
  restart-then-replay — a second, independent call with the same key/fingerprint replays without
  re-invoking the command.
- **`CommandIdempotencyMigrationIT`** (real Postgres via raw Testcontainers, no Spring context,
  same pattern as `EventOutboxEnvelopeMigrationIT`, 3 cases): the real `V65` SQL produces the
  expected column shapes; the real unique index rejects a duplicate `(site_id, user_id,
  idempotency_key)` triple; the same key at a different site is not rejected.
- Confirmed the entity-level `@UniqueConstraint` actually matters: temporarily removed it and
  reran the concurrency IT — both racing inserts silently succeeded (2 rows, `commandInvocations`
  still 2 but the "exactly one succeeds" assertion failed with "expected: 1 but was: 2"); restored
  it and reran to confirm the test passes again.

### Verification (actual commands and results, not paraphrased)

- `./mvnw -q clean test-compile` — clean.
- `./mvnw -q -Dtest=CommandIdempotencyServiceTest,CommandIdempotencyServiceIT test` — 6/6 and 3/3
  pass (real Testcontainers Postgres/Kafka for the IT).
- `./mvnw -q -Dtest=CommandIdempotencyMigrationIT test` — 3/3 pass.
- Pre-fix-failure reproduction: temporarily removed `@UniqueConstraint` from `CommandIdempotency`,
  reran `CommandIdempotencyServiceIT#concurrentDuplicateSubmissions_produceAtMostOneCommittedEffect`
  alone — failed exactly as described; restored, reran — passes.
- `./mvnw -q clean test` (full unrestricted suite) — 361 tests summed across all surefire reports
  (up from 355 by 6 new unit-test methods), `Failures: 0, Errors: 0`.
- `./mvnw -q test -Dtest='*IT'` — 437 tests (up from 431 by 6 new IT methods: 3 + 3), 8 failures,
  all the same pre-existing `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` debt;
  confirmed not a regression via the usual exclusion, exit 0.
- `./mvnw -q -Dtest=ArchitectureTest test` after an independent clean `test-compile`, run twice
  independently — 8/8 both times; `git status --porcelain` on `archunit_store/`/
  `archunit.properties` shows no diff both times (only the deliberate
  `module-dependency-edges-baseline.txt` edit shows in `git status`, which is the reviewed addition
  itself, not a regenerated store).
- `./mvnw -q -Dtest=OpenApiContractExportTest test`, then `diff` against a pre-change copy of
  `packages/contracts/openapi.json` — byte-identical (no routes added this task, as expected).
- `git diff --check` — clean.

### No new Q-6c-N

Fully within Q-6c-3's already-resolved design. One assumption worth naming for whichever session
builds T-6c-12: this service's public API (`executeIdempotent`) takes the trusted site/user as
plain `UUID` parameters, not an `AuthorizedSiteContext` directly — deliberate, since `shared.web`
and `shared.idempotency` are sibling packages with no dependency between them, and threading the
whole context type through would be a needless coupling for what only ever reads two fields.

## Review-driven fix: T-6c-8/T-6c-10 P2 findings (2026-09-13)

Independent review of the T-6c-8/T-6c-9/T-6c-10 work found two P2s. No finding in T-6c-9. Both
fixed same session.

- **P2 — outbox duplicate checks lacked a supporting index.** `EventOutboxRepository
  .existsByEntityId` (T-6c-8's dedupe guard, run on every stock movement's outbox-event creation)
  had no index on `entity_id` — V16 indexes a JSONB payload expression
  (`payload->>'stock_movement_id'`), not this plain column, so every lookup sequentially scanned
  retained outbox history as the table grows. Fixed with `V66__add_event_outbox_entity_id_index
  .sql` (`CREATE INDEX CONCURRENTLY`, paired `.conf` disabling the wrapping transaction, same
  shape as V62 — `event_outbox` is written on every production stock movement and must not be
  locked). New `EventOutboxEntityIdIndexMigrationIT` (2 cases, same standalone-Testcontainers/
  `EXPLAIN`-assertion pattern as `StockMovementSiteIndexMigrationIT`): the index exists with the
  expected definition, and an `entity_id` lookup plans through it under `enable_seqscan=off`.
- **P2 — replay validation ignored command type.** `CommandIdempotencyService.executeIdempotent`
  compared only `requestFingerprint` before replaying a stored result; the same site/user/key with
  a different `commandType` but a coincidentally (or narrowly-computed) matching fingerprint would
  replay the wrong command's result, risking a deserialization failure or silently wrong response
  shape. Fixed: the conflict check now requires both `commandType` and `requestFingerprint` to
  match before replaying; a mismatch on either throws `IdempotencyConflictException` (409), same as
  before. New unit test
  `executeIdempotent_replaySameFingerprintDifferentCommandType_throwsConflict` (same fingerprint,
  different commandType) proves this without invoking the command.
- The review also reiterated that the T-6c-9 consumer-deduplication gap (no existing event-id-
  dedupe mechanism found in forecasting-service/messaging-service) is real, unfinished AC-4
  acceptance work, not closed by recording it. Standing, unchanged from T-6c-9's own entry: carried
  forward as debt, not attempted this pass either — no dedupe mechanism exists in either Python
  service to build a test against, and implementing one there is out of this record's Java-focused
  scope for 6c to date. Recorded again here so it does not read as resolved.

### Verification (actual commands and results, not paraphrased)

- `./mvnw -q clean test-compile` — clean.
- `./mvnw -q -Dtest=CommandIdempotencyServiceTest,CommandIdempotencyServiceIT,
  CommandIdempotencyMigrationIT,EventOutboxServiceCreateEventTest,EventOutboxServicePublishTest,
  AdjustToKafkaIT,EventOutboxEntityIdIndexMigrationIT,ArchitectureTest test` — 47/47 pass (up from
  the reviewer's own 44 by the 3 new tests: 1 commandType-conflict unit test + 2 index-migration
  IT cases).
- `./mvnw -q clean test` (full unrestricted suite) — 362 tests summed across all surefire reports
  (up from 361 by 1 new unit test), `Failures: 0, Errors: 0`.
- `./mvnw -q test -Dtest='*IT'` — 439 tests (up from 437 by 2 new IT methods), 8 failures, all the
  same pre-existing `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` debt; confirmed
  not a regression via the usual exclusion, exit 0.
- `git status --porcelain` on `archunit_store/`/`archunit.properties` — no diff (only the
  pre-existing, already-committed-to-memory `module-dependency-edges-baseline.txt` edit from
  T-6c-10 shows, unrelated to this fix).
- No contract regeneration needed (no route/DTO shape changed).

### No new Q-6c-N

Both are bug fixes within already-scoped T-6c-8/T-6c-10 behavior, not new design decisions.

## 6c implementation (T-6c-11..T-6c-17) (2026-09-13)

Implements T-6c-11 through T-6c-17 from the task list, plus the 6c checkpoint gate. T-6c-0 through
T-6c-10 (already committed as `9115384`) are unchanged by this session.

- **T-6c-11 — v1 read routes.** New `inventory.api.SiteInventoryController`
  (`/api/v1/sites/{siteId}/inventory`): `GET /totals` (optional `productIds`, delegates to
  `InventoryQueries.findInventoryTotalsBySite`), `GET /products/{productId}` (delegates to a new
  `InventoryAggregateService.getInventoryByProductAndSite`, backed by a new
  `LocationInventoryRepository.findByProduct_IdAndSite_Id`; a product with no inventory at this
  site returns `entries: []`, not 404 — absence-of-stock is not absence-of-product, per F-6c-3),
  `GET /locations/{locationId}` (validates the location belongs to the site via
  `LocationService.getLocationById(siteId, id)` before querying, so a foreign-site/unknown
  location 404s instead of silently returning an empty list indistinguishable from "no inventory
  here"; new slim `SiteLocationInventoryEntryDTO`, no catalog metadata per F-6c-9), `GET
  /movements` (`itemId` param selects per-product history, else a filtered audit-log page; new
  `SiteStockMovementResponseDTO` carries `siteAttribution: "UNKNOWN"` for a null-`site` row per
  Q-6c-5, omitted otherwise). Distinct handler/DTO names from
  `InventoryAggregateController`/`StockMovementController` per F-6c-1/the `SiteLocationController`
  precedent. Role requirement mirrors the legacy endpoints (`ADMIN`/`ASSISTANT_MANAGER`/`EMPLOYEE`).
  - Test: `SiteInventoryControllerSecurityIT` (11 cases) — role/site matrix, foreign-site `siteId`
    (403), unknown `siteId` (404), a location belonging to another site (404), a product's
    per-site isolation (site A's row only, not site B's). 11/11 pass.
- **T-6c-12 — v1 mutation routes.** New `inventory.api.SiteInventoryMutationController`:
  `POST /adjustments`, `POST /transfers`, both requiring the `Idempotency-Key` header
  (`@RequestHeader`, no `required = false`) and running through
  `shared.idempotency.CommandIdempotencyService.executeIdempotent` with the trusted
  `AuthorizedSiteContext`'s site/user and a SHA-256 hash of the canonical request JSON as the
  fingerprint (hashed, not the raw JSON, since the entity column has no explicit length override
  and a batch body could exceed a default `varchar(255)`). Same-site enforcement per T-6c-4: new
  site-scoped overloads `StockMovementService.batchAdjustInventory(siteId, request)` (validates
  `request.getLocationId()` belongs to `siteId` via `LocationService.getLocationById`, which
  transitively confines every adjustment line since each is already required to belong to that
  location) and `.transferInventory(siteId, request)`/`.batchTransferInventory(siteId,
  batchRequest)` (validate each transfer's source inventory belongs to `siteId` via a new
  `requireInventoryBelongsToSite` — `requireSameSite` already forces the destination to match, so
  checking the source alone confines the whole transfer). New `shared.correlation
  .IdempotencyKeyFilter` (mirrors `CorrelationIdFilter` exactly) carries the header into
  `IdempotencyKeyContext`'s MDC for the whole request so `EventOutboxService` (already reading
  `IdempotencyKeyContext.current()` since T-6c-8) picks it up automatically; registered in
  `SecurityConfig` right after `CorrelationIdFilter`. Does not enforce the header's presence
  itself — only the v1 mutation routes require it, via their own `@RequestHeader`.
  - Test: `SiteInventoryMutationControllerAtomicityIT` (4 cases, direct bean-method calls +
    manually driven `AuthorizedSiteContextHolder`, no MockMvc — same reasoning
    `StockMovementOutboxAtomicityIT` recorded for not extending `BaseIntegrationTest`): a failure
    inside the guarded command rolls back inventory/movement/outbox **and** leaves no idempotency
    row (Q-6c-3's "no idempotency row survives a failed attempt"); success commits all four
    together; a replay with the same key+body does not re-invoke
    `StockMovementService.batchAdjustInventory` (verified via `@SpyBean`) and produces no
    duplicate effect; a foreign-site inventory id is rejected before any write. 4/4 pass.
    `SiteInventoryMutationControllerSecurityIT` (11 cases, real HTTP via `BaseIntegrationTest`):
    401/403/400(missing header)/201 role matrix, the outbox row's `idempotencyKey` matches the
    HTTP header value end-to-end, an HTTP-level replay doesn't duplicate the effect, a
    fingerprint-conflicting replay returns 409, a foreign-site location/source-inventory 404s
    before any write on both adjustments and transfers, and a same-site transfer succeeds. 11/11
    pass.
- **T-6c-13 — legacy compatibility and deprecation.** New `inventory.api
  .LegacyInventoryDeprecationFilter`/`LegacyInventoryDeprecationConfig`, byte-for-byte mirroring
  `catalog.api.LegacyCatalogDeprecationFilter`/`Config` (`Deprecation`/`Link` headers, no
  `Sunset`), registered for `/api/inventory/*` and `/api/stock-movements/*` only.
  `/api/locations/{id}/inventory` (`LocationInventoryController`) is left alone this pass, per the
  task's own scope note. Legacy endpoints' behavior/response bodies are otherwise untouched.
  - Test: `LegacyInventoryDeprecationHeadersIT` (3 cases) — headers present with the correct RFC
    8941/RFC 8288 shapes and no `Sunset` on `/api/inventory/totals` and
    `/api/stock-movements/audit-log`, absent on `/api/v1/sites/{siteId}/inventory/totals`. The
    totals case asserts headers without asserting a 200: `InventoryTotalsRepository
    .findAllInventoryTotals()`'s native SQL can 500 under the shared H2 `*IT` datasource depending
    on unrelated data committed by other IT classes (order-dependent, confirmed pre-existing and
    unrelated to this filter — see the new open risk below), the same accommodation
    `LegacyCatalogDeprecationHeadersIT` already made for `/api/suppliers`. 3/3 pass.
- **T-6c-14 — contract and client regeneration.** `packages/contracts/openapi.json` regenerated via
  `OpenApiContractExportTest` (590 lines added, 0 removed — six new paths:
  `/api/v1/sites/{siteId}/inventory/{totals,products/{productId},locations/{locationId},
  movements,adjustments,transfers}`; every existing legacy path/operation/schema byte-identical).
  `packages/api-client/src/schema.d.ts` regenerated via `npm run generate` in `packages/api-client`
  (465 lines added, 0 removed). Re-ran `OpenApiContractExportTest` standalone after the fact: the
  diff is stable (no further change).
- **T-6c-15 — stock-state compatibility guard (Row 4).** No production code changed, per the task's
  own "no production code by design." New `architecture.ProductStockStateWriterCallerSetTest`
  (ArchUnit, mirrors `InventoryOperationsCallerSetTest`'s discipline) pins
  `ProductStockStateWriter`'s exact caller set to
  `inventory.application.StockMovementService`/`services.KujiBoxService` — a future caller
  reinterpreting global activity per-site fails this loudly. New
  `inventory.application.StockStateGlobalAggregationIT` (real Postgres, two sites: MAIN and
  SECOND) proves `syncProductTotals`/`calculateTotalInventory` sum quantity **across every site**:
  a product stocked 5 at MAIN and 7 at SECOND yields `products.quantity = 12`,
  `products.isActive = true` after `syncProductTotals`; `calculateTotalInventory` alone returns
  the same cross-site sum (3 + 4 = 7) for a second product. Confirmed by reading
  `sumQuantitiesByProductIds`/`sumQuantityByProductId` (both plain, unfiltered-by-site queries) and
  `calculateTotalInventory`/`syncProductTotals`'s call chain that none of the three take a site
  parameter anywhere — this is pinning already-correct, already-global behavior (F-6c-4's
  intended resolution), not fixing a bug.
  - Test: both new tests above. 1/1 (caller-set) + 2/2 (global aggregation) pass.
- **T-6c-16 — boundary and documentation updates.** `ArchitectureTest` stayed at its expected shape
  except one genuinely new edge the task list itself flagged as the signal to watch for:
  `inventory -> shared` (the new v1 controllers reading `shared.web.AuthorizedSiteContextHolder`
  and, for the mutation controller, `shared.idempotency.CommandIdempotencyService`/`shared
  .correlation.IdempotencyKeyContext`). Added as the file's documented eighth reviewed addition in
  `module-dependency-edges-baseline.txt` — `shared` remains a pure leaf (zero outgoing edges), so
  this cannot introduce a cycle, same reasoning as every prior `<module> -> shared` addition.
  **No `archunit_store/` frozen-store regeneration was needed** (confirmed across two independent
  clean rebuilds, `git status --porcelain` empty both times) — exactly what the task predicted,
  since the v1 controllers depend only on already-approved `inventory.application`,
  `shared.web`/`shared.idempotency`/`shared.correlation`, and `identity`-adjacent context types,
  no other module's `api`. Documented the Row 4 resolution (global `products.quantity`/
  `is_active`, `ProductStockStateWriter`'s pinned caller set), the T-6c-4 same-site transfer
  precondition, and durable command idempotency's table ownership in
  `docs/specs/spring-domain-modular-monolith.md` §9. Recorded the six new v1 routes (and their
  deliberate merge into one `inventory` route family rather than a separate `stock-movements`
  family the original baseline table predicted) in `docs/baseline/api-v1-map.md`'s new
  "Implemented so far" section.
- **T-6c-17 — AC-8 "after" measurement.** New `inventory.application.InventoryEgressAfterIT`,
  same fixed 25-product/3-location/every-third-zero-stock fixture as `InventoryEgressBaselineIT`
  (T-6c-0), same `Measurement`/`DbQuery`/`measureDbEgress` technique, measuring the v1 slim/batched
  totals path and the v1 movements path. Diffed against the **round-2** (final) baseline numbers
  recorded in the "6c implementation (T-6c-0..T-6c-3)" entry above, not either superseded set:
  - `GET /api/v1/sites/{siteId}/inventory/totals`, full 25-product catalog: **1 statement,
    apiRows=25, apiBytes=2715, dbRows=25, projectedDbBytesEstimate=1023** — vs. the legacy baseline's
    **1 statement, apiRows=25, apiBytes=9445, dbRows=25, projectedDbBytesEstimate=4078**. Same row
    count and statement count (both are one unfiltered query over the same 25-row catalog); apiBytes
    drops ~71% (9445 → 2715) and the independent DB-side byte estimate drops ~75% (4078 → 1023) from
    dropping sku/name/imageUrl/category/parentCategory/unitCost/isActive per row (F-6c-9's slim DTO).
  - `GET /api/v1/sites/{siteId}/inventory/totals?productIds=...`, 3 known ids: **1 statement,
    apiRows=3, apiBytes=355, dbRows=3, projectedDbBytesEstimate=150** — proportional to the 3
    requested ids, not the 25-product catalog (12% of the products, 13% of the full-catalog slim
    response's bytes) and not 3 separate requests either (1 statement). This is AC-7's real ceiling
    made concrete: a known-IDs refresh costs proportionally to what changed, not to catalog size.
  - `GET /api/v1/sites/{siteId}/inventory/movements`, page 0/size 20: **1 statement, apiRows=16,
    apiBytes=4683, dbRows=16, projectedDbBytesEstimate=2142**. Not directly comparable to the
    legacy audit-log baseline's numbers (**1 statement, apiRows=1, apiBytes=825, dbRows=1,
    projectedDbBytesEstimate=198**) — the legacy baseline's fixture seeded exactly one audit-log
    row (one batch operation), while this measures a full page of the 16 individual stock-movement
    rows the same fixture's 16 stocked products generate; the two endpoints operate at different
    granularities (audit-log entries vs. raw movements) and this record does not claim they are
    interchangeable. Recorded as an honest data point, not forced into a false before/after pair.
  - The browser/realtime half of AC-8 (coalesced refresh, request/query counts across a live
    session) remains 6e's, per the task list's own scope note — nothing here claims that half is
    measured.
  - Test: the three cases above, 3/3 pass, numbers logged at INFO and copied here per AC-8's
    "observed, not assumed" requirement.

### Checkpoint self-review findings (2026-09-13, before the independent review pass)

Two findings caught while re-reading the T-6c-12 diff critically, both fixed same session:

- **Standards, test-coverage gap:** T-6c-4's cross-site rejection for an *implicit*
  `destinationLocationId` (as opposed to an explicit foreign-site `sourceInventoryId`, which
  T-6c-12's own tests already covered) was untested at the v1 mutation route. Tracing the code
  confirmed the underlying mechanism (`requireSameSite` inside `executeTransfer`) still catches it,
  but only *after* `insertLocationInventoryIfAbsent` has already speculatively inserted the
  destination row — a real write, rolled back by the enclosing `@Transactional`, not "no write at
  all." Added `SiteInventoryMutationCrossSiteDestinationIT` to prove the rollback actually holds
  (no permanent `location_inventory` row at the foreign destination, no `StockMovement` for the
  product, no idempotency record).
- **Standards, false-positive test:** that new test was first written inside
  `SiteInventoryMutationControllerAtomicityIT` (H2, `test` profile). It "passed," but for the wrong
  reason — H2 rejects `insertLocationInventoryIfAbsent`'s native `INSERT ... ON CONFLICT` with a
  syntax error before `requireSameSite` ever runs, so the test wasn't exercising the intended
  mechanism at all. Caught by inspecting *why* it passed, not just that it passed. Fixed by moving
  it to its own class extending `BaseKafkaIntegrationTest` (real Postgres), where it now genuinely
  exercises `requireSameSite`'s rejection path.
- A third issue surfaced only when the full `*IT` sweep ran (not in isolation): that same new
  test's `stockMovementRepository.findAll()).isEmpty()`/`eventOutboxRepository.findAll()
  ).isEmpty()` assertions are unsafe under the shared, cross-class-persistent Testcontainers
  Postgres instance the whole `*IT` suite shares — a residual row from a same-JVM-run class
  running immediately before it made the table non-empty, and AssertJ's own failure-message
  rendering then threw `LazyInitializationException` trying to `toString()` a movement's
  already-detached `AuditLog` association, masking the real cause. Fixed by scoping the assertion
  to this test's own product id (`findByItem_IdOrderByAtDesc`) instead of the whole table,
  matching the tracked-id isolation discipline `InventoryEgressBaselineIT`'s own review fix
  already established for exactly this shared-container hazard.

### Verification (T-6c-11..T-6c-17, actual commands and results, not paraphrased)

- `./mvnw -q clean test-compile` — clean, zero errors, re-run repeatedly through the slice
  including after the self-review fixes above.
- `./mvnw -q clean test` (full unrestricted suite, plain `test` — skips `*IT.java`) — exit 0, all
  green (no new unit-test failures introduced across the whole slice).
- `./mvnw -q test -Dtest='*IT'` (final run, after the self-review fixes) — **474 tests, 8
  failures, 0 errors**, all 8 exactly `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT`
  (the pre-existing order-fragile H2-native-SQL debt already documented above, e.g. under "New
  standing risk discovered this session" earlier in this log) — confirmed not a regression by
  `./mvnw -q test -Dtest='*IT,!AnalyticsControllerSecurityIT,!ForecastControllerSecurityIT'`, exit
  0. An intermediate run (before the assertion-scoping fix above) additionally showed one error in
  the not-yet-fixed `SiteInventoryMutationCrossSiteDestinationIT` and, in an earlier intermediate
  run still, one failure in `LegacyInventoryDeprecationHeadersIT.getInventoryTotals_
  carriesDeprecationHeaders` (a `ClassCastException` in `InventoryTotalsRepository
  .findAllInventoryTotals()`'s native-SQL row mapper casting a joined UUID column) — confirmed the
  SAME class of pre-existing, order-dependent H2-native-SQL fragility (not a regression: this
  session never touched `INVENTORY_TOTALS_SQL` or its mapping code) and fixed the *test*, not
  production code, by relaxing that one assertion to check headers without asserting a 200
  (matching `LegacyCatalogDeprecationHeadersIT`'s existing `/api/suppliers` precedent) rather than
  papering over or "fixing" a native-SQL bug outside this record's scope. Both intermediate issues
  are fully resolved in the final 474-test/8-failure run above.
- `./mvnw -q -Dtest=ArchitectureTest test`, run after two independent `./mvnw -q clean
  test-compile` rebuilds (re-confirmed after the self-review fixes, not just once before them) —
  both green, `git status --porcelain` on `archunit_store/`/`archunit.properties` empty both times
  (no frozen-store regeneration needed, as T-6c-16 predicted).
- `./mvnw -q -Dtest=OpenApiContractExportTest test`, then `git diff --stat
  packages/contracts/openapi.json` — 590 insertions, 0 deletions, stable across a second run.
  `npm run generate` in `packages/api-client` then `git diff --stat
  packages/api-client/src/schema.d.ts` — 465 insertions, 0 deletions.
- Individually, all re-run after the self-review fixes: `SiteInventoryControllerSecurityIT` 11/11,
  `SiteInventoryMutationControllerAtomicityIT` 4/4 (one test moved out, see below),
  `SiteInventoryMutationCrossSiteDestinationIT` 1/1 (new, real Postgres),
  `SiteInventoryMutationControllerSecurityIT` 11/11, `LegacyInventoryDeprecationHeadersIT` 3/3,
  `ProductStockStateWriterCallerSetTest` 1/1, `StockStateGlobalAggregationIT` 2/2,
  `InventoryEgressAfterIT` 3/3.

### New open risk (not fixed, recorded per the ground rules)

- **`InventoryTotalsRepository.findAllInventoryTotals()`'s native SQL is order-fragile under the
  shared H2 `*IT` datasource**, the same class of pre-existing debt as
  `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` (documented earlier in this log
  under "New standing risk discovered this session"): a `ClassCastException` casting a joined
  `category`/`parent` UUID column to `java.util.UUID` (H2 returns `byte[]` for a UUID column in
  some join/JDBC-metadata-caching states), depending on what other IT classes committed to the
  shared instance before it runs. Confirmed pre-existing (this session never touched
  `INVENTORY_TOTALS_SQL` or its row mapper) and confirmed NOT limited to the two previously-named
  classes — `LegacyInventoryDeprecationHeadersIT` newly hit it this session purely from order
  perturbation. `T-6c-0`'s and `T-6c-17`'s own baseline/after measurements never hit this because
  `InventoryEgressBaselineIT`/`InventoryEgressAfterIT` both extend `BaseKafkaIntegrationTest` (real
  Postgres), not the shared H2 `test` profile. Not fixed here (same reasoning as the
  Analytics/Forecast debt: fixing `InventoryTotalsRepository`'s native SQL or giving H2-fragile
  classes an isolated persistence context is a real, separate task, not a side effect of 6c's
  scope). A future session should either make `INVENTORY_TOTALS_SQL`'s UUID columns cast safely
  under H2 or isolate the affected test classes' persistence context.

### No new Q-6c-N

Every choice in T-6c-11..T-6c-17 was already resolved by Q-6c-1 through Q-6c-5 or was a routine
implementation detail (e.g. hashing the idempotency fingerprint, the exact slim-DTO field sets, the
`/inventory` vs. `/stock-movements` v1 route family merge) recorded as an assumption above, not
requiring a new user decision.

## Review-driven fix: AdjustToKafkaIT.outboxPublishesToKafka() first-record assumption — 2026-09-13

Independent review flagged a real, unfixed test-quality gap unrelated to the actor-id/null-site
fixes above: `outboxPublishesToKafka()` grabbed `records.iterator().next()` -- the first Kafka
record returned by a single bounded poll -- and asserted on it directly, instead of finding the
record whose `event_id` matches this test's own `outboxEventId`. The topic is a static, shared
Testcontainer across the whole class (confirmed by reading `createKafkaConsumer()` and the other
tests in the file), so a fresh earliest-offset consumer also sees every earlier test's messages;
grabbing the first one is only correct if this test happens to run before every other test that
publishes to the same topic, which is exam-order-fragile, not a real guarantee.

**Fixed:** the test now polls in a bounded loop (5 attempts x 5s, same shape as the existing
`publishUsesSiteScopedKey_whenCutoverFlagEnabled` test at the bottom of the same file, reused
rather than re-invented) searching every returned record for one whose `event_id` equals the
`outboxEventId` captured earlier in the test, and fails with a clear, descriptive message
(`assertThat(record).as("expected a record with event_id %s", outboxEventId).isNotNull()`) if no
matching record ever arrives, rather than silently asserting on the wrong record or an
`ArrayIndexOutOfBounds`-style failure. The now-unused `pollWithRetry` helper (only ever called
from this one spot) was deleted rather than left dead. No production code changed.

- Verified: `./mvnw -q clean test-compile` clean; `./mvnw -q -Dtest=AdjustToKafkaIT test` exit 0
  (all cases in the class, including the fixed one, real Kafka Testcontainer); `./mvnw -q clean
  test` exit 0; `./mvnw test -Dtest='*IT'` -- 478 tests, 8 failures, identical to the count/set
  before this fix (the same two pre-existing `AnalyticsControllerSecurityIT`/
  `ForecastControllerSecurityIT` classes, confirmed unrelated and unchanged).

### Current handoff (superseding the "Next action" note above)

- Status: **6c complete, including a post-commit independent-review fix round.** T-6c-0 through
  T-6c-17 implemented and verified; checkpoint self-review recorded in `review.md`/`validation.md`
  (commit `34dcfea`). A separate, genuinely independent review of that commit then found two P1s
  the self-review missed — see "Review-driven fix: 6c checkpoint P1 findings (independent review)"
  at the top of `review.md` for full detail. Both confirmed against the actual code and fixed
  same session: (1) the v1 mutation routes persisted a client-supplied `actorId` instead of the
  authenticated principal (`docs/specs/authentication-and-authorization.md#4`) — fixed by moving
  actor-id resolution into the three site-scoped `StockMovementService` overloads
  (`batchAdjustInventory`/`transferInventory`/`batchTransferInventory`, each now takes an explicit
  `actorId` parameter sourced from `context.backendUserId()` and overwrites the request's field
  before delegating), which also surfaced and fixed a self-inflicted idempotency-fingerprint bug
  (excluding `actorId` from the fingerprint's input, since a field the service always overwrites
  cannot legitimately be part of request identity); (2) `GET .../movements?itemId=...` hid
  null-`site` legacy rows contrary to Q-6c-5's "include and label" decision (only the audit-log
  branch of that same endpoint honored it) — fixed with a new
  `StockMovementRepository.findByItem_IdAndSiteOrUnknownOrderByAtDesc` query, leaving the original
  strict method (T-6c-1's general tenant-isolation primitive) untouched. Four new tests added,
  all passing; no other production-code changes.
- Next action: 6d (web adoption) is next per the phase spec's checkpoint sequence, NOT started by
  this record. Before starting 6d: confirm Q-6c-1's production deploy/backfill status if 6d's web
  layer needs to trust `/api/v1/sites/{siteId}/inventory/movements`'s completeness assumption in
  production (P-1's gate); Q-6c-4's Kafka partition-key cutover (verify partition count, coordinate
  a drain, confirm consumer ordering tolerance) is a separately-authorized deploy-time action this
  record explicitly does NOT close, unchanged from T-6c-8's entry above — AC-4 is not "complete"
  against production until that gate is actually passed and recorded. **This fix round's changes
  are not yet committed** — working tree has the fix; commit after this handoff entry is written.
- Surviving decisions: one branch/PR, five logical commits/checkpoints (6a-6e); no per-task
  record/commit/review gate within a checkpoint, only at checkpoint boundaries (per the shared SDD
  review cadence).
- Last verified (this fix round): `./mvnw -q clean test-compile` clean; targeted
  `SiteInventoryMutationControllerAtomicityIT` (8/8), `SiteInventoryControllerSecurityIT`,
  `SiteInventoryMutationControllerSecurityIT`, `LocationInventorySiteScopedQueriesIT`,
  `StockMovementServiceSameSiteTransferTest`, `SiteInventoryMutationCrossSiteDestinationIT`
  (real Postgres) all green; `ArchitectureTest` clean, no `archunit_store` diff; `./mvnw -q clean
  test` exit 0; `./mvnw test -Dtest='*IT'` — 478 tests (474 + 4 new), 8 failures, all still exactly
  the pre-existing `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` debt (confirmed
  via `./mvnw -q test -Dtest='*IT,!AnalyticsControllerSecurityIT,!ForecastControllerSecurityIT'`
  exit 0, 470/470); `OpenApiContractExportTest` — no diff (internal signature changes only).
- Open risks/questions: Q-6c-1 (production deploy/backfill status) and Q-6c-4's cutover remain
  open exactly as before, unchanged by this session. The `InventoryTotalsRepository` H2
  native-SQL order-fragility remains open debt (not a 6c blocker). R-9 (`LocationInventoryController`
  not moved into `inventory.api`) remains open, unchanged, out of 6c's scope.

## Current handoff (6d, superseding the 6c handoff above)

- Status: 6d backend slice (T-6d-be-1..T-6d-be-8) implemented and verified — see "6d
  implementation (T-6d-be-1..T-6d-be-8) (2026-09-14)" below. Web slice (T-6d-1..T-6d-14) not
  started.
- Next action: implement T-6d-1..T-6d-14 (web adoption). T-6d-9 (NOT_ASSIGNED on v1) and T-6d-8's
  batch-transfer piece are now unblocked — the v1 create/delete/batch-transfer routes exist and
  are verified.
- Surviving decisions: batch transfer ships as a new v1 route (not legacy, not fanned into N
  calls); R-9 resolved via a mechanical move — `LocationInventoryService`/`SiteInventoryMutationController`
  gained site-scoped create/delete, no module-graph edge change was needed, confirmed by
  `ArchitectureTest` and an unmodified `module-dependency-edges-baseline.txt`; unknown-site
  movement rows ship labeled, not gated on Q-6c-1; legacy untracked PUT (`LocationInventoryController
  .updateInventory`) is left in place but unused by any new route — deletion is 6e's bookkeeping;
  batch-transfer DTO capped at `@Size(max = 50)` (also tightens the legacy `/api/stock-movements
  /batch-transfer` route, accepted).
- Last verified: `./mvnw -q clean test` (367 run/0 failures, non-IT), `./mvnw test -Dtest='*IT'`
  (508 run/8 failures — all in `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT`, the
  pre-existing unrelated flaky classes recorded below; every inventory-related IT class passed),
  `./mvnw -q clean -Dtest=ArchitectureTest test` run twice independently (both silent/green),
  `OpenApiContractExportTest` run twice (stable), `npm run generate` + `npm run typecheck` in
  `packages/api-client` (clean).
- Open risks/questions: Q-6c-1/Q-6c-4 unchanged (see 6c handoff above). NOT_ASSIGNED
  one-location-per-site and the kuji-child/CUSTOM-parent read-filtering discrepancy are now
  resolved, not open — see T-6d-be-6 below (schema-enforced uniqueness; filtering discrepancy
  proven real and now the intended behavior of the v1 route, not a gap). Pre-existing
  `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` flakiness (500s under the shared
  `*IT` sweep) is unrelated debt, unchanged by this slice.

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

## 6d planning — web adoption worksheet (2026-09-14)

6c is complete and merged to `dev` (PR #326, `c6473a7`). `refactor/inventory-stock` is at the same
commit. 6d ("Web adoption") scope per spec.md's checkpoint table: migrate inventory reads/mutations
to v1 site-scoped routes, restore scoped Products inventory, prove rendered behavior and
site-switch isolation (AC-6). Planning done in two passes — a web-adoption baseline/task list
(`planner`) and a backend design pass (`mirai-spring-architect`) for two gaps the web baseline
surfaced that have no v1 route today.

### User decisions (all material, all confirmed 2026-09-14)

- **Batch transfer:** route the already-implemented `StockMovementService.batchTransferInventory`
  as a new v1 endpoint in 6d, rather than leaving it on the legacy route or fanning it into N
  single transfers (which would lose atomicity and turn one audit entry into N).
- **R-9 (`LocationInventoryController`'s `catalog.api` coupling):** resolve now, inside 6d, rather
  than defer further. Flagging for the record: neither `docs/plans/enterprise-modernization.md`
  nor the domain-modular-monolith migration plan ever assigned R-9 an owning phase — it had only
  been carried forward as open debt through 6a/6b/6c's "not a blocker for this checkpoint" notes
  with no scheduled resolution. 6d closes it.
- **Unknown-site movement rows:** ship v1 movement history with an explicit "unknown site" marker
  for `siteAttribution: "UNKNOWN"` rows (Q-6c-5's "include and label," now actually labeled
  client-side) rather than blocking on confirming Q-6c-1's production backfill/deploy status first.
- **Dropping the untracked PUT:** confirmed. Legacy `PUT /api/locations/{id}/inventory/{invId}`
  performs a silent absolute quantity set with no `StockMovement`/audit/outbox — a pre-existing
  AC-4 violation. R-9's v1 resolution removes it; all location-inventory edits go through the
  audited adjustment endpoint instead. No dedicated "set exact quantity, but audited" route.
- **Batch-transfer size cap:** confirmed `@Size(max = 50)` on the shared
  `BatchTransferInventoryRequestDTO`, matching `BatchAdjustStockRequestDTO`. This also tightens the
  legacy `/api/stock-movements/batch-transfer` contract; no known caller sends near 50, and it
  bounds per-transfer pessimistic-lock footprint on the single 512 MB-heap deployment.

### Backend design: R-9 resolution and batch-transfer route

Confirmed by re-reading the actual code (not assumed): the `inventory.api -> catalog.api` coupling
that blocked R-9 in 6a's T-6 is **read-side only** — `LocationInventoryResponseDTO.item`
(`catalog.api.ProductSummaryDTO`) and `LocationInventoryMapper`. `InventoryRequestDTO` (the write
body) imports no catalog type. So R-9 is a mechanical move plus a slim-response-shape change (the
same discipline T-6c-11/12 already forced on every other v1 inventory route), **not** an R-3-style
port inversion — no new `catalog.application` read contract, no module-graph edge, no
`module-dependency-edges-baseline.txt` change.

- The v1 **read** side for a real location is already shipped: `GET
  /api/v1/sites/{siteId}/inventory/locations/{locationId}` (T-6c-12). Nothing to add there.
- `GET /api/locations/{id}/inventory/{inventoryId}` has no web caller anywhere — delete the client
  function in the web slice, no v1 counterpart needed.
- New v1 write routes on the existing `SiteInventoryMutationController`:
  - `POST /api/v1/sites/{siteId}/inventory/locations/{locationId}/items` — create, returns the
    existing `SiteLocationInventoryEntryDTO` (no catalog metadata; web does the client-side join
    already required by every other v1 read). New `CreateLocationInventoryRequestDTO`
    (`productId`, `quantity` `@Min(1)`, optional `reason`/`intakeUnit`/`intakeQty`, no `actorId`
    field — actor comes from `AuthorizedSiteContext`).
  - `DELETE /api/v1/sites/{siteId}/inventory/locations/{locationId}/items/{inventoryId}` — 204,
    `ADMIN`/`ASSISTANT_MANAGER` only (method-level override of the class default), matching legacy.
  - `POST /api/v1/sites/{siteId}/inventory/transfers/batch` — 201/`Void`, same
    `BatchTransferInventoryRequestDTO` the legacy route uses, `Idempotency-Key` required.
- New site-scoped `LocationInventoryService` overloads (`addInventory`/`deleteInventory` taking
  `siteId`/`actorId` first, mirroring `StockMovementService`'s T-6c-4 pattern): resolve the
  location/row through a site-qualified repository lookup (404 before any write on a foreign-site
  location or inventory row, 400 on a path/row location mismatch), then delegate to the existing
  un-scoped methods — which stay untouched so the legacy controller keeps working until 6e deletes
  it.
- **NOT_ASSIGNED needs no special route shape.** It is an ordinary per-site `storage_locations` row
  (code `NOT_ASSIGNED`, `infra/migrations/007-seed-standard-storage-locations.sql`) with an
  ordinary `locations` row under it. `GET /api/v1/sites/{siteId}/locations?storageLocation=NOT_ASSIGNED`
  (already shipped) resolves the site's NA location; every read/write after that is an ordinary
  per-location call. This retires both the virtual-ID indirection and the site-blind
  `cachedNALocationId` module cache in `apps/web/src/lib/api/inventory.ts` (real multi-site bug:
  the cache is not keyed by site).
  - Caveat 1 (to prove, not assume, T-6d-be-6): exactly one location under NOT_ASSIGNED per site.
    The web already assumes this; it has never been enforced. If false, add a
    storage-location-scoped v1 read before the web slice depends on it.
  - Caveat 2 (real, confirmed by reading the repository): `LocationInventoryRepository
    .findByStorageLocation_Id` (today's NOT_ASSIGNED read path) applies no `parent IS NULL`/
    non-`CUSTOM`-kuji-parent exclusion, unlike `findByLocation_Id` and its site-scoped twin. Moving
    NOT_ASSIGNED onto the per-location v1 route changes observable behavior — kuji-child/`CUSTOM`
    -parent rows at NA stop appearing. This aligns behavior with what the UI's own code comment
    already (incorrectly) claims is already true; T-6d-be-6 proves it against real data before the
    web slice ships it as fact.
- Found and must fix before the batch-transfer route ships: `SiteInventoryMutationController
  .fingerprint()` strips `actorId` only at the top level of the request body. For
  `BatchTransferInventoryRequestDTO`, `actorId` is nested inside each `transfers[]` element, so an
  ignored-but-present client `actorId` would leak into the idempotency fingerprint and a legitimate
  retry could get a spurious 409. Fix: recursive `actorId` stripping (walk the JSON tree, not just
  the top level) before hashing.

**Backend task list** (T-6d-be-N, ordered, each independently verifiable; suggested commit split
T-6d-be-1..3 / T-6d-be-4..5 / T-6d-be-6..8, mirroring how 6c batched T-6c-11..17):

- T-6d-be-1: site-scoped `LocationInventoryService.addInventory`/`deleteInventory` overloads. No
  route yet. Unit tests (foreign-site 404 before write, path/row mismatch 400) + one real-Postgres
  case in the `LocationInventorySiteScopedQueriesIT` family.
- T-6d-be-2: v1 create route (`CreateLocationInventoryRequestDTO` + `POST .../items`). Extend
  `SiteInventoryMutationControllerSecurityIT`/`...AtomicityIT` — role matrix, missing-header 400,
  foreign-site 404, replay creates exactly one row, fingerprint conflict 409, atomic
  inventory+movement+audit+outbox+idempotency commit/rollback.
- T-6d-be-3: v1 delete route (`DELETE .../items/{inventoryId}`), ADMIN/ASSISTANT_MANAGER only.
  Same IT family — explicit 403 for EMPLOYEE, 204 for the two allowed roles, foreign-site 404,
  location/inventory mismatch 400, replay does not double-emit.
- T-6d-be-4: recursive `actorId` stripping in the idempotency fingerprint (must land before
  T-6d-be-5). Unit test: nested-`actorId`-only difference hashes identically; a real field
  difference still hashes differently; existing top-level cases still hold.
- T-6d-be-5: v1 batch-transfer route + `@Size(max = 50)` on the shared DTO. Role matrix,
  missing-header 400, foreign-site source 404 before any write, 51-element batch 400, replay is a
  no-op, atomic multi-transfer rollback on a mid-batch failure, one deadlock-safety concurrency
  case (two concurrent batches touching the same rows in opposite order).
- T-6d-be-6: NOT_ASSIGNED read-parity IT (seed a root product, a kuji-child, and a `CUSTOM` kuji
  parent at one NA location; assert the v1 per-location read returns only the root product) +
  one-NA-location-per-site assertion recorded in validation.md. If the one-NA-location assumption
  is false in real data, stop and add a storage-location-scoped v1 read before web depends on it.
- T-6d-be-7: legacy deprecation headers for `/api/locations/{id}/inventory*` and
  `/api/storage-locations/{id}/inventory` (extend `LegacyInventoryDeprecationConfig`; url-pattern
  can't express the mid-path wildcard, so register at `/api/locations/*` / `/api/storage-locations/*`
  and gate on the trailing `/inventory` segment so `sites`' own `LocationController` routes aren't
  mismarked). Extend `LegacyInventoryDeprecationHeadersIT`.
- T-6d-be-8: regenerate `packages/contracts/openapi.json` (`OpenApiContractExportTest`, stable
  across a second independent run) and `packages/api-client`. Expect four new paths, zero removed
  paths, and exactly one intentional legacy-schema delta (`BatchTransferInventoryRequestDTO`'s new
  `maxItems: 50`) — call it out explicitly in validation.md rather than letting it hide in the diff.

`LocationInventoryController`/`LocationInventoryMapper`/`LocationInventoryResponseDTO`/
`InventoryRequestDTO` stay in their legacy packages and stay functional through 6d; final deletion
is 6e's bookkeeping once no web caller remains, not part of closing R-9.

### Web baseline and task list

Full per-call-site baseline (every legacy inventory/stock-movement/location API function, its v1
disposition, every dependent hook/component, realtime subscription scoping, and the Kuji-gate
mechanism) and the ordered `T-6d-0..T-6d-14` web task list produced by the planning pass are
recorded verbatim in this checkpoint's planning transcript; summarized here for traceability and
expanded into the Task record as each task starts, per the established convention:

- T-6d-0 (superseded by the user decisions above — G-1/G-2/G-3 are now all resolved: batch
  transfer and R-9 both get v1 routes per the backend design; nothing stays silently on legacy).
- T-6d-1: typed v1 inventory client functions (`apps/web/src/lib/api/site-inventory.ts`).
- T-6d-2: idempotency-key strategy for the v1 mutations (generated once per user-initiated attempt).
- T-6d-3: site-qualified query keys + the late-old-site-result rejection mechanism; audit every
  `invalidateQueries`/`setQueriesData` call site over the affected prefixes; fix the
  `setQueriesData<Product[]>({queryKey:["products"]})` prefix collision with the 5d
  `["products", siteId, "site"]` key.
- T-6d-4: restore scoped quantity/status on the Products list from `getSiteInventoryTotals`; drop
  `showQuantity={false}` and the "available after inventory is migrated per site" affordance.
- T-6d-5: product detail inventory on v1 (`getSiteProductInventory`).
- T-6d-6: location-detail inventory on v1, with a shared client-side catalog join helper.
- T-6d-7: stock adjust on v1 (drop client-sent `actorId`).
- T-6d-8: stock transfer + batch transfer on v1.
- T-6d-9: NOT_ASSIGNED inventory on v1 (now unblocked by R-9's resolution — the site-keyed
  `GET .../locations?storageLocation=NOT_ASSIGNED` replaces both the virtual-ID indirection and the
  site-blind `cachedNALocationId` cache) and the create/delete flows (`product-form.tsx`'s initial
  stock, the not-assigned/location mutations) onto the new v1 create/delete routes.
- T-6d-10: movement history on v1, rendering `siteAttribution: "UNKNOWN"` rows with an explicit
  marker rather than hiding them.
- T-6d-11: site-scope the realtime inventory refresh (`stock_movements.site_id` filtering, ignore
  foreign-site events, treat null-site as possibly-relevant, re-subscribe on site change). The
  org-wide `db-changes` broadcast channel carries no `siteId` and is left as safe-but-over-invalidating
  for 6d; adding `siteId` to `SupabaseBroadcastService`'s payload is 6e's AC-7 coalescing work.
- T-6d-12: site-blind residual audit (`getLocationsWithCounts`, `dashboard.ts`'s `getAuditLog`,
  kuji dialogs' inventory reads — each recorded with its owning phase).
- T-6d-13: AC-6 rendered-test sweep (detail state, role-dependent controls, stock workflows, site
  switching asserting whole-page state not just request URLs, unresolved/error states, late-result
  rejection), following phase-5d's `products/__tests__/page.test.tsx` pattern.
- T-6d-14: confirm the non-MAIN Kuji-unavailable gate (`kuji-tab-panel.tsx`) is untouched by T-6d-4's
  hook refactor — existing `kuji-tab-panel.test.tsx` stays green unmodified.

Recorded assumptions (not escalated, per AGENTS.md's "ask only material decisions" rule): no new
client-side RBAC gates on adjust/transfer (v1 controllers already allow EMPLOYEE, matching today's
UI); movement-history `actorName` degrades to the existing `formatId(actorId)` fallback rather than
adding a users join; broadcast-channel over-invalidation stays as-is for 6d (correctness-safe,
6e's coalescing scope); `getLocationsWithCounts` stays site-blind (`sites` module territory,
recorded as residual); site switching continues to be exercised only by mocking `useCurrentSite`,
no switcher UI is built in 6d.

### Next action

Implement T-6d-be-1 through T-6d-be-8 (backend) first — the web task list's T-6d-9 depends on
R-9's resolution and T-6d-8's batch-transfer depends on T-6d-be-4/5. Then implement T-6d-1 through
T-6d-14 (web).

## 6d implementation (T-6d-be-1..T-6d-be-8) (2026-09-14)

Implemented the full backend slice per the design above, in order, TDD where a meaningful local
test existed (Mockito unit tests for the service overloads, real-H2-backed IT for repository/DB
behavior, MockMvc security IT for the HTTP surface, direct-bean atomicity IT for transactional
proof — matching 6c's established split). All commands below ran against JDK 21 via `./mvnw`.

### T-6d-be-1 — site-scoped `LocationInventoryService` overloads

- Changed: `inventory/application/LocationInventoryService.java` — added `addInventory(siteId,
  actorId, locationId, productId, quantity, reason, intakeUnit, intakeQty)` (validates the
  location via `LocationService.getLocationById(siteId, locationId)`, 404s before any write, then
  delegates to the existing un-scoped method) and `deleteInventory(siteId, actorId, locationId,
  inventoryId, reason)` (resolves the row via `LocationInventoryRepository.findByIdAndSite_Id`,
  404 for a foreign-site/unknown row, then a location/row-ownership check that 400s on a mismatch,
  then delegates to the existing un-scoped method). Neither overload changes the un-scoped methods
  the legacy controller still calls.
- Tests: `services/LocationInventoryServiceTest.java` — new `SiteScopedAddInventoryTests`/
  `SiteScopedDeleteInventoryTests` nested classes (Mockito): site-ownership delegation,
  foreign-site 404 before any write, location/row mismatch 400 before any write.
  `inventory/infrastructure/LocationInventorySiteScopedQueriesIT.java` — 4 new real-H2-backed
  cases (`addInventory_siteScoped_rejectsForeignSiteLocation_beforeAnyWrite`,
  `addInventory_siteScoped_createsInventory_whenLocationBelongsToSite`,
  `deleteInventory_siteScoped_rejectsForeignSiteInventory_beforeAnyWrite`,
  `deleteInventory_siteScoped_rejectsLocationRowMismatch`) exercising the real service bean
  against a real database round trip, not mocked repositories.
- Result: pass. `./mvnw -q -Dtest=LocationInventoryServiceTest,LocationInventorySiteScopedQueriesIT
  test` — 32 run / 0 failures.

### T-6d-be-2/T-6d-be-3 — v1 create/delete routes on `SiteInventoryMutationController`

- Changed: new `inventory/api/CreateLocationInventoryRequestDTO.java` (`productId`, `quantity
  @Min(1)`, optional `reason`/`intakeUnit`/`intakeQty`, no `actorId`). `SiteInventoryMutationController`
  gained `LocationInventoryService` as a dependency and two handlers: `POST
  .../inventory/locations/{locationId}/items` (201, returns `SiteLocationInventoryEntryDTO`,
  idempotent via `CommandIdempotencyService`) and `DELETE
  .../inventory/locations/{locationId}/items/{inventoryId}` (204, `@PreAuthorize("hasAnyRole('ADMIN',
  'ASSISTANT_MANAGER')")` overriding the controller's class-level EMPLOYEE-inclusive default,
  matching the legacy `LocationInventoryController.deleteInventory` restriction). Both require the
  `Idempotency-Key` header and derive actor identity from `AuthorizedSiteContext`, never a
  client-supplied value.
- Tests: `controllers/security/SiteInventoryMutationControllerSecurityIT.java` — 8 new cases:
  missing-key 400, EMPLOYEE 201 + persisted row (create), foreign-site location 404 + no write
  (create), EMPLOYEE 403 (delete, below ADMIN/ASSISTANT_MANAGER), ADMIN 204 + row removed
  (delete), foreign-site inventory 404 + no write (delete), location/row mismatch 400 + no write
  (delete). `inventory/api/SiteInventoryMutationControllerAtomicityIT.java` — 5 new direct-bean
  cases: create commits inventory+movement+outbox+idempotency together, create replay is a no-op
  (no second row/movement), create foreign-site rejects before any write, delete commits
  removal+movement+outbox+idempotency together, delete replay does not double-emit, delete
  location/row mismatch rejects with no write.
- Result: pass. `./mvnw -Dtest=SiteInventoryMutationControllerSecurityIT,
  SiteInventoryMutationControllerAtomicityIT test` — 30 run / 0 failures (at this point in the
  sequence, before T-6d-be-5's additions).

### T-6d-be-4 — recursive `actorId` stripping in the idempotency fingerprint

- Changed: `SiteInventoryMutationController.fingerprint()` now builds a Jackson `JsonNode` tree
  (`objectMapper.valueToTree`) and walks it recursively (`stripActorIdRecursively`), removing every
  `actorId` field at any object depth — objects and array elements alike — instead of the prior
  top-level-only `Map.remove("actorId")`. `fingerprint()` was widened from `private` to
  package-private so a focused unit test could call it directly without exercising the whole
  idempotency/HTTP stack.
- Tests: new `inventory/api/SiteInventoryMutationControllerFingerprintTest.java` (constructs the
  controller with mocked collaborators): nested-`actorId`-only difference (inside a
  `BatchTransferInventoryRequestDTO.transfers[]` element) hashes identically; a real nested field
  difference still hashes differently; the existing top-level `TransferInventoryRequestDTO` cases
  (actorId-only same, real-field different) still hold.
- Result: pass. `./mvnw -Dtest=SiteInventoryMutationControllerFingerprintTest test` — 4 run / 0
  failures.

### T-6d-be-5 — v1 batch-transfer route + `@Size(max = 50)`

- Changed: `BatchTransferInventoryRequestDTO.transfers` gained `@Size(min = 1, max = 50)`
  (previously `min = 1` only, effectively unbounded) — a shared DTO, so this also tightens the
  legacy `/api/stock-movements/batch-transfer` route per the user-confirmed decision.
  `SiteInventoryMutationController` gained `POST .../inventory/transfers/batch` (201, idempotent,
  delegates to the already-implemented `StockMovementService.batchTransferInventory(siteId,
  actorId, request)` — no new service-layer logic). The underlying atomicity/lock-ordering/deadlock
  proofs for `batchTransferInventory` itself are unchanged and already covered by
  `StockMovementServiceConcurrentBatchTransferCrossedDestinationsIT` and
  `StockMovementServiceMixedAdjustTransferLockOrderIT` at the service layer — this route is a thin
  HTTP/idempotency wrapper around that existing, already-proven method, so this task's tests focus
  on what's new at this layer (routing, auth, idempotency, size cap, atomicity of the
  wrapper+service call together) rather than re-running the full concurrency matrix through HTTP.
- Tests: `SiteInventoryMutationControllerSecurityIT.java` — 5 new cases: missing-key 400, USER
  role 403, foreign-site source 404 + no write, same-site 201 + replay is a no-op (destination
  quantity unchanged on the second call), a 51-element batch 400 (bean-validation `@Size` rejects
  before the controller body runs). `SiteInventoryMutationControllerAtomicityIT.java` — 3 new
  direct-bean cases: successful batch commits both movements+outbox+idempotency together, a
  mid-batch failure (second transfer's quantity exceeds source stock) rolls back both lines
  together (first transfer's effect is not partially applied), foreign-site source rejects before
  any write.
- Result: pass. `./mvnw -Dtest=SiteInventoryMutationControllerSecurityIT,
  SiteInventoryMutationControllerAtomicityIT,SiteInventoryMutationControllerFingerprintTest test`
  — 42 run / 0 failures.

### T-6d-be-6 — NOT_ASSIGNED read-parity IT + one-NA-location-per-site assumption

- Changed: no production code (proof-only task, as scoped).
- Tests: new `inventory/application/NotAssignedInventoryReadParityIT.java`:
  - `v1Read_excludesKujiChildAndCustomKujiParentRows_legacyReadDoesNot` seeds one root product, one
    kuji-child product (non-null `parent`), and the CUSTOM kuji parent product itself at one real
    NOT_ASSIGNED location, then asserts the legacy `findByStorageLocation_Id` returns all three
    rows (today's actual, unfiltered behavior) while the v1 route's
    `InventoryQueries.findByLocationIdAndSite` (backed by `findByLocation_IdAndSite_Id`'s existing
    `p.parent IS NULL AND (p.kujiType IS NULL OR p.kujiType <> CUSTOM)` filter) returns only the
    root product — confirms the design note's flagged discrepancy is real, and that moving the web
    NOT_ASSIGNED flow onto the v1 route (T-6d-9) is a genuine, intentional behavior change, not a
    no-op.
  - `secondNotAssignedStorageLocationForSameSite_violatesUniqueConstraint` proves the
    one-NOT_ASSIGNED-location-per-site assumption is schema-enforced, not merely conventional:
    `StorageLocation` carries an entity-level `@UniqueConstraint(columnNames = {"site_id",
    "code"})`, so a second `NOT_ASSIGNED`-coded storage location for the same site fails with
    `DataIntegrityViolationException`. **Assumption outcome: confirmed safe** — the design's "very
    likely fine" held; no design change or escalation needed.
- Result: pass. `./mvnw -Dtest=NotAssignedInventoryReadParityIT test` — 2 run / 0 failures.

### T-6d-be-7 — legacy deprecation headers for sites-shaped inventory routes

- Changed: new `inventory/api/LegacyLocationInventoryDeprecationFilter.java` — gates on the
  request path actually ending in an `inventory` segment (`^/api/locations/[^/]+/inventory(?:/.*)?$`
  or `^/api/storage-locations/[^/]+/inventory$`) before setting the `Deprecation`/`Link` headers,
  since Spring's `url-pattern` syntax can't express the mid-path `{id}/inventory` segment directly
  and a naive `/api/locations/*` registration would also catch `sites`' own `LocationController`
  routes at plain `/api/locations/{id}`. `LegacyInventoryDeprecationConfig` registers this filter
  at the `/api/locations/*`/`/api/storage-locations/*` wildcard level, alongside the existing
  `LegacyInventoryDeprecationFilter` registration (unchanged).
- Tests: `LegacyInventoryDeprecationHeadersIT.java` — 4 new cases: `GET
  /api/locations/{id}/inventory` carries the headers, `GET /api/storage-locations/{id}/inventory`
  carries the headers, `GET /api/locations/{id}` (sites' own route, no `/inventory` suffix) does
  NOT carry the headers.
- Result: pass. `./mvnw -Dtest=LegacyInventoryDeprecationHeadersIT test` — 6 run / 0 failures.

### T-6d-be-8 — regenerate `packages/contracts/openapi.json` and `packages/api-client`

- Changed: `packages/contracts/openapi.json` (regenerated via `OpenApiContractExportTest`, run
  twice independently — identical output both times, confirming stability) and
  `packages/api-client/src/schema.d.ts` (regenerated via `npm run generate` in `packages/api-client`,
  which shells out to `openapi-typescript`).
- Diff against the pre-change contract (scripted `json` comparison, not eyeballed):
  - **Paths added (3):** `POST/DELETE` on `/api/v1/sites/{siteId}/inventory/locations/{locationId}/items`
    and `/api/v1/sites/{siteId}/inventory/locations/{locationId}/items/{inventoryId}` (create is
    `POST` on the first path, delete is `DELETE` on the second), and `POST
    /api/v1/sites/{siteId}/inventory/transfers/batch`. **Deviation from the design note's estimate
    of "four new paths":** the actual count is 3 distinct path templates (create/delete share one
    `.../items` vs. `.../items/{inventoryId}` split, plus the batch-transfer path) — verified by a
    scripted set-diff over `paths.keys()`, not miscounted by hand.
  - **Paths removed:** none.
  - **Schemas added:** `CreateLocationInventoryRequestDTO` (new DTO, expected).
  - **Schemas removed:** none.
  - **Modified existing schemas:** exactly one — `BatchTransferInventoryRequestDTO.transfers.maxItems`
    changed from `2147483647` (framework default for a `@Size(min=1)` with no explicit max) to
    `50`. No other field of any existing schema changed.
- Result: pass. `npm run typecheck` in `packages/api-client` — clean, no new type errors from the
  regenerated schema.

### Full-suite verification (checkpoint-slice gate)

- `./mvnw -q clean test` (non-IT unit/component tests): **367 run / 0 failures / 0 errors.**
- `./mvnw test -Dtest='*IT'` (every integration test, since `mvn test` alone skips `*IT.java` —
  see the project's recorded `inventory-service-test-commands` note): **508 run / 8 failures.** All
  8 failures are in `AnalyticsControllerSecurityIT` (6 cases) and `ForecastControllerSecurityIT`
  (2 cases), pre-existing and recorded as unrelated flaky debt at 6c's close (500s on
  `/api/analytics/*`/`/api/forecasts` under the shared `*IT` sweep, not touched by this session).
  Every inventory-related IT class in this run passed, including
  `SiteInventoryMutationControllerSecurityIT` (23), `SiteInventoryMutationControllerAtomicityIT`
  (15), `LocationInventorySiteScopedQueriesIT` (12), `NotAssignedInventoryReadParityIT` (2), and
  `LegacyInventoryDeprecationHeadersIT` (6).
- `./mvnw -q clean -Dtest=ArchitectureTest test`, run twice as two independent clean rebuilds: both
  silent/green (no violations). `module-dependency-edges-baseline.txt` was not touched, confirming
  R-9's resolution needed no new module-graph edge, as the design predicted.
- `OpenApiContractExportTest` run twice independently: stable, identical `openapi.json` output both
  times.
- `npm run generate` + `npm run typecheck` in `packages/api-client`: clean.

### Deviations from the design

- The design's estimate of "four new paths" in the OpenAPI diff was off by one — the actual,
  verified count is three distinct path templates (see T-6d-be-8 above). This is a
  counting/estimate correction, not a scope or behavior deviation; every route the design specified
  (create, delete, batch-transfer) was implemented exactly as designed.
- No other deviations. `LocationInventoryController`/`LocationInventoryMapper`/
  `LocationInventoryResponseDTO`/`InventoryRequestDTO` were not touched or deleted, per scope.
  `module-dependency-edges-baseline.txt` was not touched, per scope.

### Nothing flagged for user input

T-6d-be-6's one-NA-location-per-site assumption came back confirmed safe (schema-enforced), so no
escalation was needed there. No other material product, security, data-loss, or
irreversible-deployment decision arose during this slice; all choices already made by the user in
the 6d planning worksheet were followed as recorded.

## Review-driven fix: 6d backend slice findings (2026-09-14)

An independent review of the committed-but-not-yet-merged 6d backend slice (T-6d-be-1..T-6d-be-8,
above) found four "Act on" findings. All four addressed this session; full disposition list
transcribed into `review.md`'s new "6d backend slice (T-6d-be-1..T-6d-be-8) — independent review,
2026-09-14" entry, commands/results in `validation.md`'s matching entry. Summary:

- **Finding 1 — delete route's idempotency fingerprint non-deterministic across JVM restarts.**
  `SiteInventoryMutationController`'s delete handler fingerprinted
  `java.util.Map.of("locationId", locationId, "inventoryId", inventoryId)`; `Map.of`'s iteration
  order is randomized per JVM (seeded from `System.nanoTime()`), so the same logical delete command
  hashed differently across JVM restarts/redeploys, risking a spurious 409 on a legitimate retry
  inside the idempotency table's 7-day window. **Fixed:** a package-private
  `DeleteInventoryFingerprintKey(UUID locationId, UUID inventoryId)` record replaces the map — a
  record's component order is fixed by declaration, so the hash is stable across JVM runs. Grepped
  the file for every other `Map.of`-as-fingerprint-carrier use; this was the only instance.
- **Finding 2 — create route's fingerprint omitted `locationId`, letting a key-reuse across
  locations silently "succeed."** `fingerprint(request)` was body-only; the same
  `Idempotency-Key` + identical body + a different `locationId` path variable matched on
  `commandType`+`requestFingerprint` and silently returned the first location's stored 201, writing
  nothing at the second. **Fixed:** a package-private `CreateInventoryFingerprintKey(UUID
  locationId, CreateLocationInventoryRequestDTO request)` record fingerprints `locationId` together
  with the body.
- **Finding 3 — T-6d-be-6's "confirmed safe" claim wasn't supported by the test that was written.**
  The existing `secondNotAssignedStorageLocationForSameSite_violatesUniqueConstraint` test proves
  only `storage_locations(site_id, code)` uniqueness, not uniqueness of `locations` rows beneath a
  site's NOT_ASSIGNED storage location (`locations` only carries `UNIQUE(storage_location_id,
  location_code)`). **Resolved to the extent real data allows:** ran a read-only query against the
  project's actual, live Supabase database this session could reach
  (`mcp__supabase__execute_sql`) — see validation.md's "Finding 3 evidence" for the exact query.
  Result: of the two real sites, `MAIN` has exactly one `locations` row under its NOT_ASSIGNED
  storage location; `SECOND` has no NOT_ASSIGNED storage location seeded yet. No violation found in
  real data, but the invariant is **not schema-enforced** — recorded as an explicit open risk below
  and in validation.md, not marked "confirmed safe." `NotAssignedInventoryReadParityIT`'s Javadoc
  corrected to state only what it actually proves.
- **Finding 4 — `review.md`/`validation.md` had no 6d entries.** Both files now carry a 6d
  backend-slice entry (review.md: the transcribed independent-review disposition list with
  findings 1/2/3 marked fixed; validation.md: this session's commands/results/evidence).

New tests (all failing-before/passing-after, verified by temporarily reverting each fix and
re-running the new test to confirm it actually catches the bug, then restoring the fix):
`SiteInventoryMutationControllerFingerprintTest.deleteFingerprint_forFixedLocationAndInventoryId_matchesPinnedHash`
(pins a hard-coded SHA-256 literal — a self-re-deriving test would not catch an ordering
regression), `.deleteFingerprint_isStableAcrossRepeatedCalls_forTheSameLogicalCommand`,
`.createFingerprint_includesLocationId_soSameBodyDifferentLocationHashesDifferently`,
`.createFingerprint_forFixedLocationAndBody_matchesPinnedHash`; and
`SiteInventoryMutationControllerSecurityIT.createItem_sameKeySameBodyDifferentLocation_returns409AndDoesNotReuseFirstLocationResponse`.

**Result:** `./mvnw -q clean test-compile` clean; `SiteInventoryMutationControllerFingerprintTest`
8/8; the full touched-class set (`SiteInventoryMutationControllerSecurityIT` 24,
`...AtomicityIT` 15, `...FingerprintTest` 8, `LocationInventorySiteScopedQueriesIT` 12,
`NotAssignedInventoryReadParityIT` 2, `LegacyInventoryDeprecationHeadersIT` 6,
`LocationInventoryServiceTest` 18) all green; `./mvnw -q clean test` — 371+ run (Surefire
text-summary undercount for `LocationInventoryServiceTest`'s nested-only tests, a pre-existing
harness quirk unrelated to this fix — its XML report shows `tests="18"`), 0 failures; `./mvnw test
-Dtest='*IT'` — 509 run (up 1 from 508), 8 failures, all exactly the pre-existing
`AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` set, confirmed by name-for-name
comparison against the prior slice's own recorded failures — no new failures, no inventory-related
IT failed.

**Disposition:** findings 1, 2, and 4 fixed and re-verified. Finding 3 resolved to the extent real
data allows: no violation found, but recorded as an **open risk, not closed** — the
one-NA-location-per-site invariant is empirically true in the organization's real data today but is
not enforced by the schema, so a future write could violate it silently. This does not block T-6d-9
(the real-data check found no counterexample, so proceeding is reasonable), but T-6d-9 and any
future session touching `LocationService.createLocation`/`getNotAssignedLocation` should treat this
as monitored debt, not a proven guarantee — a partial-unique index or application-level guard would
close it properly. `apps/web` was not touched by this session, per scope; no T-6d-1..T-6d-14 web
task was started.

## Current handoff (6d review-driven fix, superseding the plain-6d handoff above)

- Status: 6d backend slice (T-6d-be-1..T-6d-be-8) implemented, independently reviewed, and all four
  review findings addressed this session (see "Review-driven fix: 6d backend slice findings
  (2026-09-14)" above). Backend slice is done and reviewed. Web slice (T-6d-1..T-6d-14) not started.
- Next action: implement T-6d-1..T-6d-14 (web adoption). T-6d-9 (NOT_ASSIGNED on v1) and T-6d-8's
  batch-transfer piece remain unblocked — the v1 create/delete/batch-transfer routes exist, are
  verified, and their idempotency-fingerprint defects (findings 1/2) are fixed.
- Surviving decisions: unchanged from the plain-6d handoff above (batch transfer as a new v1 route;
  R-9 resolved via mechanical move; unknown-site movement rows ship labeled; legacy untracked PUT
  left in place, unused, deletion deferred to 6e; `@Size(max = 50)` batch cap, also tightening the
  legacy route).
- Last verified (this session, review-driven-fix pass): `./mvnw -q clean test-compile` clean;
  targeted reruns of every touched test class all green (`SiteInventoryMutationControllerSecurityIT`
  24/24, `...AtomicityIT` 15/15, `...FingerprintTest` 8/8, `LocationInventorySiteScopedQueriesIT`
  12/12, `NotAssignedInventoryReadParityIT` 2/2, `LegacyInventoryDeprecationHeadersIT` 6/6,
  `LocationInventoryServiceTest` 18/18); `./mvnw -q clean test` 371+ run/0 failures; `./mvnw test
  -Dtest='*IT'` 509 run/8 failures (same pre-existing `AnalyticsControllerSecurityIT`/
  `ForecastControllerSecurityIT` set as every prior checkpoint's run, no new failures). No
  OpenAPI/contract regeneration was needed — no route/DTO shape changed by this fix session.
- **Open risks/questions:**
  - Q-6c-1/Q-6c-4 unchanged (see 6c handoff further above).
  - **New, from this session's finding 3:** one-NA-location-per-site is empirically true in the
    live database today (`MAIN` has exactly 1, `SECOND` has 0 — not yet seeded) but is **not
    schema-enforced** (`locations` only carries `UNIQUE(storage_location_id, location_code)`, not a
    per-storage-location-NA-count constraint). Not a T-6d-9 blocker given the real-data check found
    no violation, but flagged as monitored debt: a future `LocationService.createLocation` call
    could add a second NA `locations` row for a site with no DB-level rejection, and
    `LocationService.getNotAssignedLocation`'s unordered `.stream().findFirst()` would then
    silently pick one of several. Consider a partial-unique index or application-level guard in a
    later checkpoint if this becomes load-bearing for more than T-6d-9's planned scope.
  - Pre-existing `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` flakiness (500s
    under the shared `*IT` sweep) is unrelated debt, unchanged by this session.
  - The five "Consider" items from the independent review (audit-row assertions in atomicity tests,
    loose `RuntimeException` exception-type assertions, OpenAPI response-status accuracy for the v1
    mutation routes, the fingerprint serialization-shape deployment note, no cross-site-destination
    test on the batch route) were not acted on this session, per the review's own "Consider, not a
    blocker" disposition — carried forward as recorded debt, not re-litigated.

## 6d implementation (T-6d-1..T-6d-14) (2026-09-14)

Implemented the full web-adoption slice per the 6d planning worksheet, in order, in `apps/web`.
`services/inventory-service`, `packages/contracts`, and `packages/api-client` were not touched
(the backend slice above already shipped and regenerated them). TDD where a meaningful local test
existed (new hook/client-layer unit tests written and run red before the implementation, then
green after); mechanical/scope-driven changes (removing dead affordance copy, wiring existing
hooks into new client functions) followed the existing rendered-test suite instead of a fresh
red/green cycle per line.

### T-6d-1 — typed v1 inventory client (`lib/api/site-inventory.ts`)

- New `apps/web/src/lib/api/site-inventory.ts`: `getSiteInventoryTotals`, `getSiteLocationInventory`,
  `getSiteProductInventory`, `getSiteMovements`, `adjustSiteInventory`, `transferSiteInventory`,
  `batchTransferSiteInventory`, `createSiteLocationInventory`, `deleteSiteLocationInventory`, and
  `newIdempotencyKey`. Every function reads the actual `packages/contracts/openapi.json` v1
  inventory paths/schemas directly (not guessed) - verified with a scripted schema dump before
  writing any mapper.
- **Real bug found and fixed while implementing `getSiteMovements`**: `GET
  /api/v1/sites/{siteId}/inventory/movements` takes a Spring `Pageable pageable` parameter, which
  Spring binds from flat `page`/`size`/`sort` query params - not the nested `pageable[page]=...`
  shape `openapi-fetch`'s default `deepObject` serializer would produce for an object-typed query
  param. Confirmed against `SiteInventoryController.getSiteMovements`'s actual parameter. Fixed
  with a per-call `querySerializer` override (`sitePageableQuerySerializer`) that flattens
  `pageable` to top-level `page`/`size`/`sort` keys; every other query param passes through
  unchanged. Without this fix, every paginated movement-history call would have silently ignored
  the caller's requested page/size and always returned the endpoint's `@PageableDefault`.
- New `apps/web/src/lib/api/inventory-join.ts`: `joinSiteLocationEntriesWithCatalog`, the one
  shared client-side join helper the worksheet asked for (T-6d-6/T-6d-9 both use it - not three
  separate copies). Joins slim v1 `SiteLocationInventoryEntry` rows against the existing
  `useProducts()` catalog by product ID, producing the same `LocationInventory[]` shape every
  existing consumer (`location-detail-sheet.tsx`, the NOT_ASSIGNED view) already expects, so those
  components needed no shape-level rewrite. Entries with no catalog match are dropped, not
  rendered with placeholder data.
- `apps/web/src/lib/api/locations.ts` gained `getSiteLocations(siteId, storageLocationCode?)`
  (the new v1 `GET /api/v1/sites/{siteId}/locations` route, mapped from the nested
  `Location.storageLocation` DTO shape to the existing flat `Location` type) and
  `resolveSiteLocationId(siteId, locationType, locationId)` - the site-scoped replacement for
  `inventory.ts`'s `resolveLocationId`, used by every new web mutation/query call site that can
  receive the `LocationSelector`'s NOT_ASSIGNED virtual ID (`"__not_assigned__"`).
- Tests: `lib/api/site-inventory.test.ts` (15 cases - mapping/defaults, the pageable-serializer
  fix proven directly by calling the captured `querySerializer` and asserting the produced query
  string never contains `pageable[page]`, idempotency-key header wiring, no-`actorId`-in-body
  assertions on every mutation) and `lib/api/inventory-join.test.ts` (3 cases) and new
  `getSiteLocations`/`resolveSiteLocationId` cases added to the existing `lib/api/locations.test.ts`
  (5 new cases, 12 total in that file).

### T-6d-2 — idempotency-key strategy

Every v1 mutation hook generates its `Idempotency-Key` in the hook's exposed `mutate`/`mutateAsync`
wrapper - one call to `newIdempotencyKey()` per invocation, attached to the mutation's `variables`
- never inside `mutationFn` itself. Since `mutationFn` always receives the same `variables` object
for a given attempt (including on a hypothetical internal TanStack retry - the app's
`lib/query-client.ts` already sets `mutations.retry: false` globally, so no retry happens by
default today, but the wiring is correct independent of that default), one user action always maps
to exactly one key, and two separate `mutate()` calls always get distinct keys. Proved directly by
`hooks/mutations/__tests__/use-stock-mutations.test.ts`'s "sends ... a freshly generated idempotency
key per mutate() call" test (two sequential `mutate()` calls on the same hook instance assert two
different keys reached the client function). Applied to: `use-stock-mutations.ts` (adjust,
transfer, batch-transfer) and `use-location-mutations.ts` (create, delete). `product-form.tsx`'s
one-off initial-stock create call calls `newIdempotencyKey()` directly at the point of the call
(not inside a retryable wrapper), satisfying the same "once per attempt" rule structurally.

### T-6d-3 — site-qualified query keys and invalidation audit

- Every inventory-related query key gained `siteId` as its second element:
  `["inventoryTotals", siteId]`, `["locationInventory", siteId, ...]`,
  `["productInventoryEntries", siteId, productId]`, `["movementHistory", siteId, productId, ...]`.
  Every one of these queries is gated with `skipToken` until `siteId` resolves, mirroring
  `use-site-products.ts`'s established pattern (disabled-but-not-falsely-loaded).
- Audited every `invalidateQueries`/`setQueriesData` call site across the touched prefixes -
  mutation hooks (`use-stock-mutations.ts`, `use-location-mutations.ts`) and every realtime hook
  that touches `stock_movements` or `products` (`use-realtime-inventory.ts`,
  `use-realtime-dashboard.ts`, `use-realtime-products.ts`; the org-wide `db-changes` broadcast
  channel, `use-realtime-broadcast.ts`, was read but left alone - see T-6d-11 below).
- **Fixed the exact `setQueriesData<Product[]>({queryKey:["products"]})` prefix-collision bug the
  worksheet flagged as a live hazard, not hypothetical**, and found it in *three* files, not the
  one originally suspected: `use-realtime-products.ts` (direct `products` table subscription) and
  `use-realtime-dashboard.ts` (`stock_movements` subscription) both had the same unguarded
  `setQueriesData({queryKey:["products"]})` call `use-realtime-inventory.ts` had - a default
  `exact:false` match that also caught the 5d site-scoped `["products", siteId, "site"]` query
  (`SiteProduct[]`) by prefix and would silently overwrite it with a differently-shaped legacy
  `Product[]` fetch on every realtime product/stock-movement event, regardless of which site was
  active. Fixed all three with one shared filter,
  `hooks/realtime/legacy-products-query-filter.ts`'s `legacyProductsListFilter`
  (`{queryKey:["products"], predicate: query => query.queryKey[2] !== "site"}`), applied at every
  `setQueriesData`/fallback-`invalidateQueries` call site in those three files.
  `use-realtime-broadcast.ts` (the org-wide broadcast channel) was checked too and turned out to
  already be safe - its own `findAll({queryKey:["products"]})` loop already filters to
  `query.queryKey.length === 2` before writing, which already excludes the 3-element site-scoped
  key - so it needed no change.
  - Proved directly: `hooks/realtime/__tests__/use-realtime-inventory.test.ts`'s "never overwrites
    the site-scoped products query..." test seeds both cache shapes, fires a same-site event, and
    asserts the site-scoped cache is byte-for-byte unchanged while the legacy cache picks up the
    new fetch - this is the regression test for the exact bug, not just a shape assertion.
  - `use-shipment-mutations.ts`/`use-supplier-mutations.ts`/`use-product-mutations.ts`'s existing
    `invalidateQueries({queryKey:["products"]})` calls were checked and left alone: plain
    `invalidateQueries` (no `setQueriesData` overwrite) is safe across shape differences - it marks
    matching queries stale and lets each one refetch through its own `queryFn`, it does not write a
    foreign shape into another query's cache.
- `use-location-mutations.ts`'s site-scoped invalidation targets `["products", siteId, "site"]`
  specifically (never the bare `["products"]` prefix), so a stock mutation can never touch the
  legacy, unscoped product list cache by accident.

### T-6d-4 — restored scoped Products-list quantity/status

`hooks/queries/use-product-inventory.ts`'s `useSiteProductInventory` now also queries
`getSiteInventoryTotals(siteId)` (site-qualified key, `skipToken`-gated) and derives
`totalQuantity`/`lastUpdatedAt`/`status` from it, joined by product ID alongside the existing
`getSiteProducts` join - a third source added to the existing two-source join, each field's source
still fixed per AC-6a's rule (quantity/status only ever come from totals, never a fallback).
`apps/web/src/app/(dashboard)/products/page.tsx` no longer passes `showQuantity={false}` to
`ProductTable` or `showInventory={false}` to `ProductModal` (both default to rendering their real
content now that quantity exists). `product-table.tsx`'s now-dead "available after inventory is
migrated per site (Phase 6)" affordance paragraph is removed, and `product-modal.tsx`'s equivalent
withheld-state branch (and its now-unused `showInventory` prop) is removed entirely - the modal
always renders its real Current Stock section.

### T-6d-5 — product detail inventory on v1

New `useSiteProductInventoryEntries(productId)` in `hooks/queries/use-product-inventory-entries.ts`
(site-qualified key `["productInventoryEntries", siteId, productId]`, `skipToken`-gated), backed
by `getSiteProductInventory`. `product-modal.tsx` now calls this instead of the legacy
`useProductInventoryEntries`. The legacy hook itself is untouched and still exported - Kuji's own
dialogs (`transfer-in-dialog.tsx`, `tier-draft-ui.tsx`, `tier-edit-dialog.tsx`) still use it,
unmodified, per this checkpoint's scope (Kuji stays legacy until Phase 7).

### T-6d-6 — location-detail inventory on v1 with the shared join helper

`hooks/queries/use-location-inventory.ts` rewritten: resolves the caller's location (a real ID
passed straight through, or the NOT_ASSIGNED virtual ID resolved via the new
`getSiteLocations(siteId, "NOT_ASSIGNED")` route) into a real, site-scoped location ID, fetches
`getSiteLocationInventory(siteId, resolvedId)`, and joins the result against `useProducts()` via
`joinSiteLocationEntriesWithCatalog`. Returns the same `LocationInventory[]` shape as before, plus
a new `resolvedLocationId` field mutation call sites need (see T-6d-7/8). Query keys:
`["locationInventory", siteId, "resolved-location", locationType, locationId]` (the resolution
step) and `["locationInventory", siteId, locationType, resolvedId]` (the entries), both
site-qualified and `skipToken`-gated. `location-detail-sheet.tsx` and `adjust-stock-dialog.tsx`
updated to pass `locationCode` (now a required third parameter) - both already had it in scope
(`LocationSelection.locationCode` / the sheet's own `getLocationCode` helper), so no new state was
needed. `location-detail-sheet.tsx` also switched its `useProductInventory()` call (used to resolve
the product shown in its own embedded `ProductModal`) to `useSiteProductInventory()`, for
consistency with the rest of the site-scoped surface it renders inside.

### T-6d-7 — stock adjust on v1

`hooks/mutations/use-stock-mutations.ts`'s `useBatchAdjustStockMutation` now calls
`adjustSiteInventory(siteId, idempotencyKey, payload)`; the client no longer sends `actorId` (the
v1 `AdjustSiteInventoryPayload` type has no such field - actor comes from
`AuthorizedSiteContext` server-side, matching every other v1 mutation route). Every caller
(`adjust-stock-dialog.tsx`'s cart-mode and single-mode submit paths, plus its "update an existing
row" path - see below) updated to stop sending `actorId` and to use `resolvedLocationId`
(from `useLocationInventory`, see T-6d-6) rather than the raw, possibly-virtual
`location.locationId`.

### T-6d-8 — stock transfer + batch transfer on v1

`useTransferStockMutation`/`useBatchTransferMutation` now call `transferSiteInventory`/
`batchTransferSiteInventory`. `transfer-stock-dialog.tsx` (which already always used the batch
mutation, even for a single item) resolves both source and destination through
`useLocationInventory`'s new `resolvedLocationId` before building the transfer payload, and no
longer sends `actorId`.

### T-6d-9 — NOT_ASSIGNED on v1 + create/delete flows

- `useLocationInventory`/`useNotAssignedInventory` (now a thin wrapper over the former) resolve
  NOT_ASSIGNED through `getSiteLocations(siteId, "NOT_ASSIGNED")`, replacing both the virtual-ID
  indirection and `lib/api/inventory.ts`'s site-blind `cachedNALocationId` module cache. That
  cache is retired outright (not just bypassed): `getNALocationId()` still exists (kuji-boxes.ts's
  own legacy, still-global NA resolution keeps calling it, unmodified, since kuji stays on
  legacy/global state through this checkpoint), but no longer caches its result across calls - a
  cross-site-blind cached value could otherwise leak once kuji itself becomes multi-site aware,
  and there is no reason to carry that latent bug forward once the web's own inventory flows no
  longer depend on it.
- `hooks/mutations/use-location-mutations.ts`'s `useCreateInventoryMutation`/
  `useDeleteInventoryMutation` rewritten onto the new v1 create/delete routes
  (`createSiteLocationInventory`/`deleteSiteLocationInventory`), each resolving
  `location`/`locationType` through `resolveSiteLocationId` before calling. **There is no v1
  "update" route** (R-9's resolution deliberately dropped the legacy untracked
  `PUT /api/locations/{id}/inventory/{invId}`, a silent absolute-quantity set with no
  `StockMovement`/audit/outbox - a pre-existing AC-4 violation). `useUpdateInventoryMutation` is
  removed; `adjust-stock-dialog.tsx`'s "update an existing row's quantity"
  path (`AddInventoryDialog`'s pre-filled-quantity flow) now computes a signed delta
  (new quantity - existing quantity) and submits it through the audited
  `useBatchAdjustStockMutation` instead - the row still gets a proper `StockMovement`/audit/outbox
  entry, unlike the dropped PUT.
- `product-form.tsx`'s initial-stock creation (on new-product submit) now resolves the selected
  location via `resolveSiteLocationId` and calls `createSiteLocationInventory` directly (not
  through a mutation hook, matching its existing one-off-call style), dropping the client-sent
  `actorId`. The now-unused `useAuth()`/`user` import was removed from this file (nothing else in
  it referenced `user` after this change - checked by grep, not assumed).
- **Dead code found and removed**: `hooks/mutations/use-not-assigned-mutations.ts`
  (`useUpdateNotAssignedInventoryMutation`/`useDeleteNotAssignedInventoryMutation`) had zero
  callers anywhere in `apps/web` (confirmed by grep before deleting) and called the same
  now-retired legacy PUT/DELETE pattern T-6d-9 is replacing. Deleted rather than left as a
  misleading, unused relic of the pre-R-9 design.
- **Behavior change this session is explicitly flagging, per the task's own instruction to call it
  out rather than let it hide**: moving NOT_ASSIGNED reads onto the v1 per-location route means
  kuji-child and CUSTOM-kuji-parent rows that the legacy, unfiltered
  `findByStorageLocation_Id`-backed read used to show at NOT_ASSIGNED no longer appear there in the
  web UI (the not-assigned inventory list on `storage/page.tsx`, and any location-detail view of
  NOT_ASSIGNED). This was already proven as an intentional, verified behavior difference by the
  backend slice's `NotAssignedInventoryReadParityIT` (see the 6d backend implementation section
  above) - this web slice is what actually surfaces that change to a real screen. No kuji dialog
  reads NOT_ASSIGNED inventory through this path (they use their own, separate legacy read paths,
  untouched), so this does not affect kuji's own management screens - only the general
  Storage/NOT_ASSIGNED view a non-kuji user would look at.
- **Open risk carried forward, not resolved by this session**: the backend slice's finding 3
  established that "one NOT_ASSIGNED location per site" is empirically true in production today
  (MAIN has 1, SECOND has 0) but is **not schema-enforced**. This web slice's
  `resolveSiteLocationId`/`getSiteLocations(..., "NOT_ASSIGNED")` calls
  `.find(loc => loc.locationCode === "NA") ?? locations[0]` - if a site ever ends up with more than
  one NOT_ASSIGNED-coded location, this resolves to an arbitrary one (whichever the backend
  returns first), not a guaranteed-correct one. This did not need to be solved to ship T-6d-9 (the
  real-data check found no violation), but it is the same open risk the backend handoff already
  flagged, now with a second, independent consumer (this web code) that would silently pick the
  wrong location if it were ever violated. Recorded here again rather than treated as newly
  discovered.

### T-6d-10 — movement history on v1

`hooks/queries/use-movement-history.ts` rewritten onto `getSiteMovements` (site-qualified key,
`skipToken`-gated). `SiteStockMovement.siteAttribution` carries `"UNKNOWN"` for rows predating the
site backfill and is never filtered out client-side (proven by `site-inventory.test.ts`'s "labels
UNKNOWN-site rows, never silently hides them" test). **Honesty note**: grepped for every consumer
of `useMovementHistory`/the legacy `getStockMovementHistory` before and after this change and found
none - no component in `apps/web` currently renders movement history through this hook (the visible
audit-log UI at `/audit-log` uses a separate endpoint, `getAuditLog`/`getAuditLogs` via
`/api/stock-movements/audit-log` and `/api/audit-logs`, explicitly out of this checkpoint's scope
per the T-6d-12 residual note below). So T-6d-10 is complete at the data-layer/contract level (the
hook and its label are real, tested, and ready), but there is currently no rendered UI surface to
exercise the "visible label" requirement against - the AC-6 rendered-test sweep for this piece is
necessarily the hook-level/client-level tests, not a component test, because there is no component.

### T-6d-11 — site-scoped realtime inventory refresh

- `hooks/realtime/use-realtime-inventory.ts`'s `StockMovementRow` gained an optional `site_id`
  field. A new `isRelevantToCurrentSite(row, siteId)` predicate: an event with no resolved current
  site is never processed; a row with `site_id === null`/`undefined` (pre-backfill/legacy) is
  always treated as possibly relevant; a row with a different site's `site_id` is dropped before
  any invalidation runs. Both `useRealtimeInventory` and `useRealtimeProductInventory` apply this
  filter first in `onReceive`, and both hooks are `enabled` only once a site is resolved.
  Re-subscription on site change falls out for free: `onReceive`'s closure identity changes with
  `siteId` (a fresh closure captured on every render, exactly as it already was before this
  change), and `use-supabase-realtime.ts`'s effect already depends on that closure, so a site
  change already triggered its existing re-subscribe machinery - no change was needed there.
- The org-wide `db-changes` broadcast channel (`use-realtime-broadcast.ts`) carries no `site_id` in
  its payload and is explicitly left over-invalidating-but-safe for this checkpoint, per the
  worksheet's own scope note - adding site scoping to `SupabaseBroadcastService`'s payload is 6e's
  AC-7 coalescing work, not 6d's.
- Tests: `hooks/realtime/__tests__/use-realtime-inventory.test.ts` (5 cases - disabled until site
  resolves, foreign-site event dropped, same-site event processed, null-site event processed, and
  the T-6d-3 collision-fix regression test described above).

### T-6d-12 — site-blind residual audit

Recorded here, not fixed (each belongs to a later phase/checkpoint):

- **`getLocationsWithCounts`** (`lib/api/locations.ts`) - legacy, unscoped `/api/locations/with-counts`.
  Powers `storage/page.tsx`'s location-count badges. Silently resolves to MAIN server-side. Owning
  phase: not yet scheduled: a `sites`-module route for this shape doesn't exist yet; tracked as the
  same kind of gap 6a/6b/6c already carried for other `sites`-owned reads.
- **`dashboard.ts`'s `getAuditLog`/`getAuditLogs`** (`/api/stock-movements/audit-log`,
  `/api/audit-logs`) - legacy, unscoped. Powers the `/audit-log` page. Not migrated this checkpoint
  - a v1 site-scoped audit-log route does not exist yet in this checkpoint's backend slice (only
  `/api/v1/sites/{siteId}/inventory/movements` was added, which is a different, narrower shape than
  the audit-log page's grouped-by-action view). Owning phase: a future Phase 6 follow-up or Phase 7,
  whichever adds the route.
- **Kuji dialogs' inventory reads** (`transfer-in-dialog.tsx`, `tier-draft-ui.tsx`,
  `tier-edit-dialog.tsx`, all still on `useProductInventoryEntries`/legacy `resolveLocationId` via
  `kuji-boxes.ts`) - explicitly out of scope per spec.md AC-6e and this checkpoint's own worksheet;
  Kuji/lootbox site migration is Phase 7's job.
- **`use-realtime-broadcast.ts`** (the org-wide `db-changes` channel) - site-blind by design for
  this checkpoint (T-6d-11 above); its AC-7 coalescing/site-scoping is 6e's job.
- **Movement history has no rendered UI consumer** (T-6d-10 above) - not a site-blind surface (the
  hook itself is correctly site-scoped), but flagged here since it means this checkpoint's AC-6
  rendered-test sweep could not exercise it end-to-end through a real component; whichever future
  work adds a movement-history UI should build directly on the already-scoped
  `useMovementHistory`/`getSiteMovements`, not reinvent it.
- **The one-NOT_ASSIGNED-location-per-site invariant is still not schema-enforced** (carried
  forward from the backend slice's finding 3, restated under T-6d-9 above with a second consumer
  now depending on it).

### T-6d-13 — AC-6 rendered-test sweep

- Fixed the two pre-existing 5d-era test files that asserted the now-superseded "withheld
  quantity" behavior (`hooks/queries/__tests__/use-site-product-inventory.test.ts` and
  `app/(dashboard)/products/__tests__/page.test.tsx`) to assert the T-6d-4 restored-quantity
  behavior instead - both now mock `getSiteInventoryTotals` with **distinct per-site totals**
  (MAIN: 42, SECOND: 3) so the site-switch test (`page.test.tsx`'s "rebinds the open detail modal
  to the new site's own settings on a site change") proves the quantity rebinds to the new site's
  own total on switch, not merely that the request carried a `siteId` - the exact trap the
  worksheet called out from 5d's own history. Also added a dedicated "zero-stock product reports
  totalQuantity: 0, never dropped or undefined" case, matching AC-5's zero-stock correctness
  requirement.
- New coverage: `lib/api/site-inventory.test.ts` (15), `lib/api/inventory-join.test.ts` (3), 5 new
  cases in `lib/api/locations.test.ts`, `hooks/queries/__tests__/use-location-inventory.test.ts` (4
  - real-location fetch, NOT_ASSIGNED resolution through the v1 locations route, disabled-until-site
  -resolves, disabled-for-display-only-type), `hooks/mutations/__tests__/use-stock-mutations.test.ts`
  (2 - the T-6d-2 idempotency-key proof, and a no-site rejection case),
  `hooks/realtime/__tests__/use-realtime-inventory.test.ts` (5, described under T-6d-11).
- **Not covered by a rendered (component-level) test in this session, and recorded rather than
  silently skipped**: the adjust/transfer dialogs' full interactive workflow (staging a cart,
  submitting a batch adjust/transfer, the delta-computation path for updating an existing row) has
  no dedicated rendered test added this session - existing coverage for these dialogs was
  unit/hook-level and at the API-client layer, not a full `render()` + `fireEvent` walkthrough of
  the dialog components themselves. This is real, scoped debt: the dialogs compile, typecheck, and
  their underlying hooks/client functions are proven correct in isolation, but no test in this repo
  currently drives `AdjustStockDialog`/`TransferStockDialog` end-to-end through a simulated user
  submitting a batch or delta-adjust. Flagging this explicitly rather than claiming full AC-6
  dialog-workflow coverage.
- Confirmed via `git status`/`grep` (not assumed) that `storage/page.tsx`, `dashboard.ts`,
  `kuji-boxes.ts`, and every kuji dialog file are untouched by this session.

### T-6d-14 — Kuji tab panel regression check

`components/products/kuji-tab-panel.tsx` and `components/products/__tests__/kuji-tab-panel.test.tsx`
are both untouched (`git status` shows no diff for either path). Ran the test file directly:
3/3 passing (`renders CustomKujiTabs at the MAIN site`, `renders an unavailable state at a
non-MAIN site...`, `renders the unavailable state while the site is still unresolved...`) -
T-6d-4's hook refactor (`useSiteProductInventory` gaining totals) did not regress the AC-6e gate.

### Full-suite verification (checkpoint-slice gate)

- `npx tsc --noEmit -p tsconfig.json` (from `apps/web`): clean, exit 0, run twice (once mid-slice
  after the T-6d-9 idempotency-key refactor, once at the end) - both clean.
- `npx vitest run` (from `apps/web`), final run: **48 test files passed, 353 tests passed, 0
  failed.** (Baseline before this session's changes: 43 files / 318 tests, all passing - net +5
  files / +35 tests, and the 5 pre-existing failures introduced mid-session by the T-6d-4 quantity
  restoration were fixed before this count, not left red.)
- `npx eslint .` (from `apps/web`): **0 errors, 51 warnings** - every warning is a pre-existing
  `react-hooks/exhaustive-deps`/`react-hooks/set-state-in-effect` pattern on lines this session did
  not touch (verified with `git diff` against each flagged file/line before accepting the count),
  plus one pre-existing unused-var warning in `use-toast.ts` (also untouched). No new lint errors
  or warnings were introduced.
- No live UI verification was performed this session (no browser automation tool was available and
  no local dev server/backend was started) - rendered-test coverage (React Testing Library,
  described above) is this session's evidence for UI correctness, not a manual click-through. This
  is stated explicitly per this task's own instruction, not implied.

### Deviations from the plan

- T-6d-be's design doc did not anticipate the `Pageable` query-serialization bug (T-6d-1) or the
  two additional `setQueriesData` collision sites beyond the one named in the worksheet (T-6d-3) -
  both are corrections found by actually reading the generated client/existing realtime code, not
  scope changes.
- `useUpdateInventoryMutation` was removed rather than kept-but-unused, since R-9's resolution
  means it has no backing v1 route and no legitimate future caller (see T-6d-9).
- `use-not-assigned-mutations.ts` was deleted outright (confirmed zero callers) rather than left in
  place, since it exercised the same now-retired legacy-PUT pattern and had no test or caller
  depending on its continued existence.
- Movement history (T-6d-10) has no rendered UI to test against (see that section and T-6d-13) -
  this is a pre-existing gap in the app, not something this session was supposed to add UI for, but
  it does mean this one task's "AC-6 rendered coverage" is necessarily narrower than the others.

### Flagged for the user / reviewer, not resolved by a recorded assumption

- **T-6d-9's NOT_ASSIGNED behavior change** (kuji-child/CUSTOM-kuji-parent rows no longer appear at
  NOT_ASSIGNED in the general Storage view) is real and now live in the web UI, not just proven in
  a backend IT. This was pre-confirmed as intentional by the backend slice's own review, but is
  restated here since it is the first point where an actual screen's behavior changes.
  - **Backend consideration surfaced by writing this web slice, but not acted on** (Standard tier
    scope guardrail: no backend edits this session, so this is a recorded observation for the
    reviewer/next backend session, not a fix): `LocationInventoryRepository
    .findByStorageLocation_Id` (the legacy read `getStorageLocationInventory`/
    `getInventoryByLocation` in `lib/api/inventory.ts` still calls, unchanged, for any caller not
    yet migrated to v1 - today only kuji, via `kuji-boxes.ts`'s own separate paths, does not call
    this particular function, so no live caller is currently affected) applies no
    `parent IS NULL`/non-CUSTOM-kuji filter, unlike its `findByLocation_Id` sibling and the v1
    route. If a future caller reaches `getStorageLocationInventory` directly for NOT_ASSIGNED
    (rather than going through the v1-backed `useLocationInventory`/`useNotAssignedInventory` this
    session already migrated), it would silently see the wider, unfiltered legacy result. No such
    caller exists today (checked by grep), so this is not a live bug, only a trap for whichever
    future caller adds one without noticing this repository's own precedent has already moved past
    it.
- **The one-NOT_ASSIGNED-location-per-site invariant** (open risk carried from the backend slice,
  restated under T-6d-9 above) now has a second consumer (`resolveSiteLocationId`) that would
  silently pick an arbitrary location if the invariant were ever violated for a real site. Still
  not schema-enforced. Not a T-6d-9 blocker (no violation found in real data), but worth the user's
  attention if a partial-unique index or application-level guard is being considered for a later
  checkpoint, since the blast radius of leaving it unenforced just grew by one caller.
- **T-6d-13's incomplete dialog-level rendered coverage** (adjust/transfer dialogs' interactive
  workflows) - see that section. Recommend a follow-up task (inside 6d's own slice-completion gate,
  or folded into 6e) to add `render()`-level tests for `AdjustStockDialog`/`TransferStockDialog`
  covering: staging a cart and submitting a batch adjust, the delta-computation "update an existing
  row" path, and a batch transfer, all against the site-scoped v1 mutations, before this checkpoint
  is considered to have full AC-6 rendered coverage rather than "hook/client-layer coverage plus
  typecheck."

## Current handoff (6d web implementation, superseding the backend-only handoff above)

- Status: 6d is now implemented end-to-end - backend slice (T-6d-be-1..8, reviewed, fixes applied)
  and web slice (T-6d-1..T-6d-14, this session) are both done. `apps/web` builds clean
  (`tsc --noEmit`), the full Vitest suite passes (353/353), and `eslint .` reports 0 errors. Not
  yet independently reviewed (Standards + Spec passes) - that review, plus `review.md`/
  `validation.md` updates for the whole checkpoint, is the coordinating session's next step, per
  this record's own process rules (implement, then a separate review pass, then fixes - this
  session did not touch `review.md`/`validation.md`, as instructed).
- Next action: independent Standards + Spec review of the full 6d slice (backend + web together),
  covering in particular: the `Pageable` query-serialization fix, the three-file `setQueriesData`
  collision fix, T-6d-9's NOT_ASSIGNED-resolution `.find() ?? locations[0]` fallback given the
  still-unenforced one-NA-location invariant, and T-6d-13's flagged dialog-level rendered-test gap.
  After review findings are fixed, close checkpoint 6d and move to 6e (targeted refresh
  coalescing and the full phase exit gate, AC-7/AC-8).
- Surviving decisions: everything recorded in the plain-6d and backend-review-driven-fix handoffs
  above, plus this session's own: quantity/status restored on the Products list and detail (AC-6,
  no longer withheld per 5d's interim AC-6b); no v1 "update inventory" route exists by design (R-9)
  - an existing row's quantity change always goes through the audited adjust mutation with a
  computed delta, never a direct set; idempotency keys are generated once per `mutate()`/
  `mutateAsync()` call in every v1 mutation hook, never inside `mutationFn`.
- Last verified (this session): `npx tsc --noEmit -p tsconfig.json` clean (exit 0); `npx vitest run`
  - 48 files / 353 tests, 0 failures; `npx eslint .` - 0 errors, 51 pre-existing warnings (verified
    none touch this session's changed lines).
- **Open risks/questions (carried forward or new, see the sections above for full detail):**
  - One-NOT_ASSIGNED-location-per-site is still not schema-enforced, now with a second consumer
    (`resolveSiteLocationId`) depending on the same `.find() ?? locations[0]` fallback the backend
    slice's `getNotAssignedLocation` already used. Monitored, not blocking.
  - `LocationInventoryRepository.findByStorageLocation_Id`'s missing root-product filter (used by
    the still-legacy `getStorageLocationInventory`) is a live trap for any future caller that
    doesn't go through the already-migrated `useLocationInventory`/`useNotAssignedInventory` - no
    current caller hits it, but nothing prevents a future one from doing so unknowingly.
  - T-6d-13's dialog-level rendered-test gap (`AdjustStockDialog`/`TransferStockDialog` full
    interactive workflows) - recommend closing before treating 6d's AC-6 evidence as complete.
  - No live UI (manual/browser) verification was performed this session - see T-6d-13's full-suite
    verification note.
  - Q-6c-1/Q-6c-4 and the pre-existing `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT`
    flakiness (see the backend handoff above) are unchanged, unrelated to this session.

## Review-driven fix: 6d web slice findings (2026-09-14)

An independent review of the committed-but-not-yet-merged 6d web-adoption slice (T-6d-1..T-6d-14,
above) found four "Act on" findings and four "Consider" findings marked fixable-cheap. All eight
addressed this session (finding 3 via a production-data check, no code change; the other seven via
code + tests). Full disposition list transcribed into `review.md`'s new "6d web slice
(T-6d-1..T-6d-14) — independent review, 2026-09-14" entry; commands/results in `validation.md`'s
matching entry. Summary:

- **Finding 1 — `product-form.tsx` silently drops initial stock when `siteId` is unresolved.**
  `siteId` was a conjunct of the guard deciding whether to create initial stock; when falsy, the
  whole block (including its error toast) was skipped, so the user saw only "Product created" with
  their typed stock silently gone. **Fixed:** moved the `siteId` check inside the block with a
  dedicated destructive toast ("Product created, but stock was not added" / "No active site.") on
  the missing-site path. New test file `components/products/__tests__/product-form.test.tsx` (2
  cases). Revert-verified: temporarily reverted the fix (restored the old outer conjunct), reran
  the new "surfaces a destructive toast..." test in isolation — failed (no destructive toast, and
  the mutation-not-called assertions held for the wrong reason); restored the fix, reran green.
- **Finding 2 — the `setQueriesData({queryKey:["products"]})` collision fix only excluded the
  sibling site-scoped key, not per-product `children`/`with-children` keys.**
  `legacy-products-query-filter.ts`'s predicate (`query.queryKey[2] !== "site"`) still matched
  `["products", productId, "children"]`/`["products", productId, "with-children"]`
  (`useProductChildren`/`useProductWithChildren`), so a realtime event's `surgicalProductUpdate`
  could append an unrelated product into those lists. **Fixed:** narrowed the predicate to
  `query.queryKey.length === 2`, matching the precedent already in `use-realtime-broadcast.ts`;
  verified the `["products", productId]` detail-entry shape (also length 2) stays safe because
  every write site already guards with `Array.isArray(oldData)`. New test in
  `use-realtime-inventory.test.ts`. Revert-verified: temporarily restored the old predicate, reran
  the new test — failed with the seeded `children` cache mutated; restored the fix, reran green.
- **Finding 3 — NOT_ASSIGNED row-hiding scale check not yet run against real data.** The
  coordinating session ran the review's specified query directly against the real production
  database (`mcp__supabase`) — see validation.md's "Finding 3 evidence" for the exact query.
  **Result: zero rows returned** — no production data is hidden by the NOT_ASSIGNED filter change.
  No code change needed; this closes T-6d-9's own "flagged, not resolved" open item with real
  evidence.
- **Finding 4 — AC-6's rendered-test list not fully satisfied.** No `render()`-level test drove
  `AdjustStockDialog`'s or `TransferStockDialog`'s submit workflow, and no test forced a late
  old-site-result resolution to prove site-qualified keys actually reject it (not just structural
  argument). **Fixed:** added `components/stock/__tests__/adjust-stock-dialog.test.tsx` (2 cases —
  subtract-adjustment submit with correct payload/success toast, and the "update an existing row"
  delta-computation path proving a computed `+3` reaches the mutation, not the raw absolute value
  8) and `components/stock/__tests__/transfer-stock-dialog.test.tsx` (1 case — source/destination/
  quantity fill and submit with correct transfer payload). Added a late-old-site-result test to
  `hooks/queries/__tests__/use-site-product-inventory.test.ts`: site A's totals request is left
  unresolved, the hook rerenders as if the site switched to B, B's totals resolve and render, and
  only then does A's stale request resolve — the rendered data is asserted to stay B's.
- **Finding 5 (Consider) — `useSiteProductInventory` fabricated `totalQuantity: 0`/
  `status: "out-of-stock"` while totals were still loading.** Not masked in
  `location-detail-sheet.tsx`'s embedded `ProductModal`, which has no loading gate around this
  hook. **Fixed:** the memo now returns `null` until `totalsQuery.data !== undefined` (once
  `siteId` is known), matching `useLocationInventory`'s existing pattern. New test: "stays null
  while totals are still loading, never fabricating totalQuantity: 0". Revert-verified:
  temporarily restored the old early-return guard, reran the new test — failed with a fabricated
  zero-quantity/out-of-stock row; restored the fix, reran green. (This fix's `useMemo` gained a
  `siteId` reference; the dependency array was updated to include it in the same edit, verified by
  `eslint` staying at the pre-existing 51-warning baseline rather than gaining a new
  `react-hooks/exhaustive-deps` warning.)
- **Finding 6 (Consider) — `useNotAssignedInventory` lost its `staleTime: 30_000`.** Rewritten as
  a thin wrapper over `useLocationInventory` (default `staleTime: 0`), causing a double refetch on
  every mount/focus. **Fixed:** `staleTime: 30_000` set on both queries inside
  `hooks/queries/use-location-inventory.ts`.
- **Finding 7 (Consider) — `use-stock-mutations.ts` had a `void locationType;` statement keeping
  an unused parameter alive.** Verified genuinely unused (grepped every reference inside
  `invalidateStockQueries`) and dropped both the parameter and its one caller's argument.
- **Finding 8 (Consider) — `getSiteProductInventory`/`getSiteMovements` dereferenced
  `data.productId`/`data.content` with no guard**, unlike `getSiteInventoryTotals`/
  `getSiteLocationInventory` (`data ?? []`). An ok-but-empty body (`unwrapGeneratedResponse` can
  return `undefined`) would throw a raw `TypeError` instead of the project's own
  `GeneratedApiError`. **Fixed:** both functions now throw `GeneratedApiError` on an empty body.
  New tests: one per function in `lib/api/site-inventory.test.ts`.

New/changed test files this session: `components/products/__tests__/product-form.test.tsx` (2,
new), `components/stock/__tests__/adjust-stock-dialog.test.tsx` (2, new),
`components/stock/__tests__/transfer-stock-dialog.test.tsx` (1, new),
`hooks/queries/__tests__/use-site-product-inventory.test.ts` (+2),
`hooks/realtime/__tests__/use-realtime-inventory.test.ts` (+1),
`lib/api/site-inventory.test.ts` (+2). Net +10 tests, +3 files.

**Result:** `npx tsc --noEmit -p tsconfig.json` clean (exit 0); `npx vitest run` — **51 test files
passed, 363 tests passed, 0 failed** (up from 48 files/353 tests); `npx eslint .` — **0 errors, 51
warnings** (identical to the prior session's baseline set — one new warning surfaced mid-session
from finding 5's fix and was closed by adding `siteId` to the `useMemo` deps array before this
count was taken). Findings 1, 2, and 5 were revert-verified (fix removed, new test confirmed red,
fix restored, test confirmed green) — see above and validation.md for each. Findings 6, 7, and 8
are small, mechanical, and covered by the full suite passing plus (for 8) dedicated new tests; not
separately revert-verified.

**Disposition:** findings 1, 2, 4, 5, 6, 7, and 8 fixed and re-verified. Finding 3 resolved via a
real production-data check (zero rows hidden) — recorded as closed, not carried forward as an open
risk, since the check found no counterexample and there is no further action the review asked for
beyond running and recording it. `services/inventory-service`, `packages/contracts`, and
`packages/api-client` were not touched this session, per scope. The working tree is left
uncommitted for the coordinating session, per instruction.

## Current handoff (6d review-driven fix, web slice — supersedes the 6d-web-implementation handoff above)

- Status: 6d is implemented end-to-end (backend + web) and both slices have now been through an
  independent review with all findings addressed: the backend slice's review-driven fix landed
  earlier this session-chain (see "Review-driven fix: 6d backend slice findings" above); this
  session closed the web slice's review (findings 1/2/4/5/6/7/8 fixed, finding 3 resolved via a
  production-data check with zero rows found). `apps/web` builds clean (`tsc --noEmit`), the full
  Vitest suite passes (363/363, up from 353/353), and `eslint .` reports 0 errors/51 warnings
  (same baseline as before this session). `review.md` and `validation.md` now carry both the
  backend-slice and web-slice review entries with real content, per spec.md's Full-tier
  requirement.
- Next action: 6d is ready to close as a checkpoint. Move to 6e (targeted refresh coalescing and
  the full phase exit gate, AC-7/AC-8) — 6e's own scope already explicitly owns
  `getLocationsWithCounts`/the org-wide broadcast-channel egress (T-6d-12's recorded residual,
  reaffirmed out of scope by this session's own review) and the site-scoping of
  `SupabaseBroadcastService`'s payload.
- Surviving decisions: everything recorded in the plain-6d, backend-review-driven-fix, and
  6d-web-implementation handoffs above, unchanged by this session, plus this session's own: a
  missing site during initial-stock creation now always surfaces a destructive toast rather than
  silently dropping the write; `legacyProductsListFilter` matches only 2-element
  `["products", ...]` list keys, never any 3-element per-product or site-scoped key;
  `useSiteProductInventory` withholds rendering (returns `null`) until totals have actually
  resolved, never fabricating a zero-quantity row; `useNotAssignedInventory`/
  `useLocationInventory` both use `staleTime: 30_000`.
- Last verified (this session): `npx tsc --noEmit -p tsconfig.json` clean (exit 0); `npx vitest
  run` — 51 files / 363 tests, 0 failures; `npx eslint .` — 0 errors, 51 warnings (identical set to
  the pre-session baseline, confirmed line-by-line). Findings 1, 2, and 5 individually
  revert-verified (fix removed → new test fails → fix restored → test passes).
- **Open risks/questions (carried forward, see the backend and 6d-web-implementation handoffs
  above for full detail — none newly introduced by this session):**
  - One-NOT_ASSIGNED-location-per-site is still not schema-enforced (both the backend's
    `getNotAssignedLocation` and the web's `resolveSiteLocationId` depend on the same
    `.find() ?? locations[0]` fallback). Monitored, not blocking — unaffected by this session.
  - `LocationInventoryRepository.findByStorageLocation_Id`'s missing root-product filter (used
    only by the still-legacy `getStorageLocationInventory`, no current caller) remains a trap for
    a future, not-yet-existing caller. Unchanged.
  - Movement history (T-6d-10) still has no rendered UI consumer. Unchanged.
  - `getLocationsWithCounts`/the org-wide broadcast-channel egress — explicitly 6e scope,
    reaffirmed by this session's review, not touched.
  - No live UI (manual/browser) verification was performed this session, consistent with every
    prior session in this checkpoint.
  - Q-6c-1/Q-6c-4 and the pre-existing `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT`
    flakiness are unchanged, unrelated to this session.

## Review-driven fix: 6d P1/P2 findings (external review) (2026-09-14)

An external review of the already-committed 6d checkpoint (backend `accd2b0`, web `232a8ca`, both
already through their own independent-review fix rounds — see the two "Review-driven fix: 6d ...
slice findings" sections above) found two additional real bugs neither of this checkpoint's own
independent reviews had flagged: a backend concurrency bug (P1) and a frontend error-swallowing bug
(P2). Both fixed this session; full disposition transcribed into `review.md`'s new "6d P1/P2
findings (external review) — 2026-09-14" entry, commands/results in `validation.md`'s matching
entry. Summary:

- **P1 — concurrent site-scoped transfers can lose source debits.** Traced every call site of
  `StockMovementService.requireInventoryBelongsToSite`, `LocationInventoryRepository
  .findByIdAndSite_Id`, and both site-scoped `transferInventory`/`batchTransferInventory` overloads
  before touching anything, and confirmed the reviewer's account exactly:
  `requireInventoryBelongsToSite` confirmed site membership via the entity-returning,
  JOIN-FETCH'd `findByIdAndSite_Id`, run *before* `planTransfers`/`lockPlannedRows` ever locked the
  row, populating the transaction's Hibernate persistence context with an unlocked, pre-lock
  quantity snapshot. The later "locked" read (`findById`/`findAllByIdWithGraph`, run only after the
  real Postgres row lock is held) does not refresh an already-managed entity's scalar state —
  Hibernate silently returns the same stale object — so the transfer computed its debit from the
  pre-lock quantity regardless of the lock. Two concurrent site-scoped transfers sharing one source
  row could each read the same stale quantity in their own transaction, and whichever committed
  last silently overwrote the other's already-applied debit. **Fixed:** added a scalar-only
  `LocationInventoryRepository.existsByIdAndSite_Id(UUID id, UUID siteId)` (a `boolean`
  projection — cannot populate the persistence context) and switched
  `requireInventoryBelongsToSite` to use it instead of the entity-returning method, so the site
  check no longer touches the row at the entity level before it's locked. This mirrors the
  principle `batchAdjustInventory` already followed correctly (it checks site membership via
  `Location`, a different, unrelated entity, never `LocationInventory`) — transfers needed a
  different mechanism (an explicit scalar existence check) since they identify rows by id rather
  than a shared, caller-known `locationId`. New test:
  `StockMovementServiceSiteScopedConcurrentSourceCheckRaceIT` — two real, concurrent, site-scoped
  `transferInventory` calls sharing one source inventory row, forced to interleave around the
  site-check/lock boundary via an externally held `SELECT ... FOR UPDATE` on the shared row plus
  `pg_stat_activity`-polled lock-wait confirmation (not timing), following
  `StockMovementServiceConcurrentTransferExistingDestinationRaceIT`'s exact style/rigor as its
  direct template. Revert-verified: with the fix reverted, the test failed exactly as the bug
  predicts — `expected: 23 but was: 53` (baseline 100, deltas 30/47, one debit silently lost);
  restored the fix, reran green.
- **P2 — failed inventory reads render as "no inventory" instead of an error.** `ProductModal`
  destructured only `data`/`isLoading` from `useSiteProductInventoryEntries`, discarding the
  `error` field the hook already computed (`error: siteError ?? query.error`). On a failed
  request, the derived `locations` array became empty exactly like a genuine zero-stock product, so
  the table's "No inventory at any location" empty-state branch rendered identically to a real
  failure — with no indication anything failed, no retry, even while the `Current Stock (N)` header
  (a separate, still-successful query) showed a nonzero total directly above it. **Fixed:**
  `useSiteProductInventoryEntries` now also returns `refetch` (a plain passthrough of
  `query.refetch` — chosen over inventing a new invalidation helper, since `useQuery` already gives
  exactly the needed retry semantics); `ProductModal` destructures `error`/`refetch` and the
  inventory table gained an explicit error branch — checked before the empty-state branch —
  rendering "Couldn't load inventory" plus a Retry button, following this codebase's existing
  destructive-state text convention (`location-detail-sheet.tsx`'s `inventoryQuery.isError`
  message) while adding the retry affordance the finding asked for. Also decided and implemented:
  Adjust and Transfer (desktop and mobile) are now `disabled` with an explanatory title while the
  inventory read has failed, since `hasInventory`/`locations` is indistinguishable from a genuine
  empty state during a failure — letting either button stay live would let a user act on a table
  that's actually just wrong, not actually empty. Transfer's `onClick` also gained a defense-in-
  depth destructive-toast branch for the failed-read case, ahead of its existing `hasInventory`
  check. New test file `product-modal.test.tsx` (3 cases): error state shows the retry affordance
  and not the empty state (with the nonzero header total still visible); Adjust/Transfer are
  disabled during the error; and the genuine-empty-state case (`data: {entries: []}`, no error)
  still renders "No inventory at any location" unchanged — a before/after regression guard.
  Revert-verified: with the fix reverted, the error-state and disabled-buttons tests both failed
  for the right reason (empty state rendered instead of the error text; buttons not disabled);
  restored the fix, reran green (all 3 pass).

**Result:** Backend — `./mvnw -q clean test-compile` clean; the new IT plus
`StockMovementServiceConcurrent*IT`/`StockMovementServiceMixedAdjustTransferLockOrderIT` all green;
`SiteInventoryMutationController*IT` (security + atomicity) all green; `./mvnw -q clean test` —
371 run (Surefire text-summary count, same pre-existing nested-test undercount noted by the prior
6d backend-slice session), 0 failures; `./mvnw test -Dtest='*IT'` — 510 run (up 1 from 509), 8
failures, name-for-name identical to the pre-existing `AnalyticsControllerSecurityIT`/
`ForecastControllerSecurityIT` set, no new failure. Web — `npx tsc --noEmit -p tsconfig.json`
clean; `npx vitest run` — **52 test files passed, 366 tests passed, 0 failed** (up from 51
files/363 tests); `npx eslint .` — **0 errors, 51 warnings** (identical set to the prior session's
baseline).

**Disposition:** both P1 and P2 fixed and re-verified, each with a revert-verified new test that
reproduces the bug before the fix and passes after. No other part of either checkpoint slice's
already-committed scope was touched; `packages/contracts`/`packages/api-client` untouched (no
contract/route shape change). The working tree is left uncommitted for the coordinating session,
per instruction.

## Current handoff (6d P1/P2 fix, external review — supersedes the 6d review-driven-fix web-slice
handoff above)

- Status: 6d is implemented end-to-end (backend + web), has been through two full rounds of
  independent review (backend and web slices, both closed — see the "Review-driven fix: 6d ...
  slice findings" sections above) plus this session's external-review fix round, which closed two
  more real bugs: a backend concurrency lost-update (P1) and a frontend error-swallowing bug (P2).
  Both fixed and re-verified this session, each with a revert-verified new test proving the bug
  existed before the fix and is gone after. `services/inventory-service` compiles clean and its
  full test suite is green (371 unit/component tests, 510 total IT run with only the pre-existing
  8 unrelated security-IT failures); `apps/web` builds clean (`tsc --noEmit`), the full Vitest
  suite passes (366/366, up from 363/363), and `eslint .` reports 0 errors/51 warnings (same
  baseline as before this session). `review.md` and `validation.md` now carry this fix round's
  entries alongside the backend-slice and web-slice review entries, per spec.md's Full-tier
  requirement.
- Next action: 6d is ready to close as a checkpoint, now with both of its own independent reviews
  and this external review's findings addressed. Move to 6e (targeted refresh coalescing and the
  full phase exit gate, AC-7/AC-8) — 6e's own scope already explicitly owns
  `getLocationsWithCounts`/the org-wide broadcast-channel egress and the site-scoping of
  `SupabaseBroadcastService`'s payload, both reaffirmed out of scope by this checkpoint's reviews.
- Surviving decisions: everything recorded in the plain-6d, backend-review-driven-fix, and
  6d-web-implementation/review-driven-fix handoffs above, unchanged by this session, plus this
  session's own: `requireInventoryBelongsToSite` is scalar-only
  (`LocationInventoryRepository.existsByIdAndSite_Id`), never entity-returning, and must stay that
  way — any future site-membership check added ahead of a lock-then-mutate sequence in
  `StockMovementService` must follow the same principle (verify site via a scalar projection or an
  unrelated entity, never the entity about to be locked); `ProductModal`'s inventory table treats a
  failed `useSiteProductInventoryEntries` read as a distinct, retryable error state, never folded
  into the empty-state branch, and gates the Adjust/Transfer actions accordingly.
- Last verified (this session): backend — `./mvnw -q clean test-compile` clean; new IT
  (`StockMovementServiceSiteScopedConcurrentSourceCheckRaceIT`) plus the full concurrency/lock-order
  sibling suite and `SiteInventoryMutationController*IT` all green; `./mvnw -q clean test` 371 run/0
  failures; `./mvnw test -Dtest='*IT'` 510 run/8 pre-existing failures (unchanged set). Web — `npx
  tsc --noEmit -p tsconfig.json` clean; `npx vitest run` 52 files/366 tests, 0 failed; `npx eslint .`
  0 errors/51 warnings. Both new tests individually revert-verified (fix removed, new test
  confirmed to fail for the predicted reason, fix restored, test confirmed green).
- **Open risks/questions (carried forward, see the backend and 6d-web-implementation/review-driven-
  fix handoffs above for full detail — none newly introduced by this session):**
  - One-NOT_ASSIGNED-location-per-site is still not schema-enforced. Monitored, not blocking —
    unaffected by this session.
  - `LocationInventoryRepository.findByStorageLocation_Id`'s missing root-product filter (used
    only by the still-legacy `getStorageLocationInventory`, no current caller) remains a trap for
    a future, not-yet-existing caller. Unchanged.
  - Movement history (T-6d-10) still has no rendered UI consumer. Unchanged.
  - `getLocationsWithCounts`/the org-wide broadcast-channel egress — explicitly 6e scope, not
    touched.
  - No live UI (manual/browser) verification was performed this session, consistent with every
    prior session in this checkpoint.
  - Q-6c-1/Q-6c-4 and the pre-existing `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT`
    flakiness are unchanged, unrelated to this session.

## 6d checkpoint close (2026-09-14)

External independent Standards and Spec review of `e39ad36` confirmed no remaining findings and
that both P1/P2 fixes address the reported bugs. Re-verified locally: `npm run test:run
--workspace=apps/web` -- 52 files/366 tests passed; JDK 21 `./mvnw` targeted run of
`StockMovementServiceSiteScopedConcurrentSourceCheckRaceIT`, `SiteInventoryMutationController
SecurityIT`, `SiteInventoryMutationControllerAtomicityIT` -- 40 tests passed, including the
real-Postgres concurrency regression. Worktree clean; nothing further changed or pushed by that
review pass.

**6d is closed.** Per spec.md's checkpoint table, 6d ("Web adoption") delivered AC-6: inventory
web workflows migrated to v1 site-qualified queries, Products quantities/status restored from
scoped totals only, and rendered/hook test coverage across detail state, role-dependent controls,
stock workflows, site switching, unresolved/error states and late-old-site-result rejection --
closing the two gaps (dialog workflow tests, late-result race test) the web slice's own review
required before this checkpoint's gate could be called satisfied. R-9 is resolved. Both backend and
web slices went through implement -> independent review -> fix, twice each (once per slice, plus
this final external round), with zero unresolved findings.

Three commits on `refactor/inventory-stock` for this checkpoint: `accd2b0` (backend: R-9 resolution
+ v1 batch-transfer route), `232a8ca` (web: v1 adoption), `e39ad36` (external-review fixes: the
transfer concurrency lost-update and the inventory-read error-swallowing bug).

**Next: checkpoint 6e -- "Targeted refresh and exit proof."** Per spec.md's table: "Coalesce
targeted refresh, prove recovery and measured savings, remove only obsolete compatible paths, and
run the complete phase gate (AC-7-8 and regression of AC-1-6)." 6e explicitly inherits, not
re-opens: `getLocationsWithCounts`'s site-blind egress, the org-wide `db-changes` broadcast
channel's lack of site scoping (`SupabaseBroadcastService`'s payload needs `siteId` for real
per-site coalescing, per T-6d-11's scope note), and the one-NOT_ASSIGNED-location-per-site
invariant (monitored, still not schema-enforced). 6e also owns removing the now-obsolete legacy
inventory/stock-movement/location-inventory paths that 6d's v1 routes superseded (the legacy
`LocationInventoryController`/`LocationInventoryMapper`/`LocationInventoryResponseDTO`/
`InventoryRequestDTO`, the untracked PUT route, and whichever `lib/api/inventory.ts`/
`stock-movements.ts` functions T-6d's web slice left in place only for compatibility) -- per this
checkpoint's own design note, their deletion was deliberately deferred to 6e, not forgotten.

## 6e planning — targeted refresh and exit proof worksheet (2026-09-15)

6e scope per spec.md's checkpoint table: "Coalesce targeted refresh, prove recovery and measured
savings, remove only obsolete compatible paths, and run the complete phase gate (AC-7-8 and
regression of AC-1-6)." Planning done in two parallel passes, mirroring 6d: a web-side
baseline/task-list pass (`planner`) and a backend design pass (`mirai-spring-architect`), then
reconciled and four material decisions confirmed with the user before implementation.

### Record correction (backend pass, verified by reading the code)

T-6d-12's residual note (above, "getLocationsWithCounts stays site-blind ... sites module
territory") understated the defect. `LocationAggregateRepository`'s two native queries
(`ALL_LOCATIONS_WITH_COUNTS_SQL`, `getLocationsByTypeWithCounts`) have **no site predicate at
all** -- not "resolves to MAIN," but a genuine cross-site data leak: a MAIN user browsing
`/storage` today sees SECOND's locations and quantities mixed into the same list with no marker,
invisible only because SECOND has almost no seeded locations. This raises T-6e-be-2/3 from
opportunistic cleanup to a live `docs/specs/multi-site-data-and-api.md` §"cross-site joins are
prohibited" violation that this checkpoint must close.

### User decisions (all material, all confirmed 2026-09-15)

- **Unmounted realtime hooks:** delete them. `useRealtimeInventory`, `useRealtimeProductInventory`,
  `useRealtimeDashboard`, `useRealtimeProducts`, `useRealtimeNotifications`, `useRealtimeShipments`,
  `useRealtimeAuditLog`, `use-supabase-realtime.ts`, `legacy-products-query-filter.ts` and their
  barrel exports/tests have zero mounting callers in production (`RealtimeProvider` mounts only
  `useRealtimeBroadcast`) -- T-6d-11's site-scoping work inside them never executes in a running
  browser. The org-wide broadcast channel becomes the single realtime path 6e builds, coalesces
  and measures; no parallel `postgres_changes` subsystem is revived.
- **Broadcast payload carries affected product IDs, not just `siteId`:** confirmed scope
  expansion beyond the 6d handoff's "add siteId" note. Without `ids[]`, batch adjust/transfer
  (`StockMovementService` at :306/:627/:745, the dominant mutation shape) would keep sending
  `itemId: null` despite already holding the affected IDs locally, so AC-7's "known IDs cause no
  full-catalog refresh" and AC-8's "result size follows affected IDs" could not be demonstrated
  for the workload that matters. Single adjust/transfer already carries one ID (:1003/:1072) and
  needs no change beyond the new field name.
- **Broadcast dispatch moves to after-commit, across every producer:** fixed in 6e as a
  correctness precondition, not deferred. Broadcasts fire inside `@Transactional` methods today,
  dispatched via an `@Async` bean, so a rolled-back mutation can still emit a notification and (once
  refresh becomes targeted/coalesced instead of blanket-invalidating) a pre-commit notification
  would refetch pre-commit data with nothing scheduled to correct it -- breaking AC-7's
  duplicate/reordered-notification convergence guarantee. Only the broadcast *call* moves
  (`TransactionSynchronizationManager.registerSynchronization(...).afterCommit(...)`); the
  business logic of every producer (inventory, kuji, shipments, notifications, audit, products)
  is untouched.
- **`SiteLocationController`'s raw-JPA-entity exposure is fixed in 6e, not recorded as a
  deviation:** pre-existing (Phase 4), unrelated to 6e's original scope, but a standing AC-5
  violation ("v1 routes expose DTOs through the application boundary") that this checkpoint's exit
  gate would otherwise have to certify silently. Add a `SiteLocationDTO`, regenerate
  `packages/contracts/openapi.json`/`packages/api-client`, update the web's hand-rolled
  `Location`-shape mapping (T-6d-1) to the generated DTO type.

### Recorded assumptions (not escalated, per AGENTS.md's "ask only material decisions" rule)

- Non-inventory broadcast producers (`ShipmentService`, `KujiBoxService`, `NotificationService`,
  `AuditLogService`, `ProductService`, `ProductDeletionCoordinator`, `EasyPostWebhookService`) are
  **not** migrated to carry `siteId`/`ids[]` in 6e -- only their broadcast dispatch moves to
  after-commit (a mechanical, behavior-preserving change to the call site, not their domain).
  They keep emitting `siteId: null`; the client treats null as "possibly relevant," T-6d-11's
  established precedent. Recorded as debt against Phase 7 (kuji/shipments). `broadcastProductUpdated`
  stays site-less by design (product identity is global), not by omission.
- Legacy `GET /api/locations/with-counts` gets a deprecation header in 6e (matching every other
  legacy inventory route's treatment) but is **not deleted** -- it is `sites`-module territory,
  its removal isn't required by AC-7/AC-8, and 6e's mandate is "remove only obsolete compatible
  paths" for the specific paths already named in the 6d handoff (the four `LocationInventory*`
  classes). Its deletion is follow-up debt for whichever phase completes the `sites` module's
  route cleanup.
- Coalescing window: 300ms. Below ~100ms coalesces almost nothing under a real event burst; above
  ~1s reads as "my adjustment didn't show up." No existing precedent in this codebase. Local
  mutations flush immediately through the same shared executor (satisfying AC-7's "share a
  coalesced, bounded strategy" without adding perceived latency to the user's own action); only
  realtime notifications actually wait out the window.
- Convergence rule for duplicate/reordered notifications: a flush always re-fetches authoritative
  totals for the affected IDs and calls `setQueryData` with the server result, never client-side
  delta arithmetic -- this makes reordering irrelevant by construction rather than by careful
  sequencing.
- `useProductInventoryEntries`/`getProductInventoryEntries` (legacy, unscoped, still used by three
  Kuji dialogs) stays out of scope -- not obsolete (it has live callers), and 6d already deferred
  Kuji's site migration to Phase 7 while preserving the non-MAIN Kuji-unavailable gate (AC-6).
- No live browser/manual verification in 6e, consistent with every prior session in this
  checkpoint; AC-8's web measurement is a scripted Vitest workload against a real `QueryClient` and
  a counting client stub, not a live Supabase websocket session -- stated explicitly in
  validation.md rather than implied as end-to-end proof.
- Movement history still has no rendered UI consumer (unchanged since 6d); the
  one-NOT_ASSIGNED-location-per-site invariant stays monitored/unenforced (unchanged since 6d).

### Backend task list (T-6e-be-N, ordered; suggested commit split 1-3 / 4-6 / 7-11)

- T-6e-be-1: AC-8 "before" measurement for `/api/locations/with-counts`
  (`LocationAggregateEgressBaselineIT`, mirroring `InventoryEgressAfterIT`'s harness), seeded
  across two sites so the cross-site leak is visible in the numbers. Must land before T-6e-be-2 --
  this "before" is unrecoverable once the query changes.
- T-6e-be-2: site-scoped `LocationAggregateRepository`/`LocationAggregateService` overloads
  (`sl.site_id = :siteId` in the outer query, `li.site_id = :siteId` inside the inventory
  subquery), no route yet. Un-scoped methods stay untouched for the legacy route.
- T-6e-be-3: new `sites/api/SiteLocationAggregateController`, `GET
  /api/v1/sites/{siteId}/locations/with-counts` (+ typed variant), reading site from
  `AuthorizedSiteContextHolder`. Security IT includes an explicit assertion that `/with-counts`
  routes here and not into `SiteLocationController`'s `/{id}` pattern.
- T-6e-be-4: broadcast payload envelope -- `siteId` + `productIds` on
  `SupabaseBroadcastService.broadcastInventoryUpdated`/`broadcastAuditLogCreated` overloads;
  existing signatures keep delegating with nulls so non-migrated producers compile unchanged.
  Unit test on the extracted payload-assembly method, no HTTP.
- T-6e-be-5: thread `siteId`/`productIds` through the inventory-module producers
  (`StockMovementService` :306/:627/:745/:1003/:1072, using each site already in local scope).
  IT with a captured `SupabaseBroadcastService`: one batch adjust of N products emits exactly one
  notification carrying the right site and exactly those N IDs.
- T-6e-be-6: move every broadcast producer's dispatch to
  `TransactionSynchronizationManager...afterCommit`. IT: a forced rollback emits zero broadcasts
  (fails today); a commit emits exactly one, observably after commit. Revert-verified.
- T-6e-be-7: `SiteLocationDTO` + mapper for `SiteLocationController`, replacing the raw
  `Location` entity in every response; regenerate contracts/client; update the web's T-6d-1
  hand-rolled mapping to the generated type.
- T-6e-be-8: deprecation header for `/api/locations/with-counts` (new predicate on
  `LegacyInventoryDeprecationConfig` -- the existing filter gates on a trailing `/inventory`
  segment and must not fire on this path or on `sites`' own `/api/locations/{id}`).
- T-6e-be-9: delete the obsolete legacy classes -- `LocationInventoryController`,
  `LocationInventoryMapper`(+Impl), `LocationInventoryResponseDTO`, `InventoryRequestDTO`, the
  four now-orphaned `LocationInventoryService` methods, `LocationInventoryRepository
  .findByStorageLocation_Id` (closes the T-6d-9-recorded trap for good),
  `LegacyLocationInventoryDeprecationFilter` + registration, `LocationInventoryControllerSecurityIT`
  (after case-by-case confirmation every behavioral assertion has a v1 equivalent), the three
  orphaned `LocationInventoryServiceTest` nested classes, the T-6d-be-7 header-test cases; rewrite
  `NotAssignedInventoryReadParityIT` to assert only v1 filter behavior; update
  `module-dependency-edges-baseline.txt`'s stale R-9 note. Web-side deletion (T-6e-7) lands in the
  same commit's diff, before this. ArchUnit run twice (clean rebuilds): frozen store
  `c1d9f1c8-...` expected to shrink by exactly 7 lines on run 1, stable on run 2; store
  `0858803e-...` unchanged.
- T-6e-be-10: regenerate `packages/contracts/openapi.json` + `packages/api-client`, run twice for
  stability. Enumerate the diff by scripted set-diff over `paths`/`components.schemas` keys, not
  by hand. Expect additions (`.../locations/with-counts`, `SiteLocationDTO`), and the phase's
  first path *removals* (4 legacy inventory paths, `InventoryRequestDTO`/
  `LocationInventoryResponseDTO` schemas) -- record each removal in validation.md with its
  zero-caller evidence.
- T-6e-be-11: AC-8 "after" measurement (`LocationAggregateEgressAfterIT` mirroring T-6e-be-1) +
  broadcast fan-out before/after counts from T-6e-be-5; record deltas against T-6e-be-1, restate
  the DTO-layer-not-HTTP-layer measurement boundary explicitly, add the required cost-impact
  statement.

### Web task list (T-6e-N, ordered; suggested commit split 1-4 / 5-7 / 8-11)

- T-6e-1: delete the unmounted `postgres_changes` realtime hooks and their barrel exports/tests
  per the confirmed decision above.
- T-6e-2: typed, site-aware broadcast handler -- extend the broadcast payload type with
  `siteId?`/`ids?`; lift `use-realtime-inventory.ts`'s `isRelevantToCurrentSite` null-is-possibly-
  relevant rule into a shared module used by `use-realtime-broadcast.ts`; mount `useCurrentSite()`
  there and re-derive the handler on site change. `[BE-DEP: T-6e-be-4/5]`.
- T-6e-3: site-qualify every bare inventory prefix in the broadcast handler
  (`locationInventory`/`productInventoryEntries`/`inventoryTotals`/the `itemId` branch), remove
  the dead `notAssignedInventory`/`dashboard` keys. Test: two sites' caches seeded, one event
  fired, only the active site's entries invalidated, the other site's cached data byte-for-byte
  unchanged.
- T-6e-4: coalescing buffer (`hooks/realtime/use-coalesced-refresh.ts`) -- per-site buffer, 300ms
  flush window, ID dedup via `Set`, degrades to "unknown" if any notification in the window lacks
  IDs. Pure module, fake-timer unit tests.
- T-6e-5: targeted flush executor -- known-ID flush calls `getSiteInventoryTotals(siteId, ids)`
  once and merges into the cache (never replaces, never client-side delta arithmetic); unknown/
  reconnect/site-switch flush does today's full invalidate. Wire into `use-stock-mutations.ts`
  (already has `productIds`) and `use-location-mutations.ts` (thread the row's `productId`
  through). `[BE-DEP: T-6e-be-4/5's ids[]]`.
- T-6e-6: reconnect/missed-event recovery -- track prior error/timeout in
  `use-realtime-broadcast.ts`'s subscribe-status callback; a `SUBSCRIBED` following an error
  triggers one full selected-site refresh through T-6e-5's unknown-ID path (not on first mount).
- T-6e-7: delete obsolete web legacy paths -- every zero-caller function in `lib/api/inventory.ts`
  (`getLocationInventory`, `getLocationInventoryItem`, `createLocationInventory`,
  `updateLocationInventory`, `deleteLocationInventory`, `getStorageLocationInventory`,
  `getInventoryByLocation`, `createInventory`, `updateInventory`, `deleteInventory`,
  `getInventoryTotals`) and `stock-movements.ts` (`batchAdjustStock`, `transferStock`,
  `batchTransferStock`, `getStockMovementHistory`), `resolveLocationId`, and
  `use-product-inventory.ts`'s `useProductInventory`. Keep `getNALocationId`/
  `NOT_ASSIGNED_VIRTUAL_ID`/`getProductInventoryEntries`/`useProductInventoryEntries` (Kuji, Phase
  7) and the three audit-log functions. Collapse the three duplicated `"__not_assigned__"` string
  literals into one shared constant. Lands in the same commit as, before, T-6e-be-9's backend
  deletion.
- T-6e-8: dead invalidation-key cleanup -- fix `["auditLogs"]`/`["auditLog"]` to the real
  `["audit-log"]`/`["audit-logs"]` keys (a real bug: stock mutations have never refreshed the
  audit-log page), remove `["dashboardStats"]`, resolve the no-op `exact:true`
  `["locationsWithCounts"]` duplication.
- T-6e-9: `getLocationsWithCounts` site scoping -- migrate `use-locations-with-counts.ts` to a
  site-qualified key against the new v1 route, and unify `use-dashboard-metrics.ts`'s separate,
  never-invalidated `["locations","with-counts"]` cache entry onto the same hook/key. `[BE-DEP:
  T-6e-be-3]`.
- T-6e-10: AC-8 web measurement harness -- scripted workload against a real `QueryClient` + a
  counting client stub, five scenarios (single known ID, 5-ID batch, unknown-ID batch, duplicate
  ID, reordered pair) at a fixed catalog size, pre-6e vs. post-6e request/byte/row counts recorded
  in validation.md alongside the backend numbers, explicit statement of what was not measured (no
  live browser/websocket session).
- T-6e-11: AC-7 rendered/behavior sweep (site switch during an in-flight coalesced flush;
  duplicate/reordered convergence; reconnect recovery) + phase exit gate (`tsc --noEmit`, `vitest
  run`, `eslint .`, regression of the 6d suites unmodified -- `kuji-tab-panel.test.tsx`,
  `product-modal.test.tsx`, `adjust-stock-dialog.test.tsx`, `transfer-stock-dialog.test.tsx`,
  `use-site-product-inventory.test.ts`'s late-old-site-result case). Baseline to beat: 52 test
  files/366 tests/0 failures, 0 eslint errors/51 warnings.

### Next action

Implement backend first (T-6e-be-1 through T-6e-be-11) -- the web task list's T-6e-2/5 depend on
the broadcast payload change (T-6e-be-4/5) and T-6e-9 depends on the new v1 counts route
(T-6e-be-3). Then implement web (T-6e-1 through T-6e-11).

## 6e implementation (T-6e-be-1..T-6e-be-7) (2026-09-15)

### T-6e-be-4/T-6e-be-5 -- broadcast payload envelope (`siteId` + `productIds`)

- `services/SupabaseBroadcastService.java`: `broadcastInventoryUpdated`/`broadcastAuditLogCreated`
  now have `(UUID siteId, ..., List<String> productIds, ...)` overloads; the legacy `(String, String)`
  two-arg signature still exists and delegates with `siteId=null`. New package-private
  `buildInventoryUpdatedPayload`/`buildAuditLogCreatedPayload` extracted for unit testing (no HTTP).
  `broadcastProductUpdated` deliberately untouched/site-less (product identity is global, per the
  confirmed decision).
- `inventory/application/StockMovementService.java`: all five call sites
  (`batchAdjustInventory` :308-309, `transferInventory` :631-632, `batchTransferInventory`
  :751-752, `addInventoryWithTracking` :1011-1013, `removeInventoryWithTracking` :1082-1084) now
  pass the site (from the already-in-scope `LocationInventory`/`Location` entity) and the affected
  product IDs. `batchAdjustInventory`/`batchTransferInventory` (previously `itemId=null` despite
  holding `affectedProductIds` locally) now carry the full affected-ID set -- this is the fix that
  makes the targeted-refresh work possible for the dominant (batch) mutation shape, per the
  confirmed scope-expansion decision.

### T-6e-be-6 -- after-commit dispatch

- New `shared/transaction/AfterCommitRunner.java`: `run(Runnable)` defers to
  `TransactionSynchronizationManager.registerSynchronization(...).afterCommit(...)` when a
  transaction is active, else runs immediately. Placed in `shared`, not inline in
  `SupabaseBroadcastService`, specifically so its anonymous `TransactionSynchronization` class
  doesn't count as a *new* class introduced into the legacy `services` package under
  `ArchitectureTest`'s `legacyTechnicalLayerPackagesDoNotGrow` frozen-violation store (confirmed by
  a failing first attempt: inlining it there broke the freeze with exactly one new
  `SupabaseBroadcastService$1` line).
- `SupabaseBroadcastService`'s constructor now takes a `@Lazy` self-reference
  (`SupabaseBroadcastService self`) so every public `broadcastXxx` method can defer via
  `AfterCommitRunner.run(() -> self.dispatchXxx(...))` while the actual network dispatch
  (`dispatchXxx`, package-private, `@Async`) still goes through the Spring AOP proxy rather than a
  same-instance self-invocation (which would silently skip `@Async`).
- Tests: `services/SupabaseBroadcastServiceTest.java` (8 cases) -- payload assembly
  (siteId/productIds present vs. omitted-when-null-or-empty) and after-commit dispatch: no active
  transaction dispatches immediately; an active transaction defers until `afterCommit()` fires;
  a transaction cleared without commit (simulated rollback) never dispatches.

### T-6e-be-2/T-6e-be-3 -- site-scoped `locations/with-counts`

Also corrected the record: T-6d-12's residual note calling this "silently resolves to MAIN" was
verified false this session -- `LocationAggregateRepository`'s native queries have **no site
predicate at all**, so a MAIN caller's `/api/locations/with-counts` call returns and counts every
site's locations/quantities together. Confirmed live (not just theoretical) via
`LocationAggregateEgressIT`'s "before" measurement below.

- `sites/infrastructure/LocationAggregateRepository.java`: added
  `SITE_SCOPED_INVENTORY_SUBQUERY` (adds `li.site_id = :siteId` inside the aggregation, letting
  Postgres use the `(site_id, product_id)` index and excluding a mismatched-site row from the
  count rather than silently counting it into the wrong site's badge) and the two site-scoped SQL
  constants/methods `findAllLocationsWithCounts(UUID)` /
  `findLocationsByTypeWithCounts(String, UUID)`. The un-scoped originals are kept, marked
  `@Deprecated`, unchanged -- both because the legacy route still needs them and because it keeps
  the "before" behavior measurable after the fact.
- `sites/application/LocationAggregateService.java`: matching site-scoped overloads
  (`getAllLocationsWithCounts(UUID)` / `getLocationsByTypeWithCounts(LocationType, UUID)`),
  preserving the Java-side NOT_ASSIGNED short-circuit identically.
- New `sites/api/SiteLocationAggregateController.java`: `GET
  /api/v1/sites/{siteId}/locations/with-counts`, reading site from
  `AuthorizedSiteContextHolder`, same role set as the legacy route.
- Tests: `sites/api/SiteLocationAggregateControllerIT.java` (5 cases) -- `/with-counts` routes
  here and not into `SiteLocationController.getSiteLocationById`'s `/{id}` pattern; returns only
  the calling site's locations/quantities (a second site's 99-quantity row never appears);
  excludes a `location_inventory` row whose `site_id` disagrees with its location's site;
  foreign-site membership 403; unauthenticated 401.

### T-6e-be-1/first half of T-6e-be-11 -- AC-8 before/after measurement

- New `sites/infrastructure/LocationAggregateEgressIT.java` (real Postgres via
  `BaseKafkaIntegrationTest`), measuring both the legacy and scoped queries in one class rather
  than 6c's separate Baseline/AfterIT files -- safe because the legacy methods were kept
  unmodified, so "before" stayed measurable after the scoped methods were added, not only
  recoverable from history. Two sites seeded, 3 locations/products each.
- **Actual measured numbers** (statements via Hibernate `Statistics`, `apiBytes` via
  `ObjectMapper.writeValueAsBytes`, one JVM run, real Postgres Testcontainer):
  - Before (legacy `findAllLocationsWithCounts()`, no site filter): 1 statement; returned exactly
    3 of siteA's + 3 of siteB's location rows for **both** sites in one undifferentiated call (the
    live cross-site leak -- and because prior test methods' fixtures in the same run accumulate,
    the untouched legacy call actually returned 18 rows by the third test method, growing
    unboundedly with every site ever created against this endpoint).
  - After (site-scoped `findAllLocationsWithCounts(siteId)`): 1 statement; returned exactly the 3
    locations belonging to the calling site, zero belonging to the other site, and zero from a
    deliberately mismatched-site `location_inventory` row (999-quantity probe never appears).
  - Full before/after byte and row deltas plus the cost-impact statement will be finalized in
    validation.md alongside the web-side AC-8 numbers (T-6e-10), per spec.md's requirement to
    record both API-bytes and request/query counts together.

### T-6e-be-7 -- `SiteLocationDTO` (AC-5 fix, confirmed decision, not originally in the 6e worksheet)

- New `sites/api/SiteLocationDTO.java` (flat `id`/`locationCode`/`storageLocationId`/
  `storageLocationCode`/`createdAt`/`updatedAt`, matching the shape the web client already
  extracted by hand from the raw entity) and `SiteLocationController` now returns
  `SiteLocationDTO`/`List<SiteLocationDTO>` from every handler instead of the raw `Location` JPA
  entity, closing the standing AC-5 gap ("v1 routes expose DTOs through the application
  boundary"). `SiteLocationControllerIT`'s existing 7 cases (asserting `$.locationCode`, never a
  nested `storageLocation.id`) needed no changes and still pass unmodified -- confirming the flat
  shape was already what every existing assertion expected.

### Verification (actual commands and results, not paraphrased)

- `./mvnw -q -o compile` / `-o test-compile`: clean, both times.
- `./mvnw -q test -Dtest=SiteLocationControllerIT`: 7/7 pass (unchanged assertions, new DTO shape).
- `./mvnw -q test -Dtest=SiteLocationAggregateControllerIT`: 5/5 pass.
- `./mvnw -q test -Dtest=SupabaseBroadcastServiceTest`: 8/8 pass.
- `./mvnw -q test -Dtest=LocationAggregateEgressIT`: 3/3 pass (real Postgres Testcontainer).
- `./mvnw -q test -Dtest='StockMovementServiceTest,StockMovementServiceConcurrent*IT,SiteInventoryMutationController*IT,AdjustToKafkaIT'`:
  all green, no failures introduced by the new broadcast call-site signatures.
- `./mvnw -q clean test` (full unit/component suite): **379 run (up from 371 baseline -- the 8 new
  `SupabaseBroadcastServiceTest` cases), 0 failures, 0 errors.**
- `./mvnw -q test -Dtest='*IT'` (full IT suite): **518 run (up from 510 -- 5 new
  `SiteLocationAggregateControllerIT` + 3 new `LocationAggregateEgressIT`), 8 failures, name-for-
  name identical to the pre-existing `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT`
  set, no new failure.**
- `./mvnw -q clean test -Dtest=ArchitectureTest` run twice (independent clean rebuilds): both
  green, zero diff in `archunit_store/` or `module-dependency-edges-baseline.txt` -- confirms the
  `AfterCommitRunner` relocation fix actually closed the freezing-rule violation (the first attempt,
  with the anonymous class inline in `SupabaseBroadcastService`, failed this exact check with a
  new `SupabaseBroadcastService$1` line; recorded here as the review-relevant near-miss).

### Deviations from the plan

- T-6e-be-7 (the `SiteLocationDTO`/AC-5 fix) was not in the original 6e worksheet's backend task
  list -- it was added as a confirmed user decision (Q-6e-5/"Entity exposure") after the planning
  pass surfaced it. Implemented as its own task, numbered after the original T-6e-be-6 to avoid
  renumbering the worksheet's other tasks.
- `AfterCommitRunner` (a new `shared.transaction` package) was not anticipated by either planning
  pass; it exists only because of the ArchUnit freezing-rule interaction described above, not
  because of any design change.

### Remaining backend work (not yet done this session)

T-6e-be-8 (deprecation header for `/api/locations/with-counts`), T-6e-be-9 (delete the four
obsolete legacy `LocationInventory*` classes -- must land in the same commit as, after, the web
slice's own legacy deletion per the worksheet's ordering note), and T-6e-be-10 (regenerate
`packages/contracts/openapi.json`/`packages/api-client`, which should happen once, after both the
new v1 route and the legacy deletions are final, not twice).

## 6e implementation (T-6e-be-8..10, T-6e-1..11, review-driven fixes) (2026-09-15)

Continued from the handoff above in the same session. Completed the remaining backend tasks, all
web tasks, and a self-review pass (see below for why it was self-review, not the independent
agent review 6a-6d each had).

### T-6e-be-8 -- deprecation header for `/api/locations/with-counts`

Repurposed `LegacyLocationInventoryDeprecationFilter` (its original target routes were about to
be deleted in T-6e-be-9) rather than writing a new class: now gates on the exact
`/api/locations/with-counts` path instead of the inventory-path regexes, and
`LegacyInventoryDeprecationConfig` drops the now-unneeded `/api/storage-locations/*`
registration. Rewrote `LegacyInventoryDeprecationHeadersIT`'s T-6d-be-7 section into T-6e-be-8's:
removed the two now-invalid `/api/locations/{id}/inventory`/`/api/storage-locations/{id}/inventory`
cases, added `/api/locations/with-counts` (headers present) and the v1 counterpart (headers
absent) cases, kept the `/api/locations/{id}` no-headers regression case. 6/6 pass.

### T-6e-be-9 -- delete the four obsolete legacy classes

Deleted `LocationInventoryController`, `LocationInventoryMapper`, `LocationInventoryResponseDTO`,
`InventoryRequestDTO` after confirming zero remaining production callers on both backend and web
(fresh grep, not assumed). Orphaned-with-them and removed: `LocationInventoryService
.listInventoryAtLocation`/`.listInventoryByStorageLocation`/`.updateInventoryQuantity` (each had
exactly one caller, the deleted controller); `getInventoryById` stayed but was made `private`
(still needed internally by the kept, un-scoped `deleteInventory`). Removed
`LocationInventoryRepository.findByStorageLocation_Id` -- its only caller was the now-deleted
`listInventoryByStorageLocation` -- which permanently closes the missing-kuji-filter trap 6d
recorded as "a trap for a future, not-yet-existing caller."

Found and handled a real gap the design pass's own "case-by-case confirmation" prerequisite was
meant to catch: `LocationInventoryControllerSecurityIT`'s `ProductTests` nested class actually
tests `GET /api/inventory/by-product/{id}`, a route owned by a *different*, still-live controller
(`InventoryAggregateController`) that happened to share a test file with the controller being
deleted, and had no other security coverage anywhere in the suite. Moved it into a new
`InventoryAggregateControllerSecurityIT` before deleting the old file, rather than dropping real
role-gated-access coverage along with the rest of that file's (genuinely superseded) content.

Updated the stale R-9 note in `module-dependency-edges-baseline.txt` and the
`LocationInventoryController` mention in `RBACAlignmentIT`'s Javadoc.

**ArchUnit store regeneration:** the first attempt at the after-commit fix (see below) broke
`legacyTechnicalLayerPackagesDoNotGrow` with a new class in the legacy `services` package; fixed
by relocating the logic, then the deletion's own store shrink was regenerated following the
established 6b/6c procedure (temporarily flip `allowStoreCreation`/`allowStoreUpdate` to `true`
in `src/test/resources/archunit.properties`, run `clean test-compile` + `ArchitectureTest`, flip
back to `false`/`false`, verify stability across two further independent clean runs). Shrank by
exactly the predicted 7 lines; the other frozen store was untouched; `archunit.properties` itself
has no net diff.

### T-6e-be-10 -- final contract regeneration

`OpenApiContractExportTest` run (picks up the already-shipped `SiteLocationAggregateController`/
`SiteLocationDTO` from the prior commit, plus this commit's deletions) then run again for
stability. Scripted Python set-diff (not hand-counted, learning from 6d's own off-by-one):
3 paths removed, 0 added; 2 schemas removed, 0 added -- see validation.md's "6e" section for the
exact list. `packages/api-client` regenerated; `apps/web`'s `toSiteLocation` mapper needed a
matching change to the flat `SiteLocationDTO` shape (see T-6e-be-7 below) -- caught by `tsc
--noEmit`, not assumed.

### T-6e-be-7 (numbered after the backend design pass's own T-6e-be-6, added per the confirmed
"Entity exposure" user decision, not originally in the worksheet's task list)

`SiteLocationController` returned the raw `Location` JPA entity from every handler
(`ResponseEntity<List<Location>>` etc.) -- a standing AC-5 violation predating 6e. New
`SiteLocationDTO` (flat `id`/`locationCode`/`storageLocationId`/`storageLocationCode`/
`createdAt`/`updatedAt`, matching the shape the web client already extracted by hand from the
entity) with a `from(Location)` factory; every handler now returns it instead. `SiteLocationControllerIT`'s
existing 7 cases needed zero changes (they only ever asserted `$.locationCode`, never a nested
`storageLocation.id`) -- confirming the flat shape was already what every assertion expected.
Web's `toSiteLocation` (`lib/api/locations.ts`) updated to the flat shape; its test fixture too.

### Web: T-6e-1 through T-6e-9

- **T-6e-1:** deleted the confirmed-dead `postgres_changes` hooks (`use-realtime-inventory.ts`,
  `use-realtime-dashboard.ts`, `use-realtime-products.ts`, `use-realtime-notifications.ts`,
  `use-realtime-shipments.ts`, `use-realtime-audit-log.ts`, `use-supabase-realtime.ts`,
  `legacy-products-query-filter.ts`) and their test/barrel exports.
- **T-6e-2/T-6e-3:** new `hooks/realtime/site-relevance.ts` (`isRelevantToCurrentSite`, the
  null-is-possibly-relevant rule lifted from the now-deleted `use-realtime-inventory.ts`);
  `use-realtime-broadcast.ts` now mounts `useCurrentSite()`, drops a foreign-site
  `inventory_updated` event before any invalidation, site-qualifies every remaining bare
  inventory key (`locationInventory`, `productInventoryEntries`, `locationsWithCounts`), and
  drops the dead `notAssignedInventory`/`dashboard` keys from `EVENT_QUERY_KEYS`.
- **T-6e-4/T-6e-5:** new `hooks/realtime/use-coalesced-inventory-refresh.ts` (per-site buffer,
  300ms window, `Set`-based ID dedup, escalates to "unknown" on any unknown-ID notification in
  the window, immutable buffer updates throughout -- an early version mutated a `Set` in place
  and tripped this codebase's `react-hooks/immutability` ESLint rule, caught and fixed before
  commit) and `hooks/realtime/inventory-refresh.ts` (`flushInventorySiteRefresh`: known-ID flush
  fetches once and merges into the cache **only if something is already cached** -- otherwise
  falls back to a full invalidate, since a partial known-IDs-only array would look like a
  complete totals list to every reader; unknown-ID flush is a full site-scoped invalidate).
  Wired into `use-stock-mutations.ts` (already had `productIds`) and `use-location-mutations.ts`
  (threaded the create mutation's `payload.productId` through; delete has no productId
  client-side, falls back to a full refresh).
- **T-6e-6:** reconnect recovery in `use-realtime-broadcast.ts` -- tracks whether the channel
  has previously errored/timed out via a ref; a `SUBSCRIBED` following a real interruption
  triggers one full site refresh through the unknown-ID path; the first, normal mount subscribe
  fires nothing.
- **T-6e-7:** deleted every zero-caller function from `lib/api/inventory.ts`
  (`getLocationInventory`, `getLocationInventoryItem`, `createLocationInventory`,
  `updateLocationInventory`, `deleteLocationInventory`, `getStorageLocationInventory`,
  `getInventoryByLocation`, `createInventory`, `updateInventory`, `deleteInventory`,
  `getInventoryTotals`) and `stock-movements.ts` (`batchAdjustStock`, `transferStock`,
  `batchTransferStock`, `getStockMovementHistory`), plus `use-product-inventory.ts`'s legacy
  `useProductInventory` hook (kept the shared `getStatus` helper, still used by the site-scoped
  hook). Collapsed the three duplicated `"__not_assigned__"` literals (`inventory.ts`,
  `locations.ts`, `location-selector.tsx`) into one new `lib/api/not-assigned.ts` module -- a
  new file, not a re-export from either existing module, because `inventory.ts` already imports
  from `locations.ts` and a reverse import would have recreated the exact cycle the original
  duplication existed to avoid.
- **T-6e-8:** fixed `use-stock-mutations.ts`'s `["auditLogs"]`/`["auditLog"]` to the real
  `["audit-log"]`/`["audit-logs"]` keys (stock mutations had never actually refreshed the
  audit-log page -- a real, if minor, user-visible bug, not cosmetic); removed
  `["dashboardStats"]` from `use-location-mutations.ts`.
- **T-6e-9:** new `getSiteLocationsWithCounts`/`useLocationsWithCounts` (site-scoped, via the new
  v1 route) and `useAllLocationsWithCounts` (unfiltered, for the dashboard's separate previous
  cache of the same legacy endpoint -- unified onto the same key family rather than left as two
  independent caches of one site-blind call).

### T-6e-10 -- AC-8 web measurement

New `ac8-web-measurement.test.ts`: scripted five-scenario workload through the real
`useCoalescedInventoryRefresh` + `flushInventorySiteRefresh` code, against a reconstructed
pre-6e baseline (one unscoped full-invalidate per event, no coalescing -- read from the pre-6e
`use-realtime-broadcast.ts` git history, not re-run live). Actual numbers are in validation.md's
"6e" section's table. 8/8 pass.

### T-6e-11 -- rendered/behavior sweep and phase-gate regression

Added one dedicated test proving a site switch mid-flight (event buffered for site-1, site
switches to site-2 before the 300ms window elapses) still flushes against site-1, never
contaminating site-2's cache -- the buffer captures `siteId` at `notify()` time, not at flush
time. Ran the five specific 6d rendered suites the worksheet named directly (not just as part of
the aggregate count): `kuji-tab-panel.test.tsx`, `product-modal.test.tsx`,
`adjust-stock-dialog.test.tsx`, `transfer-stock-dialog.test.tsx`,
`use-site-product-inventory.test.ts` -- 5 files, 16 tests, all pass unmodified.

### Self-review (not independent agent review) and its findings

**Process limitation, recorded honestly:** the session executing this checkpoint had no access
to the `Agent` tool needed to spawn `mirai-spring-reviewer`/`mirai-next-reviewer` (a fork-mode
restriction, not a decision). Every prior checkpoint (6a-6d) got independent-agent review; 6e
got a rigorous self-review applying the same checklist instead. See review.md's "6e" section for
the full disposition and the explicit note that the coordinating session should decide whether
to still run the independent agents before treating 6e as equivalent in rigor to 6a-6d.

Two real coverage gaps were found and fixed, both revert-verified:
- `StockMovementServiceBroadcastArgsIT` (new): the productIds-threading fix into
  `StockMovementService`'s five broadcast call sites had no test asserting the actual arguments
  reaching `SupabaseBroadcastService`. Revert-verified against the exact bug the worksheet
  described (batch paths sending `productIds=null`).
- `use-locations-with-counts.test.ts` (new): zero coverage of the site-scoped
  `useLocationsWithCounts`/`useAllLocationsWithCounts` hooks, including the worksheet's own
  required site-switch-rebind case.

No correctness, tenant-isolation, or security findings beyond these two coverage gaps -- see
review.md for the specific things checked and found already correct (mismatched-site-row
exclusion, coalescing-buffer site-switch safety, `@Lazy` self-injection at real Spring startup).

### Full-suite verification (final, this session)

Backend: `./mvnw -q clean test` -- 379 run, 0 failures/errors. `./mvnw -q test -Dtest='*IT'` --
502 run, 8 pre-existing failures (unchanged set), no new failure. `ArchitectureTest` stable
across independent clean rebuilds, frozen store shrank by exactly 7 lines as predicted. Web:
`npx tsc --noEmit` clean. `npx vitest run` -- 57 files/395 tests, 0 failed. `npx eslint .` -- 0
errors/51 warnings (baseline-identical).

### Commits this session (on `refactor/inventory-stock`)

`69fc6fe` (backend: broadcast payload + locations/with-counts site-scoping + SiteLocationDTO,
T-6e-be-1..7), `c8cfcd8` (backend: delete obsolete legacy inventory-at-location routes,
T-6e-be-8..10), `6c79e7c` (web: coalesced site-scoped realtime refresh, T-6e-1..9), `da3f837`
(web: AC-8 measurement harness, T-6e-10), `90231dc` (web: site-switch regression test, T-6e-11),
`8e99ed0` (review-driven: broadcast-args test), `bf1c9b2` (review-driven: locations-with-counts
hook test).

### Current handoff

**Status:** 6e is implemented end-to-end (backend T-6e-be-1..10, web T-6e-1..11) and
self-reviewed with two real findings fixed and revert-verified. It has **not** been through an
independent agent review (mirai-spring-reviewer/mirai-next-reviewer) -- the executing session
lacked Agent-tool access. The complete phase exit gate (AC-1-8 together) and the PR-gate
authoritative CI run have not been run by this session; per this record's Delivery decisions, no
production apply/deployment is authorized here regardless.

**Next action for the coordinating session:** (1) decide whether to run
`mirai-spring-reviewer`/`mirai-next-reviewer` over this checkpoint's diff before treating it as
closed with the same rigor as 6a-6d, given the self-review already found and fixed two real gaps
by the same method those agents would use; (2) if satisfied, run the complete phase exit gate
(regression of AC-1-6 together with the AC-7/AC-8 evidence already recorded here) and close 6e
and the phase per spec.md's checkpoint table.

**Open risks/questions carried forward, unchanged by this session:** everything listed in the
6d handoffs above (one-NOT_ASSIGNED-location-per-site still unenforced; movement history still
has no rendered UI consumer; Q-6c-1/Q-6c-4 and the `AnalyticsControllerSecurityIT`/
`ForecastControllerSecurityIT` flakiness, all pre-existing and unrelated). Plus this session's
own: non-inventory broadcast producers (`ShipmentService`, `KujiBoxService`, etc.) still emit
`siteId: null` by design, recorded as Phase 7 debt, not re-opened. Legacy `GET
/api/locations/with-counts` is deprecated but not deleted (deliberately, per the confirmed
worksheet decision -- `sites`-module route ownership, out of 6e's "remove only obsolete
compatible paths" mandate for the specific paths named in the 6d handoff).

## Review-driven fix: 6e independent review findings (2026-09-15)

The coordinating session ran the two independent reviewers (`mirai-spring-reviewer` for backend,
`mirai-next-reviewer` for web) that the prior self-review pass had flagged as still owed. Both
returned **block**. Full findings and disposition are recorded in review.md's "6e -- independent
review" section; this entry covers the actual code changes and verification, matching 6b/6c/6d's
review-driven-fix format.

### User decision: R-3, revert the legacy inventory-at-location route deletion

Confirmed by the user (material, since it reverses a checkpoint decision): restore
`LocationInventoryController`/`LocationInventoryMapper`(+Impl)/`LocationInventoryResponseDTO`/
`InventoryRequestDTO`, three of the four `LocationInventoryService` methods (`getInventoryById`
public again, `listInventoryAtLocation`, `listInventoryByStorageLocation` -- not
`updateInventoryQuantity`, see A-5 below), and `LocationInventoryRepository
.findByStorageLocation_Id` from git history at `c8cfcd8^` (the commit before T-6e-be-9's
deletion). Reason: T-6e-be-9 treated the deletion as a mechanical zero-caller cleanup, but the
project's own documented compatibility-removal gate (`docs/baseline/api-v1-map.md`) requires
access-log evidence of no legacy traffic plus a stabilization window on a *released* version --
impossible to satisfy when this same checkpoint's deprecation headers for these exact routes
landed on this same unmerged branch.

- Restored the four classes and `LocationInventoryController`'s GET (list/getById)/POST/DELETE
  endpoints -- **not** its PUT (`updateInventory`), which stays removed per A-5's finding that
  it's an unaudited absolute-quantity setter, exactly what R-9's original resolution removed
  from the API surface. The controller's own comment now records this explicitly.
- Restored `findByStorageLocation_Id`, but this time with the same `parent IS NULL`/
  non-CUSTOM-kuji-parent filter its `findByLocation_Id` sibling already has -- closing the
  6d-recorded "trap for a future, not-yet-existing caller" for real, since that caller (the
  restored `listInventoryByStorageLocation`) exists again.
- Moved the by-product (`/api/inventory/by-product/{id}`) security coverage that had been living
  inside `LocationInventoryControllerSecurityIT`'s `ProductTests` nested class into a permanent
  `InventoryAggregateControllerSecurityIT` (that route belongs to a different, still-live
  controller) before restoring the rest of the old security IT file, so the two files don't
  duplicate coverage of the same route.
- Rewrote `NotAssignedInventoryReadParityIT` a second time: since the legacy method is now
  filtered identically to the v1 read, the test proves read *parity* (both exclude the same
  rows), not a *delta* -- the delta this test originally proved no longer exists by design.
- Updated `RBACAlignmentIT`'s Javadoc and `module-dependency-edges-baseline.txt`'s R-9 note back
  to accurate text (the note now records both the original R-9 rationale and this revert).
- **ArchUnit frozen store:** restored `archunit_store/c1d9f1c8-...` directly from git history
  (`git show c8cfcd8^:...`) rather than via the `allowStoreCreation`/`allowStoreUpdate` flags --
  discovered that those flags only ever *prune* obsolete entries from a frozen store, they cannot
  *re-add* a violation that was previously frozen out and then removed. Verified stable across
  two independent clean rebuilds with both flags back at their normal `false`/`false`.
- Regenerated `packages/contracts/openapi.json`/`packages/api-client`; scripted set-diff against
  `HEAD` confirmed exactly the 3 paths + 2 schemas the R-3 revert restores, nothing else.

### B-1 -- the *other* legacy route, `GET /api/locations/with-counts`, was still a live cross-site leak

Separate from R-3's routes (a different controller, `sites` module, `LocationAggregateController`).
`LocationAggregateService`'s deprecated no-arg `getAllLocationsWithCounts()`/
`getLocationsByTypeWithCounts(LocationType)` called the genuinely site-blind repository methods
directly. Fixed: both now resolve `LocationService.getDefaultSiteId()` and delegate to the
already-built site-scoped overload (T-6e-be-2), matching the default-site-resolution pattern
`LocationInventoryService.listInventoryByStorageLocationCode` already used elsewhere. New
`LocationAggregateEgressIT` test calls the service method the controller actually calls (not the
already-scoped repository method) with a real 555-quantity row seeded under a different site,
confirming it never leaks through. Revert-verified.

### R-4 -- permanent proof of after-commit rollback safety and async dispatch

New `SupabaseBroadcastServiceAfterCommitIT`, deliberately placed in
`com.mirai.inventoryservice.services` (same package as `SupabaseBroadcastService`) so it could
observe the package-private `dispatchInventoryUpdated`. First attempt used a `@SpyBean` +
`doAnswer` on that package-private method directly -- this proved unreliable: Mockito's spy,
wrapping the class's *already-existing* Spring AOP `@Async` CGLIB proxy, either silently bypassed
the async advice (dispatch observed on the calling thread) or corrupted Mockito's own stubbing
state depending on which method was stubbed. Abandoned that approach and instead observed the
dispatch's own "Failed to send broadcast" WARN log line via a Logback `ListAppender` attached to
the class's logger -- a signal that only the real, dispatched call ever emits, immune to the
proxy-layering problem. Two tests: a forced-rollback transaction (via `TransactionTemplate` +
`setRollbackOnly()`) produces zero log lines within a real wait window; a committed transaction
produces exactly one, on a thread different from the calling test thread (proving `@Async` still
applies through the `@Lazy` self-proxy).

### A-5 -- `updateInventoryQuantity` was never actually deleted despite T-6e-be-9's commit message

Re-reading the actual `git show c8cfcd8` diff (not the commit message) found this method was
never removed -- a real oversight in that earlier commit. Deleted now, with zero remaining
callers confirmed by grep (its only caller was the PUT route, which this revert deliberately does
not restore).

### A-7, A-10 -- web dead-code deletion and after-commit guard hardening

Deleted `apps/web/src/lib/api/locations.ts`'s dead `getLocationsWithCounts` (zero callers post
T-6e-9; its "silently resolves to MAIN" comment was the exact claim B-1 refuted). `AfterCommitRunner`
now guards on `isSynchronizationActive() && isActualTransactionActive()`, not synchronization
alone -- synchronization can be active without a real, commit-capable transaction underneath it.
Updated `SupabaseBroadcastServiceTest`'s three existing "active transaction" cases to also call
`TransactionSynchronizationManager.setActualTransactionActive(true)` (needed once the guard
tightened, since they manually initialize synchronization without a real transaction manager)
and added a new case proving the guard itself. Both revert-verified.

### Web Blocker 1/2, Required 4/5, Advisories 6-9, R-2/Required-3

- **Blocker 1:** `flushInventorySiteRefresh`'s merge now seeds every requested ID to
  `{productId, totalQuantity: 0}` before applying the fetched response, honoring
  `InventoryQueries.java`'s documented "absence means 0" batched-mode contract instead of
  leaving a stale non-zero quantity forever.
- **Blocker 2:** the totals refresh in `use-stock-mutations.ts`/`use-location-mutations.ts` now
  `.catch()`es into a plain `invalidateQueries` fallback instead of letting a rejection fail the
  whole (already-committed) mutation -- was otherwise producing a false "Adjustment failed" UI
  state and a real double-adjustment risk on retry.
- **Required 4:** both `flushInventorySiteRefresh` and the broadcast handler's direct
  per-product branch now also invalidate the Kuji dialogs' legacy two-element
  `["productInventoryEntries", productId]` key alongside the site-qualified one.
- **Required 5:** reconnect recovery broadened from totals-only to also invalidate
  `locationInventory`, `locationsWithCounts` (site-qualified) and `productInventoryEntries` (bare
  prefix) for the current site.
- **R-2/Required-3** (same finding, both reviewers): dropped the dead type-qualified
  `locationsWithCounts` invalidation (`data.locationType` is the backend's storage-location-code
  vocabulary, not the frontend `LocationType` enum the cache key uses -- could never match) in
  favor of an unconditional site-prefix invalidation on every `inventory_updated` event.
- **Advisory 6:** `.catch()` added to the fire-and-forget flush calls in
  `use-coalesced-inventory-refresh.ts` and the reconnect-recovery call.
- **Advisory 7:** removed the broadcast effect's unmount-time `flushNow()` call, which
  contradicted `useCoalescedInventoryRefresh`'s own tested "cancels without flushing" behavior;
  added a composed-hook unmount test.
- **Advisory 8:** the merge now keeps whichever of the cached vs. fetched row is newer by
  `lastUpdatedAt`, so two overlapping flushes for the same product can't have an older response
  win by resolving second.
- **Advisory 9:** relabeled validation.md's AC-8 byte figures as derived estimates (6c's
  backend-measured per-row costs), not an independent web-side measurement.

Every fix above is revert-verified (fix removed, new test confirmed to fail for the predicted
reason, fix restored, test confirmed green) -- see review.md's "6e -- independent review"
section for which specific test proves which fix.

**Recorded, not silently skipped:** `use-location-mutations.ts`'s Blocker-2 fix was applied by
the identical pattern to `use-stock-mutations.ts`'s (already revert-verified there), but was not
independently test-covered -- that hook has no existing test file at all, a pre-existing gap this
fix round did not create and chose not to backfill from scratch, out of this round's scope.
Advisory 10 was left to judgment; no specific finding text was available to action beyond what's
already covered above.

### Final verification (this round)

Backend: `./mvnw -q clean test-compile` clean. `./mvnw -q clean test` -- **380 run, 0
failures/errors** (up from 379: +1 new `SupabaseBroadcastServiceTest` case for A-10).
`./mvnw test -Dtest='*IT'` -- **522 run, 8 failures, name-for-name identical to the pre-existing
`AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` set, no new failure** (up from 502:
the R-3-restored security IT class net of its moved `ProductTests`, plus the two new permanent
ITs and the new `LocationAggregateEgressIT` case). `ArchitectureTest` stable across two
independent clean rebuilds; frozen store restored to its exact pre-T6e-be-9 content, confirmed by
`git diff`. Contracts regenerated and stable.

Web: `npx tsc --noEmit` clean. `npx vitest run` -- **57 files, 404 tests, 0 failed** (up from
395: +9 new cases). `npx eslint .` -- **0 errors, 51 warnings**, identical to every prior
checkpoint's baseline.

### Current handoff (superseded by "6e checkpoint close" below)

6e's implementation is now independently reviewed (both `mirai-spring-reviewer` and
`mirai-next-reviewer`, both initially blocked, now both addressed) to the same standard as
6a-6d, superseding the earlier self-review-only handoff above. The complete phase exit gate
(AC-1-8 together, regression of every prior checkpoint's own gate) and the PR-gate authoritative
CI run remain the coordinating session's to run before closing 6e and the phase, per this
record's Delivery decisions (no production apply/deployment authorized here regardless).

## 6e checkpoint close (2026-09-15)

The coordinating session independently reproduced every verification command from a clean
state, rather than trusting the implementing/review sessions' reported numbers: backend
`./mvnw -q clean test-compile` clean; `./mvnw -q clean test` -- 479 run, 0 failures; `./mvnw test
-Dtest='*IT'` -- 522 run, 8 failures, name-for-name identical to the pre-existing
`AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` set, no new failure;
`./mvnw -Dtest=ArchitectureTest test` clean, frozen store confirmed unmodified (`git diff` empty)
relative to before this checkpoint. Web `npx tsc --noEmit` clean; `npx vitest run` -- 57
files/404 tests, 0 failed; `npx eslint .` -- 0 errors/51 warnings, baseline-identical. Full
results recorded in validation.md's "Phase exit gate" section.

**6e is closed.** Per spec.md's checkpoint table, 6e ("Targeted refresh and exit proof")
delivered AC-7 (coalesced, bounded local-mutation/realtime refresh; known IDs cause neither a
full-catalog refresh nor one request per product; reconnect/missed-event/unknown-ID/site-switch
cases retain full selected-site recovery; duplicate/reordered notifications converge to
authoritative state without cross-site contamination) and AC-8 (before/after measurement across
backend query egress and the web refresh path, a labeled-estimate/measured-count distinction, and
a cost-impact statement) while regressing none of AC-1-6 (every 6a-6d suite stays green,
unmodified, inside the same full-suite runs). Both the backend and web slices went through
implement -> independent review -> fix, with two real Blockers and several Required findings
caught and fixed in the review round (a stale zero-quantity cache bug, a false-failure/
double-adjustment risk on mutation refresh, a dead cache-key invalidation that regressed a
working pre-6e behavior, a live cross-site data leak in a legacy route, and a missing
after-commit/async-proxy proof) -- zero findings remain open. One deliberate scope correction
during review: the legacy inventory-at-location routes T-6e-be-9 had deleted were restored
(present, deprecated, not removed) because their removal could not satisfy the documented
compatibility-removal gate on this unmerged branch; the missing-filter trap on
`findByStorageLocation_Id` that a prior checkpoint had flagged and deferred was closed for real
in the same pass, rather than re-shipped.

Commits on `refactor/inventory-stock` for this checkpoint: `550a229` (planning worksheet),
`69fc6fe` + `c8cfcd8` (backend implementation, T-6e-be-1..10), `6c79e7c` + `da3f837` + `90231dc`
(web implementation, T-6e-1..11), `8e99ed0` + `bf1c9b2` + `c130d4a` (self-review fixes and
implementation record), `01e4fdd` + `7d74505` + `413bf96` (independent-review fixes and
disposition record).

## Phase 6 close (2026-09-15)

All five checkpoints (6a inventory module boundary, 6b site-ownership foundation, 6c scoped
inventory backend, 6d web adoption, 6e targeted refresh and exit proof) are closed with every
acceptance criterion (AC-1 through AC-8) delivered and independently reviewed, per spec.md.
Phase 6 ("Inventory and stock movements", parent plan Stage E) is complete on
`refactor/inventory-stock`. Remaining recorded debt, explicitly not blocking this closure per
each checkpoint's own scope decisions: Kuji/lootbox site migration and its remaining site-blind
inventory read (`getProductInventoryEntries`), forecasting projection migration, audited
inter-site transfers, the one-NOT_ASSIGNED-location-per-site invariant (monitored, still not
schema-enforced), non-inventory broadcast producers still emitting `siteId: null`
(`KujiBoxService`/`ShipmentService`/`NotificationService`/`AuditLogService`/`ProductService`/
`ProductDeletionCoordinator`/`EasyPostWebhookService`), and the unbounded `SimpleAsyncTaskExecutor`
backing every broadcast dispatch — all recorded against their owning later phases, not silently
dropped. This branch has not been pushed or merged; that remains the user's own action.
