# Implementation log

## Assumptions and decisions

- None yet beyond what's recorded in `spec.md`'s "Product decisions" section.

## Task record

### T-1 — Delete dead ProductCategory enum + converter

- Verified dead before deleting, not assumed:
  - `grep -rl "ProductCategory\b"` outside the enum's own file found only
    `converters/ProductCategoryConverter.java` (the converter referencing the enum type in its
    signature) — no entity field, no other class.
  - `grep -rl "ProductCategoryConverter"` outside its own file found nothing.
  - `grep -rn "@Convert"` across the codebase found only `identity/domain/User.java` (using
    `UserRoleConverter`) — no `@Convert(converter = ProductCategoryConverter.class)` anywhere.
  - `ProductCategoryConverter` is `@Converter(autoApply = true)`, so it would apply automatically
    to any field typed `ProductCategory` even without an explicit `@Convert`. Checked for such a
    field (`grep -rn "ProductCategory "`) and found none — the enum has zero fields of its type
    anywhere in the codebase, so autoApply is moot.
- Changed: deleted `models/enums/ProductCategory.java` and
  `converters/ProductCategoryConverter.java` (`git rm`).
- Tests: `./mvnw -q -DskipTests compile` — clean. Full `./mvnw test` — clean (exit 0, no
  `Tests run` failure/error lines in the log).
- ArchUnit store regeneration (required to make T-1's own deletion pass — separate from the T-2 and
  T-5 review checkpoints, which are the ArchUnit *rule amendment* and the *cycle-store* regeneration
  respectively):
  - `legacyTechnicalLayerPackagesDoNotGrow` failed first with
    `StoreUpdateFailedException` because the frozen store still listed 3 violation entries for the
    two now-deleted classes, and `allowStoreUpdate=false` blocks even removing obsolete entries.
  - Flipped `freeze.store.default.allowStoreUpdate` to `true` in
    `src/test/resources/archunit.properties`, re-ran `ArchitectureTest` to regenerate, diffed the
    store, then reverted the flag to `false` and re-ran to confirm it still passes.
  - Diff of `archunit_store/1bf5f779-3438-4a5e-b29d-b0f29a9060f3` (the legacy-growth store):
    exactly 3 lines removed, all naming `ProductCategoryConverter`/`ProductCategoryConverter$1`/
    `ProductCategory` — no unrelated entry changed. 425 → 422 lines.
- Result: **pass**.

### T-2 — Amend repositoriesAreOnlyAccessedByServicesOrRepositories (review checkpoint)

- Two correctness issues found during review of the initial proposal, both fixed before
  implementing:
  1. The proposed rule used `noClasses().should(customCondition)` where the custom
     `ArchCondition` itself emits `violated` events for the forbidden case. `noClasses()`
     inverts the should-relationship, so this combination would have inverted the intended
     semantics (the existing custom-condition rules in this file, e.g.
     `modulesDoNotDependOnAnotherModulesApi`, correctly use `classes()` for this exact pattern).
     Fixed: switched to `classes().that(notAllowedCaller).should(violateOnForbiddenDependency)`.
  2. The proposed target predicate would have classified *every* class in a domain module's
     `infrastructure` package as a repository. Checked `identity/infrastructure/`: it holds real
     repositories (`UserRepository`, `InvitationRepository`, `UserSiteMembershipRepository`)
     alongside non-repository infrastructure (`SupabaseAdminService`, `UserRoleConverter`,
     `SiteAccessAuthorizationFilter`) — the naive predicate would have wrongly forbidden
     legitimate dependencies on the latter three. Fixed: target detection is now
     `isPackageOrSubpackageOf(legacy "repositories")` (preserved exactly — two legacy
     repositories, `LocationAggregateRepository`/`InventoryTotalsRepository`, are plain
     `@Repository` classes over `EntityManager`, not Spring Data interfaces, so the package check
     stays as a fallback) **OR** `target.isAssignableTo(org.springframework.data.repository.Repository.class)`
     (catches real Spring Data repository interfaces wherever they live, without misclassifying
     non-repository infrastructure).
  3. Also tightened per review: `isPackageOrSubpackageOf` does exact-root-or-dot-prefix matching
     (`packageName.equals(root) || packageName.startsWith(root + ".")`), not the file's existing
     `isInSubpackage` helper's substring match (`.contains("." + name + ".")`), so a hypothetical
     `catalog.api.services` package could not accidentally read as the legacy `services` layer.
- Verified with real fixtures before touching the frozen store (all fixtures deleted after
  verification, per the same "add probe, confirm, remove" pattern Phase 3 used):
  - Extracted the rule construction into a package-visible `ArchitectureTest.repositoryAccessRule()`
    factory so the probe test evaluates the *exact* production rule, not a re-typed copy.
  - Case 1 (api-layer class → legacy repository, must fail): temporary
    `sites.api.ArchProbeApiToLegacyRepository` injecting `SupplierRepository`. Placed in
    `sites.api` rather than `controllers` specifically so it would not also trip the *separate*
    frozen `legacyTechnicalLayerPackagesDoNotGrow` rule.
  - Case 2 (api-layer class → domain-module repository, must fail — proves the target-selector
    expansion): temporary `sites.api.ArchProbeApiToDomainRepository` injecting `SiteRepository`.
  - Case 3 (domain module's own `application` class → its own repository, must pass — the
    caller-side exemption this task adds): temporary
    `sites.application.ArchProbeApplicationToOwnRepository` injecting `SiteRepository`.
  - Case 4 (non-repository persistence converter not misclassified, must pass): used the
    **existing real dependency** `identity.domain.User` → `identity.infrastructure.UserRoleConverter`
    (`@Convert(converter = UserRoleConverter.class)`, `User.java:44`) rather than a fabricated
    fixture — it already exercises exactly this case.
  - Ran via a temporary `ArchitectureTestRepositoryRuleProbeTest` calling `rule.evaluate(...)`
    directly (no `freeze()` wrapper, so no store dependency) and asserting on the failure report
    text for all four cases. All four passed as expected on the first run. Fixtures and probe test
    then deleted; `./mvnw -q -DskipTests compile` reconfirmed clean afterward.
- Store regeneration: changing the rule's textual description creates a **new** store entry
  (`stored.rules` keys by exact rule text) rather than updating the old one — required both
  `allowStoreCreation=true` and `allowStoreUpdate=true` for this regeneration, not just
  `allowStoreUpdate` as anticipated in `spec.md`'s T-2 wording. Flipped both in
  `src/test/resources/archunit.properties`, ran `ArchitectureTest` once to create/populate the
  new store (`6f315810-2555-4a91-b46e-591288248df0`, 148 lines), then reverted both flags to
  `false` and re-ran to confirm the rule still passes against the finalized store.
