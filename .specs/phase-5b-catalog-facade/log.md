# Implementation log

## Assumptions and decisions

- None yet beyond what's recorded in `spec.md`'s "Product decisions" section and the
  "Carried-forward debt" section (the `CostVisibilityPolicy → identity.domain` gap, unresolved,
  not this record's scope).

## Task record

### T-0 — Association-write inventory (input to T-1/5d/Phase 6)

**This entry replaces an earlier, materially wrong version of T-0, caught by review.** The first
pass classified a builder call site as "needs `CatalogEntityAccess`" whenever the physical
`.product(`/`.item(` line sat inside a method that, *somewhere*, does a `productRepository` fetch
— without checking whether the *specific local variable* on that line actually came from that
fetch, as opposed to from navigating an already-loaded association on some other entity
(`tier.getLinkedProduct()`, `box.getProduct()`, `display.getProduct()`, `mv.getItem()`,
`inventory.getProduct()`, `shipmentItem.getItem()`). Several of these existing-association sites
sit inside the *same private helper method*, or even the same file, as a genuine direct-fetch
site, which is exactly what made the shortcut look safe and wasn't. Review found this had produced
wrong strategy assignments on at least three lines and an internally inconsistent site count (a
prose summary of "27" that didn't match the table it summarized, itself built from the same
under-verified classifications) — re-auditing this by tracing every `Product`-typed local variable
to its actual declaration (not just to "a method that also happens to fetch a product") found the
error was systemic, not limited to the three lines flagged: roughly two-thirds of the original
table's "Yes" rows were wrong.

**Corrected method.** Enumerated every production call site that assigns a `Product` entity into
another aggregate's JPA association, by grepping every builder invocation for the six association
fields (`MachineDisplay.product`, `KujiBoxTier.linkedProduct`, `KujiBox.product` — not named in
spec.md's draft, found by checking `KujiBox.java` once `KujiBoxService:203` didn't match any of the
five named fields — `ShipmentItem.item`, `LocationInventory.product`, `StockMovement.item`).
`AuditLog` holds no `Product` association (`kujiBoxProductId` is a plain `UUID` column) and drops
out of scope entirely. Then, for **every** hit, traced the specific variable passed into the
builder back to its actual declaration — via `grep -n 'Product \w\+ =\|Map<UUID, Product> \w\+ ='`
across each file, not by re-reading only the first method found — classifying each declaration as:

- **Origin** (needs `CatalogEntityAccess` once the direct repository access is removed): a
  `productRepository.findById(...)`/`findAllById(...)`/`save(...)` call, or a `Map`/`List` built
  directly from one.
- **Pass-through** (needs nothing — the entity already arrived managed, via a different module's
  own already-loaded association): `x.getProduct()`, `x.getItem()`, `x.getLinkedProduct()` where
  `x` was itself loaded via a *different* repository (`LocationInventoryRepository`,
  `MachineDisplayRepository`, `StockMovementRepository`, `KujiBoxTierRepository`,
  `KujiBoxRepository`, `ShipmentItemRepository`, `ShipmentRepository`).

**Shared private helpers do not have a fixed classification — their callers do.** Several builder
lines sit inside a private method called from multiple places with *different* provenance at each
call site (`KujiBoxService.executeKujiSourceRemoval`, `MachineDisplayService`'s
`DisplayChange`-driven `StockMovement` builder in `batchSwapDisplay`,
`ShipmentService.addToInventory`/`addToNotAssignedInventory`/`removeFromInventory`/
`removeFromNotAssignedInventory`). The helper itself is pass-through: it just uses whatever
`Product` parameter its caller supplied. The correct unit of analysis is therefore **the call
site that first obtained the variable**, not the shared builder line — a helper called from five
places can have two `CatalogEntityAccess`-needing callers and three that need nothing, all
executing the identical internal `.item(product)` line. Treating that internal line as
one fixed-strategy "site" (the original mistake) conflates provenances that must stay separate.

For every origin that *does* need `CatalogEntityAccess`, the assigned strategy is uniformly
**`getReference`**, not `requireManagedProduct`: every genuine origin already has the product's
existence established by its own `findById`/`findAllById` call (or a batch map filtered to
known-existing ids) — no origin needs the facade itself to additionally verify existence.
`requireManagedProduct` has zero production callers today; `spec.md` AC-2 still requires the
method to exist for callers Phase 6 may add.

**Superseded by the second correction below — kept here, struck through in effect, only so the
reasoning trail isn't silently deleted.** ~~Two origins have a materialization nuance, not a
different strategy: `KujiBoxService`'s auto-create tier-linking branches (lines 263 and 1387,
`linkedProduct = productRepository.save(linkedProduct)` right after
`productService.createProduct(...)`) hand off a product created *in the same call*, not looked up
by a pre-existing id. Once product creation moves onto `CatalogCommands`, the natural replacement
is whatever managed reference that call returns directly — no separate `getReference` call needed
for that branch.~~ This was wrong on two counts, both fixed in "11 genuine origins require
`CatalogEntityAccess`, not 13" below: these two branches are not `CatalogEntityAccess` callers at
all (not even via a special-cased strategy), and there is no future `CatalogCommands` creation API
to move onto — `ProductService.createProduct` already is that operation, today, unchanged.

#### Genuine origins (require `CatalogEntityAccess` after migration)

| # | Origin (current direct `ProductRepository`/`SupplierRepository` access) | Feeds (physical builder line) | Strategy |
| --- | --- | --- | --- |
| 1 | `MachineDisplayService.java:78` (`findById`, `setDisplay`) | `MachineDisplay.product` (100), `StockMovement.item` (121) | `getReference` |
| 2 | `MachineDisplayService.java:179` (`findAllById` batch, `setDisplayBatch`) | `MachineDisplay.product` (200), `StockMovement.item` (226) | `getReference` |
| 3 | `MachineDisplayService.java:434` (`findById`, `swapDisplay`) | `MachineDisplay.product` (455) — this method builds no `StockMovement` | `getReference` |
| 4 | `MachineDisplayService.java:565` (`findAllById` batch, `batchSwapDisplay` **add-loop only**) | `MachineDisplay.product` (583), `StockMovement.item` (745, **only** for the `DisplayChange` entries this loop constructs — see note below) | `getReference` |
| 5 | `LocationInventoryService.java:85` (`findById`) | Threaded as a parameter into `StockMovementService.createInventoryWithTracking`, which itself builds `LocationInventory.product` (`StockMovementService.java:700`) and `StockMovement.item` (`:735`) | `getReference` |
| 6 | `ShipmentService.java:146` (`findAllById` batch, `createShipment`) | `ShipmentItem.item` (158) | `getReference` |
| 7 | `ShipmentService.java:306` (`findAllById` batch, `updateShipment`) | `ShipmentItem.item` (354) | `getReference` |
| 8 | `KujiBoxService.java:149` (`findByIdWithCategories`, `createBox`) | `MachineDisplay.product` (196), `KujiBox.product` (203) | `getReference` |
| 9 | `KujiBoxService.java:234` (`findById`, `createBox` tier-loop, existing-product branch) | `KujiBoxTier.linkedProduct` builder (270) | `getReference` |
| 10 | `KujiBoxService.java:1360` (`findById`, `addTier`, existing-product branch) | `KujiBoxTier.linkedProduct` builder (1394); also threaded into `executeKujiSourceRemoval` via the call at 1440, feeding its internal `StockMovement.item` (1877) | `getReference` |
| 11 | `KujiBoxService.java:1541` (`findById`, tier-patch method) | `KujiBoxTier.linkedProduct` setter (1544); also threaded into `executeKujiSourceRemoval` via the call at 1564, feeding its internal `StockMovement.item` (1877) | `getReference` |

