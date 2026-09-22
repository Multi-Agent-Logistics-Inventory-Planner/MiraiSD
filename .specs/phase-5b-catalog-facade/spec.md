# Phase 5b — Catalog facade and consumer migration

## Tier

Full — touches Kuji lifecycle and stock-movement/outbox atomicity in two of the migrated
consumers, and adds/enforces a new cross-module ArchUnit boundary.

## Problem and outcome

Twelve classes outside the future `catalog` module inject `ProductRepository` directly:
`NotificationController`, `ForecastService`, `LocationInventoryService`, `StockMovementService`,
`MachineDisplayService`, `AnalyticsService`, `ProductReportBundleService`,
`InventoryAggregateService`, `ShipmentService`, `KujiBoxService`, `AnalyticsSeedService`,
`DevSeedController` (`SupplierService` also does, but moves into `catalog` in Phase 5a, so it drops
out of this count as an internal caller). Phase 5's exit gate requires "catalog repository ownership
... enforced by ArchUnit/tests" — this record is what actually gets there. It is also the record
Phase 6 (inventory) depends on: whatever these facades' method signatures are becomes Phase 6's
external contract, so publishing them here, not quietly discovering them during Phase 6, matters.

Five of the twelve consumers **write** through `ProductRepository`, and the write surface is wider
than `quantity` alone — an earlier draft of this record scoped it to a `ProductQuantityWriter`,
which cannot actually migrate these callers. The real surface, from the code:

| Write | Call sites | Notes |
| --- | --- | --- |
| `quantity` **and** `isActive` together | `StockMovementService:363-364` (`saveAll` at 370), `891-892` (`save` at 893); `KujiBoxService:1300-1304` | `isActive` is derived as `total > 0` — see the hazard below |
| `isActive` alone | `KujiBoxService:261-262`, `414-415`, `639-640`, `1385-1386` | kuji box open/close/reopen flipping a child product's visibility |
| `KujiBoxTier.linkedProduct` association | `KujiBoxService:1537`, `1543` | `KujiBoxTier.linkedProduct` is a `@ManyToOne Product`, so this needs a **managed JPA entity**, which an immutable `ProductRef` record cannot supply |
| bulk entity writes (dev only) | `AnalyticsSeedService`, `DevSeedController` | exempted, see decisions |
| `preferredSupplier` from delivery | `ShipmentService:1175` (`saveAll`) | covered by `CatalogCommands` |

