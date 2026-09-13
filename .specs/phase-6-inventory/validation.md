# Validation

## Review-driven fix: 6c checkpoint P1 findings (independent review) — 2026-09-13

Commands and results for the fix recorded in `review.md`'s "Review-driven fix: 6c checkpoint P1
findings (independent review)" entry (actor-id spoofing on v1 mutation routes; null-site rows
hidden on the per-product movements branch).

```sh
cd services/inventory-service
./mvnw -q clean test-compile
./mvnw -q -Dtest=SiteInventoryMutationControllerAtomicityIT test
./mvnw -q -Dtest=SiteInventoryControllerSecurityIT,LocationInventorySiteScopedQueriesIT test
./mvnw -q -Dtest='SiteInventoryMutationController*,SiteInventoryController*,LocationInventorySiteScopedQueriesIT,StockMovementServiceSameSiteTransferTest' test
./mvnw -q -Dtest=SiteInventoryMutationCrossSiteDestinationIT test
./mvnw -q clean test-compile && ./mvnw -q -Dtest=ArchitectureTest test
./mvnw -q clean test
./mvnw test -Dtest='*IT'
./mvnw -q test -Dtest='*IT,!AnalyticsControllerSecurityIT,!ForecastControllerSecurityIT'
./mvnw -q -Dtest=OpenApiContractExportTest test
```

## Result

- `./mvnw -q clean test-compile` — PASS, clean.
- `SiteInventoryMutationControllerAtomicityIT` — PASS, 8/8 (6 pre-existing + 2 new
  actor-spoofing-rejection tests for adjust and transfer).
- `SiteInventoryControllerSecurityIT`, `LocationInventorySiteScopedQueriesIT` — PASS, all green
  (includes the new null-site-inclusion tests at both the repository and HTTP level).
- `SiteInventoryMutationController*`, `SiteInventoryController*`,
  `LocationInventorySiteScopedQueriesIT`, `StockMovementServiceSameSiteTransferTest` — PASS,
  exit 0 (confirms the `StockMovementService` signature changes did not break any other caller of
  the changed overloads or the unaffected same-site-transfer unit tests).
- `SiteInventoryMutationCrossSiteDestinationIT` — PASS, real Postgres/Testcontainers, unaffected by
  the actor-id/fingerprint change (exercises the controller HTTP path, which already derives the
  actor from context).
- `ArchitectureTest`, run after an independent clean `test-compile` — PASS, `archunit_store` diff
  empty (no new module edge — the changed methods only touch types already inside `inventory`
  and `shared.web`).
- `./mvnw -q clean test` (full unrestricted suite) — PASS, exit 0.
- `./mvnw test -Dtest='*IT'` — **478 tests, 8 failures, 0 errors** (up from 474 tests by the 4 new
  IT methods; failure count unchanged). All 8 failures are exactly
  `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT`, confirmed pre-existing and
  unrelated by `./mvnw -q test -Dtest='*IT,!AnalyticsControllerSecurityIT,!ForecastControllerSecurityIT'`
  — PASS, exit 0, 470/470.
- `OpenApiContractExportTest`, then `git diff --stat packages/contracts/openapi.json
  packages/api-client/src/schema.d.ts` — no diff. Both fixes changed only internal
  `StockMovementService`/`StockMovementRepository`/`InventoryQueries` method signatures and
  bodies, not any HTTP route or DTO shape.

## Acceptance criteria evidence

- **AC-3** (trusted site context): actor identity for every v1 mutation now derives from
  `AuthorizedSiteContext.backendUserId()`, never a client-supplied value, closing a real gap in
  the "trusted site context" requirement — proven by the two new
  `SiteInventoryMutationControllerAtomicityIT` tests asserting the persisted actor is the
  principal's id even when the request body names a different one.