**11 genuine origins require `CatalogEntityAccess`, not 13.** The auto-create branches
(`KujiBoxService.java:263` and `:1387`, feeding the same `KujiBoxTier.linkedProduct` builder calls
as origins 9 and 10's auto-create case) are excluded from this table entirely — they need NO
`CatalogEntityAccess` call at all, not a special "materialization" strategy within it, which is
what an earlier draft of this table wrongly implied by giving them their own numbered rows with a
strategy column filled in. `KujiBoxService.java:263` and `:1387`
(`linkedProduct = productRepository.save(linkedProduct)`, immediately after
`productService.createProduct(...)`) do not need a facade accessor because they never re-fetch by
id in the first place — `ProductService.createProduct` (the existing, already-in-catalog method)
returns the managed entity directly, and that same reference is what feeds the
`KujiBoxTier.linkedProduct` builder call (270, 1394, auto-create case) — no new "creation API" on
`CatalogCommands` is needed, since product creation is already `catalog`'s own operation reached
through `ProductService`, not something `KujiBoxService` does via `ProductRepository` today. The
`linkedProduct.setIsActive(true)` call immediately preceding each `save` (lines 262, 1386) is the
one thing that changes: once `KujiBoxService` loses direct `ProductRepository`/`save` access, that
flag flip routes through `ProductStockStateWriter.setActive(newProductId, true)` — the same method
already assigned to every other kuji `isActive`-only flip in `spec.md`'s write-surface table — not
through a raw entity mutation. Because `ProductService.createProduct` already sets `isActive` on
creation in the normal case, this flip is likely already redundant in production behavior; that's
worth a T-4b check, not assumed away here.

Note on #4 / line 745: `batchSwapDisplay` builds every `StockMovement` from a shared
`DisplayChange` list fed by **four** loops — the "add" loop (origin #4, needs
`CatalogEntityAccess`) and three loops that pull `product = display.getProduct()` from a
`MachineDisplay` already loaded via `machineDisplayRepository.findAllByIdInWithProduct(...)` (the
"remove", "from-target", and "to-target" loops — all pass-through, need nothing). Line 745 itself
is not one fixed-strategy site; it depends on which loop produced the particular `DisplayChange`
being mapped. Line 1877 (inside `executeKujiSourceRemoval`) has the same shape: 3 of its 5 callers
(lines 341, 1192, 1238 — all passing `tier.getLinkedProduct()`) are pass-through, only the callers
at 1440 and 1564 (origins #10, #11) need `CatalogEntityAccess`.

#### Pass-through sites verified (require nothing — listed so a future re-audit isn't tempted to
recount them as callers)

`MachineDisplayService.java:289,384,648,704,745(3 of 4 cases),821,844` (`display.getProduct()`/
`existing.getProduct()`); `StockMovementService.java:241,542,583,598,803` (`inv.getProduct()`/
`sourceInventory.getProduct()`/`destinationInventory.getProduct()`/`inventory.getProduct()`);
`ShipmentService.java:939,958,991,1010,1068,1125` (all six trace to `shipmentItem.getItem()` at
receive/undo time — `addToInventory`/`addToNotAssignedInventory`/`removeFromInventory`/
`removeFromNotAssignedInventory`'s sole callers, lines 606/611/819/821, all pass
`shipmentItem.getItem()`); `KujiBoxService.java:313,397,583,758,913,941,1290,1324,1877(3 of 5
cases),1925,1953,2014,2048,2063` (all trace to `tier.getLinkedProduct()`, `box.getProduct()`, or
`mv.getItem()`); `KujiBoxService.java:1538` (`setLinkedProduct(null)` — clears, no entity needed).

#### Write consumers, not entity-access consumers (both missing from the original table entirely)

- `Product.preferredSupplier` (setter) — `ShipmentService.java:1170`
  (`product.setPreferredSupplier(supplier)`), where `product = item.getItem()` (line 1164, an
  existing `ShipmentItem` association) and `supplier = shipment.getSupplier()` (line 1156, an
  existing `Shipment` association) — **neither side needs `CatalogEntityAccess`**, since both
  entities already arrived managed via existing associations. This is `CatalogCommands
  .assignPreferredSupplierFromDelivery`'s write surface (spec.md's write-surface table, "preferred
  Supplier from delivery" row): after migration `ShipmentService` passes only
  `(supplierId, candidateProductIds)`, and `CatalogCommands` resolves its own `Supplier` reference
  internally (`supplierRepository.getReferenceById`, already implemented this way in T-1).
- `Shipment.supplier` (builder and setter) — `ShipmentService.java:127`
  (`.supplier(supplier)`, `createShipment`) and `:273` (`shipment.setSupplier(supplier)`,
  `updateShipment`), where `supplier` in both cases comes from `supplierService.resolveOrCreate(
  supplierName)` (lines 121 and 272). `SupplierService` is already a `catalog.application` class
  (moved in Phase 5a) — `resolveOrCreate` is an existing, legitimate cross-module call from
  `shipments` into `catalog`'s own facade, already returning a managed `Supplier` entity. **Needs
  no `CatalogEntityAccess` call**, same as `Product.preferredSupplier` above: the module boundary
  here is already correctly crossed through a real catalog-owned method, not through direct
  `SupplierRepository` access, so there is nothing for this record to migrate.

Both entries are recorded because T-0's brief explicitly covers "a `Supplier`... entity into
another aggregate's JPA association," and omitting either left AC-5b's reconciliation incomplete —
even though neither required a `CatalogEntityAccess` design change or any source edit.

**Dev-only, exempt under AC-6 (never call `CatalogEntityAccess`):** `DevSeedController.java:305,
363,438,1151,1241,1407,1573`; `AnalyticsSeedService.java:250`.

**AC-5b's caller set is the 11 origins in the table above — not 13, and not a count of builder
lines.** Deliberately not restating this as a single summary number beyond that — a hand-computed
aggregate is exactly what drifted from its own table twice already in this record (the original
"27 sites" prose, and then the auto-create branches being counted as needing a strategy at all).
AC-5b's eventual test should assert `CatalogEntityAccess`'s actual call sites (post-migration)
equal these 11 origins by name/location, and separately assert that the auto-create branches and
both `Supplier`-assignment sites above call no `CatalogEntityAccess` method at all — an absence
this record must keep true, not just a presence list.

### T-1 — Facade method signatures + unit tests (review checkpoint)

Designed `CatalogQueries`, `CatalogPricing`, `CatalogCommands`, `ProductStockStateWriter`,
`CatalogEntityAccess`, plus the two records they return (`ProductRef`, `CategoryRef`,
`ProductPricing`), all in `catalog.application`. **No consumer touched** — all five classes are
new, unreferenced code; `ProductRepository`/`CategoryRepository`/`SupplierRepository` remain
exactly as they were.

Method shapes were derived from real call sites, not invented, checked against the 7 read
consumers (`grep`'d each for its actual `ProductRepository`/`CategoryRepository` field usage)
and the write-surface table:

- `NotificationController`, `ForecastService`: need `findById`/`findAllByIds` (nullable and
  batch forms — `NotificationController` uses `Optional`/`ifPresent`, `ForecastService` uses
  `.orElse(null)`, both satisfied by `CatalogQueries.findById` returning `Optional<ProductRef>`).
- `InventoryAggregateService`, `LocationInventoryService`, `MachineDisplayService`,
  `ProductReportBundleService`: need the throwing form (`getById`) matching their existing
  `.orElseThrow(() -> new ProductNotFoundException(...))` pattern exactly.
- `ProductReportBundleService.categoryPeers` (`findByCategoryIdAndIsActiveTrue`): →
  `CatalogQueries.findByCategoryIdActive`.
- `AnalyticsService`'s two full-table scans (`findAllWithCategories`, `findAll` + separate
  `categoryRepository.findAll()`): → `CatalogQueries.allProductRefs()` / `allCategoryRefs()`.
- `ProductReportBundleService` and `AnalyticsService` both read `product.getUnitCost()`/
  `.getMsrp()` directly today (confirmed by grep, not assumed) — per design, this becomes a
  **combined** call at each of those sites once migrated in T-3: `CatalogQueries` for the
  non-money fields, `CatalogPricing` for cost/MSRP. Recorded here so T-3 doesn't discover this
  as a surprise mid-migration.

**`ProductRef` carries `categoryId` only, not a category name or nested `CategoryRef`.**
**Superseded by the review round below — kept here, struck through in effect, only so the
reasoning trail isn't silently deleted.** ~~Verified this matches every real consumer: both
`AnalyticsService` and `ProductReportBundleService` already resolve category names from a separate
batch fetch (a `categoryRepository.findAll()` or equivalent, mapped by id) rather than
per-product.~~ This was wrong: `ProductReportBundleService.java:117,161` reads
`product.getCategory().getName()` directly for a **single** product, not from a batch fetch — see
"category-name requirement misread" below, which is what actually happened once this was checked
rather than generalized from `AnalyticsService`'s pattern alone. The `categoryId`-only design
itself still stands (carrying the name on every `ProductRef` would duplicate data), but its
resolution needs both `CatalogQueries.allCategoryRefs()` (bulk, `AnalyticsService`'s pattern) and
`CatalogQueries.findCategoryById(UUID)` (single, `ProductReportBundleService`'s pattern) — not the
bulk method alone. **Superseded by the AC-4 follow-up entry below — kept here only so the
reasoning trail isn't silently deleted.** ~~`CatalogQueries.allProductRefs()` is deliberately
backed by a plain `findAll()`, not `findAllWithCategories()`'s `JOIN FETCH` — `ProductRef.from()`
only calls `product.getCategory().getId()`, and Hibernate resolves a lazy proxy's id field without
a select, so dropping the join loses no correctness and removes a join the projection never
needed. T-3's own N+1/egress check (AC-4) is the place this gets proven against real Postgres, not
asserted here.~~ The "proven against real Postgres" check this promised did eventually happen, but
its first version was itself insufficient (wrong Hibernate statistics API, no payload comparison),
and by the time it was done properly, `allProductRefs()` had been redesigned to use its own JPQL
projection (`ProductRepository.findAllProductRefs()`) rather than `findAll()` at all — see the AC-4
follow-up entry for the real, verified design.

**`CatalogCommands.assignPreferredSupplierFromDelivery` moves `ShipmentService
.autoAssignPreferredSupplier`'s eligibility check itself into `catalog.application`**, not just
the write: the method takes `(UUID supplierId, Collection<UUID> candidateProductIds)` and
internally re-derives, per candidate, the existing rule (`preferredSupplierId == null ||
preferredSupplierAuto != false`, `null` treated as auto) rather than trusting the caller to
pre-filter — `catalog` owns `products.preferred_supplier_id`, so it should own deciding when to
overwrite it, not just executing an already-decided write. Returns the ids actually updated,
matching `ShipmentService`'s current return-nothing-but-log-implicitly behavior closely enough
for T-4's migration to assert against (T-4 will confirm the returned ids match exactly what the
current `productsToUpdate` list would have contained, for the same fixture).

**`ProductStockStateWriter.applyStockState`/`applyStockStateBatch`/`setActive`** implement the
dirty-check semantics of `StockMovementService.updateProductActiveStatus` /
`applyProductActiveStatusFromTotals` exactly: a call that would not change either field is
skipped (no `save()`, and for the batch form, excluded from both the save list and the returned
changed-ids list) — verified by a dedicated unit test per method (`ProductStockStateWriterTest`)
asserting `verify(productRepository, never()).save(any())` on the unchanged case, not just
asserting the changed case works. `isActive` is a required parameter, never derived inside the
writer — the `total > 0` derivation (AC-2b) stays exactly where it lives today, in
`StockMovementService`; a pinning test for that derivation itself is `StockMovementService`'s to
add at T-4, since `ProductStockStateWriter` never sees the raw total, only the already-derived
boolean.

**`CatalogEntityAccess.getReference`/`requireManagedProduct`** implement exactly what T-0 found:
`getReference` delegates to `ProductRepository.getReferenceById` (a proxy, verified in
`CatalogEntityAccessTest` by asserting `productRepository.findById` is never called for that
path); `requireManagedProduct` delegates to `findById(...).orElseThrow(ProductNotFoundException)`.
Both return the real `Product` JPA entity — the one deliberate exception to every other facade
here returning an immutable record — documented in the class Javadoc per AC-2's "no broader
`save(Product)` escape hatch" requirement (this class does not expose `save`; only
`ProductStockStateWriter` and `CatalogCommands` write, and only through their named narrow
methods).

**Final signatures** (Phase 6 plans against these):

```java
// catalog.application.ProductRef (record)
ProductRef(UUID id, String sku, String name, String imageUrl, Boolean isActive, Integer quantity,
    String letter, Integer templateQuantity, Integer packsPerBox, UUID parentId, KujiType kujiType,
    String kujiSlackWebhookUrl, Integer reorderPoint, Integer targetStockLevel, Integer leadTimeDays,
    Boolean forecastingEnabled, UUID categoryId, UUID preferredSupplierId, Boolean preferredSupplierAuto)

// catalog.application.CategoryRef (record)
CategoryRef(UUID id, String name, String slug, UUID parentId, Integer displayOrder,
    Boolean isActive, Boolean usesPacks)

// catalog.application.ProductPricing (record)
ProductPricing(UUID productId, BigDecimal unitCost, BigDecimal msrp)

// catalog.application.CatalogQueries (@Service, @Transactional(readOnly = true))
Optional<ProductRef> findById(UUID productId)
ProductRef getById(UUID productId)                                   // throws ProductNotFoundException
List<ProductRef> findAllByIds(Collection<UUID> productIds)
List<ProductRef> allProductRefs()
List<ProductRef> findByCategoryIdActive(UUID categoryId)
List<CategoryRef> allCategoryRefs()
Optional<CategoryRef> findCategoryById(UUID categoryId)     // added in review round — see below

// catalog.application.CatalogPricing (@Service, @Transactional(readOnly = true))
Optional<ProductPricing> findPricing(UUID productId)
ProductPricing getPricing(UUID productId)                             // throws ProductNotFoundException
List<ProductPricing> findPricingForIds(Collection<UUID> productIds)

// catalog.application.CatalogCommands (@Service)
@Transactional(propagation = Propagation.MANDATORY)   // MANDATORY, not REQUIRED — see review round
List<UUID> assignPreferredSupplierFromDelivery(UUID supplierId, Collection<UUID> candidateProductIds)

// catalog.application.ProductStockStateWriter (@Service) — every method Phase 6/7-disposed, see class Javadoc
@Transactional(propagation = Propagation.MANDATORY) boolean applyStockState(UUID productId, int quantity, boolean isActive)  // Phase 6: deleted
@Transactional(propagation = Propagation.MANDATORY) List<UUID> applyStockStateBatch(Map<UUID, StockState> stateByProductId) // Phase 6: deleted
@Transactional(propagation = Propagation.MANDATORY) void setActive(UUID productId, boolean active)                          // Phase 6: replaced
record StockState(int quantity, boolean isActive)   // nested in ProductStockStateWriter

// catalog.application.CatalogEntityAccess (@Service, @Transactional(propagation = Propagation.MANDATORY, readOnly = true))
Product getReference(UUID productId)          // proxy, no SELECT — default for pure association writes
Product requireManagedProduct(UUID productId) // existence-verified, throws ProductNotFoundException
```

- Changed: 8 new files in `catalog/application/` (3 records, 5 facade classes), 5 new unit test
  files in the mirrored test package (`CatalogQueriesTest` 9 cases, `CatalogPricingTest` 3,
  `CatalogCommandsTest` 5, `ProductStockStateWriterTest` 8, `CatalogEntityAccessTest` 3 — 28
  total). No existing file touched.
- Tests: the 28 new tests, run in isolation first (`-Dtest=CatalogQueriesTest,CatalogPricingTest,
  CatalogCommandsTest,ProductStockStateWriterTest,CatalogEntityAccessTest`) — all green. Then plain
  `./mvnw test` (the `unit-test-inventory-service` CI job's own command; Surefire's default include
  pattern picks up all five new `*Test` classes) — 54 test classes green (exit 0), confirming the
  new, currently-unused facade code introduced no regression anywhere else. (No `*IT` class existed
  in this facade yet at this point in the record.)
- Result: **pass, but see the review round below — this initial pass shipped three real defects.**

### Review round — transaction propagation, category-name access, and T-0 provenance

Three findings from review, all confirmed real (not disputed), all fixed:

- **P2 — transaction contract wasn't enforced.** `CatalogEntityAccess`, `CatalogCommands`, and
  `ProductStockStateWriter` used bare `@Transactional` (default `Propagation.REQUIRED`), which
  silently opens a new transaction when none exists. This directly contradicts the design's stated
  "joins the caller's existing transaction — never opens its own": under `REQUIRED`, a caller with
  no active transaction would still get a call that appears to succeed, but
  `CatalogEntityAccess.getReference`'s returned proxy (or `requireManagedProduct`'s entity) would
  be scoped to a transaction that closes the instant the method returns — a detached reference the
  caller cannot safely attach to its own aggregate. **Fixed**: all three classes now declare
  `@Transactional(propagation = Propagation.MANDATORY)` (`readOnly = true` retained on
  `CatalogEntityAccess`), which throws `IllegalTransactionStateException` immediately if invoked
  with no transaction in progress, instead of silently starting one. Added
  `CatalogTransactionPropagationIT` (9 tests, real `@SpringBootTest` context — propagation
  enforcement is Spring-AOP-interceptor behavior, invisible to the bare-Mockito unit tests, which
  never go through a proxy): one "no active transaction → throws" case per method across the three
  classes, plus two "joins the caller's transaction, and a rollback of the outer transaction rolls
  the write back too" cases (`ProductStockStateWriter.applyStockState`,
  `CatalogCommands.assignPreferredSupplierFromDelivery`) proving genuine participation, not just
  that no exception was thrown. Uncovered a pre-existing test-infrastructure gap along the way:
  `Supplier.canonicalName` is `insertable = false` (populated by a Postgres trigger, V22 migration)
  that the H2 "test"-profile schema (`ddl-auto=create-drop`, no triggers) doesn't replicate — no
  prior test had ever persisted a `Supplier` under that profile. Worked around in the new IT with a
  direct `JdbcTemplate` insert rather than `supplierRepository.save(...)`; not fixed at the
  schema/fixture level since that's a pre-existing gap unrelated to this record's scope.
