# Review

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