- **AC-5**/Q-6c-5 (v1 route correctness, documented compatibility behavior): the per-product
  movements branch now honors the same "include and label unknown-site rows" contract the
  audit-log branch already had, closing the gap between the two branches of one endpoint — proven
  by `LocationInventorySiteScopedQueriesIT`'s repository-level test and
  `SiteInventoryControllerSecurityIT`'s HTTP-level test asserting the response body's
  `siteAttribution: "UNKNOWN"` marker.

## 6c — Scoped inventory backend (T-6c-11..T-6c-17 checkpoint) — 2026-09-13

Environment: JDK 21, `./mvnw` from `services/inventory-service`, `npm` from `packages/api-client`.
Full task-by-task detail (what changed, test names, self-review findings and fixes) is in
`.specs/phase-6-inventory/log.md`'s "6c implementation (T-6c-11..T-6c-17)" section and its
"Checkpoint self-review findings" subsection; this section records the final, independently-rerun
verification state after every fix from `review.md` landed.

## Command and scope

```sh
cd services/inventory-service
./mvnw -q clean test-compile
./mvnw -q clean test
./mvnw test -Dtest='*IT'
./mvnw -q test -Dtest='*IT,!AnalyticsControllerSecurityIT,!ForecastControllerSecurityIT'
./mvnw -q clean test-compile && ./mvnw -q -Dtest=ArchitectureTest test   # run #1
./mvnw -q clean test-compile && ./mvnw -q -Dtest=ArchitectureTest test   # run #2, independent
./mvnw -q -Dtest=OpenApiContractExportTest test
cd ../../packages/api-client && npm run generate
```

## Result

- `./mvnw -q clean test-compile` — PASS, clean, zero errors.
- `./mvnw -q clean test` (full unrestricted suite, plain `test` — skips `*IT.java`) — PASS, exit 0,
  every surefire report green.
- `./mvnw test -Dtest='*IT'` — **474 tests, 8 failures, 0 errors.** All 8 failures are exactly
  `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` (pre-existing, order-fragile
  H2-native-SQL debt documented in log.md, confirmed unrelated to this checkpoint's changes — this
  session never touched `AnalyticsService`/`ForecastService`).
- `./mvnw -q test -Dtest='*IT,!AnalyticsControllerSecurityIT,!ForecastControllerSecurityIT'` — PASS,
  exit 0, 466/466, confirming the 8 failures above are the sole, already-known source of red and
  this checkpoint introduces zero new failures.
- `./mvnw -q -Dtest=ArchitectureTest test`, run after two independent `./mvnw -q clean
  test-compile` rebuilds — PASS both times; `git status --porcelain` on
  `archunit_store/`/`archunit.properties` empty both times (no frozen-store regeneration needed).
  The one genuinely new module-dependency edge this checkpoint introduces (`inventory -> shared`,
  from the v1 controllers reading `shared.web`/`shared.idempotency`/`shared.correlation`) is
  recorded as the file's eighth reviewed addition in
  `src/test/resources/module-dependency-edges-baseline.txt`.
- `./mvnw -q -Dtest=OpenApiContractExportTest test`, then `git diff --stat
  packages/contracts/openapi.json` — 590 insertions, 0 deletions, stable across repeated runs. Six
  new paths added (`/api/v1/sites/{siteId}/inventory/{totals,products/{productId},
  locations/{locationId},movements,adjustments,transfers}`); every existing legacy
  path/operation/schema byte-identical (confirmed by inspecting the diff for any `-` line — none).
- `npm run generate` in `packages/api-client`, then `git diff --stat
  packages/api-client/src/schema.d.ts` — 465 insertions, 0 deletions.
