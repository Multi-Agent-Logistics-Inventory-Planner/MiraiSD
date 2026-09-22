# Implementation log

## Current handoff

- Status: **Complete, including a post-implementation review round.** An external review of the
  working tree (before push) found four real issues -- see `review.md` for full detail. All four
  fixed, verified with new regression tests, and re-validated. `review.md`/`validation.md` added
  (this record is Full tier and was initially missing both).
- Next action: none for this record.
- Review-round summary (full detail in `review.md`):
  1. **Regression, high severity:** consolidating every `useProducts` caller onto
     `getAllProductsAsListItems()` (web-product-list-cache W1) silently dropped `hasActiveBox`
     for every rootOnly/kujiOnly web caller, including `CustomKujiTabs` -- every product would
     have rendered as Closed. Fixed by adding the missing `withHasActiveBox` call; this was a gap
     in the original plan's analysis (it tracked `hasChildren`'s fold but not `hasActiveBox`'s
     separate, selectively-applied enrichment).
  2. **Bug:** `CatalogProductMapper` never mapped `preferredSupplierName` -- v1 catalog detail
     always returned `null` even for a product with an assigned supplier.
  3. **Bug:** `CatalogProductResponse.category` had no explicit mapping, so MapStruct
     auto-generated a recursive `Category` sub-mapper that would walk the lazy `children`
     association at request time -- extra DB reads and a response shape inconsistent with the
     list endpoint's flat category.
  4. **Test-design gap:** the original `ProductListEgressIT` tested repository methods directly,
     which cannot catch a caller adding an extra query around an already-slim repository call (as
     in finding 1) and mischaracterized the site-products endpoint's real 2-statement shape as
     "1". Added three HTTP-level tests through `mockMvc` that inspect the actual SQL a real
     request emits.
