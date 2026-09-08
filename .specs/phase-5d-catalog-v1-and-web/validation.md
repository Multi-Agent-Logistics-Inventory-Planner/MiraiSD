# Validation

## Command and scope

JDK 21, from `services/inventory-service`:

`./mvnw -o test -Dtest='CatalogProductControllerIT'`
`./mvnw -o test -Dtest='*IT'`
`./mvnw -o test` (unit)
`oasdiff breaking --fail-on ERR` (packages/contracts/openapi.json, pre- vs post-change)
`packages/api-client`: `npm run generate` && `npm run typecheck`

## Result

Both P2 findings from the T-1 review checkpoint are fixed (see `review.md`):
global search no longer filters on `isActive`, and `CatalogProductMapper` maps
`parentId` from the live `parent` association instead of the unpopulated shadow
column. Two permanent regression tests were added to `CatalogProductControllerIT`
covering exactly the two reproduced failure modes.

- `CatalogProductControllerIT`: 13/13 pass (11 original + 2 new regression tests).
- Full `*IT` suite: 353/353 pass at the T-1 checkpoint (351 baseline + 2 new tests; no
  regressions elsewhere); 367/367 after T-2 added `SiteProductControllerIT` (14 tests).
- Unit suite (`./mvnw -o test`): 404/404, Maven exit 0. **Correction to an earlier revision of
  this file:** a prior run reported 1 failure + 1 error here as "pre-existing on the base commit,"
  based on a `git stash` comparison. That comparison was invalid — plain `git stash` does not
  stash untracked files, so this record's new `catalog/api/*.java` files stayed in the working
  tree while stashing removed the tracked-file changes (`ProductService`, `SiteAssortment`, etc.)
  those new files depend on, producing a broken hybrid state, not the real base commit. Re-run
  with `git stash -u` (and a clean `target/`) shows the base commit passes both tests cleanly —
  there was no pre-existing failure. The actual, later cause of an intermittent unit-suite failure
  during T-2 was real: `ArchitectureTest.moduleDependencyEdgesMatchApprovedBaseline` correctly
  caught a genuine new `catalog -> shared` dependency edge from `SiteProductController`, fixed by
  reviewing and adding that edge to `module-dependency-edges-baseline.txt` (see `log.md`'s T-2
  entry) rather than by being pre-existing/unrelated.
- `oasdiff breaking --fail-on ERR`: no breaking changes (purely additive), re-checked after T-2.
- `packages/api-client`: `npm run generate` and `npm run typecheck` both clean, re-checked after
  T-2.

## Acceptance criteria evidence

- AC-1: Global product/category/supplier v1 routes respond; `CatalogProductControllerIT`
  covers root and child creation, list, and search. No operation-ID collisions
  (springdoc/`OpenApiContractExportTest` succeeded).
- AC-1b: `createCatalogProduct_rejectsForbiddenField` (all nine forbidden fields → 400) and
  `createCatalogProduct_createsNoSiteProductRows` (successful creation still creates zero
  `site_products` rows) pass.
- AC-4: `packages/contracts/openapi.json` and `packages/api-client/src/schema.d.ts` regenerated;
  `oasdiff breaking --fail-on ERR` reports no breaking changes.
- AC-2: `SiteProductControllerIT` covers absent-row reads, assortment upsert, settings on a
  carried vs. uncarried product, and stale/missing version rejection (14/14 pass).
- AC-2b: `SiteProductControllerIT`'s permission-matrix tests cover both mutation endpoints
  directly (EMPLOYEE 403, ASSISTANT_MANAGER/ADMIN 200) plus no-membership and revoked-membership
  rejection on both reads and mutations.
- AC-2c: `SiteProductControllerIT.getSiteProduct_globallyNonexistentProduct_returns404` and
  `updateSettings_noRowExists_returns404` distinguish the two 404 cases the AC requires kept
  separate (globally-invalid product vs. a valid product this site has never carried).

## T-2 independent review validation

JDK 21, from services/inventory-service:

`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw -o test -Dtest=SiteProductT2ReviewIT,ArchitectureTest,CatalogEntityAccessCallerSetTest`

24 tests: 23 passed, 1 regression assertion failed; no errors. The temporary
review class copied the existing 14 controller tests unchanged and added one
malformed-version check. All 14 original scenarios pass; the new check expected
400 but received 200 for `expectedVersion: "nonsense"`. ArchitectureTest (7)
and CatalogEntityAccessCallerSetTest (2) both pass. Authorized execution outside
the sandbox was used because Mockito attachment previously failed inside it.

Log: `/private/tmp/phase5d-t2-review.log`. Reproduction source preserved at
`/private/tmp/SiteProductT2ReviewIT.java` and removed from the repo afterward.
No implementation changes made. Full suites and oasdiff were not independently
rerun at this checkpoint; their results above remain implementer-reported.

## T-2 review findings fix validation

All three P2 findings fixed (see `review.md`): `SiteProductSettingsRequest` replaces the raw
`JsonNode` body with a typed schema (backed by `FieldUpdateDeserializer` for the tri-state
override fields), which also fixes the version-coercion bug as a side effect (a typed `Long`
throws on a non-numeric value instead of silently becoming `0`); the permission-matrix tests now
cover settings as well as assortment. One additional test closes the review's noted residual gap
(tri-state clear vs. omit had no HTTP-level test).

- `SiteProductControllerIT`: 16/16 pass (14 original + `updateSettings_malformedVersion_returns400`
  + `updateSettings_omittedLeavesUnchanged_explicitNullClearsOverride`).