- Individually (all real, this session, re-run after every self-review fix landed):
  - `SiteInventoryControllerSecurityIT` — 11/11 pass (role/site matrix, foreign-site `siteId` 403,
    unknown `siteId` 404, foreign-site entity id 404, per-site product isolation).
  - `SiteInventoryMutationControllerAtomicityIT` — 4/4 pass (rollback-together with no surviving
    idempotency row on failure; commit-together on success; replay does not re-invoke the command
    or duplicate effects; foreign-site source inventory rejected before any write).
  - `SiteInventoryMutationCrossSiteDestinationIT` — 1/1 pass, real Postgres (foreign-site implicit
    destination location rejected, the speculative destination-row insert rolls back with it).
  - `SiteInventoryMutationControllerSecurityIT` — 11/11 pass (401/403/400-missing-header/201 role
    matrix; outbox `idempotencyKey` matches the HTTP header end-to-end; HTTP-level replay does not
    duplicate the effect; a fingerprint-conflicting replay returns 409; foreign-site
    location/source-inventory 404s before any write on both adjustments and transfers; same-site
    transfer succeeds).
  - `LegacyInventoryDeprecationHeadersIT` — 3/3 pass (RFC 8941/RFC 8288 header shapes present with
    no `Sunset` on legacy inventory/stock-movement routes, absent on the v1 route).
  - `ProductStockStateWriterCallerSetTest` — 1/1 pass (caller set pinned to exactly
    `StockMovementService`/`KujiBoxService`).
  - `StockStateGlobalAggregationIT` — 2/2 pass, real Postgres, two sites (global sum/derivation
    across sites for `syncProductTotals`/`calculateTotalInventory`).
  - `InventoryEgressAfterIT` — 3/3 pass, real Postgres (AC-8 after-measurement numbers below).
- `git diff --check` — PASS, no whitespace errors introduced.

## Acceptance criteria evidence

- **AC-1** (facade boundary): unaffected by this slice beyond the new v1 controllers themselves
  depending only on already-approved `inventory.application`/`shared.*` — no new
  `inventory.infrastructure` leak, confirmed by the stable `ArchitectureTest` runs above.
- **AC-3** (trusted site context, foreign-site rejection, concurrent/idempotent-retry proof):
  `SiteInventoryControllerSecurityIT`/`SiteInventoryMutationControllerSecurityIT`/
  `...AtomicityIT`/`...CrossSiteDestinationIT` above — every v1 route reads the site off
  `AuthorizedSiteContextHolder`, a foreign-site id 404s before any write (both for an inventory id
  and an implicit destination location), and idempotent replay is proven both at the direct-call
  level (`@SpyBean` invocation-count assertion) and over real HTTP (duplicate-effect and
  fingerprint-conflict-409 assertions).
- **AC-4** (durable envelope, atomicity, idempotency context): the v1 mutation path's idempotency
  record commits/rolls back in the same transaction as inventory/movement/outbox
  (`SiteInventoryMutationControllerAtomicityIT`), and the outbox event's `idempotencyKey` matches
  the HTTP header end-to-end (`SiteInventoryMutationControllerSecurityIT`). Q-6c-4's Kafka
  partition-key cutover to production remains **not** part of this evidence — it is a
  separately-authorized deploy-time action per P-6, unchanged from T-6c-8's own recorded scope.
- **AC-5** (v1 DTOs, slim/batched totals, zero-stock correctness, contract regen, legacy
  compatibility): `SiteInventoryControllerSecurityIT`'s per-site isolation and empty-entries cases;
  `InventoryEgressAfterIT`'s full-catalog (25 rows, row-per-product guarantee preserved) and
  known-ids-batch (exactly the requested ids) cases; `OpenApiContractExportTest`'s pure-additive
  diff; `LegacyInventoryDeprecationHeadersIT`'s proof that legacy routes are unmodified in
  behavior/status and only gain headers.
- **AC-6**: not this slice's scope (6d, web adoption) — no claim made.
- **AC-7** (coalesced/bounded refresh): partial backend-side evidence only —
  `InventoryEgressAfterIT`'s known-ids case proves the batched query returns exactly the requested
  ids (proportional cost, not full-catalog, not one-request-per-product) and
  `InventoryTotalsRepository.MAX_PRODUCT_IDS_BATCH_SIZE` (T-6c-5) is the documented ceiling. The
  browser/realtime coalescing itself is 6e's; not claimed complete here.