- Diff reviewed line by line (old entry `c75e2913-...`, 97 lines vs. new entry, 148 lines — **not**
  a net shrink, matching the reviewer's own prediction that both false positives would be removed
  and new true positives would surface):
  - **Removed** (false positive under the old rule): `identity.application.UserService` reaching
    a legacy repository — wrongly flagged before because only legacy `services` was exempt on the
    caller side, not a domain module's own `application` package.
  - **Added** (real, previously-invisible debt): `LootboxAdminController`, `LootboxController`,
    `ShipmentController`, `dtos.mappers.AuditLogMapper`, `dtos.mappers.ShipmentMapperDecorator` —
    all reach domain-module repositories (e.g. `identity.infrastructure.UserRepository`,
    `sites.infrastructure.*Repository`) that the old rule's narrower target (legacy `repositories..`
    only) could not see.
  - **Unchanged**: `LootboxDevSeed`, `DevSeedController`, `InventoryAggregateController`,
    `NotificationController`, `AnalyticsRollupScheduler` — present in both, confirming this is
    pre-existing, already-accepted debt being re-recorded under the new rule text, not something
    this change introduced.
  - No unrelated violation kind (e.g. a cycle, a different rule's text) appeared anywhere in the
    diff.
  - Removed the now-orphaned `c75e2913-...` store file and its `stored.rules` line (dangling once
    no test references the old rule text).
- Changed: `ArchitectureTest.java` (rule amendment, `repositoryAccessRule()` extraction, three new
  private helpers: `isAllowedRepositoryCaller`, `isRepositoryClass`, `isPackageOrSubpackageOf`);
  `archunit_store/stored.rules` and `archunit_store/6f315810-...` (new); deleted
  `archunit_store/c75e2913-...` (orphaned).
- Tests: full `./mvnw test` — clean (exit 0, zero `ERROR]` lines).
- Result: **pass**.

### T-3 — Move KujiType into catalog.domain

- `git mv models/enums/KujiType.java catalog/domain/KujiType.java`, updated package declaration.
  Updated every consumer: 10 main-source files (imports) plus one fully-qualified inline reference
  each in `services/ProductService.java` (x2), `services/StockMovementService.java` (x2),
  `controllers/DevSeedController.java` (x1), and `repositories/LocationInventoryRepository.java`'s
  JPQL string literal (x1) — none of these were caught by an import-only grep, so a second pass
  specifically for `models.enums.KujiType` as a substring (not just as an import line) was needed.
  `models/enums/` retains six other legitimate enums (`CarrierStatus`, `KujiBoxStatus`,
  `LocationType`, `NotificationSeverity`, `NotificationType`, `ShipmentStatus`,
  `StockMovementReason`) — out of scope for this task, untouched.
- **Process mistake found and corrected before finalizing.** The initial full-class regeneration
  runs for T-1 and T-2 (`./mvnw -Dtest=ArchitectureTest test` — the whole class, not one method)
  ran under `allowStoreUpdate=true`, which silently updates **every** `freeze()`-wrapped rule in
  the file, not just the one being worked on. Comparing the on-disk cycle store against `git HEAD`
  after T-2 showed it had already dropped from 1244 to 761 lines — obsolete cycle entries for the
  two T-1-deleted classes were pruned automatically, without being diffed or reviewed, directly
  short-circuiting AC-4's "deliberately regenerated, diff manually reviewed" requirement.
  - Corrected by restoring `archunit_store/` to `git HEAD` (`git checkout --`) and redoing T-1's
    and T-2's store regeneration scoped to exactly one method each
    (`-Dtest=ArchitectureTest#<methodName>`), confirming via `git status`/diff after each run that
    only the intended store file changed. Both reproduced identical results to the first pass
    (same 4-line legacy-growth diff for T-1; same 148-line/10-class repository-rule store for
    T-2), so no work was lost — but this is the correct discipline going forward: **never run the
    unscoped `ArchitectureTest` class with store-write flags enabled; always scope to the single
    rule under active work.**
- Moving `KujiType` changes its fully-qualified name inside cycle-violation evidence text, so
  (consistent with T-2's finding) `allowStoreUpdate` alone could not silently accept the change —
  the first scoped attempt failed with new violations reported. Used the same delete-line +
  delete-file + `allowStoreCreation=true` + `allowStoreUpdate=true` regeneration as T-2, scoped to
  `topLevelPackagesAreFreeOfCycles` only.
- Diff reviewed against `git HEAD`'s cycle store (not the corrupted intermediate state): **1244
  lines before and after** (same count). `grep -c "^Cycle detected"` — 23 cycle blocks before and
  after, identical. `grep -c "Slice catalog"` — zero in the new store: moving `KujiType` did **not**
  introduce a `catalog`-slice cycle. Every diff line was one of two benign kinds: (a) a
  `ProductService.createProduct(...)` signature evidence line updated from
  `models.enums.KujiType` to `catalog.domain.KujiType` (2 occurrences, same violation, same
  cycle), or (b) a display-sample swap where ArchUnit's truncated "N further dependencies
  omitted" evidence list shows a different sample edge because the `ProductRequestDTO -> KujiType`
  edge no longer counts toward the `dtos`-`models` pair total (3 occurrences, `530→514` and
  `2864→2858` omitted-counts) — not a structural change, just which few example lines get printed.
  No unrelated violation kind, no new/removed cycle block.
- Also found during the full-suite run (after ArchUnit checks passed): a **test-source** reference
  (`src/test/java/.../EventOutboxServiceCreateEventTest.java:7`) still imported
  `models.enums.KujiType`, causing a compilation failure. My initial consumer sweep only grepped
  `src/main`; test sources need the same sweep. Fixed; confirmed no other test-source references
  remain.
- Changed: `catalog/domain/KujiType.java` (new location); 10 main-source files' imports/inline
  references; 1 test-source file's import; `archunit_store/1bf5f779-...`,
  `archunit_store/bb39cfe9-...` → recreated as a fresh UUID, `archunit_store/stored.rules`.
- Tests: full `./mvnw test` — clean (exit 0, zero `ERROR]` lines) after the test-source fix.
- Result: **pass**.

## Test plan

- AC-3: covered by T-2's probe verification (four cases) and the reviewed store diff.
- AC-1 (partial — `KujiType` only; `Product`/`Category`/`Supplier` not yet moved): `KujiType` now
  resides in `catalog.domain`, confirmed by successful compile/test of all consumers.
- AC-4: covered by T-3's cycle-store diff review above (1244 lines before/after, 23 cycles
  before/after, zero new `catalog`-slice cycles).
### T-4 / T-4b — Ports and ProductDeletionCoordinator

- **Design pivot mid-implementation (material architecture decision, escalated and resolved with
  user).** The spec's original wording ("ProductDeletionCoordinator living outside catalog")
  turned out to have two problems, both found empirically, not by re-reading the spec:
  1. Placing new classes (`ProductDeletionCoordinator`, plus adapters for the two read-enrichment
     ports) in legacy `services` — the literal "outside catalog" reading — violates the frozen
     `legacyTechnicalLayerPackagesDoNotGrow` rule. Verified by running the rule with
     `allowStoreUpdate=true`: it reported 4 new violations (the 3 new classes + an anonymous inner
     class) by name, not just a generic failure.
  2. On reflection (user's correction): "outside catalog" was the wrong frame for the coordinator
     itself. Product deletion — identity, hierarchy, the transaction, the FK-ordered delete
     sequence — is a *catalog* use case; only its *foreign repository access* needed to move out.
  - Resolved: `ProductDeletionCoordinator` moved into `catalog.application` (not legacy
    `services`). Its six foreign-repository dependencies were replaced with five catalog-declared
    ports — `ShipmentUsageGuardPort`, `ForecastPurgePort` (extended with a batch method),
    `MachineDisplayCleanupPort`, `InventoryCleanupPort`, `KujiBoxCleanupPort` — each implemented
    by a small new adapter class in its owning module's `application` package (`shipments`,
    `analytics`, `displays`, `inventory`, `kuji` respectively), none of which are frozen legacy
    packages. The two read-enrichment adapters (`ShipmentSupplierDeliveryHistoryAdapter`,
    `KujiOpenBoxAdapter`) were relocated the same way, into `shipments.application`/
    `kuji.application`.
  - Explicitly scoped: this does **not** migrate the rest of `shipments`/`analytics`/`displays`/
    `inventory`/`kuji` — each new file is a single-purpose adapter implementing one catalog port,
    nothing else moved. Recorded here per the user's instruction so this isn't mistaken for an
    early start on those modules' own phases (shipments/kuji/displays: Phase 7; inventory:
    Phase 6; analytics: Phase 7).
  - Verified the dependency-inversion direction is correct, not just assumed: the new adapters
    depend on `catalog.application`'s port interfaces (`{shipments,kuji,...} -> catalog` edges);
    nothing in `catalog` imports any of those five modules directly. Confirmed by the cycle-store
    diff below showing zero new cycles touching those five modules.
