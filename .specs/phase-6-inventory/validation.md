# Validation

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
