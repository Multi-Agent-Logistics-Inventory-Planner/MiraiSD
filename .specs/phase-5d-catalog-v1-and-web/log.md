# Implementation log

## Current handoff

- Status: T-1 through T-4 complete. T-3's one P2 review finding (Deprecation header used an
  HTTP-date instead of RFC 9745's structured-fields Date syntax) is fixed. T-1's review findings
  and both rounds of T-2's review findings (five total: search filtering, parentId mapping,
  unusable settings schema, malformed-version coercion, incomplete auth proof; then a second
  round: missing nullable unions, fractional-version coercion) are all fixed. T-5 re-scoped (see
  below) before implementation started; T-5 and T-6 are now implemented and reviewed.
- **T-5 re-scope (before any code was written):** starting T-5 surfaced that `SiteProductResponse`
  (T-2) and `CatalogProductResponse` (T-1) together don't carry every field the Products page
  renders — `hasChildren`, `hasActiveBox`, `preferredSupplierId/Name/Auto`, `kujiSlackWebhookUrl`
  exist on neither. A pure "migrate the query, keep the component" approach as AC-6 originally
  implied wasn't reachable without either silently degrading the page or expanding the backend
  contract mid-task. Raised to the user rather than assumed; user chose to update spec.md's AC-6
  first (now AC-6a) rather than either silently picking a disposition or expanding the backend.
  Added AC-6a: a fixed, field-by-field data-source split — `site_products`-owned fields
  (`isStocked`, money fields, `version`) come from `getSiteProducts` only, ever; catalog/Kuji
  display fields (`name`, `category`, `imageUrl`, `kujiType`, `hasChildren`,
  `preferredSupplier*`, `kujiSlackWebhookUrl`) come from legacy `getProducts()` for now (tracked
  backend follow-up, not blocking); `hasActiveBox` also comes from legacy `getProducts()` —
  `kuji_boxes` has no `site_id` column yet (`KujiOpenBoxAdapter.findProductIdsWithOpenBox` →
  `KujiBoxRepository.findProductIdsWithStatus` has no site predicate), so Kuji box state is
  genuinely global/org-wide today. **Correction to this record's own earlier framing:** an
  interim draft of this note called that "not a compromise" — on further review that undersold
  it. It is a real migration gap: kuji/lootbox site-scoping is Phase 7's scope, not yet done, and
  T-5 deliberately does not pull that migration forward. Left unguarded, a page that otherwise
  reads as site-scoped could present that global Kuji data as if it belonged to whichever site is
  selected. Added **AC-6e**: the Custom Kuji tab renders normally at MAIN (today's only reachable
  site — `useCurrentSite` always resolves to MAIN, no switcher UI exists yet) and renders an
  explicit "not yet available per-site" state at any other resolved `siteCode`, so unmigrated Kuji
  data can never surface labeled as another site's own. This is a client-side display gate only —
  no backend change, no new authorization boundary — removed once Phase 7 gives kuji real per-site
  state. T-5 is a **two-query client-side join by product ID**, not a single hook that silently
  prefers one source per field. AC-6c is widened to require the site-switch test prove the join
  itself (a product carried at one site but not the other renders correctly at both) and the AC-6e
  Kuji-tab gate.
- T-5 implemented (see "T-5" task record below for the full diff, AC-6b disposition, the
  `invalidateQueries` audit conclusion, and test list). Independently reviewed 2026-09-09:
  changes required; see review.md's T-5 section. All three P2 findings fixed (see "T-5 review
  findings fixed" below): stale detail-modal state across a site change (rebind by id instead of
  snapshotting the row), the modal's second Status line still reading `p.isActive`, and a missing
  rendered whole-page acceptance test.
- Independent fix follow-up: all three P2 findings closed; T-5 review is clear.
- T-6 implemented (see "T-6" task record below): `useCategories` now reads through
  `getCatalogCategories` (`/api/v1/catalog/categories`), a straight endpoint swap (categories
  are wholly global, no join needed) rather than T-5's two-query pattern. Category mutations
  stay on the legacy routes, unchanged. Independently reviewed 2026-09-09 with no findings.
- Status of task list: T-1 through T-6 implementation complete; T-6 final review clear.
  Commit and the authoritative PR gate remain pending; this is not a merged/deployed claim.
- **Final pre-commit validation** (full record, see validation.md's matching entry): unlike the
  T-6 review's web-only rerun, this pass also reran the full backend suite (rather than relying
  on T-1-T4 checkpoint-time counts) as the last local gate before commit - JDK 21, from
  `services/inventory-service`: `./mvnw -o test` (unit) 404/404, `./mvnw -o test -Dtest='*IT'`
  (integration) 375/375, both BUILD SUCCESS. `git status --short services/inventory-service
  packages/contracts packages/api-client` — empty, confirming T-5/T-6 made no backend or contract
  changes, so the T-1/T-2 `oasdiff breaking --fail-on ERR` (no breaking changes) and
  `packages/api-client` generate/typecheck results remain valid without rerunning them.
  `apps/web`: `npm run test:run --workspace apps/web` — 318/318; `npx tsc --noEmit -p
  apps/web/tsconfig.json` — pass; `npm run lint --workspace apps/web` — 0 errors, 51 warnings
  (unchanged, all pre-existing); `git diff --check` — pass.
- Next action: commit the scoped feature changes and obtain the PR gate's independent proof.
- Latest verification (T-6 review, repository root):
  `npm run test:run --workspace apps/web` — 318/318;
  `npx tsc --noEmit -p apps/web/tsconfig.json` — pass;
  `npm run lint --workspace apps/web` — 0 errors, 51 warnings;
  `git diff --check` — pass. Prior backend/contract evidence retained, not rerun for T-6.
- Latest fix verification: `npx vitest run` (whole `apps/web` suite) — 307/307 (4 new in
  `products/__tests__/page.test.tsx`); `npx tsc --noEmit -p tsconfig.json` — clean;
  `npm run lint` — implementer reported 0 errors, 50 warnings. Independent rerun:
  `npm run test:run --workspace apps/web` — 307/307;
  `npx tsc --noEmit -p apps/web/tsconfig.json` — pass;
  `npm run lint --workspace apps/web` — 0 errors, **51 warnings** (one additional
  dependency warning for the new selection memo); `git diff --check` — pass.
