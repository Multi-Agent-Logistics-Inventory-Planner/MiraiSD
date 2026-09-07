# Phase 5a — Catalog domain module move

## Tier

Full — moves classes across module boundaries, changes an ArchUnit rule, and regenerates frozen
architecture stores (deployment-adjacent risk if a cycle or boundary regression slips through).

## Problem and outcome

`Product`, `Category`, `Supplier` and their services/repositories/controllers/DTOs still live in
the legacy technical-layer packages (`models`, `services`, `repositories`, `controllers`, `dtos`,
`converters`, `exceptions`), unlike `identity`/`sites` which Phase 4 already moved. This blocks
Phase 5b (facade), 5c (`site_products`), and 5d (v1 API) from having a real `catalog` module to add
to. This record moves the code with **no behavior change** — no schema change, no endpoint change,
no DTO shape change.

The move surfaces two structural traps that must be fixed as part of the move, not deferred:

1. `ProductService` currently injects 8 foreign repositories (`ForecastPredictionRepository`,
   `KujiBoxRepository`, `KujiBoxTierRepository`, `MachineDisplayRepository`,
   `ShipmentItemRepository`, `ShipmentRepository`, `StockMovementRepository`, plus
   `InventoryAggregateService` and `StockMovementService`) directly. Moving `ProductService` as-is
   creates a `catalog → {inventory, transfers, shipments, kuji}` dependency the target
   architecture forbids. These fall into **four** distinct call paths, not just the deletion
   cascade — an earlier draft of this record covered only deletion and would have left two
   violations behind:
   - **Deletion cascade** (`ProductService:320`, `348`, `367`, `382-385`, `395-411`) — forecast
     predictions, machine displays, inventory, stock movements, kuji boxes/tiers, shipment-item
     guard.
   - **Creation → inventory write** (`ProductService:160`): `stockMovementService
     .createInventoryWithTracking(...)` writes initial stock with an audit log when a parent
     product is created with `initialStock > 0`. This is the `catalog → inventory` cycle 5b exists
     to remove, reached from catalog's own creation path.
   - **Update → forecast purge** (`ProductService:320`): when `forecastingEnabled` flips true→false,
     `forecastPredictionRepository.deleteByItemId(...)` purges rows **in the same transaction** as
     the product save. Transactional coupling is deliberate here and must survive the extraction.
   - **Read enrichments** (`ProductService:189` `shipmentRepository.findLastDeliveredSupplier...`,
     `ProductService:529` `kujiBoxRepository.findProductIdsWithStatus(OPEN)`).
2. `Product` imports `models.enums.KujiType`, which must move with it or the module gains a
   `catalog → models` dependency.

## Durable context

- Plan: [Enterprise modernization](../../docs/plans/enterprise-modernization.md) §7 (Phase 4
  precedent), §8 (Phase 5)
- Specs: [Spring domain-modular monolith](../../docs/specs/spring-domain-modular-monolith.md),
  [Multi-site data and API](../../docs/specs/multi-site-data-and-api.md) §2
- ADRs: ADR-0001 (domain-modular Spring monolith)
- Sibling records: [5b — catalog facade](../phase-5b-catalog-facade/spec.md) (depends on this),
  [5c — site_products](../phase-5c-site-products/spec.md) (depends on this),
  [5d — catalog v1 and web](../phase-5d-catalog-v1-and-web/spec.md) (depends on 5a-5c)

## Product decisions (recorded, not open)

- Dead code found during investigation — `models/enums/ProductCategory.java` and
  `converters/ProductCategoryConverter.java` have zero live references outside each other — is
  **deleted**, not moved, once T-1 confirms no persistence mapping references them.
- Subcategory disposition: `subcategories` (bootstrap-only SQL table, no JPA entity, no code
  reference) and `products.subcategory_id`/`products.category` (superseded by
  `categories.parent_id` + `UNIQUE(parent_id, slug)`) are recorded here as **retire**, not merged.
  Actually dropping them is deferred out of this record: it is an irreversible schema change and
  first needs a production row-count check (`log.md` T-8) given Flyway is not yet canonical.

## Acceptance criteria

- AC-1: `Product`, `Category`, `Supplier`, their exceptions, and `KujiType` live in
  `catalog.domain`; `ProductRepository`, `CategoryRepository`, `SupplierRepository` live in
  `catalog.infrastructure`; `ProductService`, `CategoryService`, `SupplierService` and the
  `ProductListItem` read projection live in `catalog.application`; controllers, request/response
  DTOs, and mappers live in `catalog.api`. `packages/contracts/openapi.json` is byte-identical to
  `main` (pure package move; springdoc names schemas by simple class name).
