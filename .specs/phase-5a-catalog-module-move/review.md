# Review

## Scope reviewed

The full catalog domain-module move (T-1 through T-8): package relocation of `Product`/
`Category`/`Supplier`, their repositories, exceptions, services, DTOs/mappers, and controllers
into `catalog.{domain,infrastructure,application,api}`; the ArchUnit rule amendment enabling
domain-module repository access; the `ProductDeletionCoordinator` extraction and its five
catalog-declared ports; transaction/rollback behavior across the port boundary; the
`LIST_ITEM_SELECT` JPQL FQN update and its real-Postgres verification; `CostVisibilityPolicy`'s
promotion and RBAC/cost-redaction preservation; and seven rounds of frozen ArchUnit store
regeneration (legacy-growth, repository-access, cycle-detection). Authorization behavior
(cost/MSRP redaction, RBAC role gates) and architectural-boundary conformance (module dependency
graph, not just cycle-freedom) are both in scope, per the record's Full-tier triggers
(cross-service behavior, RBAC/auth touched by `CostVisibilityPolicy`'s relocation).

Findings below synthesize two passes — a **Standards pass** (repo conventions, module-boundary
fidelity against `docs/specs/spring-domain-modular-monolith.md`, correctness, process reliability)
and a **Spec pass** (does the diff satisfy every `AC-N` in `spec.md`) — conducted across the
conversation as each task landed, not only at closeout. Several findings below were raised by
review and are recorded with their resolution, not just their existence.

## Findings

- [Standards] `catalog.api.CostVisibilityPolicy` depends on `identity.domain.Permission`/
  `RolePermissions` directly. `docs/specs/spring-domain-modular-monolith.md` §6.2's target
  dependency graph lists `catalog ──► shared` only, not `identity`; §4.1 additionally requires
  cross-module callers to go through a documented `application`-package facade, not a domain type.
  Passing the frozen-cycle ArchUnit check does not establish compliance with this — a cycle check
  only proves no import cycle exists, a different property. — **act on**: recorded as explicit
  transitional debt in `spec.md` AC-2, with a stated removal condition (an `identity.application`
  permission-check facade, which does not exist yet) rather than left implicit or treated as
  cleared by the passing cycle check. Not fixed by building that facade now — out of this
  package-move record's scope, and the underlying RBAC behavior is genuine, pre-existing behavior
  relocated, not newly introduced.
- [Standards] `catalog.application.{ProductService,ProductDeletionCoordinator}` depend on legacy
  `services.SupabaseBroadcastService`; separately, `catalog.application` depends on `catalog.api`
  (`SupplierService` → `ProductMapper`/response DTOs; `ProductListItemDTO` →
  `CategoryResponseDTO`), an inverted application→API layer dependency the top-level
  package-cycle count cannot see at all (both sides are inside the same `catalog` slice). —
  **act on**: both recorded as named residual debt in `spec.md` AC-2 and `log.md`'s T-6/T-7
  entries, kept distinct from each other and from the `identity` finding above rather than
  merged into one vague "some coupling remains" note. Neither closed in this record.
- [Standards] Initial design for `ProductDeletionCoordinator`, `ShipmentSupplierDeliveryHistoryAdapter`,
  and two other adapters placed them in the frozen legacy `services` package ("outside catalog," a
  literal reading of the original task text), which would have failed
  `legacyTechnicalLayerPackagesDoNotGrow`. — **act on**: redesigned mid-task (T-4) after review —
  `ProductDeletionCoordinator` moved into `catalog.application` (deletion is a catalog use case;
  only foreign repository access needed to leave `catalog`), with five catalog-declared ports
  implemented by dedicated single-purpose adapters in each owning module's `application` package.
  `spec.md`'s AC-2/T-4 wording corrected to match the design actually built, not the original draft.
- [Standards] `StockMovementService implements InitialStockPort` broke `@MockBean`-based testing
  of that port in isolation — Spring's mock-bean mechanism replaces the whole bean by type,
  deleting `StockMovementService` (and everything depending on its concrete type, e.g.
  `AuditLogDTOMapper`) from the context. — **act on**: extracted a dedicated
  `inventory.application.InitialStockAdapter`, matching the single-purpose-adapter pattern used
  for the other four ports. Found only by attempting to write the transaction-behavior tests the
  review required, not by inspection.
- [Standards] `ProductDeletionCoordinatorIT` (T-4) extends `BaseIntegrationTest`, which is itself
  `@Transactional` and rolls every test back — it cannot prove commit, rollback, or
  `TransactionSynchronization.afterCommit()` behavior, only DI wiring. — **act on**: added
  `ProductLifecycleTransactionBehaviorIT`, which deliberately does not extend the transactional
  base, so each call under test runs a real transaction that actually commits or rolls back.
  Covers initial-stock-failure rollback, forecast-purge-failure rollback (with the save it's
  coupled to), deletion-failure restoring a previously-deleted child row, and a positive
  commit-then-broadcast case — not just the negative half.
- [Standards] Shipment-usage-guard *rejection* path (`ProductInUseException` when a product or
  child is referenced by a shipment) had no behavioral test — an earlier pass deferred it as "out
  of proportion" without a stubbed-port alternative being considered. — **act on**: added two
  tests using a stubbed `ShipmentUsageGuardPort` (no shipment fixture needed) — one for a
  referenced parent, one for a referenced child specifically proving discovery happens before any
  sibling is processed.
- [Standards] T-5's "no fully-qualified/implicit references" sweep script was silently broken:
  this shell is `zsh`, and `for c in $classes` does not word-split an unquoted variable the way
  bash does, so the sweep only ever checked one concatenated string as a single pattern. It
  produced false "clean" results across T-5's package moves. No incorrect code shipped — the
  Java compiler caught every real gap regardless, just across more iterative rounds than
  necessary — but the sweep's stated confidence was not earned. — **act on**: rewritten (T-6
  onward) as a shell-agnostic `while IFS= read -r c` loop reading from a file; the corrected
  version's false positives (self-package references, Javadoc/comment text) were each checked
  individually against the real package layout before being dismissed, not blanket-trusted either.