- **Kept unchanged (no growth issue, existing files):** `StockMovementService implements
  InitialStockPort` and — after being reverted once, see below — `ForecastService` no longer
  implements `ForecastPurgePort` (moved to the new `analytics.application.ForecastPurgeAdapter`
  instead, for consistency with the other four ports' "new adapter in the owning module" pattern
  once that pattern was established for the deletion-cascade ports).
- `ProductService`'s constructor shrank from 13 params (including 9 foreign
  repositories/services) to 8 (`productRepository`, `categoryService`, `broadcastService`,
  `supplierRepository`, plus the 4 ports: `InitialStockPort`, `ForecastPurgePort`,
  `SupplierDeliveryHistoryPort`, `OpenKujiBoxPort`). `deleteProduct` removed entirely — callers
  use `ProductDeletionCoordinator.deleteProduct` instead (`ProductController` updated).
- `ProductServiceForecastingToggleTest` (the one direct `new ProductService(...)` construction in
  the test suite) rewritten for the new 8-arg constructor and to verify against
  `forecastPurgePort.purgeForecastsForProduct(...)` instead of the repository directly — same 5
  test cases (true→false purges, false→true/unchanged/null do not).
- Frozen-store regeneration, done in two rounds because the first attempt (ports declared but
  coordinator still in legacy `services`) had to be reverted after the design pivot:
  - `repositoriesAreOnlyAccessedByServicesOrRepositories`: passes clean, no store change needed —
    `catalog.application` is already an allowed caller under T-2's amended rule, and none of the
    five new module adapters trip it either (same reasoning, each is its own module's
    `application` package reaching its own module's/legacy repositories).
  - `legacyTechnicalLayerPackagesDoNotGrow`: passes clean, no new entries — confirms none of the
    new files ended up in a frozen package this time.
  - `topLevelPackagesAreFreeOfCycles`: **required regeneration**, scoped delete+recreate (same
    mechanism as T-3, since cycle violation text changes on any dependency-graph change). Store
    grew from 1244 to 5654 lines (23 → 90 cycle blocks). Reviewed, not just accepted on line
    count: `grep -c "^Cycle detected: Slice catalog"` → exactly 67 new blocks, **all** naming
    `catalog`; the original 23 non-catalog cycles are byte-for-byte unchanged; `grep -c` for
    `shipments`/`kuji`/`displays`/`inventory`/`analytics` across the whole file → **zero** — the
    five new module adapters introduced no cycles at all, confirming the port-based design is
    genuinely one-directional. The 67 new `catalog` cycles are the expected, temporary
    consequence of `catalog.application` needing to reference `models.Product`/
    `repositories.ProductRepository`/legacy exception classes before **T-5** moves them into
    `catalog` itself (at which point these become intra-`catalog` dependencies, not cross-slice
    cycles, and should collapse). Flagged here explicitly for T-5's own review, since it's a much
    larger jump than T-3's (which was a net-zero-line change).
- Added `ProductDeletionCoordinatorIT` (2 tests: solo-parent delete, parent-with-child cascade
  delete) — this flow had **zero** direct test coverage before this task (only RBAC-layer coverage
  via `ProductControllerSecurityIT`). Runs through the real Spring context (all 5 ports actually
  wired via DI, not mocked), confirming the extraction didn't silently break wiring. Log output
  confirms the full original sequence still executes: shipment-usage check → child batch cleanup
  → kuji cleanup → forecast/display/inventory/stock-movement cleanup → parent-association removal
  → delete → after-commit broadcast callback registration.
  **Known gap, recorded not silently skipped:** the shipment-usage-guard *rejection* path (product
  actually in use blocks delete with `ProductInUseException`) is not behaviorally tested here —
  building a shipment fixture was out of proportion to this task's scope. **Superseded below** —
  the review round required this and it is now covered.
- Changed (first pass): 5 new port interfaces + `ForecastPurgePort` extended (`catalog/application/`);
  5 new adapter classes (`analytics|displays|inventory|kuji|shipments/application/`); 2 relocated
  adapters; `ProductDeletionCoordinator` (new location: `catalog/application/`); `ProductService`
  (constructor, 4 method bodies, `deleteProduct` removed); `ProductController` (wiring);
  `ForecastService` (interface added then removed, net no functional change); `StockMovementService`
  (interface added, kept at this point); `ProductServiceForecastingToggleTest` (rewritten); new
  `ProductDeletionCoordinatorIT`; ArchUnit stores (`1bf5f779` further pruned, cycle store
  recreated as `f54e3654-...`, 90 cycle blocks: 67 new naming `catalog`, 23 pre-existing unchanged,
  zero touching the 5 new module adapters).
- Tests (first pass): full `./mvnw test` — clean.
- **This was reported to the user as done, but AC-2's transactional/rollback claims and the
  shipment-guard rejection path had no test evidence — only wiring was proven.** Correction below.

### Review round — transactional/rollback tests and shipment-guard rejection tests

- `ProductDeletionCoordinatorIT` extends `BaseIntegrationTest`, which is itself `@Transactional`
  and rolls every test back (`@BeforeEach`/`@Test` run inside one outer transaction that never
  really commits). That proves DI wiring but **cannot** prove commit, rollback, or
  `TransactionSynchronization.afterCommit()` behavior — `afterCommit()` never fires at all inside a
  transaction that's ultimately rolled back by the test framework, not committed.
  `ProductServiceForecastingToggleTest` uses bare Mockito (`@ExtendWith(MockitoExtension.class)`,
  no Spring context, no real transaction manager), so it can't establish transaction behavior
  either — it only proves the port method is/isn't called, not that a DB write is/isn't durable.
- Added `ProductLifecycleTransactionBehaviorIT` — deliberately does **not** extend
  `BaseIntegrationTest`; plain `@SpringBootTest(webEnvironment = NONE)` with `@MockBean` on the 5
  catalog ports plus `SupabaseBroadcastService`, so each call under test runs its own real
  transaction that actually commits or rolls back, verified from a fresh repository query
  afterward (no enclosing test transaction to mask the result):
  - `initialStockFailureRollsBackProductCreation` — `InitialStockPort` mocked to throw; asserts
    the product row does not exist afterward (the earlier `productRepository.save` rolled back
    too, not just the failed port call).
  - `forecastPurgeFailureRollsBackUpdate` — `ForecastPurgePort.purgeForecastsForProduct` mocked to
    throw during a true→false toggle; asserts the reloaded product's `forecastingEnabled` is still
    `true` (the `productRepository.save` that already ran earlier in the same method rolled back
    together with the purge attempt) and that no broadcast fired.
  - `deletionFailureRestoresPreviouslyDeletedChildRow` — the literal "restores previously deleted
    dependent rows" case: creates a parent+child, lets the children's batch cleanup (including a
    **real** `productRepository.deleteAll(children)`) execute normally, then makes the *parent's*
    later single-item `forecastPurgePort.purgeForecastsForProduct` call throw. Asserts both the
    parent **and** the child (deleted earlier in the same transaction, before the throw) still
    exist afterward — proving the whole transaction, not just the step that failed, rolled back.
  - `successfulDeletionBroadcastsAfterCommit` — positive case: real delete succeeds, asserts
    `broadcastService.broadcastProductUpdated` **was** called (exactly once, with the deleted ID) —
    the counterpart proof to the three rollback tests' `never()` assertions, so "only after commit"
    is demonstrated in both directions, not just the negative half.
  - `shipmentGuardRejectsAReferencedParent_noCleanupOrDeletionOccurs` /
    `shipmentGuardRejectsAReferencedChild_protectsSiblingsFromPartialDeletion` — per the review's
    P2 finding: `ShipmentUsageGuardPort` stubbed to report one product as in-use (parent in one
    test, a child in the other). Both assert `ProductInUseException`, that the product(s) still
    exist, and — via `verify(..., never())` on all four other ports — that **no** cleanup or batch
    call happened. The child case is the one the reviewer specifically called out: it proves
    discovery of a blocked child happens during the children-validation loop, *before* any
    sibling's batch cleanup/delete runs, so an unrelated sibling is never partially processed
    ahead of the rejection being raised.
  - Each test resets `broadcastService` (and `forecastPurgePort` where reused) after fixture setup
    via `productService.createProduct`, since product creation broadcasts eagerly and unrelated to
    the scenario under test — an initial version of these tests asserted `never()` without this
    reset and failed on the setup calls' own broadcasts, not the scenario being tested.
