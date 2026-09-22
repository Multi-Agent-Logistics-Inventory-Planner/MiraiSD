# Implementation log

## Current handoff

- Status: B1, B2, B3 complete and verified (71 focused tests green, including real
  Postgres execution). Starting B4/B5 (v1 catalog field gaps + slim projection).
- Next action: B4 (add fields to CatalogProductResponse), B5 (slim projection for
  CatalogProductController), B6 (delete dead heavy queries), B7 (caller-set test),
  B8 (egress IT), then contract regen and full native gates.
- Decisions that must survive compaction:
  - `hasActiveBox` intentionally excluded from v1 catalog (Phase 7 kuji owns it).
  - `description`/`notes` kept on v1 catalog list (negligible data volume).
  - Webhook redaction folded directly into existing `CostVisibilityPolicy` methods
    rather than a sibling class -- this automatically covers 2 supplier endpoints
    (`/api/suppliers/{id}/products`, `/api/v1/catalog/suppliers/{id}/products`)
    that the original plan text didn't call out.
  - `EffectiveProductSettings`'s `Product` overloads now delegate to new
    `ProductSettingsDefaults` overloads via `ProductSettingsDefaults.from(Product)`
    (public) -- one implementation of each resolution branch, not two.
- Last verified command: `./mvnw -B clean test -Dtest='SiteProductControllerIT,
  SiteProductServiceTest,SiteProductLegacyCompatibilityIT,SiteProductConcurrencyIT,
  ProductCostVisibilityIT,ProductRepositoryListItemsIT,RedactedFieldsAreOmittedFromJsonIT'`
  -- 71/71 passed.
- Open risks/questions: none yet.

## Assumptions and decisions

### B1 -- kujiSlackWebhookUrl redaction
- Gated on `Permission.PRODUCTS_UPDATE` (the same permission required to change the
  field), not a dedicated view permission -- it is a Slack incoming-webhook bearer
  credential, and viewing/holding it is the same trust level as editing it.
- Added `@JsonInclude(NON_NULL)` to the field on both `ProductResponseDTO` and
  `ProductListItemDTO`, matching the existing unitCost/msrp pattern, so redaction
  omits the key (contract-safe) rather than emitting `null`.
- Folded into `CostVisibilityPolicy.applyCostVisibility`/`applyCostVisibilityToListItem`
  rather than a new sibling method -- automatic coverage of all 9 existing call sites
  (ProductController x2 handlers, SupplierController, CatalogSupplierController,
  SiteProductController does NOT carry this field so unaffected).
- TDD: added failing tests to `ProductCostVisibilityIT` (RED: 2 failures, employee
  saw the webhook on detail and list) before the fix; GREEN after (11/11). Added
  serialization-layer proof to `RedactedFieldsAreOmittedFromJsonIT` (key-absence,
  not null-ness).

### B2 -- hasChildren folded into LIST_ITEM_SELECT
- `CASE WHEN EXISTS (SELECT 1 FROM Product ch WHERE ch.parentId = p.id) THEN true
  ELSE false END`, correlated against the same `Product` table the outer query
  already scans. Chosen over caching per the plan (no invalidation to get wrong).
- Deleted `ProductService.withHasChildren`/`getParentProductIds` and
  `ProductRepository.findAllParentIds` entirely -- confirmed zero remaining callers
  in src/main and src/test before deleting.
- TDD: added failing test to `ProductRepositoryListItemsIT` (RED: hasChildren was
  null) proving parent/child/leaf all resolve correctly through the folded query,
  run against real Postgres.

### B3 -- slim projection for SiteProductController.getSiteProducts
- New `ProductSettingsDefaults` record (id/sku/name + 6 EffectiveProductSettings
  columns) and `ProductRepository.findAllSettingsDefaults()` (no joins).
- `EffectiveProductSettings.absent`/`from` gained `ProductSettingsDefaults`
  overloads; the original `Product` overloads now delegate to them via
  `ProductSettingsDefaults.from(Product)` (made public for this) -- single
  implementation per branch.
- `SiteProductController.toResponse(UUID, Product, SiteProduct)` now delegates to
  a new `toResponse(UUID, ProductSettingsDefaults, SiteProduct)` overload; the list
  endpoint calls the projection-based path directly.
- No new test written for this step: it is a pure refactor preserving existing
  behavior (not new behavior), and the existing SiteProductController/Service/
  LegacyCompatibility/Concurrency suites (48 tests, all against real Postgres via
  BaseKafkaIntegrationTest) are the regression safety net. The egress reduction
  itself (fewer joins/statements) is proven separately in B8.