- [Standards] My own T-4/T-7 cycle-store edge-reconciliation used a `grep` pattern
  (`services|models|repositories|dtos|exceptions|controllers`) that omitted `identity`, so the
  first attempt to explain T-7's cycle-count increase was incomplete. — **act on**: caught before
  finalizing (T-7) by rerunning the outbound-edge scan without the restrictive pattern; the
  `identity` edge was then correctly identified, verified as the sole new cause of the increase
  (43 catalog cycles, no third source beyond the two named edges), and is the same edge covered
  by the architectural finding above.
- [Standards] Full-tier SDD artifacts (`review.md`, `validation.md`) were not created alongside
  `spec.md`/`log.md` as the eight tasks landed, despite this record being classified Full-tier
  from the outset. — **act on**: this document and `validation.md` added at closeout, covering
  the full T-1–T-8 scope rather than being backfilled piecemeal.
- [Spec] AC-1 through AC-8 (spec.md's tasks; note the acceptance criteria are numbered AC-1
  through AC-7 in `spec.md`) — see `validation.md` for the evidence table. No open gaps found on
  this pass beyond what's already recorded as accepted transitional debt above.

## Residual risk

- **`catalog → identity` dependency does not match the durable target graph** (see Standards
  finding above). Accepted as transitional debt with a recorded removal condition, not
  unconditionally accepted. Risk is low in practice (the behavior is unchanged, pre-existing RBAC
  logic, not new authorization surface), but the architectural gap is real until an
  `identity.application` facade exists to replace the direct `identity.domain` call.
- **`SupabaseBroadcastService` cross-module edge** (`catalog.application` → legacy `services`).
  Two candidate resolutions recorded (move the service to `shared`, or introduce a narrow
  catalog-declared broadcast port), neither chosen — a design decision deliberately left to
  whoever picks it up, since wide legacy usage alone doesn't establish `shared` is the right home.
- **Intra-catalog `application → api` layer inversion** (`SupplierService`/`ProductListItemDTO`
  depending on `catalog.api` types). Invisible to the cycle-count metric entirely; found only by
  direct inspection. No fix proposed in this record beyond the two remediation shapes named in
  `spec.md`.
- **Two adjacent DevSeed/seed-only exemptions were not re-examined this pass**
  (`DevSeedController`/`AnalyticsSeedService` continuing direct `ProductRepository` access,
  pre-existing from Phase 5b's design, not re-verified here since this record's scope was the
  package move, not the facade). Not a new risk from this record, but flagged for whoever next
  touches catalog's repository-access rule.
- Flyway is still not canonical (Phase 1's known open item) — every schema-adjacent action in this
  record (none were schema changes; T-8 stops short of one deliberately) inherits that risk if
  and when a future record acts on T-8's recorded row-count queries.