- **A real design defect surfaced by attempting to write these tests, not by inspection**:
  `StockMovementService implements InitialStockPort` meant `@MockBean InitialStockPort` in the new
  test class deleted the *entire* `StockMovementService` bean from the Spring context (Boot's
  mock-bean mechanism replaces by type, and `StockMovementService` was the only bean implementing
  that interface), breaking unrelated beans that depend on `StockMovementService` by its concrete
  type (e.g. `AuditLogDTOMapper` — `UnsatisfiedDependencyException` at context startup). Fixed by
  extracting a dedicated `inventory.application.InitialStockAdapter` implementing
  `InitialStockPort` (delegating to `StockMovementService.createInventoryWithTracking`, same
  behavior), and reverting `StockMovementService` to not implement the port — matching the
  dedicated-adapter pattern already used for the other four ports, which is exactly what avoided
  this problem for them.
- This adapter relocation shifted the cycle graph again (an inventory-module class now depends on
  `catalog.application.InitialStockPort` and legacy `StockMovementService`/enum classes, instead of
  `StockMovementService` itself doing so). Regenerated the cycle store a second time, scoped, same
  delete+recreate mechanism. **Note on process:** mid-diagnosis I captured what I believed was the
  pre-fix baseline and its cycle count didn't match my own earlier recorded figure (90 vs. an
  observed 70) — I could not fully reconstruct why from the command history, and rather than trust
  either stale number, treated the regeneration as a fresh, independent computation from the
  current codebase and verified its *content* directly instead of its line-count history:
  `grep -c "^Cycle detected: Slice catalog"` → 67 (unchanged), non-catalog cycle count → 23
  (unchanged, same as every prior round), and zero cycles touching any of the 5 module adapters,
  `inventory` included — confirming `InitialStockAdapter`'s move didn't introduce a new cycle
  either. New store: `72e7b627-...`.
- Changed (this round): `StockMovementService` (port implementation removed);
  `inventory/application/InitialStockAdapter.java` (new); new
  `ProductLifecycleTransactionBehaviorIT` (6 tests); cycle store recreated again (`72e7b627-...`).
- Tests: full `./mvnw test` — clean (exit 0, zero `ERROR]` lines) after this round.
- Result: **pass**.

### T-5 — Move Product, Category, Supplier + exceptions to catalog; repositories to catalog.infrastructure (review checkpoint)

- Confirmed before moving (per T-4's diagnosis method — no fully-qualified inline references, so
  a plain import-line move should suffice): `grep` for fully-qualified references to
  `models.Product`/`models.Category`/`models.Supplier`, the 3 repositories, and the 7 exception
  classes outside import lines, across both main and test sources — all clear, only import-line
  usage. This assumption was later found incomplete (see below).