- Decisions that must survive compaction:
  - **This project's springdoc emits OpenAPI 3.1, and `@Schema(nullable = true)` is silently a
    no-op under this swagger-core version's 3.1 output** (verified empirically, with and without
    `implementation`, on both Lombok-generated and hand-written getters). To get a real
    `T | null` union in the generated contract/TypeScript client, use
    `@Schema(types = {"<type>", "null"}, implementation = <Wrapper>.class)` instead - `types`
    alone (without `implementation`) additionally leaks the annotated getter's actual return
    type as a stray sibling `$ref` plus a bogus component schema when that return type isn't the
    "real" wire type (e.g. a generic wrapper like `FieldUpdate<T>`). Apply this pattern to any
    future nullable-field OpenAPI annotation in this codebase; `nullable = true` alone will look
    like it worked (compiles, no error) while silently producing a non-nullable schema.
  - The app's Jackson config globally disables `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES`
    (confirmed empirically — no property in `application*.properties` sets this; it is Spring
    Boot's own default). A class-level `@JsonIgnoreProperties(ignoreUnknown = false)` does **not**
    override that: Jackson can't distinguish an explicit `false` from the annotation's own default
    value, so the global "ignore" setting still wins (verified with a standalone Jackson
    reproduction outside Spring). `CatalogProductRequest` instead uses `@JsonAnySetter` on a
    method that throws `IllegalArgumentException` for any unmapped property — this intercepts
    before the global feature is consulted, so it rejects unconditionally regardless of that app
    default. Any future master-identity-only DTO needing the same "reject site-owned fields"
    behavior should use this pattern, not `@JsonIgnoreProperties`.
  - `GlobalExceptionHandler` had no handler for `HttpMessageNotReadableException`, so a malformed
    request body (including the `@JsonAnySetter` rejection above) fell through to the generic
    `Exception` handler and returned 500 instead of 400. Added a dedicated handler mapping it to
    400 with the deserialization failure's message — this is a general correctness fix (a bad
    request body is a client error, not a server error) that also applies to every existing
    controller, not just the new v1 routes. Verified no existing test asserted the old 500
    behavior.
  - New-product global creation (`ProductService.createGlobalProduct`) and global update
    (`ProductService.updateGlobalProduct`) are new methods rather than reusing
    `createProduct`/`updateProduct` outright for create (that method always dual-writes a MAIN
    `site_products` row, which AC-1's "creates zero site_products rows" explicitly forbids).
    `updateGlobalProduct` *does* delegate to the existing `updateProduct`, passing every
    site-owned/legacy-only parameter as its no-op value (`null`/`false`) — safe because
    `updateProduct` already treats `null` as "field not provided" for every one of those
    parameters, so no site-owned column or `site_products` row is touched.
  - Global v1 product routes are master-identity-only per spec.md; PUT therefore has no
    `clearParent`/`clearPacksPerBox` equivalent (those flags exist only on the legacy route and
    are out of scope for the narrower DTO).
  - Categories and suppliers routes (`CatalogCategoryController`, `CatalogSupplierController`)
    mirror their legacy controllers 1:1 (including `GET /{id}/products` on suppliers, which still
    returns the legacy `ProductResponseDTO` shape with `CostVisibilityPolicy` applied) — spec.md
    calls these "wholly global," so no field narrowing applies to them.
- Last verified (T-4): `./mvnw -o test -Dtest='*IT'` — 375/375, Maven exit 0 (374 T-3 baseline + 1
  new `CatalogMultiSiteExitGateIT` test). `./mvnw -o test` (unit) — 404/404, Maven exit 0 (test-only
  change, no architecture-baseline edit needed).
- Open risks/questions: T-1's categories/suppliers routes were not given their own dedicated IT
  file (they mirror already-tested legacy behavior 1:1 through the same service layer); if T-5/T-6
  web migration surfaces a gap, add targeted coverage then rather than duplicating
  `CategoryControllerSecurityIT`/`SupplierController` tests preemptively.
  - **Pre-existing, unrelated to T-3:** `GET /api/suppliers` 500s under this project's `test`
    profile (H2, `ddl-auto=create-drop`) because `SupplierRepository`'s query reads the
    `mv_lead_time_stats` materialized view, which V20/V30's raw-SQL Flyway migrations create in
    Postgres but which never exists here (this profile doesn't run Flyway). Confirmed pre-existing
    by temporarily removing T-3's new files from the working tree and re-probing the same route —
    it 500s identically with zero T-3 code present. Out of scope for this header-only task;
    `LegacyCatalogDeprecationHeadersIT.getSuppliers_carriesDeprecationHeaders` asserts header
    presence without asserting a 200 for this reason. Flag for a future record if `/api/suppliers`
    coverage is ever added for real.

## Assumptions and decisions

- AC-1's product list/detail reads (`getCatalogProducts`) reuse `ProductService.getAllProducts`/
  `getProductsByCategory`/`searchProducts`/`getProductBySku` (entity-returning, not the
  `*AsListItems` DTO variants used by the legacy list endpoint) since `CatalogProductResponse` is a
  new, narrower shape mapped directly from the entity — no need for the legacy `ProductListItemDTO`
  path.
- `CatalogProductResponse` includes `categoryId`/`categoryName` (denormalized at map time from the
  eagerly-fetched `Category` association) since AC-5 step 6 requires the global product identity
  read to be usable on its own, and category is part of master identity, not a site-owned field.
- DELETE reuses `ProductDeletionCoordinator.deleteProduct` directly (no narrower global-only
  variant) — deletion isn't a site-owned mutation kind the spec's forbidden-field list addresses.

## Task record

### T-1 — Global catalog v1 routes (products/categories/suppliers)

- Changed:
  - Added `catalog/api/CatalogProductRequest.java`, `CatalogProductResponse.java`,
    `CatalogProductMapper.java`, `CatalogProductController.java` (global
    `/api/v1/catalog/products` routes: `GET`/`POST` list+create, `GET`/`PUT`/`DELETE /{id}`,
    `GET /sku/{sku}`).
  - Added `catalog/api/CatalogCategoryController.java` and `CatalogSupplierController.java`
    (`/api/v1/catalog/categories/**`, `/api/v1/catalog/suppliers/**`), mirroring the legacy
    `CategoryController`/`SupplierController` 1:1 with distinct handler/operation names.
  - Added `ProductService.createGlobalProduct` and `ProductService.updateGlobalProduct` (see
    "Assumptions and decisions" above for why these are new methods, not a widened
    `createProduct`/`updateProduct` call).
  - `GlobalExceptionHandler`: added `HttpMessageNotReadableException` → 400 handler (see handoff
    decisions).
  - Regenerated `packages/contracts/openapi.json` (via `OpenApiContractExportTest`) and
    `packages/api-client/src/schema.d.ts` (via `npm run generate`).
- Tests: `catalog/api/CatalogProductControllerIT.java` (new) —
  `createCatalogProduct_rejectsForbiddenField` (parameterized over `isActive`, `unitCost`, `msrp`,
  `reorderPoint`, `targetStockLevel`, `leadTimeDays`, `forecastingEnabled`, `quantity`,
  `initialStock`; each asserts 400 and that `site_products` row count is unchanged),
  `createCatalogProduct_createsNoSiteProductRows` (asserts 201, no `isActive`/`unitCost`/
  `quantity` in the response body, and `site_products` count unchanged),
  `createCatalogProduct_validRequest_succeeds` (happy path).
- Result: Pass. `./mvnw -o test -Dtest='CatalogProductControllerIT'` — 11/11.
  `./mvnw -o test -Dtest='*IT'` — 351/351 (no regressions in the existing 340).
  `./mvnw -o test` (unit) — 404/404. `oasdiff breaking --fail-on ERR` against the prior contract —
  no breaking changes (purely additive: new `/api/v1/catalog/**` paths and schemas).

## Test plan

- AC-1: `CatalogProductControllerIT.createCatalogProduct_validRequest_succeeds` (product routes
  respond; distinct operation IDs verified by successful springdoc contract generation with no
  collision errors during `OpenApiContractExportTest`). Categories/suppliers routes verified by
  reuse of the same service layer as their already-tested legacy controllers; no dedicated new IT
  yet (see "Open risks/questions").
- AC-1b: `CatalogProductControllerIT.createCatalogProduct_rejectsForbiddenField` (all nine
  forbidden fields, 400 + zero `site_products` rows created) and
  `createCatalogProduct_createsNoSiteProductRows` (successful creation still creates zero rows).
- AC-4: `packages/contracts/openapi.json` and `packages/api-client/src/schema.d.ts` regenerated in
  this commit; `oasdiff breaking --fail-on ERR` reports no breaking changes.
- AC-2: `SiteProductControllerIT.getSiteProduct_absentRow_returnsNotStockedWithGlobalFallback`,
  `updateAssortment_upsertsRow`, `updateSettings_noRowExists_returns404`,
  `updateSettings_onCarriedProduct_appliesOverride`, `updateSettings_staleVersion_returns409`.
- AC-2b: `SiteProductControllerIT.employee_canReadButNotMutate`,
  `assistantManager_canMutate`, `admin_canMutate`, `noMembership_rejectedOnReadsAndMutations`,
  `revokedMembership_rejectedOnReadsAndMutations` — the mutation endpoints specifically, not just
  reads.
- AC-2c: `SiteProductControllerIT.getSiteProduct_globallyNonexistentProduct_returns404` (the
  product-identity 404) alongside `getSiteProduct_absentRow_returnsNotStockedWithGlobalFallback`
  and `updateSettings_noRowExists_returns404` (the assortment-resource 404, distinct from the
  first).
- Cost visibility: `SiteProductControllerIT.employee_costAndMsrpNulled`,
  `assistantManager_seesMsrpNotUnitCost`, `admin_seesUnitCostAndMsrp`.
- AC-5: `CatalogMultiSiteExitGateIT.oneGlobalProduct_independentlyConfiguredAtBothSites` (all six
  exit-gate steps in one end-to-end scenario).

## T-1 review checkpoint

- Review disposition: changes requested; see `review.md` and `validation.md`.
- Both findings fixed:
  - Added `ProductRepository.searchAllWithCategories` (no `isActive` predicate) and
    `ProductService.searchAllProducts`; `CatalogProductController.getCatalogProducts` now calls
    `searchAllProducts` instead of the legacy `searchProducts`, so a freshly created (inactive)
    global product is findable by name/SKU immediately.
  - `CatalogProductMapper.toResponse` now explicitly maps `parentId` from `product.parent.id`
    instead of letting MapStruct's default same-name mapping pull the entity's `parentId` shadow
    column (`insertable = false, updatable = false` — only populated after a reload, so it read
    `null` on a just-created/updated `Product` in the same transaction/session).
  - Added two permanent regression tests to `CatalogProductControllerIT`:
    `searchCatalogProducts_findsNewlyCreatedInactiveProduct` and
    `createCatalogProduct_childProduct_returnsCorrectParentId` — both reproduced the bug before the
    fix and pass after.
- Reviewed `review.md`/`validation.md` and updated their dispositions to **fixed** with the
  post-fix evidence (13/13 `CatalogProductControllerIT`, 353/353 `*IT`).
- **Correction to this record's own earlier claim:** the previous revision reported two unit-test
  failures (`CatalogEntityAccessCallerSetTest`, `ArchitectureTest.repositoriesAreOnlyAccessed...`)
  as "pre-existing on the base commit, confirmed by stashing." That confirmation was invalid: it
  used a plain `git stash` (not `git stash -u`), which does not stash *untracked* files — this
  record's brand-new `catalog/api/*.java` files stayed in the working tree while the tracked-file
  changes they depend on (`ProductService`, `SiteAssortment`, etc.) were stashed away, leaving a
  broken hybrid state that doesn't correspond to either the base commit or this record's actual
  diff. Re-run with `git stash -u` (both directions, clean `target/`): the base commit passes both
  tests cleanly. There is no pre-existing baseline failure. Use `git stash -u` for any future
  "is this pre-existing" check in this repo — plain `git stash` silently gives a false positive
  whenever the change under test adds new files.
- Next action: T-3 (Deprecation/Sunset/Link headers on legacy catalog routes).
- Last independently verified (JDK 21, from `services/inventory-service`, clean `target/`):
  `./mvnw -o test` (unit) — 404/404, Maven exit 0. `./mvnw -o test -Dtest='*IT'` — 367/367, Maven
  exit 0 (353 T-1 baseline + 14 new `SiteProductControllerIT` tests). `oasdiff breaking
  --fail-on ERR` — no breaking changes. `packages/api-client`: `npm run generate` +
  `npm run typecheck` — clean.

## T-2 — Site-scoped assortment/settings routes (review checkpoint)

- Changed:
  - Added `catalog/api/SiteProductController.java` at `/api/v1/sites/{siteId}/products`: `GET`
    (list), `GET /{productId}` (detail), `PUT /{productId}/assortment` (upsert), `PUT
    /{productId}/settings` (requires an existing row). Reads `siteId` from
    `AuthorizedSiteContextHolder.require()`, matching `SiteStorageLocationController`'s precedent
    (the `siteId` path variable is still declared on every handler for springdoc/the generated
    client, even though the body reads the authorized context instead).
  - Added `catalog/api/SiteProductResponse.java` (effective, site-scoped view: `isStocked` plus
    every override field resolved through `EffectiveProductSettings`, and `version` — `null` only
    when the site has never carried the product) and `SiteAssortmentRequest.java` (`{isStocked}`).
  - `catalog/application/SiteAssortment.java`: added `rowsForSite(siteId)` (bulk map for the list
    read, one query instead of one per product) and `rowFor(siteId, productId)` (single-row lookup
    exposing the row's `@Version` for a detail read).
  - `catalog/infrastructure/SiteProductRepository.java`: added `findBySiteId(siteId)` backing
    `rowsForSite`.
  - Settings PUT accepts `@RequestBody JsonNode` rather than a typed DTO, since the tri-state
    contract (`FieldUpdate.omitted()` vs. explicit-`null`-clear vs. a value, per 5c AC-6) can't be
    expressed with a plain nullable Java field — no `JsonNullable`-style library is on the
    classpath. A private `fieldUpdate(JsonNode, field, Class)` helper checks `body.has(field)` for
    presence and `value.isNull()` for an explicit clear, converting present values via the
    injected `ObjectMapper`.
  - `SiteProductResponse.isStocked` needed an explicit `@JsonProperty("isStocked")`: Lombok's
    `@Data` on a primitive `boolean isStocked` field generates the JavaBean getter `isStocked()`,
    which Jackson serializes as `"stocked"` (stripping the `is` prefix) unless told otherwise —
    caught by the two tests that assert on `$.isStocked` in the response JSON.
  - `test/resources/module-dependency-edges-baseline.txt`: added the reviewed `catalog -> shared`
    edge — `SiteProductController` is the first `catalog.api` class to read
    `shared.web.AuthorizedSiteContextHolder`, the same seam `sites.api.SiteStorageLocationController`
    already uses via `sites -> shared`. Safe for the same reason the file's existing `catalog ->
    sites` entry documents: `shared` has zero outgoing edges in this file (a pure leaf, like it
    already is for `auth`, `identity`, `services`, `sites`), so this can't create a new cycle.
    Caught by `ArchitectureTest.moduleDependencyEdgesMatchApprovedBaseline` failing with
    `["catalog -> shared"]` on the first full-suite run after adding the controller.
  - Permission gating (AC-2b): `@PreAuthorize("hasAnyRole('ADMIN','ASSISTANT_MANAGER','EMPLOYEE')")`
    on both `GET`s (all three roles hold `PRODUCTS_VIEW`); `@PreAuthorize("hasAnyRole('ADMIN',
    'ASSISTANT_MANAGER')")` on both `PUT`s (only these two hold `PRODUCTS_UPDATE`) — this mirrors
    the existing codebase pattern (role-based `@PreAuthorize` plus a manual
    `RolePermissions.hasPermission(authentication, Permission.X)` check for field-level visibility)
    rather than a custom `@PreAuthorize("hasPermission(...)")` SpEL expression, since no such
    evaluator exists anywhere else in this codebase and introducing one for a single controller
    would be a new, unreviewed authorization mechanism.
  - Regenerated `packages/contracts/openapi.json` and `packages/api-client/src/schema.d.ts`.
- Tests: `catalog/api/SiteProductControllerIT.java` (new, 14 tests) — AC-2 read semantics (absent
  row → 200 not 404 with global fallback; assortment upsert creates the row; settings PUT against
  an uncarried product → 404; settings PUT on a carried product applies the override and advances
  `version`; stale/missing version → 409), AC-2b's full permission matrix on the **mutation**
  endpoints specifically (EMPLOYEE 403 on both PUTs but 200 on GET; ASSISTANT_MANAGER and ADMIN
  succeed; no membership and revoked membership both 403 on reads and mutations), AC-2c (a
  globally nonexistent product is 404; an uncarried-but-globally-valid product is not), and cost
  visibility (EMPLOYEE sees neither `unitCost` nor `msrp`; ASSISTANT_MANAGER sees `msrp` but not
  `unitCost`; ADMIN sees both).
- Result: Pass. `./mvnw -o test -Dtest='SiteProductControllerIT'` — 14/14.
  `./mvnw -o test -Dtest='*IT'` — 367/367 (353 baseline + 14 new). `./mvnw -o test` (unit) —
  404/404 (the `catalog -> shared` architecture-baseline addition above was required to keep this
  green). `oasdiff breaking --fail-on ERR` — no breaking changes.

## T-2 independent review result

- Status: changes requested; see review.md for three P2 findings (settings request
  contract, malformed version coercion, incomplete mutation authorization proof).
- Next action: fix the request schema and version validation, complete the settings
  permission matrix tests, and rerun the focused checks.
- Last independently verified: JDK 21 `./mvnw -o test
  -Dtest=SiteProductT2ReviewIT,ArchitectureTest,CatalogEntityAccessCallerSetTest` —
  23 pass, 1 targeted malformed-version assertion fails, no errors.
- The corrected baseline account is consistent with both architecture classes
  passing now; the historical stash experiment was not repeated by this review.

## T-2 review findings fixed

- Finding 1 (generated settings request unusable): `SiteProductController.updateSettings` took a
  raw `@RequestBody JsonNode`, which springdoc/openapi-typescript can only describe as an opaque
  `Record<string, never>` — a typed client had no way to know which fields exist or their types.
  Replaced with a real `SiteProductSettingsRequest` DTO (`catalog/api/`) carrying named,
  correctly-typed fields. The five override fields still need 5c AC-6's tri-state contract
  (omitted = leave stored value untouched; explicit `null` = clear the override) — a plain
  nullable Java field can't express that distinction, so each is typed
  `FieldUpdate<T>` with a new `catalog/application/FieldUpdateDeserializer.java`
  (`ContextualDeserializer`, resolves `T` per field so one deserializer instance serves every
  `FieldUpdate<T>` field) plus a field initializer of `FieldUpdate.omitted()` (never touched by
  Jackson when the JSON key is absent) and `getNullValue()` override (returns
  `FieldUpdate.of(null)` for an explicit JSON `null`). Each field also carries
  `@Schema(implementation = <T>.class, nullable = true)` so springdoc emits the real wrapped type
  in the OpenAPI schema rather than the wrapper's own internal shape. Verified the regenerated
  schema directly: `SiteProductSettingsRequest` now has `expectedVersion: integer(int64)`,
  `forecastingEnabled: boolean`, `unitCost`/`msrp: number`, `reorderPoint`/`targetStockLevel`/
  `leadTimeDays: integer(int32)` — no wrapper leakage — and `packages/api-client/src/schema.d.ts`
  now types every field correctly instead of `Record<string, never>`.
- Finding 2 (malformed version authorizes writes): fixed as a side effect of Finding 1's typed
  DTO. The previous code read `body.get("expectedVersion").asLong()` from a raw `JsonNode` —
  Jackson's `asLong()` silently returns `0` for a non-numeric value instead of throwing, so
  `"expectedVersion": "nonsense"` was indistinguishable from a genuine `0` and could authorize a
  write against a fresh (version-0) row. `SiteProductSettingsRequest.expectedVersion` is a plain
  typed `Long`, so normal Jackson deserialization now throws `InvalidFormatException` for a
  non-numeric value, mapped to 400 by the `HttpMessageNotReadableException` handler added in T-1.
  New regression test: `SiteProductControllerIT.updateSettings_malformedVersion_returns400` — first
  asserts the fresh row's version really is `0` (so a naive "coerce to 0" bug would otherwise slip
  through unnoticed), then asserts `"expectedVersion": "nonsense"` is rejected with 400.
- Finding 3 (incomplete authorization proof): `assistantManager_canMutate` and `admin_canMutate`
  only exercised the assortment PUT; `noMembership_rejectedOnReadsAndMutations` and
  `revokedMembership_rejectedOnReadsAndMutations` likewise only checked assortment for the
  mutation half of "reads and mutations." Added a settings PUT call to all four tests (ADMIN/
  ASSISTANT_MANAGER now assert 200 on settings too; no-membership/revoked-membership now assert
  403 on settings too), so AC-2b's permission matrix is proven on both mutation endpoints, not
  just one.
- `SiteProductController`'s constructor no longer takes an `ObjectMapper` (the `fieldUpdate`
  helper it was needed for is gone); the `JsonNode`/`FieldUpdate` imports it needed are gone too.
- Also closed the review's noted residual gap (tri-state clear-vs-omit had no HTTP-level test):
  added `updateSettings_omittedLeavesUnchanged_explicitNullClearsOverride` — sets an `msrp`
  override while omitting `reorderPoint` (asserts `reorderPoint` still reads the global fallback),
  then sends an explicit `null` for `msrp` (asserts it reads back the global fallback too) while
  `reorderPoint` stays omitted and untouched throughout.
- Result: Pass. `./mvnw -o test -Dtest='SiteProductControllerIT'` — 16/16 (14 original + the
  malformed-version regression test + the tri-state HTTP-level test). `./mvnw -o test
  -Dtest='*IT'` — 369/369. `./mvnw -o test` (unit) — 404/404. `oasdiff breaking --fail-on ERR` —
  no breaking changes. `packages/api-client`: `npm run generate` + `npm run typecheck` — clean,
  and `SiteProductSettingsRequest`'s generated type now has named, typed fields instead of
  `Record<string, never>` (checked directly in `src/schema.d.ts`).

## T-2 typed-request follow-up handoff

- Status: authorization proof resolved; two P2 issues remain (missing nullable
  contract types and fractional-version coercion), documented in review.md.
- Next action: export nullable override types and reject fractional versions,
  then regenerate contracts and rerun focused checks.
- Last independently verified: JDK 21 `./mvnw -o test
  -Dtest=SiteProductControllerIT,SiteProductT2FollowupIT` — permanent 16/16 pass;
  fractional-version reproduction fails with 200 instead of 400.

## T-2 nullable-union and fractional-version findings fixed

- Finding 1 (generated types cannot clear overrides): root cause was not Lombok (the earlier
  T-2 fix's doc comment guessed wrong, since it never actually tested `nullable = true` on a
  hand-written getter before switching approaches) - it's that this project's springdoc emits
  OpenAPI 3.1, and this swagger-core version's 3.1 serialization path silently drops
  `@Schema(nullable = true)` regardless of `implementation` or getter-vs-field placement (verified
  empirically by testing each combination and regenerating the contract after each). OpenAPI 3.1
  expresses nullability as a JSON Schema `type` array (`["number", "null"]`), not the OpenAPI 3.0
  `nullable` keyword, and swagger-annotations 2.2.x's `@Schema` has a `types()` array attribute
  specifically for this. Switched every override field's annotation to
  `@Schema(types = {"<type>", "null"}, implementation = <Wrapper>.class)`. `types` alone produces
  the null union but also makes swagger-core additionally resolve `FieldUpdate<T>`'s own
  `present`/`value` shape as a stray sibling `$ref` plus a bogus
  `FieldUpdateBoolean`/`FieldUpdateBigDecimal`/`FieldUpdateInteger` component schema (observed
  directly when I tried `types` without `implementation`); `implementation` is still required
  alongside it to suppress that leak. `catalog/api/SiteProductSettingsRequest.java`'s doc comment
  is corrected to describe this actual root cause instead of the earlier (wrong) Lombok theory.
- Finding 2 (fractional versions authorize writes): added
  `catalog/api/StrictLongDeserializer.java`, applied via `@JsonDeserialize` to just the
  `expectedVersion` setter (scoped to this one field, not a global Jackson coercion-config
  change, per the review's explicit preference). It accepts only a `VALUE_NUMBER_INT` JSON token
  and calls `ctxt.reportInputMismatch(...)` for anything else, so `expectedVersion: 0.9` is now
  rejected the same way the earlier fix already rejected `"nonsense"`.
- New test: `SiteProductControllerIT.updateSettings_fractionalVersion_returns400` (the review's
  exact `0.9` reproduction, following the same "assert the fresh row's version really is 0 first"
  pattern as the malformed-version test, so a naive truncate-to-0 bug can't slip through
  unnoticed). The nullable-union fix has no corresponding new HTTP-level test: the server already
  accepted an explicit JSON `null` for these fields at runtime before this fix (proven by
  `updateSettings_omittedLeavesUnchanged_explicitNullClearsOverride`, which predates it) - only
  the *exported contract* was wrong, so this fix is verified by direct inspection of the
  regenerated `openapi.json`/`schema.d.ts` instead.
- Result: Pass. `./mvnw -o test -Dtest='SiteProductControllerIT'` — 17/17 (16 + the new
  fractional-version test). `./mvnw -o test -Dtest='*IT'` — 370/370. `./mvnw -o test` (unit) —
  404/404. `oasdiff breaking --fail-on ERR` — no breaking changes. Regenerated `openapi.json`:
  every `SiteProductSettingsRequest` override field is `{"type": ["<type>", "null"]}` with no
  stray `$ref`/leaked `FieldUpdate*` schemas. Regenerated `schema.d.ts`: every override field is
  `T | null` (e.g. `msrp?: number | null`). `npm run typecheck` — clean.
- Next action: T-3 (Deprecation/Sunset/Link headers on legacy catalog routes). T-2 is now fully
  resolved across both review rounds.

## T-2 final independent review handoff

- Status: all T-2 review findings resolved; no new follow-up findings.
- Next action: T-3 (legacy catalog deprecation headers).
- Last independently verified: JDK 21 `./mvnw -o test
  -Dtest=SiteProductControllerIT` — 17/17, BUILD SUCCESS. Generated nullable
  override types verified in both contract artifacts.

## T-3 — Deprecation/Sunset/Link headers on legacy catalog routes

- Changed:
  - Added `catalog/api/LegacyCatalogDeprecationFilter.java` (`OncePerRequestFilter`): sets
    `Deprecation` (a fixed HTTP-date, since this commit is when deprecation begins) and `Link`
    (`rel="deprecation"`, pointing at `docs/baseline/api-v1-map.md` — the document that classifies
    every legacy route and its v1 replacement, per AGENTS.md's sources-of-truth list) on every
    response. `Sunset` is intentionally omitted: no removal date has been decided, and spec.md
    AC-3 says "where known."
  - Added `catalog/api/LegacyCatalogDeprecationConfig.java`: a `FilterRegistrationBean` scoping the
    filter to `/api/products/*`, `/api/categories/*`, `/api/suppliers/*` only — not a `@Component`
    on the filter itself, which would have registered it for every route including the new
    `/api/v1/catalog/**` and `/api/v1/sites/**` families this same record just added.
  - No changes to `ProductController`/`CategoryController`/`SupplierController` or any DTO: the
    filter runs entirely outside the handler methods, so response bodies and status codes are
    unchanged (verified below), satisfying AC-3's "behavior is otherwise byte-identical to today."
  - No contract regeneration: this is transport-level header metadata, not part of the OpenAPI
    request/response schema springdoc emits, and AC-3 doesn't call for it (unlike AC-1/AC-1b/AC-2
    in T-1/T-2, which changed the actual route/DTO surface).
- Tests: `catalog/api/LegacyCatalogDeprecationHeadersIT.java` (new, 4 tests) — `GET /api/products`
  and `GET /api/categories` assert 200 plus both headers present with the exact expected values
  (and `Sunset` absent); `GET /api/suppliers` asserts both headers present without asserting a 200
  (see below); `GET /api/v1/catalog/products` asserts neither header is present, proving the filter
  doesn't leak onto the new v1 routes.
  - Pre-existing, unrelated failure discovered while writing this test: `GET /api/suppliers` 500s
    under the `test` profile because `SupplierRepository`'s list query reads the
    `mv_lead_time_stats` materialized view, which V20/V30's Flyway migrations create in Postgres
    but this profile never creates (H2, `ddl-auto=create-drop`, no Flyway run). Confirmed
    pre-existing, not caused by this task: temporarily moved both new main-source files and the
    new test out of the working tree and re-ran an ad hoc probe hitting `GET /api/suppliers` with
    zero T-3 code present — same 500, same `mv_lead_time_stats` `SQLGrammarException`. Left
    unfixed as out of scope for a header-only task; recorded as an open risk above.
- Result: Pass. `./mvnw -o test -Dtest='LegacyCatalogDeprecationHeadersIT'` — 4/4.
  `./mvnw -o test -Dtest='*IT'` — 374/374 (370 baseline + 4 new). `./mvnw -o test` (unit) —
  404/404 (no architecture-baseline edit needed: the new classes stayed in `catalog.api` and
  introduced no new module dependency edge).
- Next action: T-4 (AC-5 exit-gate integration test).


## T-3 independent review handoff

- Status: review complete; one P2 remains before T-3 can be considered complete.
- Next action: correct `Deprecation` to RFC 9745 Structured Field Date syntax
  (`@1788825600`) and add an independent format assertion, then proceed to T-4.
- Last independently verified: JDK 21 `./mvnw -o test
  -Dtest=LegacyCatalogDeprecationHeadersIT` — 4/4, BUILD SUCCESS.
- Implementation and the unrelated `.claude/scheduled_tasks.lock` left untouched.

## T-3 review finding fixed

- Finding (P2, RFC 9745 header syntax): `LegacyCatalogDeprecationFilter.DEPRECATION_DATE` was an
  HTTP-date string (`"Tue, 08 Sep 2026 00:00:00 GMT"`). RFC 9745 (which obsoletes the earlier
  draft this record's implementation had followed) defines the `Deprecation` header's value as
  an RFC 8941 Structured Fields Date, written `@<unix-timestamp>` — not an HTTP-date. Changed the
  constant to `"@1788825600"` (the same instant, 2026-09-08T00:00:00Z, re-expressed in the
  required syntax) and corrected the class doc comment to cite RFC 9745/RFC 8941 instead of
  implying an HTTP-date was correct.
- Also addressed the review's "test its format independently" note: the previous test asserted
  `header().string("Deprecation", LegacyCatalogDeprecationFilter.DEPRECATION_DATE)`, which would
  pass even if the implementation constant were wrong in the same way the test's expectation was
  wrong. `LegacyCatalogDeprecationHeadersIT` now parses the raw header value against a
  independently-written regex for RFC 8941's `sf-date` syntax (`^@(-?\d+)$`) and RFC 8288's Link
  syntax, decodes the epoch seconds, and asserts the resulting `Instant` equals
  `2026-09-08T00:00:00Z` computed from an independent `ZonedDateTime.parse` call — not from the
  implementation's constant. A regression back to an HTTP-date (or any other non-conforming
  value) now fails the format assertion regardless of what the implementation constant contains.
- Result: Pass. `./mvnw -o test -Dtest='LegacyCatalogDeprecationHeadersIT'` — 4/4.
  `./mvnw -o test -Dtest='*IT'` — 374/374 (no regressions). `./mvnw -o test` (unit) — 404/404.
- T-3 is now fully resolved. Next action: T-4 (AC-5 exit-gate integration test).

## T-3 independent fix follow-up handoff

- Status: P2 independently verified resolved; T-3 review closed.
- Next action: T-4, the AC-5 independent configuration integration test.
- Last verified: JDK 21 `./mvnw -o test -Dtest=LegacyCatalogDeprecationHeadersIT`
  — 4/4, BUILD SUCCESS.
- No implementation changes; unrelated lock file untouched.

## T-4 — Exit-gate integration test (AC-5)

- Changed: test-only. Added
  `catalog/api/CatalogMultiSiteExitGateIT.java` — a single end-to-end test walking all six AC-5
  steps against the real routes added in T-1/T-2 (no production code changed; the test proves
  existing behavior, it doesn't add new behavior):
  1. `POST /api/v1/catalog/products` creates one global product (with a seeded category, since root
     products require one — `newProduct`'s equivalent for this test file).
  2. `PUT .../assortment` upserts it at both a fresh MAIN-like site and a fresh SECOND-like site
     (both named via unique site codes rather than reusing the literal `MAIN`/`SECOND` codes, since
     `BaseIntegrationTest.ensureMainSiteExists()` already seeds a real `MAIN` row per test and this
     test needs two sites it fully controls).
  3. Distinct `msrp`/`reorderPoint` settings are set at each site and each site's `GET` is asserted
     to return its own values.
  4. MAIN's settings are updated again and SECOND's full `GET` response is asserted **structurally
     equal (whole-`JsonNode` equality)** to its snapshot from step 3, not just equal on the two
     fields this test happens to touch — and the reverse for MAIN after SECOND's update. This
     proves the complete API response is unchanged; it is not a literal byte comparison and not a
     direct comparison of the persisted database row, only of what the read endpoint serializes
     from it. It is the actual cross-site non-interference proof AC-5 calls for (not merely "an
     unwritten row stayed unwritten").
  5. SECOND's assortment is deactivated (`isStocked: false`); MAIN is asserted still
     `isStocked: true` with its own settings intact, and SECOND's retained (de-assorted) row is
     asserted to keep its saved overrides rather than falling back to global values — per AC-2's
     "retained row with `is_stocked = false` resolves its own saved overrides" rule, distinct from
     the absent-row global-fallback rule.
  6. `GET /api/v1/catalog/products/{id}` is asserted, via `JsonNode.has()` (key absence, not
     `jsonPath(...).doesNotExist()`, which only fails on a present-with-non-null value and would
     pass even if the response carried e.g. an explicit `"targetStockLevel": null`), to omit every
     site-owned field from AC-1b's forbidden-field list (`isActive`, `unitCost`, `msrp`,
     `reorderPoint`, `targetStockLevel`, `leadTimeDays`, `forecastingEnabled`, `quantity`,
     `initialStock`) plus `isStocked`.
  - The test needed `rateLimitingFilter.clearBuckets()` calls between phases: this scenario makes
    ~14 authenticated requests against the `test` profile's `rate.limit.requests.per.minute=10`,
    which every other existing IT stays under per test. `BaseIntegrationTest` already clears the
    bucket once per `@BeforeEach`; this test additionally clears it between its numbered phases
    (a rate-limiter test artifact, not a behavior being asserted) rather than lowering coverage by
    combining/dropping assertions to fit under 10 requests.
- Tests: `catalog/api/CatalogMultiSiteExitGateIT.oneGlobalProduct_independentlyConfiguredAtBothSites`
  (new, 1 test covering all of AC-5's six steps in one scenario, matching AC-5's own framing as a
  single end-to-end proof rather than six independent unit-style tests).
- Result: Pass. `./mvnw -o test -Dtest='CatalogMultiSiteExitGateIT'` — 1/1.
  `./mvnw -o test -Dtest='*IT'` — 375/375 (374 baseline + 1 new). `./mvnw -o test` (unit) —
  404/404 (test-only change; no architecture-baseline edit needed). No contract regeneration —
  T-4 adds no route or DTO, only a test exercising existing ones.

## T-4 review findings fixed

- Finding 1 (P2, non-interference not fully asserted): the original step-4 assertions only
  compared `msrp`, `reorderPoint`, and `version` between sites, so a mutation that leaked into some
  other field (e.g. `unitCost`, `forecastingEnabled`, `leadTimeDays`) on the untouched site would
  have passed silently. `setSettings` now returns the full response `JsonNode` (was: just the
  parsed `version` long) so the test can snapshot the *entire* API response. Step 4 now asserts
  whole-node equality (`assertThat(secondUnaffected).isEqualTo(secondAfterInitialSet)`, and the
  mirror for MAIN after SECOND's update) against the complete response captured immediately after
  the prior settings call on that same site — every field the read endpoint serializes, not a field
  subset. This is structural equality of the API response, not a literal byte comparison and not a
  direct comparison of the persisted database row.
- Finding 2 (P2, global field exclusion incomplete): step 6 previously asserted only five fields
  via `jsonPath(...).doesNotExist()`, which passes for an explicit JSON `null` as well as a missing
  key — not what "does not carry this field" should mean, and it omitted `targetStockLevel`,
  `leadTimeDays`, `forecastingEnabled`, `quantity`, and `initialStock` entirely. Replaced with a
  loop over AC-1b's complete forbidden-field list plus `isStocked`, asserting `JsonNode.has(field)`
  is `false` for each — `has()` fails on any key presence regardless of value, closing both gaps at
  once.
- No production code changed; both fixes are test-only.
- Result: Pass. `./mvnw -o test -Dtest='CatalogMultiSiteExitGateIT'` — 1/1.
  `./mvnw -o test -Dtest='*IT'` — 375/375 (no regressions). `./mvnw -o test` (unit) — 404/404.
- T-4 is now fully resolved. Next action: T-5 (review checkpoint: web Products list migration to
  `getSiteProducts`, query-key audit, AC-6b inventory-totals disposition, AC-6c site-switch test).

## T-5 — Web Products list migration (review checkpoint)

- Re-scoped before any code was written; see "T-5 re-scope" in the Current handoff section above
  for AC-6a (data-source split) and AC-6e (Kuji-tab gate), both added to spec.md before
  implementation.
- Changed:
  - `apps/web/src/lib/api/products.ts`: added `getSiteProducts(siteId)` (calls `GET
    /api/v1/sites/{siteId}/products`) and the `SiteProduct` type, alongside the untouched legacy
    `getProducts`/`ProductListItem` - mirrors `getSiteStorageLocations`'s precedent in
    `lib/api/locations.ts` (drop-invalid-records `toSiteProduct` mapper, same shape as
    `toStorageLocationSummary`).
  - Added `hooks/queries/use-site-products.ts` (`useSiteProducts`): wraps `getSiteProducts` with
    `useCurrentSite()`, keyed `["products", siteId, "site"]`, disabled via `skipToken` until
    `siteId` resolves - mirrors `use-storage-locations.ts`'s precedent exactly, including its
    disabled-query-still-loading and unresolved-membership-is-an-error handling.
  - `hooks/queries/use-product-inventory.ts`: added `useSiteProductInventory(rootOnly)` - joins
    the untouched legacy `useProducts` (catalog/Kuji display fields) with the new
    `useSiteProducts` (site-owned fields only) by product ID, per AC-6a. `ProductWithInventory`
    widened additively: `totalQuantity`/`lastUpdatedAt`/`status` now optional (undefined on
    site-scoped rows - AC-6b withholds them, never zeroed), plus new optional `isStocked` and
    `siteProductVersion` fields. The pre-existing `useProductInventory` (still used by
    `location-detail-sheet.tsx`, out of this record's scope) is untouched.
  - `types/api.ts`: added `ProductListItem.forecastingEnabled?: boolean` (site-scoped-only field
    the join needed to carry; every other AC-6a site-owned field already existed on the type).
  - `components/products/product-table.tsx`: added `showQuantity` prop (default `true`). When
    `false`: the Stock column and its skeleton cells are removed entirely (not zeroed), and a
    `"Quantity and stock status are available after inventory is migrated per site (Phase 6)"`
    note renders below the table. The Status badge/sort now reads `row.isStocked ?? row.product.
    isActive` (falls back to the legacy field only when a row has no site data at all) instead of
    always reading `isActive` - AC-6d's "assortment eligibility only from is_stocked" made
    concrete as the one place the page shows an eligibility-like signal.
  - `components/products/product-sort-utils.ts`: `compareProducts`'s `"status"` case updated to
    the same `isStocked ?? isActive` fallback (existing `isActive`-only tests still pass unchanged,
    since they never set `isStocked`); `"stock"` case guards `totalQuantity ?? 0` since it's
    unreachable via the site view's UI (no Stock header to sort by there) but must not throw
    `NaN` if called directly.
  - `components/products/product-modal.tsx`: added `showInventory` prop (default `true`,
    `location-detail-sheet.tsx`'s usage untouched). When `false`, the "Current Stock" section
    renders a withheld-state card with the same explanatory copy instead of the quantity/location
    breakdown table. Adjust/Transfer buttons are **not** gated by this prop - they still use their
    own `useProductInventoryEntries`/`useKujiAllocationsByProduct` fetches, which stay unscoped;
    stock mutation itself is inventory work, out of this record's "no inventory work" boundary
    (spec.md AC-6b only withholds *display* of quantity/stock-status, not the ability to adjust
    it).
  - New `components/products/kuji-tab-panel.tsx` (`KujiTabPanel`, exports `MAIN_SITE_CODE`):
    AC-6e's gate, extracted into its own component (rather than left inline in `page.tsx`) so it's
    independently unit-testable without the page's Sidebar/permissions/router provider stack.
    Renders `CustomKujiTabs` (moved here from `page.tsx`'s own `dynamic()` call) only when
    `siteCode === "MAIN"`; otherwise renders a "Kuji is not yet available per-site" card and never
    mounts `CustomKujiTabs` at all (no request, no dynamic import triggered). **Fails closed**: an
    unresolved `siteCode` (`undefined`, e.g. while `/api/v1/me/sites` is still loading) also
    renders the unavailable state rather than assuming MAIN - Kuji data is never shown before the
    site is confirmed.
  - `app/(dashboard)/products/page.tsx`: swapped `useProductInventory(true)` for
    `useSiteProductInventory(true)`; `ProductTable` gets `showQuantity={false}`; `ProductModal`
    gets `showInventory={false}`; the Custom Kuji tab content is now `<KujiTabPanel
    siteCode={list.siteCode} items={customKujiItems} />` in place of the previous unconditional
    `<CustomKujiTabs items={customKujiItems} />`. Filtering (search/category), pagination, the
    `excludeCustomKuji`-equivalent split (`productsTabItems`/`customKujiItems`), and money-field
    visibility (`canViewCosts`/`canViewMsrp` gating in `ProductModal`, unaffected by this change)
    are otherwise unchanged - they all operate on fields the join leaves untouched or correctly
    overrides.
  - `components/stock/adjust/types.ts`: `createNormalizedInventory` (dead code - exported but
    called nowhere in the app, confirmed by grep) now defaults `quantity: totalQuantity ?? 0`
    since `totalQuantity` became optional; no behavior change for its only real inputs (the
    legacy view, where it's always a number).
- AC-6b disposition: **(a) Withhold**, as spec.md assumes by default. No site-scoped inventory-
  totals endpoint exists yet (Phase 6). Quantity/stock-status are removed from both the list
  (Stock column) and the detail view (Current Stock section), each replaced with the same
  "available after inventory is migrated per site (Phase 6)" explanation - never a zeroed value.
- `invalidateQueries` audit (AC-6): every `hooks/mutations/*.ts` call site invalidating products
  uses a bare `queryKey: ["products"]` (7 call sites across `use-not-assigned-mutations.ts`,
  `use-location-mutations.ts`, `use-stock-mutations.ts`, `use-product-mutations.ts`,
  `use-supplier-mutations.ts`, `use-shipment-mutations.ts`) plus a few product-ID-scoped keys in
  `use-stock-mutations.ts`/`use-product-mutations.ts` (`["products", id]`,
  `["products", id, "with-children"]`, `["products", id, "children"]`) that target the unrelated
  single-product/children queries, not the list. TanStack Query's default `invalidateQueries`
  matches by array-prefix (`exact: false`), so a bare `["products"]` call already invalidates
  every query whose key starts with `"products"` - both the legacy list (`["products", opts]`)
  and the new site-scoped list (`["products", siteId, "site"]`) - with no code change needed.
  Verified empirically, not assumed: a scratch `QueryClient` test seeded all three key shapes plus
  an unrelated key, called `invalidateQueries({ queryKey: ["products"] })`, and asserted all three
  product keys were marked invalidated while the unrelated key was not (run once to confirm, then
  discarded - not a permanent regression test, since it tests TanStack's own documented behavior
  rather than this codebase's code). **No `hooks/mutations/` files were changed for T-5** - the
  audit's conclusion is that the existing keys already cover the new query.
- Tests:
  - `hooks/queries/__tests__/use-site-products.test.ts` (new, 4 tests): disabled-until-resolved,
    calls through with the resolved `siteId`, site-resolution error propagation, unresolved-
    membership-with-no-error surfaces as an error - mirrors `use-storage-locations.test.ts`.
  - `hooks/queries/__tests__/use-site-product-inventory.test.ts` (new, 4 tests): stays `null`
    until both queries load; AC-6a's field-source split (money/settings fields only from the site
    response even when the legacy catalog object carries different values for the same field
    names, catalog fields only from legacy, quantity fields always `undefined`); a simulated site
    switch (rerender with a different `useSiteProducts` result) proves the row's site-owned fields
    change while catalog fields stay identical, and that an absent-from-site-list product safely
    degrades to `isStocked: false`/`siteProductVersion: null` rather than throwing; site-error
    propagation.
  - `components/products/__tests__/kuji-tab-panel.test.tsx` (new, 3 tests, the AC-6e boundary
    test the user explicitly asked to see alongside the assortment/settings behavior): MAIN
    renders `CustomKujiTabs` with the given items; a non-MAIN `siteCode` renders the unavailable
    card and never calls/mounts `CustomKujiTabs`; an unresolved (`undefined`) `siteCode` also
    fails closed to the unavailable card. Uses a local `next/dynamic` stand-in (resolves the
    loader asynchronously via `useEffect`, same shape as the real implementation) so the test
    exercises the same dynamic-import wiring the page uses, not a bypassed one.
  - No dedicated AC-6c "whole page" test: reaching `ProductsContent` requires a `SidebarProvider`
    (`ProductHeader`'s `SidebarTrigger` throws without one) and full RBAC/auth context
    (`usePermissions` inside `ProductFilters`/`ProductModal`), neither of which any existing test
    in this codebase currently provides a harness for. Per Track D's AC-7 precedent (already
    invoked by spec.md's own AC-6 for this exact page), page-level end-to-end coverage is manual/
    Playwright smoke rather than a from-scratch provider harness built for this one page. The
    join and the AC-6e boundary - the two behaviors AC-6c is actually protecting against
    regressing - are covered directly by the hook and `KujiTabPanel` tests above; what remains
    untested at the unit level is wiring (`ProductTable`/`ProductModal` prop plumbing in
    `page.tsx`), verified instead by reading the diff and by `tsc --noEmit` (no untyped prop
    mismatches) plus the full existing suite staying green.
- Result: Pass. `npx vitest run` (whole `apps/web` suite) — 303/303 (11 new: 4 + 4 + 3 above).
  `npx tsc --noEmit -p tsconfig.json` — clean. `npm run lint` — 0 errors, 50 warnings (all
  pre-existing, none in a file this task touched beyond the one pre-existing warning already
  present on `products/page.tsx`'s unrelated `items` useMemo dependency, confirmed unchanged from
  before this task). No contract regeneration - T-5 is web-only, calls only endpoints T-1/T-2
  already published.
- Next action: T-6 (web categories migration, `useCategories` → `/api/v1/catalog/categories`).

## T-5 independent review result

- Status: changes requested; see review.md for three P2 findings (stale detail-modal state
  across a site change, the modal's Status line still reading global `isActive`, missing
  whole-page acceptance proof).
- Verified by the reviewer: `npm run test:run --workspace apps/web` — 303/303;
  `npx tsc --noEmit -p apps/web/tsconfig.json` — pass; `npm run lint --workspace apps/web` —
  0 errors, 50 warnings.
- Next action: fix all three findings, then rerun the review checkpoint before T-6.

## T-5 review findings fixed

- Finding 1 (P2, modal retains the previous site's settings — `page.tsx`): `selected` was a
  `ProductWithInventory` object snapshotted at click time (`setSelected(row)`), so once
  `useSiteProductInventory` refetched on a site change, the open modal kept showing whichever
  site's `isStocked`/money fields were current *when the row was clicked*, not the new site's.
  Changed the page to hold only `selectedProductId`/`editingProductId` (`string | null`) and
  derive `selected`/`editing` via `useMemo(() => items.find(row => row.product.id === id) ??
  null, [items, id])` on every render - since `items` comes from the same `list.data` that
  refetches on a site change, an open modal now automatically re-resolves to the new site's row
  the next time `items` updates, with no explicit "clear on site change" needed. The `editing`
  local variable was fully replaced by this derivation (`ProductForm` only ever needed the id,
  not the row object, so `editingProductId` is passed directly). Also fixes the same staleness
  for the edit form's `initialProductId` and for `onAddClick`'s `setEditing(null)` reset
  (now `setEditingProductId(null)`).
- Finding 2 (P2, modal still displays global stock status — `product-modal.tsx`): a second,
  separate "Status:" row (distinct from the one in `product-table.tsx`, already fixed in the
  initial T-5 pass) still rendered `p.isActive` unconditionally - missed in the original pass.
  Now reads `product.isStocked` when present (site-scoped rows) and falls back to `p.isActive`
  with its original "Active"/"Inactive" copy only when `isStocked` is `undefined` (the legacy,
  unscoped `location-detail-sheet.tsx` path, left unchanged). The site-scoped path now always
  shows "Stocked"/"Not Stocked", matching the table badge for the same row - they can no longer
  disagree.
- Finding 3 (P2, missing whole-page acceptance proof): added
  `app/(dashboard)/products/__tests__/page.test.tsx` (4 tests), a real rendered-page test
  (`SidebarProvider` + `QueryClientProvider` around the actual `ProductsPage`, with `next/dynamic`
  stubbed via the same async loader shim as `kuji-tab-panel.test.tsx` so `ProductModal` still
  loads through its real dynamic-import wiring) rather than only hook-level mocks:
  - list withholds quantity/Stock column and shows the withheld-inventory affordance, and the
    legacy site-blind `quantity: 999` value never renders anywhere;
  - opening the detail modal also withholds inventory and hides Edit for an EMPLOYEE-shaped
    permission set;
  - a role with `PRODUCTS_UPDATE` sees Edit and its cost/MSRP fields (gated correctly per
    permission, sourced from the site response, not any legacy value);
  - the AC-6c site-switch scenario Finding 1 above fixes: opens the modal at MAIN (`Stocked`,
    `$20.00` msrp), then re-renders with `useCurrentSite` resolving to a `SECOND` site whose
    `getSiteProducts` mock returns **realistic, distinct** fallback settings (`isStocked: false`,
    `msrp: 99`, different `unitCost`/`reorderPoint`/`leadTimeDays` - not an empty/placeholder
    stand-in), and asserts the *same open modal* now shows `Not Stocked`/`$99.00`, that MAIN's
    `$20.00` is gone, and that inventory stays withheld throughout.
  - `window.matchMedia` polyfilled at the top of the file (`SidebarProvider`'s `useIsMobile`
    reads it; jsdom doesn't implement it) - a test-environment gap, not a product code change.
  - `ProductForm`/`ManageCategoriesDialog`/`AdjustStockDialog`/`TransferStockDialog`/
    `KujiTabPanel` are stubbed (not under test here - opening them, and the AC-6e Kuji gate, are
    covered elsewhere); `usePermissions`, `useCurrentSite`, and the `@/lib/api/products`/
    `@/lib/api/categories` network functions are mocked; `useSiteProducts`, `useProducts`,
    `useSiteProductInventory`, and `ProductModal` itself all run for real.
- No production code behavior changed beyond Findings 1 and 2 above.
- Result: Pass. `npx vitest run` (whole `apps/web` suite) — 307/307 (4 new). `npx tsc --noEmit -p
  tsconfig.json` — clean. `npm run lint` — 0 errors, 50 warnings (all pre-existing; the `items`
  useMemo dependency warning on `page.tsx` now appears twice, once per derived-state `useMemo`
  that reads it, same pre-existing warning class as before, not a new issue).
- Next action: T-5 review re-verification, then T-6 (web categories migration).

## T-6 — Web categories migration

- Changed:
  - `apps/web/src/lib/api/categories.ts`: added `getCatalogCategories()` (calls `GET
    /api/v1/catalog/categories`) alongside the untouched legacy `getCategories` (now marked
    `@deprecated` in its doc comment only - not removed, since `use-category-mutations.ts` and
    any other lingering caller stay on it). Added `toCategory`, a recursive mapper (categories
    nest via `children`) matching the drop-invalid-records pattern used by `toSiteProduct`/
    `toStorageLocationSummary` - a record missing `id` or `name` (and, since mapping recurses,
    its whole subtree) is dropped rather than defaulted.
  - `apps/web/src/hooks/queries/use-categories.ts`: `useCategories`'s `queryFn` swapped from
    `getCategories` to `getCatalogCategories`. Unlike T-5's products, this is a straight
    swap, not a join: categories are wholly global (verified directly -
    `CatalogCategoryController.getCatalogCategories` calls the same
    `categoryService.getRootCategoriesWithChildren()` the legacy `CategoryController` uses, so
    the two routes return identical data), so there is no siteId to key on and no second query
    to merge. The `["categories"]` query key is unchanged - every consumer
    (`useChildCategories`/`useSubcategories`, and every component using `useCategories`
    directly: the Products page, `manage-categories-dialog`, `product-form`,
    `adjust-stock-dialog`) moves to v1 in this one change, with no per-caller edits needed.
  - Category **mutations** (`use-category-mutations.ts`: create/update/delete,
    `manage-categories-dialog.tsx`) are untouched - still call the legacy
    `createCategory`/`updateCategory`/`deleteCategory`, per spec.md's T-6 task description
    ("`useCategories` migrates to v1... `manage-categories-dialog`... unchanged"). Their
    `invalidateQueries(["categories"])` calls still correctly invalidate the (unchanged) read
    query key - no audit needed here the way T-5 needed one, since the key itself didn't change.
  - `app/(dashboard)/products/__tests__/page.test.tsx`: the `@/lib/api/categories` mock gained
    `getCatalogCategories` (routed through the same `mockGetCategories` spy) so the page's
    `useCategories` call keeps resolving in that test.
- Tests:
  - `lib/api/categories.test.ts` (new, 4 tests): calls the v1 route with no `siteId` param;
    recursively maps nested children, dropping only the invalid node (not its valid siblings);
    drops root records missing `id`/`name`; propagates a `GeneratedApiError` on an API error.
    Closes a gap T-5 should have had too (see below).
  - `hooks/queries/__tests__/use-categories.test.ts` (new, 3 tests): confirms `useCategories`
    calls `getCatalogCategories` and never `getCategories`; the existing alphabetical-sort
    `select` behavior is unchanged for both root and child categories; `useChildCategories`
    resolves correctly from the v1-backed data.
  - `lib/api/products.test.ts` (new, 4 tests): backfills the same API-layer coverage for T-5's
    `getSiteProducts` that `getSiteStorageLocations`/`getCatalogCategories` have -
    `getSiteProducts` was only covered indirectly (via the hook-level join tests) before this;
    added directly here since I was already in this area. Covers the request shape, the
    `version: null` mapping for an absent/undefined version (AC-2: no version to compare against
    means `null`, not `0` or `undefined`), dropping records missing `productId`, and API-error
    propagation.
- Result: Pass. `npx vitest run` (whole `apps/web` suite) — 318/318 (11 new: 4 + 3 + 4 above).
  `npx tsc --noEmit -p tsconfig.json` — clean. `npm run lint` — 0 errors, 51 warnings (same set
  as T-5's last verified count; none new). No contract regeneration - T-6 calls only the
  `/api/v1/catalog/categories` route T-1 already published; no backend change.
- Independent review: completed 2026-09-09, no Standards or Spec findings. Full-tier final
  review applies even without a separately named T-6 checkpoint. Verified 318/318 tests,
  typecheck pass, lint 0 errors/51 warnings, and clean diff whitespace. See review.md and
  validation.md. Implementation task list complete; commit and PR gate remain pending.