- **P2 — category-name requirement misread.** `ProductRef`'s original Javadoc claimed every real
  consumer resolves category names via a separate batch fetch, generalizing from
  `AnalyticsService`'s pattern without checking `ProductReportBundleService`, which reads
  `product.getCategory().getName()` directly for a **single** product
  (`ProductReportBundleService.java:117,161`) — under the original two-method design, that consumer
  would have had to call `allCategoryRefs()` (every category) to resolve one name. **Fixed**: added
  `CatalogQueries.findCategoryById(UUID)` (single lookup, `Optional<CategoryRef>`) alongside
  `allCategoryRefs()` (bulk), and corrected `ProductRef`'s Javadoc to name both real access patterns
  instead of asserting only the one that happened to fit `AnalyticsService`. Added
  `findCategoryById_singleLookup_doesNotScanEveryCategory` /
  `findCategoryById_missing_returnsEmpty` to `CatalogQueriesTest`, the first explicitly asserting
  `categoryRepository.findAll()` is never called for a single-id lookup.
- **P2 — T-0's table was substantively wrong, not just three lines.** Reported to the user as three
  KujiBoxService lines (313, 397, 1324) misassigned `getReference` when they use
  `tier.getLinkedProduct()`, plus a prose "27 sites" summary that didn't match its own table's sum
  (35), plus `Product.preferredSupplier`'s association assignment (`ShipmentService:1170`) missing
  from the inventory entirely. Re-auditing by tracing every `Product`-typed local variable to its
  actual declaration (not assuming a method's pattern from its first use) found the error was
  systemic: roughly two-thirds of the original "Yes" rows were wrong, because several sit inside
  shared private helpers (`executeKujiSourceRemoval`, `batchSwapDisplay`'s `DisplayChange`
  construction, `ShipmentService`'s `addToInventory`/`removeFromInventory` family) called from
  multiple places with *different* provenance — the original pass checked one call site per helper
  and assumed it held for the others. T-0's entry above is a full rewrite, each site attributed to
  the call site that first obtains the `Product`, not to the shared builder line it eventually
  reaches. No source code changed as a result of this correction — the facade's public method
  signatures were already provenance-agnostic (`getReference(UUID)` doesn't care which caller
  supplies the id) — this only corrects the *documentation* AC-5b's future "caller set" test must
  be built against. **This pass's own count (originally reported as "13 genuine origins") was
  itself still wrong — see the second correction below.**
- Changed (this round): `CatalogEntityAccess.java`, `CatalogCommands.java`,
  `ProductStockStateWriter.java` (propagation), `CatalogQueries.java`/`ProductRef.java`
  (`findCategoryById` + corrected Javadoc); new `CatalogTransactionPropagationIT` (9 tests); 4 new
  cases added to `CatalogQueriesTest`; T-0's table in this file fully rewritten.
- Tests: the 9 new propagation-IT tests plus 4 new `CatalogQueriesTest` cases, run via an explicit
  `-Dtest=CatalogTransactionPropagationIT,CatalogQueriesTest,...` invocation — all green. Plain
  `./mvnw test` re-run after this round — 54 test classes green (exit 0), **unchanged from the
  prior round's count**: Surefire's default include pattern (`*Test.java`/`*Tests.java`) does not
  match `*IT.java`, so `CatalogTransactionPropagationIT` is never picked up by plain `./mvnw test`
  regardless of how many times it's run — it only runs via the explicit `-Dtest` invocation above,
  or via CI's separate `integration-test` job (`mvn test -Dtest='*IT'`, per
  `docs/runbooks/ci-cd-pipeline.md`). **An earlier version of this entry claimed "55 test classes
  ... one more ... from the new IT file" — that was wrong**, and reflected a stale
  `target/surefire-reports/` directory (not cleaned between this session's `-Dtest=...` run and the
  following plain `./mvnw test` run) being misread as evidence of the IT file running under plain
  `test`, rather than actually re-verifying with `./mvnw clean test`. Corrected below.
- Result: **pass, but see the second correction below — this round's own validation claims needed
  a follow-up fix.**

### Second correction — caller-set/no-accessor mismatch, missing Shipment.supplier inventory, validation wording

Three more findings from a second review pass over the same T-1 checkpoint, all confirmed real:

- **P2 — the "genuine origins" table contradicted itself.** The auto-create branches
  (`KujiBoxService.java:263`, `:1387`) were kept as numbered rows in the "requires
  `CatalogEntityAccess`" table with a "materialization nuance" strategy filled in, while the
  closing summary simultaneously said all 13 numbered origins must become `CatalogEntityAccess`
  calls — directly contradicting the note against those same two rows saying they need no such
  call. **Fixed**: removed both rows from the table entirely (they are not `CatalogEntityAccess`
  callers, full stop — `ProductService.createProduct` already returns a managed entity directly,
  no new `CatalogCommands` creation API is needed, and the `isActive`-forcing write right before
  each `save()` routes through `ProductStockStateWriter.setActive` instead). The table now holds
  **11** genuine origins, and the closing summary states 11, not 13, and explicitly calls out the
  auto-create branches and both `Supplier`-assignment sites (below) as origins that must call *no*
  `CatalogEntityAccess` method — a fact AC-5b's eventual test must assert as an absence, not just
  omit.
- **P2 — `Shipment.supplier` assignments were still missing from the inventory.**
  `ShipmentService.java:127` (`.supplier(supplier)`, `createShipment`) and `:273`
  (`shipment.setSupplier(supplier)`, `updateShipment`) both assign a `Supplier` entity into
  `Shipment`'s own association, and both sources (`supplierService.resolveOrCreate(...)`, lines 121
  and 272) were missed in the prior pass even though `Product.preferredSupplier`'s analogous
  `Supplier`-into-aggregate assignment had just been added. **Fixed**: added as a second bullet
  under "Write consumers, not entity-access consumers" — `SupplierService` is already a
  `catalog.application` class (Phase 5a), so `resolveOrCreate` is a legitimate existing facade call
  returning a managed entity; no `CatalogEntityAccess` call is needed here either, and no source
  changed.
- **Validation wording was imprecise about what plain `./mvnw test` proves.** Corrected above (see
  "An earlier version of this entry claimed...") — plain `./mvnw test` is the `unit-test-*` CI
  job's own command and never runs `*IT` classes; `*IT` validation requires either an explicit
  `-Dtest` invocation (used throughout this record) or CI's separate `integration-test` job. Every
  "full `./mvnw test`" claim in this log now means Surefire's default-pattern run only, with `*IT`
  results reported separately wherever they're relevant.
- Changed (this round): T-0's table (`KujiBoxService.java:263`/`:1387` rows removed, summary count
  corrected to 11, `Shipment.supplier` write-consumer entry added); no source files changed — every
  finding this round was a documentation defect, not a code defect.
- Tests: none added — no code changed. Re-ran `./mvnw clean test` (clean, not incremental, so
  `target/surefire-reports/` could not contain stale entries from an earlier `-Dtest` invocation)
  to obtain a trustworthy count: **49 test classes**, zero failures/errors — the true plain-Surefire
  baseline once catalog's five new unit-test classes and this record's earlier, unrelated test
  additions are all included and no `*IT` class is. `CatalogTransactionPropagationIT` was
  separately re-confirmed green via its own explicit `-Dtest` run.
- Result: **pass.**

### T-2 — Outside-catalog → catalog.infrastructure ArchUnit rule (frozen, probe-verified)

Added `ArchitectureTest.noProductionClassOutsideCatalogDependsOnCatalogInfrastructure()` per AC-3.
Per the explicit instruction, the source selector is **not** `businessModulePackages()` (the
selector `modulesDoNotDependOnAnotherModulesApi` uses) — that selector only covers the named
domain-module packages, and every one of T-0's twelve consumers still lives in legacy
`services`/`controllers`, outside that list; reusing it would make the rule pass vacuously against
exactly the classes it exists to catch. The rule built instead: source = "every class whose
top-level module segment is not `catalog`" (via the existing `moduleOf()` helper — a class in
`services`, `controllers`, or any other domain module all count), target = anything in
`catalog.infrastructure..`, which already covers `ProductRepository`, `CategoryRepository`, and
`SupplierRepository` (AC-3b) as one package rather than three named classes, since all three
already live there from Phase 5a's move.

**Probe verification before touching the frozen store** (same discipline as Phase 5a's T-2,
`repositoryAccessRule()`): extracted `outsideCatalogToCatalogInfrastructureRule()` as a
package-visible static factory so the probe evaluates the exact production rule, not a re-typed
copy. Two temporary fixtures, both in `src/main/java` (not `src/test/java` — `ArchitectureTest`'s
`@BeforeAll` import uses `DO_NOT_INCLUDE_TESTS`, so a test-sourced fixture would be invisible to
the rule entirely and prove nothing):

- `services.ArchProbeLegacyServiceToCatalogInfrastructure` (must fail) — a legacy `services` class
  injecting `catalog.infrastructure.ProductRepository`. This is the specific case AC-3 calls out:
  proving the selector reaches a legacy package, not only a domain module.
- `identity.application.ArchProbeOtherModuleToCatalogInfrastructure` (must fail) — a **different**
  domain module's own `application` package injecting `catalog.infrastructure.SupplierRepository`.
  Proves two things at once: the target selector reaches `SupplierRepository`, not only
  `ProductRepository` (AC-3b), and that `repositoriesAreOnlyAccessedByServicesOrRepositories`
  already allowing `identity.application` to reach *some* repository does not make it exempt from
  *this* rule — the two rules ask different questions (same-module-layering vs. cross-module
  infrastructure access).
- Case 3 (must pass): no fixture needed — used the existing real
  `catalog.application.{CatalogQueries,CatalogPricing,...}` → `catalog.infrastructure` dependencies
  built in T-1.