- Moved via `git mv`: `Product`/`Category`/`Supplier` → `catalog/domain/`; `ProductRepository`/
  `CategoryRepository`/`SupplierRepository` → `catalog/infrastructure/`; `ProductInUseException`/
  `ProductNotFoundException`/`CategoryInUseException`/`CategoryNotFoundException`/
  `DuplicateCategoryException`/`DuplicateSkuException`/`SupplierNotFoundException` →
  `catalog/domain/` (13 files total). Updated package declarations; removed `Product.java`'s
  now-redundant self-import of `KujiType` (same package since T-3).
- Updated every import-line reference across main + test sources (scripted `sed`, one pass per
  class): 26 main files for `Product`, 9 for `Category`, 5 for `Supplier`, plus the repository and
  exception import lines, plus ~20 test files.
- **The "no fully-qualified references" check missed same-package implicit access — files that
  never had an import line at all because they lived in the same legacy package as the class
  being moved.** Found only by compiling, not by the earlier grep, since there was no import line
  for the grep to match in the first place:
  - `models/MachineDisplay.java` referenced `Product` with zero import (same package before the
    move).
  - `exceptions/GlobalExceptionHandler.java` referenced all 7 moved exception classes with zero
    imports (same package before the move).
  - Both fixed with explicit imports.
- **Also found only by compiling**: five files used a wildcard import
  (`import com.mirai.inventoryservice.repositories.*;` or `.exceptions.*;`) that silently stopped
  covering `ProductRepository`/`CategoryRepository`/`ProductNotFoundException` once those classes
  moved out of the wildcarded package — `StockMovementService`, `LocationInventoryService`,
  `ShipmentService`, and two test classes (`AuditLogSpecificationsIT`, `StockMovementRepositoryIT`,
  both in the `repositories` test package with the same same-package-implicit issue as
  `MachineDisplay`). Fixed with explicit imports alongside each wildcard.
- One fully-qualified inline reference in test sources
  (`ShipmentServiceOverrideTest.java:37`, a `@Mock` field type) that the pre-move grep should have
  caught but didn't — rechecked why: the original grep excluded `import` lines but this file's
  reference wasn't on an import line either; it was simply missed in the first pass and caught by
  `test-compile`. Fixed.
