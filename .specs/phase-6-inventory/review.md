# Review

## 6c — Scoped inventory backend (T-6c-11..T-6c-17 checkpoint) — 2026-09-13

**Methodology note:** this checkpoint's review was performed as a critical self-review pass over
the completed diff (T-6c-11 through T-6c-17), not a separately-invoked review agent — the session
that implemented this slice ran as a background fork with no ability to spawn a reviewer subagent.
It follows the same two-pass discipline (Standards, then Spec, synthesized after) the earlier
checkpoints used, and is held to the same bar: findings below were only accepted after tracing the
actual code path and, where a fix was made, re-running the affected tests against real Postgres to
confirm the fix closes the gap rather than papering over it. This is recorded as a residual-risk
caveat, not hidden — the PR gate remains the authoritative independent proof per AGENTS.md.

**Scope reviewed:** T-6c-11 (v1 read routes), T-6c-12 (v1 mutation routes, idempotency wiring),
T-6c-13 (legacy deprecation), T-6c-14 (contract/client regeneration), T-6c-15 (stock-state
compatibility guard), T-6c-16 (boundary/doc updates), T-6c-17 (AC-8 after-measurement), against
spec.md's AC-1, AC-3, AC-4, AC-5, and the portions of AC-7/AC-8 this slice's backend work touches.

### Findings

- **[Standards] act on — cross-site destination-location gap untested.** T-6c-12's site-scoped
  `StockMovementService.transferInventory(siteId, request)` validates only the *source* inventory's
  site before delegating to the un-scoped method; an implicit `destinationLocationId` at a
  different site was not separately covered by any new test, even though the underlying
  `requireSameSite` check (T-6c-4) still catches it. Tracing the code found that check runs *after*
  `LocationInventoryRepository.insertLocationInventoryIfAbsent` has already speculatively inserted
  the destination row — a real write, rolled back by the enclosing `@Transactional`, not "no write
  at all" in the strictest sense. **Fixed:** added
  `SiteInventoryMutationCrossSiteDestinationIT.transfer_foreignSiteImplicitDestinationLocation_
  rollsBackTheSpeculativeInsert`, proving no permanent `location_inventory` row survives at the
  foreign destination and no `StockMovement`/idempotency row is left behind.
- **[Standards] act on — that same new test was first written as a false positive.** Initially
  added inside `SiteInventoryMutationControllerAtomicityIT` (H2 `test` profile), it "passed," but
  for the wrong reason: H2 rejects `insertLocationInventoryIfAbsent`'s native
  `INSERT ... ON CONFLICT` with a syntax error *before* `requireSameSite` ever runs, so the test
  never exercised the intended rejection path. Caught by asking why it passed, not just that it
  did. **Fixed:** moved to its own class, `SiteInventoryMutationCrossSiteDestinationIT`, extending
  `BaseKafkaIntegrationTest` (real Postgres) — it now genuinely exercises `requireSameSite`.
- **[Standards] act on — unsafe blanket-table assertion under the shared `*IT` Postgres instance.**
  The same new test's first version asserted
  `stockMovementRepository.findAll()).isEmpty()`/`eventOutboxRepository.findAll()).isEmpty()`,
  which surfaced only when the *whole* `*IT` sweep ran (not in isolation): a residual row from a
  same-JVM-run class executing immediately before it made the table non-empty, and AssertJ's own
  failure-message rendering then threw `LazyInitializationException` trying to render a detached
  `AuditLog` association, masking the real cause. **Fixed:** scoped the assertion to this test's
  own product id (`stockMovementRepository.findByItem_IdOrderByAtDesc`), matching the tracked-id
  isolation discipline `InventoryEgressBaselineIT`'s own review fix already established for
  exactly this shared-container hazard.