Ran via a temporary `ArchitectureTestCatalogInfrastructureRuleProbeTest` calling `rule.evaluate(...)`
directly (no `freeze()` wrapper, so no store dependency). **First assertion attempt was wrong and
caught before relying on it**: checking `report.doesNotContain("CatalogQueries")` etc. for case 3
failed immediately, not because `CatalogQueries` violated anything, but because the rule's own
violation-message text names all five facade classes as the suggested fix ("...via
CatalogQueries/CatalogPricing/..."), so that bare substring is present in *every* violation line
regardless of source. Fixed by checking the fully-qualified origin pattern
(`"catalog.application.CatalogQueries."`) instead of the bare class name — a real defect in the
test, not the rule, that a less careful read of the first failure could have mistaken for the rule
itself being wrong. All three cases passed after the fix. Fixtures and probe test deleted
afterward.

**Store regeneration and count, not assumed but reconciled to zero unexplained gap.** Scoped to
this one test method (`allowStoreCreation=true`/`allowStoreUpdate=true`, ran
`-Dtest=ArchitectureTest#noProductionClassOutsideCatalogDependsOnCatalogInfrastructure` only, then
reverted both flags to `false` and re-ran to confirm the rule still passes against the finalized
store — the same "never run the unscoped class with store-write flags enabled" discipline Phase 5a
established). New store: **87 violations** (`archunit_store/af02cbd2-...`, plus a new line in
`stored.rules`). The probe run (with both fixtures present) had reported 91; naively expected
91 − 2 = 89 once the two fixtures were removed, and 87 didn't match that on the first check. Ran
down the 2-violation gap rather than accepting the mismatch: each fixture contributes **two**
violation lines, not one — a `Constructor <...>` line (the constructor-parameter dependency) *and*
a separate `Field <...>` line (the resulting field assignment) — confirmed by diffing the sorted
origin-class lists of both reports. 91 − (2 fixtures × 2 lines) = 87, exact match, no unexplained
remainder.
- Changed: `ArchitectureTest.java` (new rule + `outsideCatalogToCatalogInfrastructureRule()`
  factory); `archunit_store/stored.rules` (new line); `archunit_store/af02cbd2-...` (new store
  file, 87 lines). No production or other test file changed — the two probe fixtures and the probe
  test were temporary and are deleted.
- Tests: `ArchitectureTest` run alone — all 7 methods (6 pre-existing + the new one) pass, proving
  the new rule didn't regress the others. Then `./mvnw clean test` — 49 test classes, zero
  failures, exit 0.
- Result: **pass.**

### T-3 — Migrate the 7 read-only consumers to CatalogQueries/CatalogPricing/CatalogEntityAccess

Migrated `NotificationController`, `InventoryAggregateService`, `LocationInventoryService`,
`MachineDisplayService`, `ForecastService`, `ProductReportBundleService`, `AnalyticsService` off
direct `ProductRepository`/`CategoryRepository` access. Before touching each file, re-verified its
real usage by reading the whole file rather than trusting T-0's summary characterization — this
surfaced two things T-0's read-only framing understated, both handled correctly rather than
papered over:

- **`LocationInventoryService` needed `CatalogEntityAccess`, not `CatalogQueries`.** It passes the
  fetched product into `StockMovementService.rejectIfCustomKujiParent(Product)` (reads
  `getKujiType()`) and `StockMovementService.createInventoryWithTracking(..., Product, ...)`
  (which builds `LocationInventory.product`/`StockMovement.item` — T-0 origin #5) — both need the
  real JPA entity, not `ProductRef`. Since `StockMovementService` is a T-4 write-consumer, its
  method signatures are out of this task's scope to change, so `LocationInventoryService` keeps
  passing a real `Product`. Used `catalogEntityAccess.requireManagedProduct(productId)`, not
  `getReference`: this fetch is the *first* existence check for this id in the method (unlike every
  T-0-origin site, which had already validated existence via an earlier fetch) — matching the
  facade's own documented distinction (`requireManagedProduct` for a caller that hasn't already
  established existence and needs the call itself to fail loudly). This is a correction to T-0's
  origin #5 strategy note, not a new design decision.
- **Three of the seven consumers read cost/MSRP directly and needed `CatalogPricing` too**,
  confirmed by grep rather than assumed from spec.md's naming only `ProductReportBundleService`
  and `AnalyticsService` for this: `ForecastService.convertToDTO` reads `product.getUnitCost()`
  (line 170, pre-migration) — a third consumer of the combined `CatalogQueries` + `CatalogPricing`
  pattern, not named in spec.md's Product-decisions section. Recorded here so nothing downstream
  assumes only two consumers need pricing.

**Exception-type preservation, checked per call site, not assumed.** Several call sites throw
`IllegalArgumentException` (`MachineDisplayService`) or `EntityNotFoundException`
(`ProductReportBundleService`) for a missing product, not `ProductNotFoundException` — using
`CatalogEntityAccess.requireManagedProduct` there would have silently changed the HTTP response
from 400/{whatever EntityNotFoundException maps to} to 404 (`ProductNotFoundException` is mapped
to `HttpStatus.NOT_FOUND` in `GlobalExceptionHandler`, `IllegalArgumentException` to
`BAD_REQUEST`). Kept every site's original exception type and message: `CatalogQueries.findById`
(`Optional`) plus the caller's own `.orElseThrow(() -> new <OriginalException>(...))`, never a bare
`getById`/`requireManagedProduct` call, wherever the original code's exception type wasn't already
`ProductNotFoundException`.

**`MachineDisplayService`'s 4 T-0 origins, migrated exactly as designed**, including the
shared-helper nuance T-0 flagged: `batchSwapDisplay`'s add-loop (origin #4) constructs a real
`Product` via `catalogEntityAccess.getReference(productId)` for the `DisplayChange` record (shared
with the other three pass-through loops, which still supply `display.getProduct()` unchanged —
untouched, confirmed by re-reading, not just trusting T-0's earlier note that they need nothing).

**`AnalyticsService`'s `Collectors.groupingBy(Product::getCategory)` in `getInventoryByCategory`
could not carry over as `groupingBy(ProductRef::categoryId)`'s obvious analogue without a design
change**: the original grouped by the `Category` *entity* (relying on Lombok `@Data`'s
field-based `equals`/`hashCode`), which has no `ProductRef` equivalent since `ProductRef` carries
only `categoryId`. Rewrote to group by `UUID categoryId` and resolve names via a new shared
`loadCategoriesById()`/`categoryNameOrUncategorized()` pair (bulk `catalogQueries.allCategoryRefs()`
mapped once per request), reused across `getInventoryByCategory`, `getActionCenter`, `getInsights`
(via `computeMoversFromRollups`, which gained a `Map<UUID, CategoryRef> categoriesById` parameter),
and `getDemandLeaders`. This is a genuine, disclosed query-count change: `getInsights` previously
made one `findAllWithCategories()` call (category eagerly `JOIN FETCH`ed per row); it now makes two
bulk calls (`allProductRefs()` + `allCategoryRefs()`), each O(1) regardless of product count — not
an N+1 regression (verified: `allCategoryRefs()` is called exactly once per request, never once per
product), just one more total round trip. Recorded per AC-4's explicit instruction to check this
migration for exactly this class of regression.

**Consumer-by-consumer summary:**

| Consumer | Facades used | Notes |
| --- | --- | --- |
| `NotificationController` | `CatalogQueries` | Simple `findById`/`findAllByIds`, name lookups only |
| `InventoryAggregateService` | `CatalogQueries` | `getById` — already threw `ProductNotFoundException`, matches `getById`'s contract exactly |
| `LocationInventoryService` | `CatalogEntityAccess` | `requireManagedProduct`, not `CatalogQueries` — see correction above |
| `MachineDisplayService` | `CatalogQueries` + `CatalogEntityAccess` | 4 origins; every `IllegalArgumentException` site preserved |
| `ForecastService` | `CatalogQueries` + `CatalogPricing` | Newly identified `CatalogPricing` consumer |
| `ProductReportBundleService` | `CatalogQueries` + `CatalogPricing` | Added `CatalogQueries.findCategoryById` single-lookup use (T-1's fix for this exact consumer) |
| `AnalyticsService` | `CatalogQueries` + `CatalogPricing` | Both full-table scans → `allProductRefs()`; `CategoryRepository.findAll()` → `allCategoryRefs()`; `getActionCenter` gained a pricing map for `revenueAtRisk`'s `msrp` |

- Changed: all 7 consumer files listed above; test files updated to match:
  `LocationInventoryServiceTest` (mock field swap, no behavioral stub existed for the migrated
  method), `MachineDisplayServiceBatchQueryGuardTest` (mock swap + `catalogEntityAccess.getReference`
  stub), `MachineDisplayServiceNotificationTest` (mock swap, `ProductRef.from` stubs),
  `AnalyticsServiceTest` (mock swap, `ProductRef`/`CategoryRef`/`ProductPricing` stubs, added
  `catalogQueries.allCategoryRefs()` stubs the migration newly requires in `getInsights`).
  `AnalyticsCacheIntegrationTest` needed **no changes** — its `@SpyBean ProductRepository`/
  `CategoryRepository` still intercept the real beans transparently, since `CatalogQueries`/
  `CatalogPricing` are themselves just thin Spring beans wrapping those same repositories; confirmed
  by running it, not assumed. `ForecastService` and `ProductReportBundleService` had no existing
  tests referencing the migrated fields at all.
- ArchUnit store regeneration, three stores, each reviewed independently (same discipline as every
  prior round):
  - `noProductionClassOutsideCatalogDependsOnCatalogInfrastructure` (T-2's new rule): **87 → 50**,
    exactly matching the pre-computed reconciliation (87 baseline − 37 entries attributed to these
    7 consumers, confirmed by `grep -c` per consumer name against the frozen store before touching
    any code) — not assumed, verified both before and after.
  - `repositoriesAreOnlyAccessedByServicesOrRepositories`: **148 → 144**, diff reviewed line by
    line: exactly 4 lines removed, all `NotificationController` (the one consumer among the 7 that
    was also a *controller*-layer direct-repository violator under this separate, layer-focused
    rule — the other 6 are `services`-package classes, already exempt under this rule regardless of
    module boundary). No unrelated change.
  - `topLevelPackagesAreFreeOfCycles`: same delete-file + recreate mechanism as every prior Phase
    5a/5b round (toggling `allowStoreUpdate` alone on the existing file did not converge after two
    attempts — this rule's freeze store needs the file actually deleted first, not just updated in
    place, matching Phase 5a's T-3/T-4 note about this exact mechanism). New store:
    same **4605 lines, same 66 cycle blocks (43 catalog-involving, 23 non-catalog, both
    unchanged)** as the pre-T-3 baseline — confirmed via `diff`, not line-count alone: exactly 168
    lines changed on each side, a pure 1:1 evidence-text substitution (old constructor signatures
    naming `ProductRepository`/`CategoryRepository` replaced by ones naming
    `CatalogQueries`/`CatalogPricing`/`CatalogEntityAccess`), zero structural change to the cycle
    graph itself.
- Tests: full `./mvnw clean test` — 49 test classes, zero failures (exit 0). This already includes
  `AnalyticsCacheIntegrationTest` — its name ends in `Test`, not `IT`, and it extends
  `BaseIntegrationTest` (H2, `@Transactional` test-rollback), not `BaseKafkaIntegrationTest`
  (real Postgres via Testcontainers), so it runs under plain `./mvnw test` and is **not** selected
  by `-Dtest='*IT'` — an earlier version of this entry wrongly claimed the opposite (that
  `-Dtest='*IT'` was what confirmed this class); corrected here and in the Test plan section below.
  Separately, `./mvnw test -Dtest='*IT'` — every real `*IT` class green (exit 0), confirming the
  real-Postgres/real-Spring-context paths this migration touches (`ProductLifecycleTransactionBehaviorIT`
  and friends) aren't just passing under H2-mocked unit tests. Neither run on its own would have
  caught a genuine N+1 in `CatalogQueries.allProductRefs()` — see the dedicated egress IT added
  below, which is why AC-4 asked for one explicitly rather than treating this pass as sufficient.
- Result: **pass, but see the follow-up finding below — AC-4's N+1/egress check was still
  missing.**

### Follow-up — AC-4's required N+1/egress check for `allProductRefs()`

**P2 finding, confirmed real.** T-3's own text (above) claimed the migration was "checked for
N+1/pooler-egress regression against the existing slim `ProductListItem` projection," per AC-4's
explicit instruction — but no test anywhere actually measured SQL query count or compared it
against `ProductListItemDTO`'s projection. Running the consumer's tests once and seeing them pass
is not the same claim; those tests mock `CatalogQueries` (or, for `AnalyticsServiceTest`, mock the
repositories underneath it) and never touch real SQL, so an actual N+1 in `allProductRefs()`
would have passed every existing test silently.

**First attempt at the fix (`CatalogQueriesEgressIT` v1) was itself insufficient — caught by a
second review pass, not self-caught:**

- **`Statistics.getQueryExecutionCount()` doesn't count lazy-load SQL.** It only counts Hibernate
  "query" executions (HQL/JPQL/Criteria) — a lazy-load select triggered by touching an
  uninitialized association is a different internal Hibernate code path and does not increment
  it. A real N+1 could sit at `getQueryExecutionCount() == 1` and pass v1's check anyway.
- **Equal query counts don't prove equal egress.** v1 only compared counts between
  `allProductRefs()` and `findAllAsListItems()`; it never looked at *what* either query actually
  selected. `allProductRefs()` was still backed by `productRepository.findAll()` at that point —
  a full-entity load pulling every mapped column (`description`, `notes`, etc.) across the wire —
  while `findAllAsListItems()`'s JPQL constructor-expression selects only named columns. Same
  query count, materially different payload per row.

**Fixed at the source, not just re-measured**, per the second point: added
`ProductRepository.findAllProductRefs()`, a JPQL constructor-expression projection matching
`LIST_ITEM_SELECT`'s existing pattern exactly — `SELECT NEW ...ProductRef(p.id, p.sku, ..., 
p.category.id, ...) FROM Product p`, never touching `description`/`notes`/`unitCost`/`msrp`/the
`category`/`parent` associations as entities. `CatalogQueries.allProductRefs()` now calls this
directly instead of `findAll()` plus in-memory `ProductRef::from` mapping. This is a genuine
production-code fix, not a test-only patch — the original design (a plain `findAll()` relying on
Hibernate's proxy-id-without-select behavior) was real transitional debt this closes properly,
matching how `ProductListItemDTO` was already handled.

Rewrote `CatalogQueriesEgressIT` against both findings:

- **Statement counting**: switched to `Statistics.getPrepareStatementCount()` — every JDBC
  `PreparedStatement` Hibernate sends to the driver, regardless of which internal path triggered
  it, not just "query" executions.
- **Payload comparison**: added `CapturingStatementInspector` (a `StatementInspector` registered
  via `hibernate.session_factory.statement_inspector`, capturing raw SQL text) so the test can
  assert on the actual selected-column shape, not just a count. Seeded products with distinctive
  `description`/`notes` values specifically so their absence from the captured SQL is an
  unambiguous, direct assertion (`doesNotContain("description")`/`doesNotContain("notes")`) rather
  than an inference from row counts or timing.
- `allProductRefs_issuesOneStatement_notOnePerProductOrCategory`: 5 products across 3 categories,
  `getPrepareStatementCount() == 1`.
- `allProductRefs_selectsTheSameSlimColumnShapeAsTheExistingListItemProjection`: both
  `allProductRefs()` and `findAllAsListItems()` prepare exactly one statement each, and neither
  statement's captured SQL contains `description`/`notes` — the actual AC-4 comparison, verified
  directly against the real SQL text rather than assumed from equal counts.

**Verified the new counter methodology can actually fail, not just pass** — per the explicit
instruction, not asserted on faith: temporarily reverted `CatalogQueries.allProductRefs()` to the
old `findAll()`-based implementation and added a throwaway `product.getCategory().getName()` call
inside `ProductRef.from()` to force category-proxy initialization (the exact shape of regression
`getQueryExecutionCount()` would have missed). Re-ran
`allProductRefs_issuesOneStatement_notOnePerProductOrCategory`: it failed, `getPrepareStatementCount()`
reporting **2**, not 1 — Hibernate's batch-fetching (`Category`'s `@BatchSize(50)`) folded the
three distinct categories' lazy loads into one follow-up `IN (...)` select rather than three
separate ones, so the regression showed up as 2 statements, not 4 — still correctly caught as
"more than the ideal 1," proving the test fails on a real regression rather than only ever
confirming what's already believed to pass. Both temporary edits reverted immediately after;
diffed against the pre-edit files to confirm a byte-for-byte clean revert before moving on.
- Changed: `ProductRepository.java` (new `findAllProductRefs()` projection query),
  `CatalogQueries.java` (`allProductRefs()` now calls it), new `CapturingStatementInspector.java`,
  rewritten `CatalogQueriesEgressIT` (2 tests), `CatalogQueriesTest`'s `allProductRefs` case updated
  to stub the new repository method instead of `findAll()`.
- Tests: `CatalogQueriesEgressIT` (2 tests, requires Docker, run explicitly — not part of plain
  `./mvnw test`, consistent with every other Testcontainers-backed IT in this codebase) and
  `CatalogQueriesTest` (11 tests) both green. Full `./mvnw clean test` — 49 classes, zero failures,
  confirming the production change to `ProductRepository`/`CatalogQueries` introduced no
  regression elsewhere.
- Result: **pass.**

### T-4 — Review checkpoint: migrate StockMovementService and ShipmentService onto the facades

**StockMovementService.** Its only direct `ProductRepository` usage (4 call sites, all inside two
private helpers) is exactly the write-surface table's "quantity + isActive together" row:

- `applyProductActiveStatusFromTotals(Set<UUID>, Map<UUID, Integer>)` — the batch path (called
  from `batchAdjustInventory` and one other site). Rewrote to build a
  `Map<UUID, ProductStockStateWriter.StockState>` from the pre-computed totals and delegate to
  `productStockStateWriter.applyStockStateBatch(...)` in one call — the per-row dirty-check and
  batched `saveAll` this method's own comment already described are now `ProductStockStateWriter`'s
  responsibility, not reimplemented here.
- `updateProductActiveStatus(Product)` / `syncProductTotals(List<UUID>)` — the single/loop path.
  `updateProductActiveStatus` keeps its existing signature (three other call sites pass it an
  already-loaded `Product` obtained via association navigation, e.g. `sourceInventory.getProduct()`
  — untouched, per T-0) but its body now computes `shouldBeActive` and calls
  `productStockStateWriter.applyStockState(product.getId(), total, shouldBeActive)` instead of
  mutating and saving the entity directly. `syncProductTotals` no longer fetches `Product` entities
  at all — it uses `catalogQueries.findAllByIds(productIds)` purely to reproduce
  `productRepository.findAllById(...)`'s "silently skip ids that no longer exist" behavior (a
  product deleted concurrently), then calls `applyStockState` by id. Verified this preserves the
  original semantics via the shared persistence context, not asserted: `product` in the three
  `updateProductActiveStatus` call sites and `ProductStockStateWriter`'s own internal
  `productRepository.findById(productId)` return the *same* Hibernate-managed Java object within
  one transaction (same session, same id → identity-map hit), so mutating it inside the writer is
  observable to the caller exactly as before, not a divergent copy.
- The `total > 0` derivation itself (AC-2b) was **not** touched — it still lives in
  `StockMovementService`, computed before the delegated write, matching spec.md's explicit
  instruction that the writer only applies an already-derived boolean, never derives one itself.

**ShipmentService.** Three direct `ProductRepository` usages:

- `createShipment` (T-0 origin #6) / `updateShipment` (origin #7): both batch-fetch a product map
  then build `ShipmentItem.item(product)`. Rewrote to batch-check existence via
  `catalogQueries.findAllByIds(...)` (preserving the exact `ProductNotFoundException` throw on a
  missing id) and obtain the managed reference for the builder via
  `catalogEntityAccess.getReference(...)` — matching T-0's assigned `getReference` strategy for
  both origins exactly, now implemented rather than just planned.
- `autoAssignPreferredSupplier`: previously read each `ShipmentItem.getItem()` (association-derived,
  no repository call — confirmed again here), inlined the eligibility check, and called
  `productRepository.saveAll(...)`. Rewrote to collect just the candidate product ids and call
  `catalogCommands.assignPreferredSupplierFromDelivery(supplier.getId(), candidateProductIds)` —
  the eligibility check itself is now `CatalogCommands`' own responsibility (already implemented
  that way in T-1), so this method no longer duplicates it.

**Both services now depend on `CatalogQueries`/`CatalogEntityAccess`/`CatalogCommands`/
`ProductStockStateWriter` instead of `ProductRepository`**, joining the caller's existing
transaction in every case (both classes are `@Transactional` at the relevant method or class
level — `ShipmentService` via `jakarta.transaction.Transactional`, which Spring's transaction
interception honors the same as its own annotation — satisfying every facade's `MANDATORY`
propagation requirement without any special-casing).

**AC-5's required atomic-commit test — first version was itself insufficient, per review.**
Added `StockMovementOutboxAtomicityIT` (`@SpringBootTest(webEnvironment = NONE)`, H2 "test"
profile, deliberately not extending `BaseIntegrationTest`'s rolled-back-transaction wrapper — same
reasoning `ProductLifecycleTransactionBehaviorIT` recorded for Phase 5a's equivalent check).
`batchAdjustInventory` creates the `StockMovement` row, the `LocationInventory` quantity change,
and the `EventOutbox` row, *then* calls `applyProductActiveStatusFromTotals` (now
`ProductStockStateWriter`) — all inside one `@Transactional` method.

The first version put `@MockBean` on `ProductStockStateWriter` itself and injected the failure
there. That proved *a* failure at that call site rolls everything back, but never exercised the
real writer at all — it could not distinguish "the outbox and `LocationInventory` roll back
together" (true) from "the product's own `quantity`/`isActive` commit or roll back with them"
(the actual claim AC-5 makes, and the one thing this version never touched). **Fixed**: removed
the `ProductStockStateWriter` mock entirely: the real bean now runs — real fetch, real dirty-check,
real derivation — and the failure is injected one layer deeper, via `@SpyBean` on
`ProductRepository` (`doThrow(...).when(productRepository).saveAll(any())`), the same failure
shape a real constraint violation or connection drop would produce at the writer's actual
persistence step.

- `batchAdjust_whenProductRepositorySaveAllFails_rollsBackStockMovementLocationInventoryOutboxAndProductStateToo`:
  drains the seeded inventory row to exactly 0 (`quantityChange=-20`), so the real writer would
  derive and attempt to persist `isActive=false`; asserts the `StockMovement` table, the
  `EventOutbox` table, the `LocationInventory` row (restored, not left deleted — see below), *and*
  a fresh `productRepository.findById` read all show no trace of the attempt — proving the
  product's own state genuinely rolled back with the rest, not just that the mocked call site did.
- `batchAdjust_onSuccess_stockMovementLocationInventoryOutboxAndProductStateAllCommitTogether`: the
  positive counterpart — no failure injected, same `-20` (fully-drained) case, asserting the real
  writer actually flipped `isActive` to `false` and `quantity` to `0` on the `Product` row itself,
  not merely that some downstream mock was called with the right arguments.

**Found and fixed two bugs while building this, before trusting the result — neither was a
production defect:**
- The first run reported the rollback test's `StockMovement`/`EventOutbox` tables non-empty: the
  two tests in this class share one H2 database with no enclosing rolled-back transaction
  (deliberately, per the class's own reasoning above), so the *other* test's real,
  successfully-committed row was still present when the first test's assertion ran (JUnit doesn't
  guarantee declaration order). Fixed with an `@AfterEach` cleanup deleting the
  outbox/stock-movement/location-inventory tables between tests — the same pattern `AdjustToKafkaIT`
  already uses for the identical reason.
- Once the real writer was wired in, the positive-commit test's `-20` case failed with
  `LocationInventory` not found: `batchAdjustInventory` **deletes** a `LocationInventory` row
  drained to exactly 0 rather than saving it at quantity 0 (pre-existing behavior, unrelated to
  this migration) — the test had assumed the row would still exist with `quantity=0`. Fixed the
  assertion to expect deletion, not a zero-quantity row.

**AC-2b's required `shouldBeActive` pinning test — first version covered only one of the two
derivation sites named in spec.md's write-surface table, per review.** The original
`StockMovementServiceActiveStatusDerivationTest` exercised only `syncProductTotals` (the single
path, originally `StockMovementService.java:891-892`); the batch path (originally
`StockMovementService.java:363-364`, `applyProductActiveStatusFromTotals`) had no coverage at all.
**Fixed**: added three more cases exercising `batchAdjustInventory` directly (its real public
entry point for the batch derivation) — a single-line batch reaching a zero total, a single-line
batch reaching a positive total, and a genuinely multi-line batch where two products land on
different sides of the `total > 0` line in the same call, proving the batch derivation is computed
independently per product, not just once for the whole request. The existing `syncProductTotals`
cases (zero total, positive total, and `null` sum from `sumQuantityByProductId` treated as zero —
pinning `calculateTotalInventory`'s null-guard too) are unchanged. Every case verifies the exact
`ProductStockStateWriter` call (`applyStockState(id, total, shouldBeActive)` for the single path,
`applyStockStateBatch(Map<UUID, StockState>)` for the batch path) with the precise expected
arguments, so a future accidental change to either derivation fails immediately.

**This "two sites" accounting was itself still wrong, per a third review pass — `syncProductTotals`
does not exercise `updateProductActiveStatus` at all.** The claim above (and the class's own
javadoc) said `syncProductTotals` covered "the single path, originally
`StockMovementService.java:891-892`" — but that line range is `updateProductActiveStatus`, and
T-4's `syncProductTotals` rewrite (see above) computes its own independent
`totalInventory`/`shouldBeActive` pair inline rather than calling that helper, because it only has
product ids in hand, not already-loaded `Product` entities the way `updateProductActiveStatus`'s
three real callers (`transferInventory`, `createInventoryWithTracking`,
`removeInventoryWithTracking`) do. So `updateProductActiveStatus` itself had **zero** coverage,
not partial coverage as claimed. **Fixed**: added two more cases exercising
`removeInventoryWithTracking` (one of its three real callers) — zero remaining total → `isActive`
false, positive remaining total → `isActive` true (the latter deliberately framed as "removing
this one inventory row doesn't necessarily zero the product out," since that's exactly why
`updateProductActiveStatus` re-derives the total from `calculateTotalInventory` rather than
assuming removal implies inactive). Corrected the class javadoc to state accurately that
`syncProductTotals` and `updateProductActiveStatus` are two *different* inline derivations, not one
shared helper reached two ways. **Verified the new tests actually catch a regression, not just
that they pass**, per the same discipline as every prior mutation check in this project: temporarily
changed `updateProductActiveStatus`'s `totalInventory > 0` to `>= 0`, reran, confirmed
`removeInventoryWithTracking_zeroRemainingTotal_derivesIsActiveFalse` failed with the exact
expected argument mismatch (`applyStockState(id, 0, true)` instead of `(id, 0, false)`), then
reverted the mutation and confirmed a clean diff against the pre-mutation file before moving on.
Eight cases total now: three `syncProductTotals`, two `updateProductActiveStatus` (via
`removeInventoryWithTracking`), three `batchAdjustInventory` — covering all three real derivation
call sites, not two.

- ArchUnit store regeneration, two stores (the third,
  `repositoriesAreOnlyAccessedByServicesOrRepositories`, did not need it — both services already
  live in the legacy `services` package, already exempt under that rule regardless of module
  boundary, so removing their direct repository access changed nothing it tracks):
  - `noProductionClassOutsideCatalogDependsOnCatalogInfrastructure`: **50 → 39**, diff reviewed —
    exactly 11 lines removed (constructor, field, and per-call-site entries for
    `StockMovementService`/`ShipmentService` only), 0 added, no unrelated change.
  - `topLevelPackagesAreFreeOfCycles`: same delete-file + recreate mechanism as every prior round
    (needed on the second attempt again, like T-3 — toggling `allowStoreUpdate` on the existing
    file alone still doesn't converge for this rule in this codebase). New store: same **4605
    lines, same 66 cycle blocks (43 catalog, 23 non-catalog)** — diff showed exactly 111 lines
    changed on each side, a 1:1 evidence-text substitution (updated constructor signatures), zero
    structural change.
- Changed: `StockMovementService.java`, `ShipmentService.java` (both migrated); `ShipmentServiceOverrideTest.java`
  (mock swap, no behavioral change needed — the override path doesn't touch the migrated methods);
  new `StockMovementOutboxAtomicityIT` (2 tests, revised to use the real `ProductStockStateWriter`
  and `@SpyBean ProductRepository`, not `@MockBean ProductStockStateWriter`), new
  `StockMovementServiceActiveStatusDerivationTest` (8 tests: 3 `syncProductTotals`, 2
  `updateProductActiveStatus` via `removeInventoryWithTracking`, 3 `batchAdjustInventory`).
- Tests: full `./mvnw clean test` (49 classes) and `./mvnw test -Dtest='*IT'` (34 classes,
  including the 2 new ones) both green, zero failures/errors — this also re-confirms
  `AdjustToKafkaIT` (the existing real-Postgres-and-Kafka "stock adjust" coverage AC-5 names) and
  `StockMovementControllerSecurityIT` (real H2-backed context exercising the migrated transfer/
  adjust/receive endpoints, not just RBAC status codes) stayed green through the migration.
- Result: **pass.**

### T-4b: Review checkpoint (kuji entity association) — migrate `KujiBoxService`

Migrated `KujiBoxService`'s constructor from `ProductRepository productRepository` to
`CatalogQueries catalogQueries`, `CatalogEntityAccess catalogEntityAccess`,
`ProductStockStateWriter productStockStateWriter`, and rewrote each of the 9 call sites T-0's
table (origins #8-#11, plus the two auto-create branches) already recorded, verifying every
current line number and surrounding branch still matched T-0's analysis before touching it (line
numbers had drifted by a handful from spec.md's original citation due to intervening T-1/T-3/T-4
edits — the mapping to origins #8/#9/#10/#11 and the auto-create branches was otherwise unchanged):

- `openBox` (line 149, origin #8): `catalogQueries.findById(productId).orElseThrow(...)` (same
  `ProductNotFoundException` type and message as the old `findByIdWithCategories(...).orElseThrow`)
  into a `ProductRef`, then `catalogEntityAccess.getReference(productRef.id())` at both places the
  entity is needed — the auto-attached-`MachineDisplay`'s `.product(...)` and `KujiBox.product`.
  `findByIdWithCategories`'s eager `category`/`parent`/`preferredSupplier` joins were dropped: the
  method only ever reads `kujiType`/`name`/`id` off the fetched product (verified by grepping the
  method body), all present on `ProductRef`, so the eager fetch was already wasted egress that this
  migration incidentally removes — not something T-4b set out to fix, just a side effect of no
  longer having `findByIdWithCategories` available outside `catalog.infrastructure`.
- `openBox`'s existing-linked-product tier branch (line 234, origin #9) and `addTier`'s
  existing-linked-product branch (line ~1364, origin #10) and `patchTier`'s linked-product-change
  branch (line ~1545, origin #11): same `catalogQueries.findById(...).orElseThrow(new
  ProductNotFoundException("Linked product not found: " + id))` then `catalogEntityAccess
  .getReference(...)` pattern, preserving the original "Linked product not found" message text
  exactly (distinct from origin #8's "Product not found" message — confirmed both origins'
  messages are unchanged, not silently unified onto `CatalogEntityAccess.requireManagedProduct`'s
  own message, ~~which per T-0's log has zero production callers today and stays that way~~ —
  **wrong, corrected in the "T-4b review round 3" section below**: `LocationInventoryService
  .addInventory` was already a deliberate `requireManagedProduct` caller since T-3, before this
  task started; the claim of zero callers was never true during T-4b, not something T-4b changed).
- The two auto-create branches (`openBox` line 263, `addTier` line ~1387): unchanged per T-0 — no
  `CatalogEntityAccess` call, `ProductService.createProduct`'s returned entity is used directly.
  The one thing T-0 flagged as "worth a T-4b check": the `linkedProduct.setIsActive(true)` call
  immediately following each `createProduct(...)` now routes through
  `productStockStateWriter.setActive(linkedProduct.getId(), true)` instead of a raw
  `linkedProduct.setIsActive(true); productRepository.save(linkedProduct)`. T-0's note that "this
  flip is likely already redundant in production behavior" (since `createProduct` with
  `initialStock=null` sets `isActive=false` on the new row, so the flip is never a no-op here) was
  checked against `ProductService.createProduct`'s actual body: `startsActive = initialStock !=
  null && initialStock > 0`, and both call sites pass `initialStock=null` — so the flip is real,
  not redundant, and `ProductStockStateWriter.setActive`'s own dirty-check will still apply
  (unchanged`false`→`true`) rather than no-op.
- The 3 `isActive`/quantity write sites (`closeBox` line 416, `reopenBox` line 641, both plain
  `isActive`-only flips) now call `productStockStateWriter.setActive(child.getId(), <bool>)`
  directly (the local `child.setIsActive(...)` mutation was removed — the writer's own
  `findById`+`save` on the same persistence-context row mutates the identical managed instance, so
  nothing downstream in either method reads a stale in-memory value). `mintAutoCreatedAtBox`'s
  paired quantity+`isActive` write (line ~1305) now calls
  `productStockStateWriter.applyStockState(child.getId(), prev + quantity, true)` — verified the
  hardcoded `true` reproduces the old conditional (`if (!Boolean.TRUE.equals(child.getIsActive()))
  { child.setIsActive(true); }`) exactly, since both branches of that conditional converge on
  `isActive=true`.

**Finding, not a T-4b regression — pre-existing and left as-is:** `mintAutoCreatedAtBox` has zero
callers anywhere in `KujiBoxService` (confirmed by grep — the method is only referenced at its own
declaration). It predates this migration; T-4b touched its body (the `productRepository.save`
line) because the migration mechanically requires updating every direct `ProductRepository` call
site regardless of reachability, but did not add a caller, since inventing one would be scope
beyond "migrate the existing call sites onto the facades." Flagging this for whoever next touches
kuji transfer-in flows — either wire it up or remove it — rather than silently leaving it
undiscovered.

**AC-5b caller-set verification.** New `KujiBoxServiceCatalogFacadeMigrationTest` (7 tests) asserts
both halves of T-0's table for this class: `catalogEntityAccess.getReference` is called for
exactly the parent-product and linked-product ids at the 4 genuine origins (#8-#11, one test per
origin plus one combined "both origins fire together" case for `openBox`), and is **never** called
for the two auto-create branches or the three `isActive`/quantity write sites (asserted via
`verify(catalogEntityAccess, never())...` alongside `verify(productStockStateWriter, times(1))...`
in the same test). Mutation-tested one of these: temporarily changed `openBox`'s auto-create branch
to call `catalogEntityAccess.getReference(linkedProduct.getId())` instead of
`productStockStateWriter.setActive(...)` — confirmed
`openBox_autoCreateTier_neverCallsCatalogEntityAccessForCreatedChildOnlyForParent` failed with
Mockito's "Never wanted here... But invoked here" on exactly that line, then reverted and confirmed
`git diff --stat` showed only the legitimate T-4b migration diff remaining.

- Changed: `KujiBoxService.java` (constructor + 9 call sites); `KujiBoxServiceTest.java` (mock/
  constructor swap only — none of its 17 existing tests exercise `openBox`/`closeBox`/`reopenBox`/
  `addTier`/`patchTier`, so no behavioral stubbing was needed, but this also means those 17 tests
  provided zero coverage of the methods this migration actually touched before today); new
  `KujiBoxServiceCatalogFacadeMigrationTest.java` (7 tests, the only coverage — new or pre-existing
  — of `openBox`/`closeBox`/`reopenBox`/`addTier`/`patchTier` in this codebase).
- ArchUnit stores (same delete-file + recreate mechanism as every prior round for the cycle rule;
  the infrastructure rule updated in place since it only shrank):
  - `noProductionClassOutsideCatalogDependsOnCatalogInfrastructure`: **39 → 28**, diff reviewed —
    exactly 11 lines removed (`KujiBoxService`'s constructor param, field, and 9 call-site entries),
    0 added, no unrelated change.
  - `topLevelPackagesAreFreeOfCycles`: same **4605 lines, same 66 cycle blocks** as every prior
    round — `KujiBoxService` mentions within the frozen evidence text went from 26 to 32 (it now
    appears via `catalog.application` dependencies instead of `catalog.infrastructure`), zero
    structural change (no new cycle groups, same catalog↔services cycle already frozen since T-2).
- Tests: full `./mvnw clean test` (375 tests, up from 368 with the 7 new
  `KujiBoxServiceCatalogFacadeMigrationTest` cases) and `./mvnw test -Dtest='*IT'` (298 tests) both
  green, zero failures/errors.
- Result: **pass**, pending user review of this checkpoint.

### T-4b review round 2: AC-5b's caller-set check was per-class, not whole-codebase

**Finding (P2):** `KujiBoxServiceCatalogFacadeMigrationTest` only exercises `KujiBoxService`
through hand-built Mockito fixtures — it cannot detect a `CatalogEntityAccess` consumer introduced
in a class it never instantiates, or a branch none of its 7 tests happen to drive. It also only
ever calls `verify(catalogEntityAccess, ...).getReference(...)` / `.setActive(...)` — nothing in
the suite would fail if a call site used `requireManagedProduct` instead of the assigned
`getReference`, since that's simply a different mock method nobody asserted against.

**Fix:** added `CatalogEntityAccessCallerSetTest` (`architecture` package, alongside
`ArchitectureTest`) — a whole-codebase static inspection, not a per-class behavioral test. It
imports every production class (`ClassFileImporter` + `DO_NOT_INCLUDE_TESTS`, the same import
`ArchitectureTest` already uses), walks every method/constructor's `getMethodCallsFromSelf()`,
and collects every call whose target owner is `CatalogEntityAccess`, keyed by
(origin class, origin method, accessor method called). A call made inside a lambda (the common
shape at every genuine origin — e.g. `orElseGet(() -> ... catalogEntityAccess.getReference(...))`)
is reported by ArchUnit under a synthetic `lambda$method$N` name; the test strips that back to the
enclosing method name so a lambda-wrapped call site still matches its real origin, rather than
silently falling outside both the expected and actual sets and passing for the wrong reason.

Two assertions:
1. The actual set of (class, method, accessor) triples equals T-0's 11-origin table collapsed to
   method granularity (10 distinct methods, since origins #8/#9 both live in
   `KujiBoxService.openBox`) — fails with the symmetric difference (unexpected vs. missing) printed
   directly, covering both "new consumer elsewhere" and "known origin, wrong accessor."
2. `requireManagedProduct` has zero production callers, asserted as its own test so a future
   regression reads as a stated design violation, not just a generic diff.

**~~This found a real, live defect while being built, not a hypothetical one it might catch
someday.~~ Wrong — corrected in "T-4b review round 3" below.** Running the caller inventory against
the actual compiled codebase (before writing the expected set) surfaced
`LocationInventoryService.addInventory` calling `catalogEntityAccess.requireManagedProduct
(productId)`. ~~a T-3-era migration (not part of this session's T-4b work) that never went through
the catalogQueries.findById(...).orElseThrow(...) + catalogEntityAccess.getReference(...) pattern
every other origin uses... the deviation was undocumented and broke T-0's stated invariant... Fixed
by switching LocationInventoryService to the standard catalogQueries.findById(...).orElseThrow(new
ProductNotFoundException(...)) + catalogEntityAccess.getReference(...) pattern~~ — **this was
backwards.** T-3's own log.md entry (the "T-3 — Migrate the 7 read-only consumers" section, "
`LocationInventoryService` needed `CatalogEntityAccess`, not `CatalogQueries`" bullet) already
explains exactly why `requireManagedProduct` is correct here: this fetch is the method's *first*
existence check for the id, unlike every T-0 origin, which had already validated existence earlier
in the same method — matching the facade's own documented distinction. T-3 recorded this as a
correction to T-0's origin #5 note, not an oversight. I found the code diff via `git diff HEAD`,
confirmed the message text was unchanged, and concluded "undocumented drift" — without reading
T-3's own log section that documented exactly this decision. Reverted the "fix" entirely; see
round 3 below.

Also discovered while (wrongly) fixing this: `addInventory` — the sole `CatalogEntityAccess` call
site in `LocationInventoryService` — had **zero test coverage** anywhere in the suite (the
`@Mock CatalogEntityAccess catalogEntityAccess` field in `LocationInventoryServiceTest` was
declared but never stubbed or verified). This part stands: added 3 tests to
`LocationInventoryServiceTest` (`AddInventoryTests` nested class, kept in round 3 but retargeted
onto `requireManagedProduct` instead of `getReference`).

- Changed (round 2, since corrected — see round 3): `LocationInventoryService.java`,
  `LocationInventoryServiceTest.java`; new `CatalogEntityAccessCallerSetTest.java` (2 tests, kept,
  corrected in round 3).
- Result: **superseded by round 3.**

### T-4b review round 3: the "live defect" was a documented T-3 decision, and the caller-set scan missed method references

Two P2 findings, both confirmed real:

1. **`CatalogEntityAccessCallerSetTest` only scanned `getMethodCallsFromSelf()`.** ArchUnit tracks
   a method reference (e.g. `ids.stream().map(catalogEntityAccess::getReference)`) as a distinct
   `JavaMethodReference` access, separate from a `JavaMethodCall` — the test's scan would silently
   miss a new consumer written in that style. Fixed by switching to
   `JavaCodeUnit.getAccessesFromSelf()` (returns `Set<JavaAccess<?>>`, covering method calls,
   constructor calls, method/constructor references, and field accesses in one pass), filtering out
   `CatalogEntityAccess`'s own internal self-accesses (its constructor/methods touching their own
   `productRepository` field, which otherwise showed up as false "unexpected callers" once
   `getAccessesFromSelf()` widened the scan).
2. **Round 2's `LocationInventoryService` "fix" reverted a documented, deliberate T-3 decision, not
   undocumented drift.** As detailed above — restored `LocationInventoryService.addInventory` to
   `catalogEntityAccess.requireManagedProduct(productId)` exactly as T-3 left it (confirmed via
   `git diff HEAD` matching the pre-round-2 diff exactly), removed the `CatalogQueries` dependency
   round 2 added, and updated `CatalogEntityAccessCallerSetTest`'s expected set: every origin uses
   `getReference` **except** `LocationInventoryService.addInventory`, which uses
   `requireManagedProduct` as T-3's one documented, reviewed exception — not zero. The test's
   `requireManagedProductHasNoProductionCallers` assertion (wrong, per finding 2) was replaced with
   `requireManagedProductHasExactlyTheDocumentedT3Exception`, asserting the caller set equals
   exactly `{LocationInventoryService.addInventory}` — so a *second*, unreviewed
   `requireManagedProduct` caller still fails loudly, while the one T-3 already reviewed does not.

`LocationInventoryServiceTest`'s `AddInventoryTests` (added in round 2, kept here) were retargeted
onto `requireManagedProduct`: happy path stubs `catalogEntityAccess.requireManagedProduct(productId)`
and asserts `getReference` is never called; not-found path stubs `requireManagedProduct` to throw
`ProductNotFoundException` directly (there is no separate `catalogQueries.findById` existence check
to stub — `requireManagedProduct` *is* the existence check here) and asserts
`rejectIfCustomKujiParent` is never reached; existing-inventory-conflict path unchanged in shape.
Removed the now-unused `CatalogQueries` mock from the test class.

Mutation-tested `CatalogEntityAccessCallerSetTest` on the finding-1 axis specifically (the axis the
review named): temporarily added `List.of(inventoryId).stream().map(catalogEntityAccess
::getReference).forEach(p -> {});` to the unrelated `LocationInventoryService.getInventoryById`,
using method-reference syntax rather than a plain call. With the corrected
`getAccessesFromSelf()`-based scan, `catalogEntityAccessCallersMatchT0Inventory` failed, listing
`LocationInventoryService.getInventoryById -> getReference` as unexpected. Then, to prove the fix
was actually load-bearing (not coincidentally passing either way), temporarily reverted the scan to
the old `getMethodCallsFromSelf()`-only approach with the same method-reference mutation still in
place — both tests passed, confirming the old scan really was blind to this exact shape. Reverted
both the scan and the mutation; `git status`/`diff` on all touched files afterward matched the
intended round-3 state exactly, no leftover mutation code.

- Changed: `LocationInventoryService.java` (reverted to T-3's `requireManagedProduct`, exactly
  matching the pre-round-2 diff against `HEAD`); `LocationInventoryServiceTest.java` (`
  AddInventoryTests` retargeted onto `requireManagedProduct`, `CatalogQueries` mock removed);
  `CatalogEntityAccessCallerSetTest.java` (switched to `getAccessesFromSelf()`, self-access filter
  added, expected set corrected to carry `LocationInventoryService.addInventory ->
  requireManagedProduct` as the one documented exception, `requireManagedProductHasNoProduction
  Callers` replaced with `requireManagedProductHasExactlyTheDocumentedT3Exception`).
- ArchUnit stores: `topLevelPackagesAreFreeOfCycles` regenerated a third time (delete-file +
  recreate, same mechanism as every prior round) since reverting `LocationInventoryService` off
  `CatalogQueries` changes its dependency edges back — same **4605 lines, same 66 cycle blocks**,
  diff reviewed, `LocationInventoryService` mention count back to 43 (matching the pre-round-2
  count exactly, confirming a clean revert). `noProductionClassOutsideCatalogDependsOnCatalog
  Infrastructure` untouched throughout rounds 2 and 3 (neither round touched
  `catalog.infrastructure`).
- Tests: full `./mvnw clean test` (**380** tests — same count as round 2, since round 3 only
  corrected existing tests' targets rather than adding or removing any) and
  `./mvnw test -Dtest='*IT'` (298 tests) both green, zero failures/errors.
- Result: **pass**, pending user review of this round.

### T-5: Class-scoped exemption for DevSeedController/AnalyticsSeedService (AC-6)

Before touching the rule, re-verified against the live 28-line frozen store
(`archunit_store/af02cbd2-...`) that every remaining violation belongs to exactly these two
classes — confirmed by inspection, not assumed from spec.md's plan. **Corrected per review**: an
initial pass here mis-split this as 17/11 by a naive `grep -c` on class name, which double-counts
`AnalyticsSeedService` wherever it appears as one of the many constructor-parameter types listed on
`DevSeedController`'s own constructor-injection violation lines (it is one of `DevSeedController`'s
dependencies). Counting only lines whose **origin** (the `Constructor`/`Field`/`Method` the
violation is reported against) is each class gives the correct split: all 28 lines have an origin
of either `DevSeedController` (**16** lines: 2 constructor-parameter, 2 field, 12 call-site) or
`AnalyticsSeedService` (**12** lines: 2 constructor-parameter, 2 field, 8 call-site). No other class
appears, so the exemption list needs exactly these two names, not a third.

Added `ArchitectureTest.CATALOG_INFRASTRUCTURE_ACCESS_EXEMPTIONS` (an array of two fully-qualified
class names: `controllers.DevSeedController`, `services.AnalyticsSeedService`) and
`isExemptFromCatalogInfrastructureRule(JavaClass)`, checked by exact class-name equality (not a
package wildcard, per AC-6's explicit instruction — a new production class added to `controllers`
or `services` is still caught). Wired into `outsideCatalogToCatalogInfrastructureRule()`'s source
predicate: a class now only enters the rule's scope if it is both outside `catalog` **and** not
one of the two named exemptions.

### T-6: Remove the freeze on `noProductionClassOutsideCatalogDependsOnCatalogInfrastructure`, confirm it passes live (AC-8)

**Naming correction:** spec.md's AC-8 and T-6 both name the rule under review as
`noModuleDependsOnAnotherModulesInfrastructure`. No rule by that name exists anywhere in
`ArchitectureTest.java` or the codebase (confirmed by grep) — this is spec-drafting drift for T-2's
actual rule, `noProductionClassOutsideCatalogDependsOnCatalogInfrastructure`, which is Phase 5b's
one and only outside-catalog-to-infrastructure boundary rule and the obvious referent of AC-8's
"this rule now runs live... Phase 5's module-boundary exit-gate proof" language (T-2's own comment
already reads nearly verbatim). Treated as the same rule under two names, not a missing task.

Removed the `freeze(...)` wrapper from `noProductionClassOutsideCatalogDependsOnCatalogInfrastructure()`
— the test now calls `outsideCatalogToCatalogInfrastructureRule().check(importedClasses)` directly.
Deleted the now-orphaned frozen store file (`archunit_store/af02cbd2-0c1a-4bcb-9d26-fc138e429b56`,
87→50→39→28 across T-2/T-3/T-4/T-4b) and its line in `stored.rules`, rather than leaving a stale
file nothing reads once the rule is unfrozen.

**Confirmed live, not assumed:** ran `ArchitectureTest` alone — all 7 methods pass, including the
now-unfrozen rule with zero violations (the two exemptions cover exactly the remaining 28-line
baseline, per T-5). Then `./mvnw clean test` — **380 tests, zero failures**, unchanged from T-4b's
count (this round changed test infrastructure only, not production code, so no test was
added/removed). Then `./mvnw test -Dtest='*IT'` — **298 tests, zero failures**, also unchanged from
T-4b. This satisfies AC-8: the rule runs live against the real codebase, not a frozen snapshot,
proving Phase 5's module-boundary exit gate (catalog repository ownership enforced by ArchUnit) is
actually true today, not just true against a point-in-time freeze.

- Changed: `ArchitectureTest.java` (freeze removed, exemption list + predicate added);
  `archunit_store/stored.rules` (orphaned line removed); deleted
  `archunit_store/af02cbd2-0c1a-4bcb-9d26-fc138e429b56`.
- Tests: `ArchitectureTest` alone (7/7 pass); `./mvnw clean test` (380, zero failures);
  `./mvnw test -Dtest='*IT'` (298, zero failures).
- Result: **pass.**

### T-7: Reconcile and record the SupplierService bookkeeping note (AC-7)

Confirmed `SupplierService` is not one of this record's twelve migrated consumers and needs no
migration here: `grep -n "^package" .../catalog/application/SupplierService.java` shows it already
lives in `catalog.application`, placed there by Phase 5a's move (86ce983, "move catalog domain
(product/category/supplier) into modular package") — it is `catalog`'s own class, not an external
caller of `catalog.infrastructure`, so it was correctly excluded from T-0's twelve-consumer count
and never appears as an origin-class in any frozen-store diff this record produced (T-2 through
T-6). `ShipmentService.createShipment`/`updateShipment` call
`supplierService.resolveOrCreate(supplierName)` — an existing, legitimate same-module-boundary-safe
call from `shipments` into `catalog`'s own facade (recorded already in T-0's "Write consumers, not
entity-access consumers" section) — which is the one place this record's write-surface table
touches `SupplierService` at all, and that call needed no change.

**Count reconciliation (AC-7's explicit ask):** T-0/T-2's "twelve consumers" arithmetic already
excludes `SupplierService`. Of the twelve, ten are migrated consumers — the 7 read-only consumers
(T-3) plus `StockMovementService`/`ShipmentService` (T-4) plus `KujiBoxService` (T-4b) — and the
remaining two, `DevSeedController`/`AnalyticsSeedService`, are exempted rather than migrated
(T-5/T-6). **Corrected per review**: an earlier version of this entry said "eleven other named
consumers plus" the two exemptions, which both double-counts against the twelve-consumer total
(ten migrated + two exempted = twelve, not eleven + two = thirteen) and mislabels the two exempted
classes as "named consumers" alongside the migrated ones. Every store-shrink entry recorded across
T-2 (87), T-3 (87→50), T-4 (50→39), and T-4b (39→28) is attributable entirely to those ten migrated
consumers, with the remaining 28→0 (T-5/T-6) attributable to the two exempted consumers — each of
those rounds' own
log entries above (T-2 through T-4b) names exactly which class's constructor/field/call-site lines
were removed at each step, and `SupplierService` never appears in any of them. The final 28-line
store, read in full during T-5 (above) before its deletion in T-6, also names only
`DevSeedController` and `AnalyticsSeedService`. The intermediate 87/50/39-line stores were
overwritten by later rounds and are not separately recoverable in this session, so this
reconciliation rests on the prior rounds' own recorded diffs plus the final store's contents, not
on re-diffing files that no longer exist. The twelve-minus-`SupplierService` accounting in
spec.md's "Problem and outcome" section reconciles against what's recorded; nothing about the
migration produced a `SupplierService`-related violation or required a `SupplierService` change.

- Changed: nothing (this task is a bookkeeping confirmation, not a code change).
- Tests: none added — no code changed.
- Result: **pass.**

### T-5/T-6 review round: exemption included a production class, and count reconciliation was wrong

Two findings from review, both confirmed real:

- **P2 — the AC-6 exemption's "dev-only, no production equivalent" claim was false for
  `AnalyticsSeedService`.** `AnalyticsController` (a plain `@RestController`, no `@Profile` guard)
  injected `AnalyticsSeedService` and called `recomputeAllRollups(monthsBack)` from its
  `POST /api/analytics/recompute-rollups` endpoint — a real, always-registered production route,
  not gated the way `DevSeedController` (`@Profile("dev")`) and `AnalyticsRollupScheduler`
  (`@Profile("dev")`) are. A class-wide exemption on `AnalyticsSeedService` therefore also exempted
  a production-reachable class from the outside-catalog-to-infrastructure rule, not just the two
  throwaway dev-seed consumers AC-6 and T-2's own rule comment describe. Adding `@Profile("dev")`
  to `AnalyticsSeedService` itself, as a quick fix, would have broken `AnalyticsController`'s
  dependency injection in every non-dev profile (the class the reviewer explicitly flagged as
  wrong to reach for).
  <br>**Fixed by splitting responsibilities, not by broadening the exemption's documentation.**
  Traced `recomputeAllRollups`/`recomputeRollupsOptimized` (the only path
  `AnalyticsController` calls) and confirmed neither method touches `ProductRepository` or
  `CategoryRepository` at all — the recompute path aggregates purely over
  `StockMovementRepository`/`DailySalesRollupRepository` plus a cache clear. Extracted both methods
  into a new `SalesRollupRecomputeService` (`analytics.application` — not the legacy `services`
  package, which is itself frozen against new classes by
  `legacyTechnicalLayerPackagesDoNotGrow`; confirmed by trying `services` first and hitting that
  frozen rule immediately) with its own three constructor dependencies
  (`DailySalesRollupRepository`, `StockMovementRepository`, `CacheManager`) — no
  `ProductRepository`/`CategoryRepository` dependency, so it needs no exemption at all. Repointed
  `AnalyticsController` to depend on `SalesRollupRecomputeService` instead of
  `AnalyticsSeedService`, removing the latter import/field entirely — `AnalyticsController` no
  longer references `AnalyticsSeedService` in any form. Removed the now-dead `CacheManager` field
  (and its now-unused `CacheConfig`/`CacheManager` imports) from `AnalyticsSeedService`, since
  nothing in the class uses it once the two extracted methods are gone (checked by grepping every
  remaining `cacheManager`/`CacheConfig` reference in the file — zero). `AnalyticsSeedService`'s
  remaining callers are now exactly `DevSeedController` and `AnalyticsRollupScheduler`, both
  `@Profile("dev")` — the exemption's "dev-only, no production equivalent" claim is now literally
  true, not just asserted.
  <br>**ArchUnit fallout, resolved with the same discipline as every prior round.** Placing the new
  class under `services` first tripped `legacyTechnicalLayerPackagesDoNotGrow` (a new class in a
  frozen-against-growth legacy package) — fixed by moving it to `analytics.application` (the
  existing `analytics` module skeleton already used by `ForecastPurgeAdapter` for the same
  legacy-repository-from-a-domain-module shape). `topLevelPackagesAreFreeOfCycles`'s frozen
  evidence text then needed regeneration, same delete-file + recreate mechanism as every T-2/T-3/
  T-4/T-4b round: deleted the store file and its `stored.rules` line, toggled
  `archunit.properties`' `allowStoreCreation`/`allowStoreUpdate` to `true` for one scoped run of
  `ArchitectureTest#topLevelPackagesAreFreeOfCycles`, reverted both flags to `false`, and re-ran to
  confirm the rule still passes against the finalized store. New store: same **4605 lines, same 66
  cycle blocks** as every prior round — diffed against the pre-round file (recovered via
  `git show HEAD:...`, since it was still committed at the session's start): exactly 124 lines
  changed on each side, all attributable to `AnalyticsSeedService`'s constructor losing its
  `CacheManager` parameter (a 1:1 evidence-text substitution); `AnalyticsController` and
  `SalesRollupRecomputeService` do not appear in the new store at all — neither participates in any
  frozen cycle.
  <br>Full `./mvnw clean test` (**380 tests**, zero failures — unchanged from T-4b/T-6, since no
  test was added or removed) and `./mvnw test -Dtest='*IT'` (**298 tests**, zero failures,
  likewise unchanged) both green after the fix. No pre-existing test exercised
  `AnalyticsController`'s `/recompute-rollups` route or `AnalyticsSeedService.recomputeAllRollups`
  by name (confirmed by grep across `src/test`), so this fix could not regress an existing
  assertion about that behavior — flagged here as a pre-existing coverage gap the fix inherits,
  not one it introduces or was asked to close.
- **P3 — the reconciliation counts were wrong.** T-5's entry originally reported a 17/11 split
  between `DevSeedController` and `AnalyticsSeedService`, and T-7's entry said "eleven other named
  consumers plus" the two exemptions. Both wrong: the 17/11 split came from a naive
  `grep -c "AnalyticsSeedService"` over the deleted store, which double-counts every line where
  `AnalyticsSeedService` appears as one of `DevSeedController`'s own listed constructor-parameter
  types (not `AnalyticsSeedService`'s own violation). Counting by **origin**
  (`Constructor`/`Field`/`Method` the violation is reported against) instead — recovered via
  `git show HEAD:services/inventory-service/archunit_store/af02cbd2-...` since the file itself was
  already deleted in T-6 — gives the correct split: `DevSeedController` **16** (2
  constructor-parameter, 2 field, 12 call-site), `AnalyticsSeedService` **12** (2
  constructor-parameter, 2 field, 8 call-site), summing to the true 28. Separately, "eleven other
  named consumers plus two exemptions" both miscounts against the twelve-consumer total (eleven
  plus two is thirteen, not twelve) and mislabels the two exempted classes as migrated consumers:
  the correct accounting is **ten** migrated consumers (7 read-only in T-3, `StockMovementService`/
  `ShipmentService` in T-4, `KujiBoxService` in T-4b) plus **two** exempted consumers
  (`DevSeedController`/`AnalyticsSeedService`, T-5/T-6) = twelve, matching spec.md's baseline
  exactly. Both corrections are recorded in place in the T-5 and T-7 entries above rather than as a
  separate restated table, so the reasoning trail isn't duplicated.
- Changed (this round): `AnalyticsSeedService.java` (methods extracted, `CacheManager` field/import
  removed); `AnalyticsController.java` (dependency swapped); new
  `analytics/application/SalesRollupRecomputeService.java`; `ArchitectureTest.java` unchanged by
  this round (the T-5/T-6 exemption code itself was correct — only its supporting class-placement
  needed the split); `archunit_store/stored.rules` and the cycle-rule store file regenerated (new
  id, same 4605 lines/66 blocks); T-5/T-7 log entries corrected in place.
- Tests: `ArchitectureTest` alone (7/7 pass, including the now-materially-true dev-only exemption
  and the regenerated cycle store); full `./mvnw clean test` (380, zero failures); full
  `./mvnw test -Dtest='*IT'` (298, zero failures).
- Result: **pass.**

### Final validation

- `./mvnw clean test`: **380 tests, 0 failures, 0 errors, 0 skipped.** Matches T-4b's count exactly
  — the T-5/T-6 review round's `AnalyticsSeedService`/`SalesRollupRecomputeService` split changed
  production code, not test counts (no test named either method by name).
- `./mvnw test -Dtest='*IT'`: **298 tests, 0 failures, 0 errors, 0 skipped.** Matches T-4b's count
  exactly, for the same reason.
- `ArchitectureTest` run in isolation: all 7 methods pass, including
  `noProductionClassOutsideCatalogDependsOnCatalogInfrastructure` now running unfrozen with zero
  violations against the real codebase (the AC-8 exit-gate proof), with the AC-6 exemption now
  genuinely limited to two classes with no production-reachable path — see the T-5/T-6 review
  round above, which moved `AnalyticsController`'s only production dependency
  (`recomputeAllRollups`) off `AnalyticsSeedService` entirely.
- All acceptance criteria (AC-1 through AC-8) are satisfied: the facade classes and their recorded
  signatures (AC-1), the temporary write surface exactly as enumerated with no `save(Product)`
  escape hatch (AC-2), preserved `shouldBeActive`/kuji-flip behavior with a pinning test (AC-2b),
  the live (unfrozen) outside-catalog-to-infrastructure rule covering all three catalog
  repositories with a probe-verified selector (AC-3, AC-3b), correct baseline arithmetic (AC-3c,
  corrected in the T-5/T-6 review round after an initial miscount), all 7 read-only consumers
  migrated with the N+1/egress check (AC-4), all 3 write consumers migrated with atomic-commit
  coverage (AC-5), `CatalogEntityAccess`'s caller set matching T-0's inventory exactly, including
  the one documented `requireManagedProduct` exception (AC-5b), the named class-scoped dev-seed
  exemption now covering only genuinely dev-only classes (AC-6, corrected in the T-5/T-6 review
  round after `AnalyticsSeedService` was found to have a production caller), and
  `SupplierService`'s exclusion reconciled (AC-7).
- `LocationInventoryService.addInventory`'s `requireManagedProduct` call (T-3's documented
  exception to every other origin's `getReference` strategy) is preserved unchanged through T-5/T-6/
  T-7 — neither task touched `LocationInventoryService`, and `CatalogEntityAccessCallerSetTest`'s
  `requireManagedProductHasExactlyTheDocumentedT3Exception` assertion (added in T-4b round 3) still
  passes as part of the 380-test run above, continuing to enforce that this is the *only*
  `requireManagedProduct` caller in the codebase.
- Phase 5b is complete. Its public contract (the final signatures recorded in T-1) is Phase 6's
  external contract to plan against, per spec.md's "Feeds" section.

## Test plan

- T-0 has no test surface of its own — it's a documentation/inventory task. Its output is verified
  by tracing each `Product`-typed variable to its actual declaration (origin fetch vs. pass-through
  association navigation), not by a test — see the review round's correction above for why the
  original, less rigorous version of this verification wasn't sufficient.
- T-1: covered by the 28 initial unit tests (all `*Test`, run under plain `./mvnw test`) plus the
  review round's 9 `CatalogTransactionPropagationIT` tests (run only via explicit `-Dtest`, never
  under plain `./mvnw test` — Surefire's default pattern excludes `*IT`) and 4 additional
  `CatalogQueriesTest` cases — 41 total, each asserting the specific behavior named in the relevant
  entry (dirty-check preservation, no-cost-in-ProductRef, eligibility-rule parity,
  reference-vs-managed-fetch distinction, MANDATORY-propagation rejection/participation, single- vs.
  bulk-category lookup). The second correction round changed no code, so added no tests; a clean
  `./mvnw clean test` (49 classes, zero failures) plus a separate explicit run of
  `CatalogTransactionPropagationIT` together reconfirm zero regression.
- T-2: covered by `ArchitectureTest.noProductionClassOutsideCatalogDependsOnCatalogInfrastructure`
  itself (frozen against the 87-violation baseline) plus the probe verification's three cases
  (legacy-package reach, cross-module `SupplierRepository` reach, catalog's-own-access passes) —
  the probe test and its two fixtures were temporary and are deleted, so their proof lives in this
  log entry, not in a persisting test file. `ArchitectureTest` run alone (all 7 methods) and a full
  `./mvnw clean test` (49 classes) both confirm zero regression.
- T-3: covered by each migrated consumer's existing tests (updated in place, not rewritten from
  scratch, to keep proving the same behavior the original tests targeted) plus the three
  independently-reviewed ArchUnit store shrinks recorded above, which are themselves the proof that
  every one of the 37 baseline violations attributed to these 7 consumers is now gone and no new
  one appeared. Full `./mvnw clean test` (49 classes, includes `AnalyticsCacheIntegrationTest` —
  an H2-backed `*Test`, not a Postgres-backed `*IT`) and `./mvnw test -Dtest='*IT'` (every real
  `*IT` class, separately) both run to confirm zero regression. AC-4's N+1/egress requirement
  needed its own dedicated test, `CatalogQueriesEgressIT` (see the follow-up entry above) — neither
  of these two runs would have caught a real N+1 in `allProductRefs()` on its own.
- T-4: covered by the two `StockMovementOutboxAtomicityIT` tests (AC-5's atomic-commit
  requirement, both directions, exercising the real `ProductStockStateWriter` and asserting on the
  `Product` row's own `quantity`/`isActive` — not a mocked stand-in, per the review-round fix
  above), the eight `StockMovementServiceActiveStatusDerivationTest` cases (AC-2b's
  `shouldBeActive` pinning across all three real derivation call sites —
  `syncProductTotals`'s own inline derivation, `updateProductActiveStatus` via
  `removeInventoryWithTracking`, and `batchAdjustInventory`'s batch derivation — confirmed to
  actually catch a regression via a temporary `> 0` → `>= 0` mutation, not just asserted to pass),
  and `ShipmentServiceOverrideTest` (updated mocks, unchanged behavior — the override path doesn't
  touch the migrated methods). The two ArchUnit store shrinks
  recorded above are themselves proof the 11 baseline violations these two consumers contributed
  are gone with no new ones. Full `./mvnw clean test` (49 classes) and `./mvnw test -Dtest='*IT'`
  (34 classes) both green — the latter re-confirms `AdjustToKafkaIT` and
  `StockMovementControllerSecurityIT` (real-context coverage of adjust/transfer/receive) survived
  the migration unchanged.