**Historical behavior, resolved by the durable data/API specification §2:** `StockMovementService:360` computes
`shouldBeActive = total > 0` and writes it to `products.is_active`. So today `is_active` in practice
means "has stock somewhere", not "not retired from the master catalog" — which is the meaning
[5c](../phase-5c-site-products/spec.md) assigns it when it introduces `site_products.is_stocked`.
This is the legacy global stock-derived state, not catalog retirement or a site assortment flag.
Phase 5 must not silently change this
behavior (it would alter kuji Active/Closed tabs and forecasting's `WHERE p.is_active = true`), but
5c records the intended semantics and Phase 6 owns the actual split when `quantity` moves to
`inventory`.

## Carried-forward debt (not this record's scope)

- `catalog.api.CostVisibilityPolicy → identity.domain.{Permission,RolePermissions}`, recorded in
  [5a's spec.md](../phase-5a-catalog-module-move/spec.md) AC-2. Its removal condition has two
  parts, both required: an `identity.application` permission-check facade must exist (closes the
  §4.1 "callers go through a documented facade" violation), **and**
  `docs/specs/spring-domain-modular-monolith.md` §6.2's target graph must be amended to list
  `identity` as an intended dependency of `catalog` (closes the "edge isn't in the intended graph
  at all" gap). Neither alone resolves it — building the facade without amending §6.2 leaves the
  edge itself still unsanctioned; amending §6.2 without the facade leaves the direct
  domain-type-access violation in place. This record does not build that facade or touch §6.2 —
  not in this record's task list, and the facade's shape is identity's decision, not catalog's.

## Durable context

- Plan: [Enterprise modernization](../../docs/plans/enterprise-modernization.md) §8 (Phase 5), §9
  (Phase 6 — the consumer of this record's public contract)
- Specs: [Spring domain-modular monolith](../../docs/specs/spring-domain-modular-monolith.md)
- Depends on: [5a — catalog module move](../phase-5a-catalog-module-move/spec.md)
- Feeds: [5d — catalog v1 and web](../phase-5d-catalog-v1-and-web/spec.md) (controllers call these
  facades, not the repository), and Phase 6 (inventory) directly

## Product decisions (recorded, not open)

- The facade is split narrowly rather than one broad `CatalogFacade`: `CatalogQueries` (global
  reads, returns `ProductRef` records — never the JPA entity, and never cost/MSRP), `CatalogPricing`
  (cost/MSRP reads, isolated so money access stays greppable and role-gate-able), `CatalogCommands`
  (narrow permanent writes: preferred-supplier assignment from a delivery).
- The temporary write surface is modeled to match the table above, each operation individually
  javadoc'd with its Phase 6 disposition, rather than one vague quantity setter:
  - `ProductStockStateWriter.applyStockState(productId, quantity, isActive)` and its batch form —
    covers the paired `quantity`+`isActive` writes. **Phase 6: deleted** with the `quantity` column.
  - `ProductStockStateWriter.setActive(productId, active)` — covers kuji's `isActive`-only flips.
    **Phase 6: replaced** by a per-site assortment mutation once `is_active`'s stock-derived
    meaning is split from its master-catalog meaning (see the hazard above).
  - `CatalogEntityAccess.requireManagedProduct(UUID)` / `getReference(UUID)` — supplies the
    persistence reference other modules need when **writing their own aggregates** that hold a
    `@ManyToOne Product`. This is **not** kuji-only: an earlier draft scoped it to
    `KujiBoxTier.linkedProduct` and could not have migrated the other callers. Confirmed
    association writes: `MachineDisplayService` builds `MachineDisplay.product(...)` in 7 places
    (lines ~100, 200, 455, 583, 648, 704, 821), `KujiBoxService` sets
    `KujiBoxTier.linkedProduct` (1537, 1543), plus shipment-item and location-inventory creation.
    These callers are "read-only" against `ProductRepository` but the entity they read becomes a
    persisted association, so a `ProductRef` record cannot substitute. **T-0 inventories every such
    site and assigns each an explicit strategy** — `getReference` (proxy, no select — the default
    for pure association writes), `requireManagedProduct` (when fields are also read), or
    conversion to a raw UUID FK column where the owning entity already has one. Note that moving
    kuji into its own module in Phase 7 does **not** remove its association to the catalog-owned
    `Product`; only an FK-column conversion would, and that is out of scope here.
  - Every one of these joins the caller's existing transaction — never opens its own.
- `DevSeedController`/`AnalyticsSeedService` keep direct repository access behind a named,
  class-scoped ArchUnit exemption (dev-profile only, no production equivalent per
  `docs/baseline/api-v1-map.md`) rather than building a seeding port for two throwaway consumers.

## Acceptance criteria

- AC-1: `CatalogQueries`, `CatalogPricing`, `CatalogCommands` exist in `catalog.application` as
  `@Service` / `@Transactional(readOnly = true)` (where applicable) facades returning immutable
  records (`ProductRef`, `ProductPricing`). Their method signatures are recorded verbatim in this
  spec's `log.md` as Phase 6's external contract.
- AC-2: The temporary write surface exists exactly as enumerated in Product decisions —
  `ProductStockStateWriter.applyStockState`/`setActive` and `CatalogEntityAccess
  .requireManagedProduct` — each javadoc'd with its named Phase 6/7 disposition, and no broader
  `save(Product)` escape hatch is exposed anywhere.
- AC-2b: Behavior is preserved exactly, not "cleaned up" in passing: `isActive` continues to be
  written as `total > 0` by `StockMovementService`, kuji continues to flip child `isActive` on
  box open/close/reopen, and no call site changes which flag it writes. A test pins the current
  `shouldBeActive` derivation so Phase 6's intended change is a deliberate, visible diff rather
  than an accident here.
- AC-3: A new ArchUnit rule is added, initially frozen, proving it fires before consumers are fixed.
  **It must not copy `modulesDoNotDependOnAnotherModulesApi`'s source selector.** That rule selects
  `businessModulePackages()` — only the named domain modules — and every consumer this record
  migrates still lives in legacy `services`/`controllers`, which are *not* in that list. Mirroring
  it would produce a rule that passes vacuously against exactly the classes it exists to catch.
  The rule is instead: **no production class residing outside `catalog` may depend on
  `catalog.infrastructure..`**, with the dev-seed exemptions from AC-6 named explicitly by class.
  A probe violation placed in a legacy `services` class (not only in a domain module) must fail the
  build, proving the selector actually reaches the legacy packages.
- AC-3b: The rule covers `CategoryRepository` and `SupplierRepository`, not just `ProductRepository`
  — `AnalyticsService` consumes `CategoryRepository` directly today and is migrated in this record
  (an earlier draft enumerated only `ProductRepository` consumers and left this dependency with no
  owning task). `DevSeedController`/`AnalyticsSeedService` also consume it and stay exempt.
- AC-3c: Baseline arithmetic is stated correctly in `log.md`: there are **twelve** external
  production consumers, already excluding `SupplierService` (which becomes internal in 5a), and the
  frozen store counts **dependency violations, not one entry per class** — a single class with
  several repository call sites contributes several entries. Consumer-migration tasks assert the
  store shrinks to a re-counted expected value, never "by N entries for N classes".
- AC-4: The 7 read-only consumers (`NotificationController`, `InventoryAggregateService`,
  `LocationInventoryService`, `MachineDisplayService`, `ForecastService`,
  `ProductReportBundleService`, `AnalyticsService`) are migrated to `CatalogQueries`/
  `CatalogPricing`; the frozen store shrinks by exactly their entries; existing tests for each stay
  green. `AnalyticsService`'s two full-table product scans become an explicit
  `CatalogQueries.allProductRefs()` call and are checked for N+1/pooler-egress regression against
  the existing slim `ProductListItem` projection.
- AC-5: The 3 write consumers with existing `@Transactional` boundaries (`KujiBoxService`,
  `StockMovementService`, `ShipmentService`) are migrated onto `CatalogQueries` +
  `ProductStockStateWriter` + `CatalogCommands` + `CatalogEntityAccess`, joining the caller's
  existing transaction rather than opening a new one. All call sites in the write-surface table are
  migrated — including kuji's four `isActive`-only flips and both `linkedProduct` assignments, not
  just the `quantity` writes. Kuji draw/undo/open/close/reopen, stock adjust/transfer, and
  shipment-receive integration tests stay green, and a new test asserts the outbox row and the
  quantity change still commit atomically.
- AC-5b: `CatalogEntityAccess` usage matches T-0's association inventory exactly — every call site
  is one T-0 listed, using the strategy T-0 assigned it. The check is "no undocumented caller",
  **not** "kuji tier-linking only": `MachineDisplayService`, shipment-item creation and
  location-inventory creation are all legitimate callers per T-0. A test asserts the set of callers
  equals T-0's recorded set, so a new unreviewed consumer fails the build.
- AC-6: `DevSeedController`/`AnalyticsSeedService` are exempted from AC-3's rule by class name (not
  package wildcard) — the rule still fails if a new production class becomes a direct
  `ProductRepository` consumer.
- AC-7: `SupplierService` is confirmed excluded from the "12 consumers" count (it moved into
  `catalog` in 5a); the frozen-store entry count reconciles with the reduced consumer list.
- AC-8: `noModuleDependsOnAnotherModulesInfrastructure`'s freeze is removed once all consumers are
  migrated — this rule now runs live, which is Phase 5's module-boundary exit-gate proof.

## Tasks

- T-0: **Association inventory (input to 5d and Phase 6).** Enumerate every production site that
  assigns a `Product` (or `Supplier`/`Category`) entity into another aggregate's JPA association —
  `MachineDisplay.product`, `KujiBoxTier.linkedProduct`, `ShipmentItem.item`,
  `LocationInventory.product`, `StockMovement.item`, `AuditLog` — and record, per site, the chosen
  strategy (`getReference` / `requireManagedProduct` / existing raw UUID FK). Record the table in
  `log.md`; it is a named input to 5d and Phase 6, not just an internal note.
- T-1: **Review checkpoint (public shape).** Define `CatalogQueries`, `CatalogPricing`,
  `CatalogCommands`, `ProductStockStateWriter`, `CatalogEntityAccess` with unit tests against a
  stubbed repository, deriving each method from the write-surface table and T-0's association
  inventory rather than from `ProductRepository`. No consumer changed yet. Record the final
  signatures in `log.md` for Phase 6 to plan against, including each temporary operation's stated
  removal trigger.
- T-2: Add the outside-`catalog` → `catalog.infrastructure` rule per AC-3, frozen with whatever
  violation count the freeze actually records — count it, do not assert a predicted number (per
  AC-3c, entries are dependency violations, not one per class, across all three catalog
  repositories). Prove it with a probe violation placed in a legacy `services` class.
- T-3: Migrate the read-only consumers to `CatalogQueries`/`CatalogPricing` — including
  `AnalyticsService`'s `CategoryRepository` dependency (per AC-3b) and `MachineDisplayService`'s
  association writes via T-0's chosen strategy. Verify the frozen store shrinks to the re-counted
  expected value (per AC-3c, not "by 7"); add the `AnalyticsService` egress/N+1 check.
- T-4: **Review checkpoint (Kuji lifecycle + outbox atomicity).** Migrate `StockMovementService`
  and `ShipmentService` onto the facades; add the atomic-commit test for outbox + quantity change,
  and the AC-2b test pinning the `shouldBeActive = total > 0` derivation.
- T-4b: **Review checkpoint (kuji entity association).** Migrate `KujiBoxService` — the heaviest
  consumer, with 9 call sites spanning quantity writes, four `isActive`-only flips, and both
  `KujiBoxTier.linkedProduct` assignments. Verify per AC-5b that its `CatalogEntityAccess` calls
  match T-0's assigned strategies (not that it is the only caller — `MachineDisplayService` and
  shipment/inventory creation are legitimate callers migrated in T-3). Split from T-4 because kuji
  lifecycle regressions are a Full-tier trigger on their own and should not share a review with the
  stock-movement migration.
- T-5: Add the class-scoped exemption for `DevSeedController`/`AnalyticsSeedService`.
- T-6: Remove the freeze on `noModuleDependsOnAnotherModulesInfrastructure`; confirm it passes live.
- T-7: Reconcile and record the `SupplierService` bookkeeping note in `log.md`.