- **[Standards] act on — `@SneakyThrows` introduced a pattern with no other precedent in this
  codebase.** `SiteInventoryMutationController.fingerprint()` used Lombok's `@SneakyThrows` around
  `ObjectMapper.writeValueAsBytes`/`MessageDigest.getInstance`; grepping confirmed it is used
  nowhere else in `inventory-service`, while `CommandIdempotencyService.serialize`/`deserialize`
  (the class this very method feeds) explicitly catches and wraps in `IllegalStateException`.
  **Fixed:** replaced with an explicit try/catch matching that existing convention.
- **[Spec] No blocking findings.** AC-3 (trusted site context, foreign-site rejection, idempotent
  retries tested) and AC-5 (v1 DTOs, slim/batched totals, zero-stock correctness, contract
  regeneration, legacy compatibility with no unintended break) are satisfied for this slice's
  scope. AC-4's envelope/atomicity requirements are unaffected by this slice except for confirming
  the idempotency wiring integrates correctly through the v1 mutation path (proven by
  `SiteInventoryMutationControllerAtomicityIT`/`...SecurityIT`); AC-4's Kafka partition-key cutover
  (Q-6c-4) remains explicitly open, not claimed complete here. AC-6/AC-7's web/realtime halves
  remain 6d/6e's, per the task list's own scope note — T-6c-17's numbers are backend-side evidence
  feeding those later checkpoints, not a claim that they are closed.
- **[Spec] Consider — recorded, not a blocker:** `InventoryTotalsRepository
  .findAllInventoryTotals()`'s native SQL is order-fragile under the shared H2 `*IT` datasource
  (a `ClassCastException` casting a joined UUID column, confirmed pre-existing and unrelated to
  this slice's changes — see log.md's "New open risk" entry). Carried forward as debt for a future
  session, same disposition as the pre-existing `AnalyticsControllerSecurityIT`/
  `ForecastControllerSecurityIT` debt this checkpoint did not introduce and does not fix.

### Disposition

**6c (T-6c-11..T-6c-17) approved**, all four "act on" findings fixed and re-verified in the same
session (see validation.md's 6c entry for the exact commands/results). No finding required a
production-code behavior change beyond the fingerprint-hashing refactor (style/consistency, not a
functional fix) — the two real gaps found (cross-site destination coverage, the false-positive
test) were test-coverage gaps, not production bugs; the underlying `requireSameSite`/idempotency
mechanisms were already correct. Residual risks: Q-6c-1 (production deploy/backfill status) and
Q-6c-4 (Kafka partition-key cutover) remain open, unchanged, both requiring separate authorization
before AC-4/AC-8 can be called complete against production; the `InventoryTotalsRepository`
H2 order-fragility above; R-9 (`LocationInventoryController` not moved into `inventory.api`)
remains open, out of 6c's scope. This disposition does not extend to 6d (web adoption) or 6e
(targeted refresh/exit proof).

## 6b re-review — 2026-09-10

Reviewed the complete uncommitted 6b slice, including V58–V60, all changed movement writers,
site persistence and display fixtures, against AC-2 and the rollout worksheet. Independent
Standards and Spec passes ran in parallel and were synthesized after completion.

- [Standards] The sign-aware V60 fix agrees with transfer withdrawal/deposit writers. Dev seed
  paths set a real site, display swap assertions distinguish the two sites, and all three
  prebuilt movement entry points reject a null site before saving. **No blocking findings.**
- [Spec] Nullable expansion and deferred enforcement preserve old-writer compatibility within
  the agreed 6b scope. Trusted context, scoped queries and event changes remain 6c work.
  **No blocking findings.**
- [Spec] **Consider — recorded:** the concurrent site test uses different locations/products;
  same-row contention and inventory-row site assertions remain unproven and are explicitly
  carried into 6c's concurrency gate. Existing concurrency behavior was not changed by 6b.

**Disposition: fixed 6b implementation approved for progression to 6c planning.** Focused native
validation passed 40 tests (validation.md). This review did not repeat the previously reported
332-test unit suite or 537-test IT sweep.

Residual risks: V58 SQL execution coverage remains absent; production schema/trigger checks,
backfill counts and MAIN-fallback assumptions still require operator verification. V61 must
remain a separate release after the writer prerequisite and verification are satisfied. This
disposition is not production deployment or constraint-enforcement approval.

## Scope reviewed

Planning structure only: one Phase 6 PR with five ordered implementation/review checkpoints,
the shared Full-tier record, acceptance gates and migration compatibility exception.

## Findings

2026-09-09: independent Standards and Spec passes completed in parallel. No planning findings.
Both plans and this record consistently express the agreed five checkpoints within one PR, permit
fix commits, retain the separate-release exception, and preserve the durable phase exit gates.
Caller/table inventory and rollout decisions are explicitly prerequisites, not claimed evidence.

Disposition: planning structure clear. Implementation review and checkpoint dispositions remain
pending; this review does not approve unspecified schema, contract or stock-state changes.

## Residual risk

Caller/table inventory and concrete tests remain to be specified before dependent implementation.
The migration release sequence and global stock-state compatibility remain unresolved; the spec
requires these decisions before the affected changes. No runtime readiness is claimed.

## 6a — Inventory module boundary (AC-1)

Independent implementation review ran across four rounds as 6a's tasks (T-0–T-8) landed; full
detail is in log.md's per-task Result subsections and its two "Review-driven fix" entries. This
section records the checkpoint's final disposition.

**Findings and disposition:**

- Round 1 (T-5, two P2s): (1) `InventoryQueries.findHistoryByItemId` returned the
  infrastructure-layer `StockMovementHistoryView` projection to callers outside `inventory`,
  which would have failed T-7's then-planned boundary rule. Fixed: a new application-owned
  `StockMovementHistoryEntry` record. (2) The 6a task list's own test plan required a real-transaction
  IT proving `applyDelta` preserves a caller-opened transaction (AC-1) before 6a closes; this had
  been prematurely deferred to 6c. Fixed: `InventoryOperationsCallerTransactionIT`. No
  business-behavior regression found in this round.
- Round 2 (T-5, one gap): the task list's delete-on-zero/find-or-create IT (the
  `ShipmentService`/`KujiBoxService` shared path) was still outstanding after round 1's fixes
  landed, and the log's "Current handoff" summary was stale. Fixed: `InventoryOperationsSharedCallerPathIT`,
  and the summary corrected.