- Decisions that must survive compaction: see "Assumptions and decisions" below, organized by
  task. Most consequential: `CatalogProductResponse` gained a hand-written flat-args constructor
  (mirroring `ProductListItemDTO`'s precedent) alongside its Lombok `@AllArgsConstructor` for the
  JPQL list projection -- no signature collision since the two take genuinely different shapes
  (flat category columns vs. a nested `CategoryResponseDTO`).
- Last verified command: `./mvnw -B clean test` (482/482) and `./mvnw -B clean test -Dtest='*IT'`
  (560/560), both on JDK 21. Contract regenerated
  (`./mvnw -B -Dtest=OpenApiContractExportTest test`) and TS client regenerated + typechecked
  (`npm run generate && npm run typecheck` in packages/api-client) -- both diffs additive only.
- Open risks/questions: none.

## Assumptions and decisions

### B1 -- kujiSlackWebhookUrl redaction
- Gated on `Permission.PRODUCTS_UPDATE` (the same permission required to change the field), not a
  dedicated view permission -- it is a Slack incoming-webhook bearer credential, and
  viewing/holding it is the same trust level as editing it.
- Added `@JsonInclude(NON_NULL)` to the field on `ProductResponseDTO`, `ProductListItemDTO`, and
  the new `CatalogProductResponse` (B4), matching the existing unitCost/msrp pattern, so redaction
  omits the key (contract-safe) rather than emitting `null`.
- Folded into `CostVisibilityPolicy.applyCostVisibility`/`applyCostVisibilityToListItem` rather
  than a new sibling method -- automatic coverage of all 9 existing legacy call sites
  (ProductController x2 handlers, SupplierController, CatalogSupplierController). The v1 catalog
  controller has its own `applyKujiWebhookVisibility` (same permission check) since
  `CatalogProductResponse` lives in a different module than `CostVisibilityPolicy`'s typed methods.
- TDD: failing tests added to `ProductCostVisibilityIT` first (RED: 2 failures, employee saw the
  webhook on detail and list); GREEN after (11/11). Serialization-layer proof added to
  `RedactedFieldsAreOmittedFromJsonIT` (key-absence, not null-ness).

### B2 -- hasChildren folded into LIST_ITEM_SELECT
- `CASE WHEN EXISTS (SELECT 1 FROM Product ch WHERE ch.parentId = p.id) THEN true ELSE false END`,
  correlated against the same `Product` table the outer query already scans.
- Deleted `ProductService.withHasChildren`/`getParentProductIds` and
  `ProductRepository.findAllParentIds` entirely -- confirmed zero remaining callers before
  deleting. This was the single most-called query in the database (44,185 calls since stats
  reset) and ran on every one of the 8 `*AsListItems` methods, not just the main list.
- TDD: failing test added to `ProductRepositoryListItemsIT` (RED: hasChildren was null) proving
  parent/child/leaf all resolve correctly, run against real Postgres.

### B3 -- slim projection for SiteProductController.getSiteProducts
- New `ProductSettingsDefaults` record (id/sku/name + 6 EffectiveProductSettings columns) and
  `ProductRepository.findAllSettingsDefaults()` (no joins at all).
- `EffectiveProductSettings.absent`/`from` gained `ProductSettingsDefaults` overloads; the
  original `Product` overloads now delegate to them via `ProductSettingsDefaults.from(Product)`
  (made public) -- single implementation per resolution branch, not two.
- No new test: pure refactor preserving existing behavior, covered by the existing
  SiteProductController/Service/LegacyCompatibility/Concurrency suites (48 tests, real Postgres).
  The egress reduction itself is proven in B8.

### B4 -- v1 catalog field gaps
- Added `hasChildren`, `preferredSupplierId/Name/Auto`, full `category` object, and
  `kujiSlackWebhookUrl` to `CatalogProductResponse`. Kept `categoryId`/`categoryName` for
  compatibility. Deliberately excluded `hasActiveBox` (Phase 7 kuji slice owns it -- `kuji_boxes`
  has no `site_id` yet, so today's value is global and would change meaning once site-scoped).
- `CatalogProductRequest` has no field to *set* `kujiSlackWebhookUrl` -- a pre-existing write-side
  gap on v1 catalog, out of scope here (this record is read-path egress only). Flagged for
  whoever eventually migrates web callers off legacy `/api/products` onto v1 catalog for writes.

### B5 -- slim projection for CatalogProductController
- New `CATALOG_LIST_SELECT` JPQL constant mirroring `LIST_ITEM_SELECT`'s structure, feeding a new
  hand-written constructor on `CatalogProductResponse` (alongside its Lombok `@AllArgsConstructor`
  used by the builder/mapper -- no signature collision, see handoff above).
- Single-product endpoints (`/{id}`, `/sku/{sku}`) keep the entity path (`getProductById`/
  `getProductBySku`, already `JOIN FETCH`ing everything needed for one row) and compute
  `hasChildren` via the existing `productService.countChildren(id) > 0` (a single COUNT query,
  not N+1 since only one product is read).
- TDD: failing tests added to `ProductRepositoryListItemsIT` (repository-level, real Postgres:
  hasChildren/category/preferredSupplier all correct) and `CatalogProductControllerIT`
  (controller-level: hasChildren on list+detail, webhook redaction on list+detail).
- Test-setup gotcha hit and fixed: `Supplier.canonicalName` is populated by a Postgres trigger
  (V22) that this Testcontainers instance's Hibernate `ddl-auto=create-drop` schema does not
  replicate (same limitation `CatalogTransactionPropagationIT.createSupplier` already documents,
  and the same limitation Slice B's PostgreSQL authorization work hit -- Testcontainers Postgres
  here proves Postgres *semantics*, not Flyway migration parity). Used the same direct-JDBC-insert
  workaround.

### B6 -- dead code removal
- Deleted 10 `ProductRepository` `*WithCategories`/`*WithCategories`-adjacent list methods and 11
  `ProductService` wrapper methods, all with zero callers remaining after B3/B5 rewired their only
  callers onto slim projections. Verified zero callers in both src/main and src/test before each
  deletion (`grep` across the whole tree, not just the obvious call sites).
- Kept: `findAllWithCategories` (AnalyticsSeedService, dev-profile-only), `findByParentIdWithCategories`
  (ProductDeletionCoordinator, genuine entity navigation), `findByPreferredSupplierIdWithCategories`
  (SupplierService, same). All three pinned by B7.
- Incidental finding, not acted on (out of scope for this record): `ProductRepository.findByCategoryId`
  and `findByIsActiveTrue` (the plain, non-`WithCategories` derived queries) also have zero
  callers. Lower priority than the `*WithCategories` methods since they carry no JOIN/egress cost;
  left for a future dead-code pass.

### B7 -- caller-set test
- `ProductFullHydrationCallerSetTest`, modelled directly on `CatalogEntityAccessCallerSetTest`.
  Scoped to the 3 remaining list-returning `*WithCategories` methods, not the single-row ones
  (`findByIdWithCategories` etc.) -- those have many legitimate callers and pinning them would be
  noise, not signal; a single-product detail read hydrating one row is not the egress pattern this
  guard exists to catch.
- One correction during writing: `AnalyticsSeedService`'s second caller is `rollupCategoryDemand`,
  not `computeRollupsFromExistingData` as first assumed -- verified against the actual source
  before finalizing the expected-origins set, not left as a guess.

### B8 -- comprehensive egress IT
- `ProductListEgressIT`, reusing `CatalogQueriesEgressIT`'s established pattern
  (`CapturingStatementInspector` + Hibernate `getPrepareStatementCount()`, real Postgres). Proves,
  for all three touched list reads: exactly one JDBC statement per call, and (for the two v1
  routes) the actual captured SQL's column shape -- not just a statement count, which alone
  wouldn't prove `CatalogProductResponse` excludes site-owned columns or that
  `findAllSettingsDefaults` has no JOIN.
- One assertion bug caught and fixed during this step, not weakened: a plain substring check for
  `"quantity"` was a false positive against the legitimate master-identity column
  `template_quantity` (kuji prize child sizing). Replaced with a negative-lookbehind regex
  (`doesNotContainPattern("(?<!template_)\\bquantity\\b")`) that is both precise and independent
  of Hibernate's chosen table alias.

## Verification record

| Command | Result |
| --- | --- |
| `./mvnw -B clean test` | 482/482 |
| `./mvnw -B clean test -Dtest='*IT'` | 560/560 |
| `./mvnw -B -Dtest=OpenApiContractExportTest test` + `git diff packages/contracts/openapi.json` | additive only (new `CatalogProductResponse` fields, description-only changes elsewhere) |
| `npm run generate && npm run typecheck` (packages/api-client) | clean; `schema.d.ts` diff additive only |