- **AC-8** (before/after measurement): `InventoryEgressAfterIT`'s recorded numbers, diffed against
  the round-2 baseline in log.md:
  - Full-catalog totals: apiBytes 9445 → 2715 (−71%), DB-side projected bytes 4078 → 1023 (−75%),
    same row/statement counts (25 rows, 1 statement) both before and after.
  - Known-ids batch (3 of 25 products): apiBytes 355, dbRows 3, 1 statement — proportional to the
    requested ids, not the catalog size, and not one request per product.
  - Movements page vs. legacy audit-log page: recorded as a non-equivalent, honest data point (the
    two endpoints operate at different granularities — audit-log entries vs. raw movements — per
    log.md's explicit note), not forced into a false before/after pair.
  - The full phase-exit gate (all of AC-1–8, including the browser/realtime half of AC-7/AC-8)
    remains 6e's; this evidence is what 6e regresses against, not a claim that AC-8 is closed.

Result: **6c (T-6c-0 through T-6c-17) satisfies its checkpoint scope.** All four review findings
were fixed and re-verified above. Production readiness for Q-6c-1 (backfill/deploy confirmation)
and Q-6c-4 (Kafka partition-key cutover) remain explicitly outstanding, gated on separate
authorization, and are not claimed as validated by this entry.

## 6b re-review — 2026-09-10

From `services/inventory-service`, using JDK 21 and the project wrapper:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=StockMovementSiteBackfillIT,StockMovementSiteMigrationIT,InventoryOperationsSiteIT,InventoryOperationsTest,MachineDisplayServiceNotificationTest,MachineDisplayServiceBatchQueryGuardTest,ArchitectureTest test
```

PASS — exit 0; seven Surefire reports total 40 tests, zero failures/errors/skips. Counts:
backfill 6, expansion 2, site persistence 4, facade unit tests 10, display notifications 6,
display batch guards 4, architecture 8. Both migration classes executed against PostgreSQL 16
Testcontainers. No frozen-store or archunit.properties changes appeared.

The first sandboxed attempt failed on Mockito JVM attachment and Docker access. The identical
command passed after approved execution outside the sandbox. Output:
`/private/tmp/phase6b-rereview.log` (session-local evidence).

AC-2 evidence covers sign-aware transfer backfill, MAIN fallback/guard/idempotence, nullable
UUID expansion without FK/default, explicit writer site persistence and separate-thread site
isolation. Same-row contention, V58 execution and production verification are not proven by
this focused run. Earlier full-suite totals in log.md were not independently rerun here.

## Command and scope

Documentation-only update, checked from the repository root on 2026-09-09:

- `git diff --check` — PASS.
- `python3` inline documentation check using `pathlib`/`re` — PASS: six documents, 26 local
  Markdown link targets exist, all four Full-tier files present, and five checkpoint rows in spec.md.
  Checked file targets, not generated heading anchors. The six documents are both changed plans
  and this record's spec, log, review and validation files.

## Result

Documentation checks pass. No application code, schema, API or event changes. Native runtime tests are not applicable to this
planning update; Phase 6 implementation acceptance criteria remain unverified.

## Planned implementation evidence

- 6a: JDK 21 with `./mvnw`; relevant unit/integration and ArchUnit checks, including legacy callers.
- 6b/6c: PostgreSQL migration, authorization, concurrency and atomicity tests; affected event
  producer/consumer suites; generated OpenAPI/client checks and contract compatibility.
- 6d/6e: web native test/typecheck/lint commands, rendered workflow/site-switch and recovery tests,
  controlled query/egress measurements, then the full phase exit gate and authoritative PR gate.

Record exact commands, results, evidence paths and remaining limitations as each checkpoint runs.

## 6a — Inventory module boundary (AC-1)

Environment: JDK 21, `./mvnw` from `services/inventory-service`, as required above. Full detail
and intermediate/superseded runs are in `.specs/phase-6-inventory/log.md` (Current handoff,
per-task Result subsections, and the "Review-driven fix" entries); this section records the final
state after T-0 through T-8 and both post-T-5 review rounds.

- `./mvnw -q clean test-compile` — clean, verified stable across multiple independent clean
  rebuilds (required after the T-3/T-4 lambda-synthetic-naming fragility finding; see log.md).
- `./mvnw -q clean test` (full unrestricted suite) — exit 0, zero failures/errors across every
  surefire report, most recently re-run after the T-6/T-7/T-8 slice and again after the
  FQN-vs-simple-name fix to `InventoryOperationsCallerSetTest` below.
- `./mvnw -q -Dtest=ArchitectureTest test` — 8/8 rules pass (was 7 before T-7's new
  `noProductionClassOutsideInventoryDependsOnInventoryInfrastructure` rule), stable across
  independent clean rebuilds. Frozen stores (`legacyTechnicalLayerPackagesDoNotGrow`,
  `repositoriesAreOnlyAccessedByServicesOrRepositories`) were regenerated exactly twice this
  checkpoint (T-3's initial move, T-6's controller/DTO move) — each regeneration diffed against
  the prior saved store and confirmed to only shrink (moved-out classes, one retired
  direct-repository-access violation), never gain a violation.
- `./mvnw -q -Dtest=InventoryOperationsTest,InventoryOperationsCallerTransactionIT,
  InventoryOperationsSharedCallerPathIT test` — 14/14 pass: unit coverage of
  `InventoryOperations`/`InventoryQueries`, an `@SpringBootTest` (H2 `test` profile) proving
  `applyDelta` joins and rolls back/commits with a caller-opened transaction (AC-1's "facades
  preserve caller transactions"), and an `@SpringBootTest` proving the find-or-create/delete-on-zero
  path through real `ShipmentService`/`KujiBoxService` calls.
- `./mvnw -q -Dtest=InventoryOperationsCallerSetTest,CatalogEntityAccessCallerSetTest test` —
  2/2 pass; pins the exact, fully-qualified-name eight-class set of production callers reaching
  `InventoryOperations`/`InventoryQueries` from outside `inventory` (T-7; corrected from
  simple-name to fully-qualified-name comparison after independent review, 2026-09-10, to avoid a
  same-named class in a different package silently collapsing into an existing entry).
- `./mvnw -q -Dtest=OpenApiContractExportTest test` then `diff` against a pre-T6 copy of
  `packages/contracts/openapi.json` — byte-identical (AC-5's "no unintended contract break," for
  the endpoint moves T-6 made).
- `git diff --check` — passed, re-confirmed after every slice (T-5, T-6/T-7/T-8, and the two P3
  fixes below).
- Independent review passed three rounds against the T-5 deliverable (two P2s, both fixed same
  session: an infrastructure-layer type leak in `InventoryQueries.findHistoryByItemId`, and a
  missing 6a-closing IT proof that had been prematurely deferred to 6c) and one round against the
  completed T-6–T-8 slice (two P3s: `InventoryOperationsCallerSetTest`'s simple-name comparison,
  fixed as above; this file and review.md needing actual 6a results before checkpoint closure,
  addressed by this update). No runtime regression was found in any review round.

Result: AC-1 satisfied for 6a's scope — inventory owns its entities/persistence/workflows, external
production callers use `InventoryOperations`/`InventoryQueries` (verified live by ArchUnit, not
frozen), and facades preserve caller transactions (proven by a real-transaction IT, not only
Mockito). R-9 (see log.md) is recorded as open debt carried forward, not a 6a blocker: no
acceptance criterion required moving `LocationInventoryController`'s cluster in this checkpoint.
6b's own worksheet/rollout requirements (AC-2) are unaffected by and not claimed by this checkpoint.