- Round 3 (T-6–T-8 slice, two P3s): (1) `InventoryOperationsCallerSetTest` compared caller origins
  by simple name, which a same-named class in a different package could silently collapse into an
  existing entry. Fixed: switched to fully-qualified-name comparison. (2) This checkpoint's
  `review.md`/`validation.md` still held only planning-stage content, not 6a's actual results,
  ahead of checkpoint closure. Fixed: both files updated with 6a's commands, results and
  disposition (this entry; validation.md's "6a" section).

No runtime regression was found in any of the four rounds. The limited Kuji/display migration
scope (T-5, rewiring call sites without migrating those domains' packages) was confirmed supported
by the spec's own Delivery decisions.

**Disposition: 6a (AC-1) approved.** Inventory owns its entities/persistence/workflows; external
production callers use `InventoryOperations`/`InventoryQueries`, enforced live by ArchUnit
(`noProductionClassOutsideInventoryDependsOnInventoryInfrastructure`, T-7) and pinned by a
caller-set test; facades preserve caller transactions, proven by a real-database IT, not only
Mockito. `packages/contracts/openapi.json` stayed byte-identical through the T-6 endpoint moves.

**Carried-forward, not a 6a blocker:** R-9 (`LocationInventoryController`/`LocationInventoryMapper`/
`LocationInventoryResponseDTO`/`InventoryRequestDTO` not moved into `inventory.api` — their
dependency on `catalog.api` types would violate the live `modulesDoNotDependOnAnotherModulesApi`
rule; resolving it needs a catalog-owned application-layer product-summary contract, real design
work for a later checkpoint, not 6a's mechanical-move scope). R-4 through R-8 (see log.md) also
remain open, each flagged for its owning later phase/checkpoint. This approval does not extend to
6b's schema/rollout worksheet (AC-2) or any later checkpoint's acceptance criteria.
