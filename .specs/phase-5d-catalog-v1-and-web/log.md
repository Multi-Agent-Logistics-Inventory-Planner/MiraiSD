# Implementation log

## Current handoff

- Status: T-1 and T-2 complete. T-1's review findings and both rounds of T-2's review findings
  (five total: search filtering, parentId mapping, unusable settings schema, malformed-version
  coercion, incomplete auth proof; then a second round: missing nullable unions, fractional-version
  coercion) are all fixed. T-3 through T-6 not started.
- Next action: T-3 (Deprecation/Sunset/Link headers on legacy catalog routes).
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
- Last verified: `./mvnw -o test -Dtest='*IT'` — 351/351, Maven exit 0 (was 340/340 baseline from
  5c + this record's 11 new `CatalogProductControllerIT` tests). `./mvnw -o test` (unit) —
  404/404, Maven exit 0. `oasdiff breaking --fail-on ERR` against the pre-change contract — no
  breaking changes. `packages/api-client` `npm run generate` + `npm run typecheck` — clean.
- Open risks/questions: T-1's categories/suppliers routes were not given their own dedicated IT
  file (they mirror already-tested legacy behavior 1:1 through the same service layer); if T-5/T-6
  web migration surfaces a gap, add targeted coverage then rather than duplicating
  `CategoryControllerSecurityIT`/`SupplierController` tests preemptively.

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
