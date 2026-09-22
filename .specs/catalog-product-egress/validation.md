# Validation

## Required limitation

`application-integration.properties` sets `spring.jpa.hibernate.ddl-auto=create-drop`. The
PostgreSQL-backed tests in this record (`ProductRepositoryListItemsIT`, `ProductListEgressIT`)
therefore prove PostgreSQL runtime semantics and real query shapes, not that the production
schema was created or upgraded by Flyway. `docs/plans/phase-4-6-closeout.md` Slice F2 owns Flyway
baseline and migration parity; that work is unrelated to and does not block this record.

## Commands

```bash
cd services/inventory-service
./mvnw -B clean test
./mvnw -B clean test -Dtest='*IT'
./mvnw -B -Dtest=OpenApiContractExportTest test

cd ../../packages/api-client
npm run generate && npm run typecheck

cd ../../apps/web
npx tsc --noEmit
npx vitest run --pool=threads
npm run lint
```

`clean` is required for the Maven commands: deleting methods (B6) leaves orphaned `.class`
files that make ArchUnit report phantom violations against artifacts that no longer exist in
source (a known, documented trap in this repo -- see `.specs/phase-4-6-exit-gate/log.md`).

## Result

All commands green after the four review-finding fixes:

| Gate | Result |
| --- | --- |
| `./mvnw -B clean test` | 483/483 |
| `./mvnw -B clean test -Dtest='*IT'` (real Postgres, Testcontainers) | 563/563 |
| Contract regen + `git diff packages/contracts/openapi.json` | additive only |
| TS client regen + typecheck | clean; diff additive only |
| `npx tsc --noEmit` (apps/web) | clean |
| `npx vitest run --pool=threads` (apps/web) | 67 files / 461 tests |
| `npm run lint` (apps/web) | 0 errors, 53 pre-existing warnings, none in touched files |

A `vitest run` invocation using the default forks pool intermittently hit
`[vitest-pool-runner]: Timeout waiting for worker to respond` on unrelated files under load from
concurrent Maven runs in the same session -- environment/resource contention, not a test
regression. `--pool=threads` and isolated re-runs of every flagged file confirmed all green;
recorded here (and in `.specs/web-product-list-cache/log.md`) so a future flaky run isn't
mistaken for a real failure.

## Acceptance criteria evidence

- AC-1 (webhook redaction): `ProductCostVisibilityIT` (11 tests), `RedactedFieldsAreOmittedFromJsonIT`
  (2 tests), `CatalogProductControllerIT` (2 tests).
- AC-2 (hasChildren folded): `ProductRepositoryListItemsIT.findAllAsListItems_computesHasChildrenInTheSameQuery`.
- AC-3 (site-products slim, no joins): `ProductListEgressIT.findAllSettingsDefaults_issuesOneStatement_withNoJoinAtAll`,
  `siteProductsList_throughController_isTheSlimQueryPlusTheSeparateAssortmentQuery`.
- AC-4 (v1 catalog field gaps, additive contract): `packages/contracts/openapi.json` diff;
  `CatalogProductControllerIT`; `ProductListEgressIT.catalogProductDetail_endToEnd_...`
  (preferredSupplierName, flat category -- both review-finding fixes).
- AC-5 (dead code removed, caller-set pinned): `ProductFullHydrationCallerSetTest`.
- AC-6 (comprehensive egress proof, both layers): `ProductListEgressIT`, 6 tests -- 3
  repository-level (query shape in isolation) and 3 through-controller (the real regression
  boundary, added during review).

## Not covered by this record, by design

- Migrating the 17 legacy `/api/products` web callers onto v1 catalog (Phase 7 work).
- `hasActiveBox` on v1 catalog's `CatalogProductResponse` (Phase 7 kuji slice; `kuji_boxes` has
  no `site_id` yet).
- `CatalogProductRequest` still cannot *set* `kujiSlackWebhookUrl` (pre-existing write-side gap,
  noted in `log.md`).
- `staleTime` policy on the legacy product list (`.specs/web-product-list-cache` W3, a documented
  no-op).