- Full `*IT` suite: 369/369, Maven exit 0 (367 + 2 new tests).
- Unit suite (`./mvnw -o test`): 404/404, Maven exit 0.
- `oasdiff breaking --fail-on ERR` (pre-T-1 contract vs. current): no breaking changes.
- `packages/api-client`: `npm run generate` + `npm run typecheck` clean.
  `SiteProductSettingsRequest` in `src/schema.d.ts` now has named, correctly-typed fields
  (`expectedVersion?: number`, `forecastingEnabled?: boolean`, `unitCost?: number`,
  `msrp?: number`, `reorderPoint?: number`, `targetStockLevel?: number`, `leadTimeDays?: number`)
  instead of `Record<string, never>` — checked directly against the generated file.
- Malformed-version fix confirmed via the actual server log line for the regression test:
  `Cannot deserialize value of type java.lang.Long from String "nonsense": not a valid
  java.lang.Long value`, mapped to 400 by the existing `HttpMessageNotReadableException` handler.

## T-2 typed-request follow-up validation

JDK 21, from services/inventory-service:

`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw -o test -Dtest=SiteProductControllerIT,SiteProductT2FollowupIT`

Permanent controller suite: 16/16 pass. A temporary copy with only the malformed
version payload changed from `"nonsense"` to `0.9`: 15 pass, 1 fails (expected
400, actual 200). Total: 32 run, 1 failure, no errors.
Evidence: `/private/tmp/phase5d-t2-followup.log`; reproduction preserved as
`/private/tmp/SiteProductT2FollowupIT.java`, removed from repo after execution.
Direct OpenAPI/TS inspection confirms nullable override unions are missing.
Full suites and oasdiff were not rerun for this follow-up.

## T-2 nullable-union and fractional-version fix validation

Both findings fixed (see `review.md`): `SiteProductSettingsRequest`'s override fields now use
`@Schema(types = {"<type>", "null"}, implementation = <Wrapper>.class)` instead of
`nullable = true` (which this project's OpenAPI-3.1 springdoc output silently ignores), and
`expectedVersion` is deserialized via a new `StrictLongDeserializer` that rejects any non-integral
JSON number instead of letting Jackson truncate it.

- `SiteProductControllerIT`: 17/17 pass (16 + new `updateSettings_fractionalVersion_returns400`,
  the review's exact `expectedVersion: 0.9` reproduction).
- Full `*IT` suite: 370/370, Maven exit 0.
- Unit suite (`./mvnw -o test`): 404/404, Maven exit 0.
- `oasdiff breaking --fail-on ERR` (pre-T-1 contract vs. current): no breaking changes.
- Direct inspection of the regenerated `packages/contracts/openapi.json`:
  `SiteProductSettingsRequest`'s six override-adjacent properties are now
  `{"type": ["<type>", "null"]}` with no stray `$ref` and no leaked
  `FieldUpdateBoolean`/`FieldUpdateBigDecimal`/`FieldUpdateInteger` component schemas (an earlier
  attempt using `types` without `implementation` produced exactly that leak; confirmed it's gone
  in the final version).
- `packages/api-client/src/schema.d.ts`: `SiteProductSettingsRequest` fields are now
  `forecastingEnabled?: boolean | null`, `unitCost?: number | null`, `msrp?: number | null`,
  `reorderPoint?: number | null`, `targetStockLevel?: number | null`,
  `leadTimeDays?: number | null` — checked directly against the generated file.
- `npm run typecheck`: clean.

## T-2 final independent follow-up validation

JDK 21, from services/inventory-service:

`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw -o test -Dtest=SiteProductControllerIT`

17 tests, zero failures/errors, BUILD SUCCESS. Evidence:
`/private/tmp/phase5d-t2-final-review.log`. OpenAPI and generated TypeScript
nullable unions inspected directly. Full suites/oasdiff not rerun in this
follow-up; their results remain implementer-reported.


## T-3 independent review validation

From `services/inventory-service`:

`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw -o test -Dtest=LegacyCatalogDeprecationHeadersIT`

4 tests, zero failures/errors, BUILD SUCCESS. The sandbox run could not attach
Mockito to the JVM; the authorized outside-sandbox rerun passed. Evidence:
`/private/tmp/catalog-t3-review.log`. These assertions compare the emitted value
with the implementation constant, so passing does not validate RFC 9745 syntax.
Full integration/unit suites were not rerun; their counts remain implementer-reported.

## T-3 review fix validation

`DEPRECATION_DATE` changed to `"@1788825600"`; `LegacyCatalogDeprecationHeadersIT` now decodes
the raw header via an independent regex (`^@(-?\d+)$` for Deprecation, RFC 8288 syntax for Link)
and asserts the resulting `Instant` against a separately-parsed `2026-09-08T00:00:00Z`, rather
than reusing the implementation constant.

`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw -o test -Dtest=LegacyCatalogDeprecationHeadersIT`
— 4/4, BUILD SUCCESS. `./mvnw -o test -Dtest='*IT'` — 374/374 (no regressions).
`./mvnw -o test` (unit) — 404/404. T-3 is closed; proceeding to T-4.

## T-3 independent fix follow-up validation

From `services/inventory-service`, JDK 21:

`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw -o test -Dtest=LegacyCatalogDeprecationHeadersIT`

4 tests, zero failures/errors, BUILD SUCCESS. Used the previously authorized
outside-sandbox command for Mockito JVM attachment. Log:
`/private/tmp/catalog-t3-review.log`. Full suites were not rerun in this
follow-up; their results above remain implementer-reported.
