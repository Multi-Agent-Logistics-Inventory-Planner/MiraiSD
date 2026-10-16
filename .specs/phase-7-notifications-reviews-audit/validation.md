# Validation

## Command and scope

Run 2026-10-16 on branch `refactor/phase-7`, uncommitted tree, JDK 21, local Docker for Testcontainers.

| Deployable / boundary | Command | Result |
| --- | --- | --- |
| inventory-service (unit + all ITs) | `./mvnw -o clean test -Dtest='*'` | 1191 tests, 0 failures, 0 errors, BUILD SUCCESS (final run, after the review fixes) |
| Architecture / module edges | `ArchitectureTest`, `*CallerSetTest` (inside the run above) | green; `archunit.properties` unchanged (`allowStoreUpdate=false`) |
| messaging-service | `.venv/bin/python -m pytest tests` | 96 passed |
| Event-envelope contracts | `tests/contracts` with the forecasting venv, `PYTHONPATH=services/messaging-service` | 32 passed (one test updated: the messaging envelope now models `site_id`) |
| apps/web | `npx tsc --noEmit`; `npx vitest run` | clean; 74 files, 483 tests passed (re-run against the final regenerated client) |
| REST contract | `OpenApiContractExportTest` (inside the full run) then `npm run generate` in `packages/api-client` | `openapi.json` and `schema.d.ts` regenerated; web re-verified against them |

## Result

Pass for the commands above.

## Not run / not verified

- V81-V83 against a production dump or the Supabase schema (column and constraint names assumed from earlier migrations); the fixed stale-display query against the live schema.
- Playwright / `tests/e2e` (not started in this session; the previous slice recorded the forecasting healthcheck blocker as pre-existing).
- Web lint (no ESLint config in the repo).
- Slack delivery end to end.

## Acceptance criteria evidence

- AC-1: `NotificationsReviewsAuditSiteMigrationIT` (5 tests: audit inheritance order, mixed-site movements, notification inventory/machine/shipment site, MAIN fallback, idempotent re-run, missing-MAIN failure, repeatable valid concurrent indexes). `review_daily_counts` was already site-scoped and unique per site/user/date (V75-V79). Constrain deliberately deferred.
- AC-2: `SiteAuditLogControllerAuthorizationIT` (foreign-site id 404, own-site 200, list scoped, non-member rejected, page size capped); `SiteReviewControllerAuthorizationIT` (non-member user 404 on user/reviews/stats, site-filtered reviews and summaries, tracked users limited to members, oversized body rejected, page size capped, non-member caller rejected); `AuditLogSpecificationsIT.siteFilterIsolatesSites`; `ActivityFeedServiceTest`. Legacy routes resolve MAIN through `LegacyMainSiteContextResolver`. System-admin cross-site reporting: none added (policy in `log.md`).
- AC-3: `AuditLogServiceSiteTest`, `AuditLogServiceShipmentEventTest`, `KujiBoxServiceTest.addSlip_stampsTheBoxSiteOnTheAuditLog`, `StockMovementServiceSameSiteTransferTest`, `MachineDisplayServiceNotificationTest`, `EasyPostWebhookServiceTest` (writers stamp the site); `SupabaseBroadcastServiceTest`, `NotificationServiceSiteTest`, `ShipmentServiceOverrideTest` (broadcasts carry the site). Messaging: `test_notification_site.py`, `test_stale_display_site.py`, `test_review_row_site.py`, `test_review_site_membership.py` (envelope keeps `site_id`, rows and dedupe keys carry it, reviews only credit active members of the review site).
- AC-4: `audit` and `reviews` modules own their repositories; `StockMovementService` and `ActivityFeedService` use `AuditLogService`; `lootbox -> reviews` repository access remains (follow-up facade). Baseline edges reviewed and 17 stale ones pruned.
- AC-5: `/api/v1/sites/{siteId}/audit-logs` and `/reviews` added and consumed by the web; `LegacyAuditDeprecationHeadersIT` and `LegacyReviewDeprecationHeadersIT` prove legacy headers and clean v1 routes; `docs/baseline/api-v1-map.md` updated. Notification route/web migration parked by the user.
- AC-6: PostgreSQL migration IT, MockMvc authorization ITs, broadcast/async unit tests, contract regeneration, ArchUnit - all above.