- `LIST_ITEM_SELECT`'s JPQL constructor-expression string in the moved `ProductRepository` was
  checked, not assumed unaffected: it references `dtos.responses.ProductListItemDTO`'s FQN, which
  has not moved (that's T-6, not T-5) — confirmed no change needed here, despite spec.md's T-5
  wording implying an FQN update in this task. Recorded as a spec-wording correction, not a
  behavior change: the FQN update happens in T-6, when the DTO itself moves.
- Frozen-store regeneration, three rules, each scoped and reviewed independently:
  - `legacyTechnicalLayerPackagesDoNotGrow`: pure obsolete-removal, `allowStoreUpdate` alone
    sufficient. Diff: exactly 11 entries removed (`Product`/`Category`/`Supplier` classes +
    builders, 3 repositories) — the 7 exception classes never counted, since `exceptions` was
    never one of the six frozen packages. No unrelated change.
  - `repositoriesAreOnlyAccessedByServicesOrRepositories`: text-changed (package names in
    evidence), same delete+recreate mechanism as T-2/T-4. New store: same **148 lines**, and a
    diff by class name (not exact text) against the pre-move store showed **zero** difference in
    the set of violating classes — confirms this is the identical pre-existing debt, just
    re-pathed, not new debt.
  - `topLevelPackagesAreFreeOfCycles`: delete+recreate. **90 → 70 cycle blocks** (down from the
    47 `catalog`-involving... see below for the precise count and the edge-level review the
    reviewer asked for, not just this line-count headline).
- **Cycle-store deep review, edges not just count, per the explicit instruction to treat T-4's 67
  `catalog` cycles as provisional and verify what actually resolves at T-5:**
  - Non-catalog cycle count: **23**, byte-identical to every prior round — untouched pre-existing
    debt, confirmed again.
  - `catalog`-involving cycle count: **67 → 47** (not to zero, and not expected to reach zero at
    this task — see next point).
  - Traced the remaining 47 to their root cause rather than accepting the lower number at face
    value: `grep`'d every `catalog` package file for outbound imports to legacy packages. Found
    **exactly two** surviving edges, and confirmed (via the actual cycle evidence text) that these
    two edges are what every one of the 47 cycle blocks cites as `catalog`'s outgoing dependency —
    no third source:
    1. `catalog.infrastructure.ProductRepository → dtos.responses.ProductListItemDTO` (the
       `findActiveAsListItems`/`findAllAsListItems` JPQL projection return type). **Resolves at
       T-6**, when `ProductListItem` moves into `catalog.application` per spec.md.
    2. `catalog.application.ProductDeletionCoordinator → services.SupabaseBroadcastService`.
       **Will not resolve at T-6 or anywhere in Phase 5.** `SupabaseBroadcastService` is a
       cross-cutting realtime-broadcast utility that many legacy `services` classes depend on
       (confirmed: `AnalyticsSeedService`, `InventoryAggregateService`, `KujiBoxService`, and
       others all appear as the `services → catalog` half of these same cycle blocks, via their
       own `ProductRepository`/`CategoryRepository` dependencies). Closing this specific edge
       would require moving `SupabaseBroadcastService` itself into `shared` (the one module every
       business module may depend on, per `sharedDoesNotDependOnBusinessModules`) — genuinely out
       of Phase 5's scope, not a T-5 or T-6 task. **Recorded here as a concrete, named follow-up**
       rather than left as an unexplained residual cycle count.
  - This confirms the reviewer's prediction exactly: T-5 alone does not collapse all of T-4's
    `catalog` cycles, and the reason is precisely the kind of remaining-legacy-dependency edge
    named in the review, not some other regression.
- Changed: 13 files moved (`Product`/`Category`/`Supplier`, 3 repositories, 7 exceptions); ~40
  main + ~22 test files' imports updated; `MachineDisplay.java`/`GlobalExceptionHandler.java`
  (explicit imports added for same-package-implicit references); `StockMovementService.java`/
  `LocationInventoryService.java`/`ShipmentService.java`/`AuditLogSpecificationsIT.java`/
  `StockMovementRepositoryIT.java` (explicit imports added alongside wildcards);
  `ShipmentServiceOverrideTest.java` (fully-qualified reference updated); ArchUnit stores: all
  three rules regenerated (`1bf5f779` further pruned; repository store recreated as
  `f0384129-...`; cycle store recreated as `f46f1405-...`).
- Tests: full `./mvnw test` — clean (exit 0, zero `ERROR]` lines).
- Result: **pass**.

### T-6 — Move ProductService/CategoryService/SupplierService to catalog.application; DTOs/mappers to catalog.api; update LIST_ITEM_SELECT's FQN

- Scoped the DTO/mapper set from AC-1's wording ("controllers, request/response DTOs, and mappers
  live in catalog.api") plus actual usage, not from name pattern-matching alone. Checked each
  candidate's real consumer before moving:
  - Moved (genuinely Product/Category/Supplier's own request/response shape, consumed only by
    their own controller/service/mapper): `ProductRequestDTO`, `ProductResponseDTO`,
    `ProductSummaryDTO`, `CategoryRequestDTO`, `CategoryResponseDTO`, `SupplierRequestDTO`,
    `SupplierResponseDTO`, `BulkAssignProductsRequestDTO`, `ProductMapper`, `CategoryMapper`.
  - **Deliberately not moved**, despite "Product"/"Category" in the name, matching the
    `LocationAggregateController`/`ProductReportBundleService` precedent (Phase 4/5a leaves
    cross-module read models behind): `ProductInventoryEntryDTO`/`ProductInventoryResponseDTO`
    (consumed only by `InventoryAggregateController`/`InventoryAggregateService` — an
    inventory-domain aggregation view, not a catalog DTO) and `CategoryInventoryDTO` (consumed
    only by `AnalyticsController`/`AnalyticsService` — an analytics rollup, not exposed by
    `CategoryController` at all, confirmed by checking its actual usage before assuming from the
    name).
- Moved `ProductListItemDTO` to `catalog.application` (the read-projection DTO, per AC-1), not
  `catalog.api` — it's an internal repository projection type, not a request/response wire shape.
- `ProductRepository.LIST_ITEM_SELECT`'s JPQL constructor-expression FQN updated **in the same
  edit** as the DTO's move (`sed` on both the import line and the string literal in one command),
  per the explicit instruction — not as two separate steps that could drift apart.
- **Implicit same-package and wildcard-import checks, done properly this time.** T-5's ad-hoc
  shell sweep silently mis-executed: this shell is `zsh`, and `for c in $classes` does not
  word-split an unquoted variable the way bash does, so every prior "clean, no implicit
  references" sweep in T-5 was actually only ever testing the first word of the class list as one
  literal string — a methodology bug, not a deliberate check. It caused no incorrect *shipped*
  code (the compiler still caught every real gap in T-5, just across more iterative rounds than
  necessary), but it meant the sweep's "clean" result was not trustworthy. Fixed for this task by
  writing the class list to a file and reading it with `while IFS= read -r c`, which is
  shell-agnostic. Found by the corrected sweep (all real, all fixed):
  - `RedactedFieldsAreOmittedFromJsonIT.java` (test, `dtos.responses` package) used
    `ProductResponseDTO` and `ProductListItemDTO` with zero imports (same-package-implicit,
    the same failure shape as T-5's `MachineDisplay`/`GlobalExceptionHandler`).
  - `ShipmentServiceOverrideTest.java` and `KujiBoxServiceTest.java` (test, `services` package)
    used `ProductService`/`CategoryService`/`SupplierService` with zero imports
    (same-package-implicit).
  - `ProductService.java` and `ProductListItemDTO.java` themselves (now in `catalog.application`)
    implicitly referenced `SupabaseBroadcastService` (still legacy `services`) and
    `CategoryResponseDTO` (now `catalog.api`) respectively — same-package-implicit *before* the
    move, needing a new explicit import *after* it.
  - The corrected sweep also surfaced 10 **false positives** (self-references between classes now
    correctly co-located in the same target package, e.g. `ProductResponseDTO` ↔
    `ProductSummaryDTO` both in `catalog.api` — no import needed or expected — plus a few hits
    inside Javadoc comments, not code). Each was checked individually against the actual package
    layout before being dismissed, not assumed safe.
  - `services/ShipmentService.java` and `services/KujiBoxService.java` also gained explicit
    imports for `ProductService`/`SupplierService` (same-package-implicit before the move).
- No wildcard imports of `dtos.responses.*`/`dtos.requests.*`/`dtos.mappers.*`/`services.*` exist
  anywhere in the codebase — checked directly, none found, so no wildcard-related gap this round
  (T-5's wildcard problem doesn't recur here).
- Frozen-store regeneration, two rules affected (repository-access rule untouched — no repository
  moved this task):
  - `legacyTechnicalLayerPackagesDoNotGrow`: pure obsolete-removal. Diff: exactly 26 entries
    removed — the 13 moved classes plus their Lombok-generated `Builder` companions and the
    MapStruct-generated `ProductMapperImpl`. No unrelated change.
  - `topLevelPackagesAreFreeOfCycles`: delete+recreate (same mechanism as every prior round).
    **70 → 48 total cycle blocks.**
- **Cycle-store reconciliation before accepting, per the explicit instruction — edges, not just
  the count:**
  - Non-catalog cycles: **23**, byte-identical yet again (6th consecutive confirmation this exact
    figure never moves).
  - `catalog`-involving cycles: **47 → 25**. Re-ran the same edge-tracing method as T-5: grepped
    every file under `catalog/` for outbound imports to legacy packages. Found exactly **one**
    surviving edge now (`catalog.application.{ProductService,ProductDeletionCoordinator} →
    services.SupabaseBroadcastService`) — the `dtos` edge (`ProductRepository →
    ProductListItemDTO`) is gone, exactly as predicted at T-5. Confirmed every one of the 25
    remaining cycle blocks cites this single edge as `catalog`'s outgoing dependency — no third
    source, no surprise regression. 25 + 23 = 48, matching the total.
  - **Per instruction, not resolved and not prescribed a specific fix.** Recorded as residual
    architectural debt with two named options for whoever eventually addresses it (Phase 6/7,
    likely alongside `analytics`'s own broadcast/notification needs), neither chosen here:
    (a) move `SupabaseBroadcastService` itself into `shared`, or (b) declare a narrow
    `catalog.application` broadcast port (matching this task's own port pattern) implemented by an
    adapter that wraps `SupabaseBroadcastService`. Being widely depended on by legacy `services`
    classes doesn't by itself establish that `shared` is the right home — that depends on whether
    `shared`'s cross-cutting-infrastructure charter actually fits a Supabase-specific realtime
    broadcast concern, which is a design question for whoever picks this up, not decided here.
  - **Separate finding, recorded on its own — not a cycle, and the cycle count does not expose
    it.** "One remaining edge" above is accurate only at the top-level *package* boundary
    (`catalog` vs. every other slice). Within `catalog` itself there is an inverted
    application→API layer dependency: `catalog.application.SupplierService` imports
    `catalog.api.ProductMapper`/`ProductResponseDTO`/`SupplierResponseDTO`, and
    `catalog.application.ProductListItemDTO` imports `catalog.api.CategoryResponseDTO`. The
    intended layering (matching `identity`/`sites`'s existing shape) is `api → application`, not
    the reverse — `application` is supposed to be usable without the wire/request-response layer.
    ArchUnit's cycle rule slices by top-level package only, so `application → api` inside the same
    `catalog` slice is invisible to it; this was found by direct inspection
    (`grep "^import com.mirai.inventoryservice.catalog.api" catalog/application/*.java`), not by
    any automated check in this repo. Not fixed in this record — untangling it means either
    giving `SupplierService` its own response shape instead of reusing `ProductResponseDTO`, or
    moving `ProductMapper`'s mapping logic so `application` doesn't need `api` to produce a
    response, and `ProductListItemDTO`'s dependency on `CategoryResponseDTO` similarly needs its
    own lighter category projection instead of reusing the API response type. Recorded as a named
    follow-up, distinct from the `SupabaseBroadcastService` cross-module edge above.
- **`LIST_ITEM_SELECT` executed against real PostgreSQL, per the explicit instruction — not just
  compiled.** Added `ProductRepositoryListItemsIT` extending the existing
  `BaseKafkaIntegrationTest` (Testcontainers Postgres 16, matching the codebase's established
  precedent for repository tests needing real Postgres semantics, e.g.
  `UserSiteMembershipRepositoryIT`). Two tests: `findAllAsListItems` returns a real product with
  its nested category populated correctly, and `findActiveAsListItems` correctly filters out an
  inactive product. Confirmed from the run log that the datasource was real PostgreSQL
  (`org.postgresql.jdbc.PgConnection`, "Database version: 16.15"), not H2 — this is the specific
  scenario AC-6 exists to catch: a compile-clean, runtime-broken JPQL string. Both tests passed.
- Changed: 10 files moved to `catalog/api/` (4 request DTOs, 4 response DTOs, 2 mappers); 3
  services moved to `catalog/application/`; `ProductListItemDTO` moved to `catalog/application/`;
  `LIST_ITEM_SELECT` FQN updated; ~15 files across main+test gained explicit imports for
  same-package-implicit references; new `ProductRepositoryListItemsIT`; ArchUnit stores
  (`1bf5f779` further pruned; cycle store recreated as `6da7a570-...`).
- Tests: full `./mvnw test` — clean (exit 0, zero `ERROR]` lines), plus the real-Postgres
  `ProductRepositoryListItemsIT` run separately (Testcontainers, not part of the default H2 suite).
- Result: **pass**.

### Documentation corrections (before T-7, per review)

- `spec.md`'s AC-2 and T-4 still described `ProductDeletionCoordinator` as living **outside**
  `catalog` — the design actually accepted (and implemented) in T-4 after review is catalog-owned
  orchestration with foreign access expressed as ports. Updated both to match what was built, not
  what the original draft assumed.
- Recorded, separately from the `SupabaseBroadcastService` cross-module edge, an **intra-catalog**
  layer inversion the cycle count cannot see (ArchUnit's cycle rule slices by top-level package
  only): `catalog.application.SupplierService` imports `catalog.api.ProductMapper`/
  `ProductResponseDTO`/`SupplierResponseDTO`, and `catalog.application.ProductListItemDTO` imports
  `catalog.api.CategoryResponseDTO`. The intended layering (matching `identity`/`sites`) is
  `api → application`, not the reverse. Not fixed here — recorded as a named follow-up requiring
  either a dedicated response shape for `SupplierService` or relocating the mapping logic so
  `application` doesn't need `api`.

### T-7 — Move ProductController/CategoryController/SupplierController to catalog.api; promote CostVisibilityPolicy; re-run RBAC/cost-visibility ITs; regenerate contracts

- Checked real consumers before moving anything (no surprises this round): grepped every
  `applyCostVisibility` call site across the whole codebase, not just `ProductController`/
  `SupplierController`. Found `ShipmentController` and `KujiBoxController` mention it too, but
  only in **comments** ("same pattern as...") — `ShipmentController` has its own independent
  `applyCostVisibility(ShipmentResponseDTO, ...)` method operating on a different DTO; neither
  actually calls the moving code. Confirmed the only real cross-controller consumer is
  `SupplierController`'s `getSupplierProducts` endpoint (`GET /api/suppliers/{id}/products`,
  returning `List<ProductResponseDTO>`) — this is what "preserve redaction across both product
  and supplier responses" means in practice, since `SupplierResponseDTO` itself carries no
  cost/MSRP fields to redact.
- Moved `ProductController`/`CategoryController`/`SupplierController` to `catalog.api`. Promoted
  the package-private statics into `catalog.api.CostVisibilityPolicy` — a plain class with public
  static methods (matching `RolePermissions`'s own style, per AGENTS.md's "don't force DI where
  there's no state"), not a `@Component`, since there is nothing to inject. Preserved all three
  original method signatures (`applyCostVisibility(ProductResponseDTO, ...)`,
  `applyCostVisibility(List<ProductResponseDTO>, ...)`, `applyCostVisibilityToListItem(...)`)
  and call-site behavior exactly; only the call site changed from an implicit same-class call to
  `CostVisibilityPolicy.xxx(...)`.
- The original class comment's stated reason for keeping this same-package rather than in the
  DTO/mapper layer ("adding one more [identity] edge here tipped an existing frozen-cycle
  violation") no longer applies verbatim — `ProductMapper`/`ProductResponseDTO` are themselves now
  in `catalog.api`, the same package `CostVisibilityPolicy` moved into, so this isn't a new
  concern introduced by this move; it was already resolved by T-6 relocating the DTOs/mapper.
- Implicit/wildcard sweep (shell-agnostic `while read`, per T-6's fix) found zero real gaps this
  round — all 10 hits were same-package self-references or comment/Javadoc/`@DisplayName` text
  (e.g. `ProductControllerSecurityIT`'s own class name containing "ProductController" as a
  substring). Checked each individually rather than trusting the sweep's raw hit list.
- Frozen-store regeneration, two rules (repository-access rule unaffected — no repository moved
  this task):
  - `legacyTechnicalLayerPackagesDoNotGrow`: pure obsolete-removal. Diff: exactly 3 entries removed
    (the 3 moved controllers). No unrelated change.
  - `topLevelPackagesAreFreeOfCycles`: delete+recreate. **48 → 66 total cycle blocks** (an
    increase, not a decrease — flagged and explained below, not just accepted).
- **Cycle-store reconciliation before accepting.** My first edge-check reused T-5/T-6's grep
  pattern (`services|models|repositories|dtos|exceptions|controllers`), which does **not**
  include `identity` — a gap in the check itself, not just the code, caught by rerunning it
  without that restriction:
  - Non-catalog cycles: **23**, unchanged (7th consecutive confirmation).
  - `catalog`-involving cycles: **25 → 43**, a genuine increase, fully explained: promoting
    `CostVisibilityPolicy` into `catalog.api` created a **new** `catalog → identity` edge
    (`RolePermissions.hasPermission`/`Permission.COSTS_VIEW`/`MSRP_VIEW`) that didn't exist before
    — previously this logic lived in legacy `controllers`, which already depended on `identity`
    with no cycle consequence attributed to `catalog`. Confirmed by re-running the full outbound
    scan (`grep` every `catalog/` file for `import com.mirai.inventoryservice.<non-catalog>`)
    that exactly **two** source methods explain every one of the 43 blocks — no third source:
    `CostVisibilityPolicy.applyCostVisibility` (new, → `identity`) and
    `ProductDeletionCoordinator`'s constructor (→ `services.SupabaseBroadcastService`, carried
    over from T-6, unchanged).
  - **Correction to this entry's original wording**: the first pass through this reconciliation
    treated the `identity` edge as settled because it passed the frozen-cycle check and because
    the dependency is real and pre-existing (legacy `ProductController`/`SupplierController`
    already called `identity.domain` directly before this move). Review caught that this
    reasoning conflates two different properties: passing the cycle check only proves no import
    *cycle* exists — it says nothing about whether the dependency matches
    `docs/specs/spring-domain-modular-monolith.md`'s intended graph. Checked against that doc
    directly: §6.2 lists `catalog ──► shared` only, not `identity`; §4.1 additionally requires
    cross-module calls to go through a documented `application`-package facade, and
    `RolePermissions`/`Permission` are `identity.domain` types called directly, not a facade. This
    **is** a real gap against the durable architecture. Recorded properly in `spec.md`'s AC-2 as
    transitional debt with a removal condition (an `identity.application` permission-check facade
    that doesn't exist yet), not silently cleared because a narrower check happened to pass.
- RBAC and cost-visibility ITs run **explicitly**, not just as part of the full suite, per
  instruction: `ProductCostVisibilityIT` (6 tests), `SupplierProductsCostVisibilityIT` (3),
  `ShipmentCostVisibilityIT` (3), `ProductControllerSecurityIT` (22 — nested `@Nested` nested
  test classes; Surefire's plain-text summary misreports these as "Tests run: 0" for the outer
  class, a known nested-class display quirk — the XML report's `tests="22"` is what actually ran),
  `RBACAlignmentIT` (28). All green, zero failures.
- Both contract artifacts regenerated **before** checking the diff, not assumed unaffected:
  `OpenApiContractExportTest` re-run (`packages/contracts/openapi.json`) — diff empty.
  `npm run generate` in `packages/api-client` (`src/schema.d.ts`) — diff empty. `npm run
  typecheck` also clean. Confirms the pure package move changed no wire shape.
- Changed: 3 controllers moved to `catalog/api/`; new `catalog/api/CostVisibilityPolicy.java`;
  `ProductController`'s/`SupplierController`'s/`CategoryController`'s self-package imports
  cleaned up; ArchUnit stores (`1bf5f779` further pruned; cycle store recreated as
  `9d1ae9e1-...`).
- Tests: full `./mvnw test` — clean (exit 0, zero `ERROR]` lines). RBAC/cost-visibility ITs and
  contract regeneration also run and verified separately, per instruction.
- Result: **pass**.

### T-8 — Subcategory retirement decision recorded

- Restating the decision already established in `spec.md`'s "Product decisions" (T-1's
  investigation): `subcategories` (bootstrap-only SQL in `infra/init-db/04-dynamic-categories.sql`,
  no JPA entity, no repository, no code reference anywhere in `services/inventory-service`) and
  `products.subcategory_id`/`products.category` (superseded by `categories.parent_id` +
  `UNIQUE(parent_id, slug)`, which `CategoryService` actively uses for real subcategory CRUD
  today) are **retired, not merged**. This task does not change that decision — it records the
  concrete row-count queries a future drop migration must run first, per spec.md's explicit
  scoping ("no schema change in this task").
- Production row-count queries to run before any future drop (not run in this task — no
  production database access was used or attempted; per AGENTS.md, schema drops follow
  expand/backfill/verify/constrain and this task is documentation only):
  ```sql
  SELECT count(*) FROM subcategories;
  SELECT count(*) FROM products WHERE subcategory_id IS NOT NULL;
  SELECT count(*) FROM products WHERE category IS NOT NULL;
  ```
  If any query returns nonzero, the fallback recorded in the architect's original design note
  applies: backfill the affected rows into `categories` (with `parent_id` set appropriately)
  before dropping the legacy columns/table, rather than dropping data outright.
- Not done in this task, deliberately: no migration file, no `ALTER TABLE`/`DROP TABLE`. This
  also interacts with Phase 1's still-open item (Flyway not yet canonical) — whoever picks up the
  actual drop needs either a Flyway-baselined environment or the same manual-DDL discipline used
  for `V50`/`V52`-`V55`.
- Result: **pass** (documentation task; no code or schema changed).

## Test plan

- AC-1: covered — every class named in AC-1 now lives where AC-1 says: `catalog.domain`
  (`Product`/`Category`/`Supplier`/exceptions/`KujiType`), `catalog.infrastructure` (3
  repositories), `catalog.application` (3 services + `ProductListItemDTO`), `catalog.api` (3
  controllers + request/response DTOs + mappers + `CostVisibilityPolicy`).
  `openapi.json`/`schema.d.ts` both regenerated and confirmed byte-identical to `main`.
- AC-2: covered — `catalog.application` has zero imports of foreign repositories; all
  cross-module access goes through the 5 ports, each implemented by a dedicated adapter outside
  `catalog`. Transaction atomicity proven by `ProductLifecycleTransactionBehaviorIT`. Wording
  corrected to reflect the accepted catalog-owned-coordinator design (see "Documentation
  corrections" above).
- AC-3: covered by T-2's probe verification and every round's reviewed store diffs (T-2 through
  T-7, seven rounds of scoped regeneration, each independently reviewed).
- AC-4: covered — both affected stores regenerated and reviewed this round. Cycle store: 43
  `catalog`-involving cycles (up from 25, a reviewed and explained increase — new, necessary
  `identity` dependency from `CostVisibilityPolicy`) + 23 non-catalog (unchanged, 7th round) = 66.
  Two named residual edges remain, both deliberately unresolved in this record:
  `SupabaseBroadcastService` (cross-module, T-6) and the intra-catalog `application → api`
  inversion (this round's documentation correction, above) — the latter is invisible to the cycle
  count entirely and was found only by direct inspection.
- AC-5: covered — `CostVisibilityPolicy` is a real, shared `catalog.api` class; RBAC/cost
  redaction preserved and explicitly re-verified for both product and supplier responses.
- AC-6: covered (T-6) — `LIST_ITEM_SELECT`'s FQN updated with the DTO's move;
  `ProductRepositoryListItemsIT` proved real-PostgreSQL execution.
- AC-7: covered — full `./mvnw test` green; `openapi.json`/`schema.d.ts` diffs both empty,
  confirmed by regenerating, not assumed.
