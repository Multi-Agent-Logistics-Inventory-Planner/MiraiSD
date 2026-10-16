# Validation

## Command and scope

Backend (`services/inventory-service`):
- `./mvnw -o clean compile` / `./mvnw -o clean test-compile`
- `./mvnw -o -Dtest=ArchitectureTest test`
- `./mvnw -o test` (full unit suite)
- `./mvnw -o -Dtest='*IT' test` (full integration suite, Testcontainers/real PostgreSQL)
- `./mvnw -o -Dtest=OpenApiContractExportTest test` (contract regeneration)
- Targeted runs during review-finding fixes: `EasyPostWebhookControllerSecurityIT`,
  `SiteShipmentControllerCrossSiteAccessIT`, `LegacyShipmentDeprecationHeadersIT`

Web (`apps/web`):
- `npx tsc --noEmit`
- `npx vitest run`
- `packages/api-client`: `npm run generate` (openapi-typescript)

## Result

Pass, after fixing one review-found blocker (v1 webhook route returning 401) and one review-found
latent bug that took two follow-up passes to genuinely close (mutation-hook `options` forwarding
in `use-shipment-mutations.ts` — see `review.md`).

- Backend unit suite: 505/505 pass.
- Backend integration suite: 585/585 pass (581 before the review-fix pass added 4 new tests).
- ArchUnit (`ArchitectureTest`): 8/8 pass, including both `FreezingArchRule` checks and the
  module-dependency-edge baseline check. One new frozen violation
  (`DevSeedController.seedShipments()`'s new `SiteRepository.findByCode` call) hand-added to
  `archunit_store/0858803e-dd98-414e-afe2-201ea1504769` via the documented
  `allowStoreUpdate=true` procedure, then `allowStoreUpdate=false` restored and re-verified.
- `packages/contracts/openapi.json`: regenerated twice (once for the new v1 routes, once after
  the webhook-allowlist fix removed a stale `401` response entry on
  `/api/v1/webhooks/easypost`) — final diff against `main` is additive-only.
- `packages/api-client/src/schema.d.ts`: regenerated to match, additive-only diff.
- Web typecheck: clean (0 errors).
- Web unit suite: 462/462 pass (vitest) — one new test file,
  `use-shipment-mutations.test.tsx`, added to close the `options`-forwarding finding; it directly
  caught that `override`'s wrapper had also been missed by both earlier fix attempts. No other
  new web unit tests were added this pass (see `review.md`'s residual risk).

## Acceptance criteria evidence

- AC-1: Unaffected by T-5/T-6; already proven by T-3's `ShipmentSiteBackfillIT`/
  `ShipmentSiteIndexMigrationIT` (Testcontainers). Constrain migration confirmed still not
  written — a separately released step, per the spec and this slice's own decision record.
- AC-2: `SiteShipmentControllerSecurityTest`/`SiteTrackingControllerSecurityTest` (v1 routes:
  401 no-auth, 403 foreign-site-no-membership, 403 wrong-role, allowed-through-auth smoke tests);
  `SiteShipmentControllerCrossSiteAccessIT` (v1 routes: GET/DELETE/receive against a real
  foreign-site shipment id all return 404, DELETE proven non-destructive);
  `EasyPostWebhookControllerSecurityIT` (webhook mutation path reachable with no JWT on both
  legacy and v1 paths, matching its server-derived site resolution).
- AC-3: Unaffected by T-5/T-6; `InventoryOperations` usage and transactional audit/outbox
  coupling were proven in T-3/T-4 and re-confirmed passing by this pass's full IT sweep.
- AC-4: Unaffected by T-5/T-6; `shipments -> repositories`/`-> identity.infrastructure`/
  `-> sites.infrastructure` edges closed in T-4, re-confirmed by `ArchitectureTest` 8/8 and a
  fresh `.infrastructure.` import grep of `shipments/**` (none outside `shipments.infrastructure`
  itself). `shipments -> catalog` unchanged, intentionally kept (facade-mediated, not a violation).
- AC-5: `SiteShipmentController`/`SiteTrackingController` deliver all 11 + 2 v1 mappings;
  `EasyPostWebhookController` maps both legacy and v1 webhook paths (fixed to actually be
  reachable, see `review.md`); `apps/web`'s shipment/tracking consumers migrated
  (`lib/api/site-shipments.ts`/`site-tracking.ts` plus every hook/component caller, verified by
  grep that no remaining import is a function, only shared types); `LegacyShipmentDeprecationFilter`
  adds `Deprecation`/`Link` headers to every legacy shipment/tracking/webhook route, proven by
  `LegacyShipmentDeprecationHeadersIT` (now asserting an actual 200 on the v1 "does not carry
  headers" cases, not a 403 that trivially lacks them); `docs/baseline/api-v1-map.md` records the
  phase-7 entry against the existing compatibility-removal-gate section.
- AC-6: PostgreSQL migration tests (T-3, unaffected). Authorization tests as listed under AC-2.
  Concurrency/idempotency test coverage predates this slice, unaffected. Contract tests:
  `OpenApiContractExportTest` + `npm run generate`, additive-only diffs confirmed by `git diff
  --stat`. Module-boundary tests: `ArchitectureTest` 8/8.
