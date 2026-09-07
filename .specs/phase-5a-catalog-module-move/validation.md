# Validation

## Command and scope

Re-run fresh at closeout (independent of every intermediate per-task run recorded in `log.md`),
with both ArchUnit freeze-write flags confirmed disabled beforehand:

```
cat services/inventory-service/src/test/resources/archunit.properties
# freeze.store.default.allowStoreCreation=false
# freeze.store.default.allowStoreUpdate=false

cd services/inventory-service
./mvnw -q test
# exit 0, zero "ERROR]" lines

./mvnw -q -Dtest=ArchitectureTest test
# exit 0 — all 6 rules pass against the finalized, committed frozen stores

cd ..
git diff --stat packages/contracts/openapi.json packages/api-client/src/schema.d.ts
# empty (both regenerated during T-7, re-diffed here with no further code changes since)
```

Additionally, from earlier in this record and not re-run at closeout (no code changed since,
listed here as the evidence source, per task):

- T-2: probe-fixture verification of the amended `repositoriesAreOnlyAccessedByServicesOrRepositories`
  rule (four cases: api→legacy repo fails, api→domain repo fails, application→own repo passes,
  domain→non-repository converter passes), fixtures deleted after verification.
- T-4/T-6: `ProductLifecycleTransactionBehaviorIT` (6 tests: initial-stock rollback, forecast-purge
  rollback, deletion-failure child-row restoration, commit-then-broadcast, shipment-guard
  rejection ×2) and `ProductDeletionCoordinatorIT` (2 tests: solo delete, parent/child cascade).
- T-6: `ProductRepositoryListItemsIT` (2 tests) against real PostgreSQL 16 via Testcontainers
  (confirmed from the run log: `org.postgresql.jdbc.PgConnection`, "Database version: 16.15", not
  H2) — the specific AC-6 scenario (compile-clean, runtime-broken JPQL FQN) a mock-based test
  cannot see.
- T-7: `ProductCostVisibilityIT` (6), `SupplierProductsCostVisibilityIT` (3),
  `ShipmentCostVisibilityIT` (3), `ProductControllerSecurityIT` (22 — nested `@Nested` classes;
  the XML surefire report's `tests="22"` is authoritative over the plain-text summary's
  misleading "Tests run: 0" for the outer class), `RBACAlignmentIT` (28) — all run explicitly, all
  green.

## Result

**Pass.** Full `./mvnw test` exit 0 with zero `ERROR]` lines at closeout. `ArchitectureTest`'s all
6 methods pass against the finalized frozen stores (no pending regeneration, both freeze-write
flags disabled). Both `openapi.json` and `packages/api-client/src/schema.d.ts` diff empty against
`main` after regeneration.

Two items are recorded as **accepted transitional debt, not passing criteria** — see `review.md`'s
Residual risk section: the `catalog → identity` dependency (does not match
`docs/specs/spring-domain-modular-monolith.md` §6.2/§4.1; recorded with a removal condition) and
the `SupabaseBroadcastService`/intra-catalog `application→api` residual couplings. These do not
block this record's completion (the spec's own AC-2 explicitly scopes them as accepted, reviewed
debt), but are not silently resolved either.

## Acceptance criteria evidence

- AC-1: `Product`/`Category`/`Supplier`/exceptions/`KujiType` in `catalog.domain`; 3 repositories
  in `catalog.infrastructure`; `ProductService`/`CategoryService`/`SupplierService`/
  `ProductListItemDTO` in `catalog.application`; 3 controllers + request/response DTOs + mappers +
  `CostVisibilityPolicy` in `catalog.api`. Verified by directory listing and successful compile;
  `openapi.json`/`schema.d.ts` diff empty (command above).
- AC-2: `catalog.application` has zero imports of the six original foreign repositories
  (`ForecastPredictionRepository`, `KujiBoxRepository`, `KujiBoxTierRepository`,
  `MachineDisplayRepository`, `ShipmentItemRepository`, `ShipmentRepository`,
  `StockMovementRepository`) — verified by grep and by the live (unfrozen within its own scope)
  ArchUnit repository-access rule. All four original call paths (deletion cascade, initial-stock
  creation, forecast-off purge, two read enrichments) covered by ports with dedicated adapters.
  Transaction atomicity proven by `ProductLifecycleTransactionBehaviorIT` (see above). Two
  residual couplings recorded as accepted debt, not closed — see Result section.
- AC-3: `repositoriesAreOnlyAccessedByServicesOrRepositories` amended in both directions (target
  selector covers domain-module repositories via `Repository`-assignability + legacy package;
  caller side allows a domain module's own `application`/`infrastructure`). Verified against four
  injected probe cases (T-2) before the frozen store was ever touched.
- AC-4: all three ArchUnit stores (`legacyTechnicalLayerPackagesDoNotGrow`,
  `repositoriesAreOnlyAccessedByServicesOrRepositories`, `topLevelPackagesAreFreeOfCycles`)
  regenerated across seven scoped, independently-reviewed rounds (T-1, T-2, T-3, T-4, T-5, T-6,
  T-7). Every regeneration diffed and either matched by class-name identity to prior debt or
  explained edge-by-edge, never accepted on line-count alone. Final cycle store: 43 `catalog`
  cycles (two named residual edges, see Result) + 23 non-catalog cycles (byte-identical across
  all seven rounds) = 66 total blocks.
- AC-5: `CostVisibilityPolicy` is a real, shared `catalog.api` class (not package-private statics
  on `ProductController`). Redaction preserved and explicitly re-verified for both product
  responses (`ProductCostVisibilityIT`) and the supplier-scoped product list
  (`SupplierProductsCostVisibilityIT`) — the only two real consumers, confirmed by grepping every
  `applyCostVisibility` call site codebase-wide before moving anything (T-7).
- AC-6: `LIST_ITEM_SELECT`'s FQN updated in the same edit as `ProductListItemDTO`'s move (T-6);
  `ProductRepositoryListItemsIT` proves the query executes against real PostgreSQL, not just that
  it compiles (see above).
- AC-7: full `./mvnw test` green at closeout (command above); `openapi.json`/`schema.d.ts` diffs
  both empty, confirmed by regenerating both artifacts and re-diffing, not assumed unaffected.