- AC-2: `catalog.application` has zero remaining imports of legacy `repositories..`/`models..`
  classes outside `catalog` — verified by grep **and** by the ArchUnit rule, covering all four call
  paths in "Problem and outcome", not deletion alone. **Design accepted after review (superseding
  this AC's original wording, which put the coordinator outside catalog): product deletion is a
  catalog use case — identity, hierarchy, the transaction, and the delete sequence stay in
  `catalog.application`. Only the foreign-repository *access* moves out, expressed as
  catalog-declared ports implemented in each owning module's `application` package.**
  - `ProductService.deleteProduct`'s cascade moves into `catalog.application.
    ProductDeletionCoordinator` (not outside catalog), which orchestrates the full delete using
    `ProductRepository` directly (an allowed same-module repository access) plus five ports —
    `ShipmentUsageGuardPort`, `ForecastPurgePort`, `MachineDisplayCleanupPort`,
    `InventoryCleanupPort`, `KujiBoxCleanupPort` — each implemented by a single-purpose adapter in
    its owning module's `application` package (`shipments`, `analytics`, `displays`, `inventory`,
    `kuji` respectively).
  - Initial-stock creation is expressed as an `InitialStockPort` declared in `catalog.application`
    and implemented by a dedicated `inventory.application.InitialStockAdapter` (not by
    `StockMovementService` directly — implementing the port on that heavily-depended-upon class
    breaks `@MockBean`-based testing of the port in isolation, since Spring's mock-bean mechanism
    replaces the whole bean by type). A transaction test asserts product creation with
    `initialStock > 0` still writes the product, the inventory row, **and** the audit log in one
    transaction, and that a failure in the stock write rolls the product back.
  - The forecasting-off purge is expressed as a `ForecastPurgePort` (same pattern, implemented by
    `analytics.application.ForecastPurgeAdapter`). A transaction test asserts that toggling
    `forecastingEnabled` true→false still deletes forecast predictions in the same transaction as
    the product save, and that a rollback leaves both intact.
  - The two read enrichments are expressed as `SupplierDeliveryHistoryPort`
    (`shipments.application`) and `OpenKujiBoxPort` (`kuji.application`).
  - **Known, accepted residual coupling this AC does not require resolving**: `catalog.application`
    classes (`ProductService`, `ProductDeletionCoordinator`) still import legacy
    `services.SupabaseBroadcastService`, and separately, within `catalog` itself,
    `catalog.application` imports `catalog.api` (`SupplierService` → `ProductMapper`/
    `ProductResponseDTO`/`SupplierResponseDTO`; `ProductListItemDTO` → `CategoryResponseDTO`) — an
    inverted application→API layer dependency the top-level package-cycle count does not expose,
    since both sides are inside the same `catalog` slice. Neither is required to close for this
    record; see `log.md`'s T-6 entry for the full accounting.
  - **`catalog.api.CostVisibilityPolicy` → `identity.domain.{Permission,RolePermissions}` (T-7):
    reconciled against `docs/specs/spring-domain-modular-monolith.md`, not just against ArchUnit's
    cycle check.** §6.2's target graph lists `catalog ──► shared` only — `catalog → identity` is
    not in the intended graph. §4.1 additionally requires cross-module callers to go through "a
    documented public type in the owning module's `application` package," and `RolePermissions`/
    `Permission` are `identity.domain` types called directly, not a published `identity.application`
    facade. **This is a real gap against the durable architecture, not something the frozen-cycle
    check clears** — that check only proves no import cycle exists, which is a different property
    than "matches the intended dependency graph." Accepted here as transitional debt, not silently
    passed: the redaction behavior this dependency preserves is genuine, pre-existing, unchanged
    RBAC behavior (the legacy `ProductController`/`SupplierController` already depended on
    `identity.domain` directly before this move; T-7 relocated that dependency into `catalog`, it
    did not introduce it), and building a new `identity.application` permission-check facade to
    remove it is out of this record's scope — a Full-tier record's own scope discipline argues
    against expanding a package-move task into designing a new cross-module facade.
    **Removal condition**: when `identity.application` publishes a documented permission-check
    facade (e.g. a method alongside `MembershipAuthorizer`'s existing role), `catalog.api`
    should be updated to call that facade instead of `identity.domain` directly, closing this gap.
    Not scheduled to a specific phase; tracked here so it isn't silently forgotten.
- AC-3: `ArchitectureTest.repositoriesAreOnlyAccessedByServicesOrRepositories` is amended in **both**
  directions, not just the caller side:
  - **Target selector expanded.** The rule currently targets only
    `BASE_PACKAGE + ".repositories.."`, so the moment `ProductRepository` lands in
    `catalog.infrastructure` it falls outside the rule entirely and coverage *decreases while the
    build stays green*. The target must also cover domain-module repositories
    (`..catalog.infrastructure..` and the equivalent for every domain module).
  - **Caller side allowed.** Domain-module `..application..`/`..infrastructure..` classes are
    treated equivalently to the legacy `services` layer, so a module's own service reaching its own
    repository is not a violation.
  Verified against injected probe violations for both halves (a controller reaching a domain
  repository must fail; a domain application class reaching its own must pass), per the Phase 3
  precedent.
- AC-4: The frozen cycle-detection and legacy-technical-layer-growth ArchUnit stores are
  regenerated once, deliberately (`allowStoreUpdate` flipped only for the regeneration commit), and
  the diff is manually reviewed to confirm every new entry names `catalog` and no unrelated
  violation kind appears.
- AC-5: `ProductController`'s package-private `applyCostVisibility`/`applyCostVisibilityToListItem`
  statics are promoted into a real `catalog.api.CostVisibilityPolicy` component shared by
  `ProductController` and `SupplierController`. Existing `COSTS_VIEW`/`MSRP_VIEW` RBAC integration
  tests pass unchanged.
- AC-6: `ProductRepository.LIST_ITEM_SELECT`'s hardcoded fully-qualified `ProductListItemDTO`
  constructor-expression string is updated in the same commit as the DTO's move, and a repository
  integration test executes the query against real PostgreSQL (a compile-clean, runtime-broken JPQL
  string is the specific failure mode a mock-based test would miss).
- AC-7: Full `./mvnw test` is green. `openapi.json`/`schema.d.ts` diff is empty.

## Tasks

- T-1: Verify `models/enums/ProductCategory.java` and `converters/ProductCategoryConverter.java`
  have no live `@Convert`/field usage; delete both if confirmed dead.
- T-2: **Review checkpoint.** Amend `repositoriesAreOnlyAccessedByServicesOrRepositories` to cover
  `..application..`/`..infrastructure..`; prove with a probe violation; regenerate the affected
  frozen store and confirm it shrinks by roughly the Phase 4 `sites`/`identity` entries it was
  already silently carrying.
- T-3: Move `KujiType` into `catalog.domain`; update `Product`'s import.
- T-4: Extract `ProductDeletionCoordinator` **into `catalog.application`** (accepted design —
  product deletion is a catalog use case; see AC-2) for `deleteProduct`'s cross-module cascade,
  expressing the foreign-repository access as five catalog-declared ports implemented in the
  owning modules' `application` packages; declare `SupplierDeliveryHistoryPort`/`OpenKujiBoxPort`
  in `catalog.application` with implementations outside it (`shipments.application`/
  `kuji.application`).
- T-4b: Declare `InitialStockPort` and `ForecastPurgePort` in `catalog.application` with
  implementations outside `catalog`, replacing `ProductService`'s direct `stockMovementService`
  (creation, line ~160) and `forecastPredictionRepository` (forecasting-off purge, line ~320)
  calls. Add the two transaction/rollback integration tests named in AC-2 — these paths are
  transactionally coupled today, and a port that silently opens its own transaction would be a
  behavior change that compiles clean.
- T-5: **Review checkpoint (cycle-store regeneration).** Move `Product`, `Category`, `Supplier` +
  exceptions to `catalog.domain`; `ProductRepository`/`CategoryRepository`/`SupplierRepository` to
  `catalog.infrastructure`, updating `LIST_ITEM_SELECT`'s FQN in the same commit. Regenerate the
  cycle store; review the diff for unrelated regressions before accepting.
- T-6: Move `ProductService`/`CategoryService`/`SupplierService` (now free of foreign-repository
  imports per T-4) to `catalog.application`; move `ProductListItem`/DTOs read-model projections to
  `catalog.application`, wire DTOs and mappers to `catalog.api`.
- T-7: Move `ProductController`/`CategoryController`/`SupplierController` to `catalog.api`; promote
  `CostVisibilityPolicy`; re-run RBAC cost-visibility ITs.
- T-8: Record the subcategory retirement decision in `log.md`, including the production row-count
  queries (`subcategories`, `products.subcategory_id`, `products.category`) to run before any
  future drop migration. No schema change in this task.
