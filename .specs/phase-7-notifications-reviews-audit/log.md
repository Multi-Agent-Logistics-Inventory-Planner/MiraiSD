# Implementation log

## Current handoff

- Status: implementation of T-1..T-5 complete; independent Spring, web and security reviews done and their required findings fixed (see `review.md`). Final gate result is in `validation.md`. Not committed; ask the user before committing.
- Next action: confirm the final gate (below) is green, then ask the user whether to commit. Follow-ups after merge: V84 constrain (repeat V82's body first), messaging-before-constrain ordering, drop `@Lazy` in `AuditLogDTOMapper`, `lootbox -> reviews` facade, per-site review tracking, email visibility.
- Decisions that must survive compaction: tests first; site authorization precedes lookup; foreign-site id and non-member user are 404; constrain (NOT NULL/FK/uniqueness, V84+) is a separate later release and needs: messaging deployed writing site_id, `SELECT count(*) WHERE site_id IS NULL` = 0 on notifications/reviews/audit_logs; notification Slack routing/config/custom messages and removal of the dead notification controller/web code are parked by the user; always `./mvnw -o clean ...`; `allowStoreUpdate` in archunit.properties must stay `false` when committing.
- Last verified (2026-10-16): backend full suite 1207 tests, 0 failures (before the notification-broadcast, shipment-broadcast and review-row changes; those were verified with focused runs, rerun pending); messaging pytest 92/92; web `tsc --noEmit` clean and vitest 480/480; `ArchitectureTest` + `*CallerSetTest` green.
- Open risks/questions: see "Deferred" below.

## Deferred / not done

- Constrain release (V84+): NOT NULL on `notifications.site_id`, `reviews.site_id`, `audit_logs.site_id`, composite same-site FKs, site-scoped review uniqueness `(site_id, external_id, review_date)` replacing `(external_id, review_date)` (the messaging INSERT's ON CONFLICT depends on the old one). Intentionally a separate release per spec T-3.
- Null-site rows stay invisible to scoped reads until backfill/constrain; V82 backfills all existing rows, so only rows written by an old writer between V82 and the code rollout could be affected (re-run V82, it is idempotent).
- `lootbox -> reviews` still reads `ReviewDailyCount` through `ReviewDailyCountRepository`; a `reviews` facade for the two credit queries is a follow-up.
- `User.isReviewTracked/canonicalName/nameVariants` are global user attributes; the site review routes only require membership at the site. A per-site tracking table is out of scope.
- `/api/stock-movements/audit-log` (dashboard 30-day stats) stays on the legacy MAIN-scoped route; its v1 shape (`SiteStockMovement`) differs from `AuditLogEntry`, so migrating `lib/api/dashboard.ts` is a separate change.
- Notifications: no v1 route, no web migration (UI is archived); `NotificationController` and web notification code remain. User decision 2026-10-15.
- Two `ShipmentService` notification stamps (damaged/completed) have no direct unit test.
- Production-dump run of V81-V83 and a run of the fixed stale-display query against the live schema.
- The OpenAPI diff is larger than this slice because the checked-in contract was already stale (shipments v1 etc.).

## Reviews module (done, test-first except the mechanical move)

- Move into `reviews` (domain `Review`/`ReviewDailyCount`, infrastructure repositories, application `ReviewService`, api controllers/DTOs) verified by the existing lootbox tests and ArchUnit; then RED/GREEN: `ReviewServiceSiteTest` (7), `SiteReviewControllerAuthorizationIT` (5), `LegacyReviewDeprecationHeadersIT` (2).
- Unscoped repository methods (`getMonthlySummariesByUser`, `getAllTimeStatsByUser`, `getAllTimeTotalsByUser`, `getAllTimeRankByUser`, `findByUserIdWithUser`, `findByUserIdAndDateRangeWithUser`, and the unused derived finders) were deleted; replacements filter on `siteId`.
- User-addressed operations resolve the user through the site's active membership (`UserService.findActiveSiteMember`) and throw `EntityNotFoundException` (404) before any review data is read or written.
- `ReviewUserDTO` replaces `identity.api.UserResponseDTO` on review routes (a module may not depend on another module's api package); fields are identical.
- Messaging: `create_review` writes `site_id`; `ReviewProcessor` resolves the site once per event and uses it for both the review row and the daily count (`tests/test_review_row_site.py`, 4 failed first, messaging suite 92/92).
- Baseline: 4 reviewed `reviews` edges added; 17 stale edges pruned (`models -> *`, `repositories -> *`, `lootbox -> models|repositories`, etc.).

## Web, contracts, broadcasts

- Web: `site-reviews.ts`, `site-audit-logs.ts`, hooks gated on `useCurrentSite` with site-qualified query keys (prefix keys used by realtime invalidation still match). RED/GREEN vitest: `site-reviews.test.ts`, `site-audit-logs.test.ts`, `use-audit-log.test.ts`, `use-top-reviewers.test.ts` (12 new; web suite 480/480; `tsc` clean).
- `packages/contracts/openapi.json` and `packages/api-client/src/schema.d.ts` regenerated (`OpenApiContractExportTest` writes the spec during the full suite; `npm run generate` for the client).
- Realtime: `notification_created` and `audit_log_created` carry `siteId`; `ShipmentService`'s three audit broadcasts now pass the site (RED then GREEN in `ShipmentServiceOverrideTest`); `NotificationService.createNotification` broadcasts the notification's site (`NotificationServiceSiteTest`, `SupabaseBroadcastServiceTest`).
- Docs: `docs/baseline/api-v1-map.md` "Implemented so far" gained the audit/review/notification entry.

## Audit logs (done, test-first)

- RED then GREEN: `AuditLogServiceSiteTest` (4), `AuditLogSpecificationsIT.siteFilterIsolatesSites`, `KujiBoxServiceTest.addSlip_stampsTheBoxSiteOnTheAuditLog`, `StockMovementServiceSameSiteTransferTest.transferInventory_sameSite_stampsTheSourceSiteOnTheAuditLog`, `ActivityFeedServiceTest` (3 new, written with the change, not seen failing first), `SiteAuditLogControllerAuthorizationIT` (4; the two positive cases failed first), `LegacyAuditDeprecationHeadersIT` (2; legacy case failed first).
- `AuditLog.siteId` (nullable column). `AuditLogService.createAuditLog`/`createShipmentEvent` take `siteId` first and broadcast site-scoped; reads are `getAuditLogs(siteId, ...)`, `getAuditLogDetail(siteId, id)`, `findById(siteId, id)` (kuji undo), `findRecent(siteId, ...)` (activity feed); `AuditLogFilterDTO.siteId` is set by the service, never bound from request params.
- Writers stamped: `MachineDisplayService` (machine location site, 7), `ShipmentService` (shipment site, 5), `KujiBoxService` (box site / destination location site, 11), `StockMovementService` (location site, 5), `DevSeedController`.
- Module move into `audit`: `AuditLog` (domain), `AuditLogRepository`/`AuditLogSpecifications` (infrastructure), `AuditLogService` (application), `AuditLogDTO`/`AuditLogDetailDTO`/`AuditLogDTOMapper`/`AuditLogController`/`SiteAuditLogController` (api). Left in legacy `dtos`: `AuditLogFilterDTO`, `AuditLogEntryDTO`, `AuditLogMapper` (shared with the stock-movement audit view). `StockMovementService` and `ActivityFeedService` no longer touch `AuditLogRepository`. `AuditLogDTOMapper` takes `@Lazy StockMovementService` to break the AuditLogService <-> StockMovementService bean cycle.
- Baseline: 13 reviewed `audit` edges added with justification; `archunit_store/0858803e-...` hand-renamed for the moved FQCNs; `archunit_store/c1d9f1c8-...` auto-pruned (moved classes left the legacy packages).
- Legacy `/api/audit-logs` resolves MAIN through `LegacyMainSiteContextResolver` and now carries `Deprecation`/`Link` headers (no Sunset). The stock audit dashboard route `/api/stock-movements/audit-log` was already MAIN-scoped and has a v1 equivalent (`.../inventory/movements`).
- Stale-display job fixed (see messaging notes above).

## Scope decision (2026-10-15)

Notifications: only `site_id` on rows. No Slack per-site routing, no config, no custom messages, no v1 notification route. Web notification UI is archived/dead (`/notifications` redirects to a deprecated analytics tab; only unused `use-dashboard-metrics` reads it) - deletion is parked.

## Notification site stamping (done, test-first)

- RED then GREEN: `services/messaging-service/tests/test_notification_site.py` (6 tests failed before the change) - envelope now keeps `site_id`, `NormalizedEvent.site_id`, `create_notification(site_id=)` writes it in both INSERT variants (NULL when unknown), alert notifications carry the event's site.
- Java: `Notification.siteId` (nullable column); stamped by `EasyPostWebhookService` (shipment site), `ShipmentService` damaged/completed (shipment site), `MachineDisplayService.emitDisplayNotification` (site of the machine location). RED then GREEN: `deliveredNotificationCarriesTheShipmentsSite`, `setDisplay_stampsTheMachinesSiteOnTheNotification` (failed to compile before, pass after).
- Not stamped: `DevSeedController` (dev only), stale-display job (see risk 1), `NotificationService.createNotification` does not default a site.
- Environment note: a stale `target/` produced a bogus `VerifyError`/`cannot access` failures; `./mvnw -o clean test-compile` fixed it.

## T-1 inventory (2026-10-15)

Writers
- `NotificationService.createNotification` (inventory-service, then `broadcastNotificationCreated()` with no site).
- messaging-service `supabase_repo.create_notification` raw `INSERT INTO notifications` (two variants, dedupe on `dedupe_key`); no site column written. Event envelopes already carry `site_id`, so the source is available there.
- messaging-service `create_review` raw `INSERT INTO reviews ... ON CONFLICT (external_id, review_date)`; attributed via `get_review_site_id()` (`REVIEW_SITE_CODE`, default MAIN) but never persisted on the `reviews` row. `review_daily_counts` already carries `site_id` (V75/V76/V79).
- `AuditLogService.createAuditLog` x2 and `createShipmentEvent`; callers: `StockMovementService` (6), `KujiBoxService` (11 via private wrapper), `MachineDisplayService` (7), `ShipmentService` (5). None of these pass a site into the log; audit broadcasts at `AuditLogService` lines ~128/174 are site-less, while `KujiBoxService` already passes `siteId`.

Readers
- `NotificationController` `/api/notifications` (list, unread, by id, read, mark-read, read-all, delete, search, resolve, unresolve, counts): none site-scoped; `recipientId` is a client query parameter; `read-all` and the counts touch every row.
- `ReviewController` `/api/reviews` (tracked users, all users, user, tracking update, monthly summaries, user reviews, user stats).
- `AuditLogController` `/api/audit-logs` and `StockMovementController` `/api/stock-movements/audit-log` (stock audit dashboard).
- `ActivityFeedService` (analytics slice) reads notifications and audit logs.
- `DevSeedController` writes all three tables.

Web consumers
- `lib/api/notifications.ts` (`/api/notifications`), `lib/api/reviews.ts` (`/api/reviews`), `lib/api/stock-movements.ts` (`/api/audit-logs`, `/api/stock-movements/audit-log`; comment already notes no v1 audit route), hooks `use-notifications`, `use-audit-log`, `use-top-reviewers`, `components/reviews/*`, `team/_components/tab-reviews.tsx`; realtime keys `notifications`, `notifications/counts`, `audit-log*` in `use-realtime-broadcast.ts`.

Existing v1 pattern to follow: `/api/v1/sites/{siteId}/...` controllers guarded by `SiteAccessAuthorizationFilter` (kuji, lootbox, displays, shipments).

## T-2 site sources and policy (assumptions)

- `audit_logs.site_id`: stock movements of the log (when they agree on one site) -> shipment site -> site of the primary location -> MAIN. New writes take the authorized site from the caller, never infer it.
- `notifications.site_id`: site of the location behind `inventory_id` -> MAIN. New writes take `site_id` from the event envelope (messaging) or the caller (inventory-service).
- `reviews.site_id`: MAIN for history, because one Google place is attributed to `REVIEW_SITE_CODE` and users belong to many sites. New rows take the same attributed site as `review_daily_counts`.
- `review_daily_counts`: already satisfied by V75-V79 (site_id NOT NULL, site/user/date unique); AC-1 needs no further migration for it.
- System-admin reporting: no cross-site read on the new v1 routes by default; any cross-site view must be a separate explicit route, decided at T-4.
- Broadcast notifications (`recipient_id IS NULL`) are visible to members of the notification's site only.

## T-3 migrations

- V81 adds nullable `site_id` to `notifications`, `reviews`, `audit_logs`.
- V82 backfills per T-2; requires a MAIN row, idempotent.
- V83 adds `(site_id, created_at DESC)` / `(site_id, recipient_id, created_at DESC)` / `(site_id, user_id, review_date DESC)` / audit `(site_id, created_at DESC)` indexes CONCURRENTLY (paired `.conf`, `executeInTransaction=false`).
- No FK/NOT NULL/uniqueness change yet: constrain is deferred to its own release, after the messaging service writes `site_id`. Existing `(external_id, review_date)` conflict target is left untouched because the messaging INSERT depends on it.
- Evidence: `NotificationsReviewsAuditSiteMigrationIT` (4 tests: audit inheritance order incl. mixed-site movements, notification/review fallback, idempotent re-run and missing-MAIN failure, index repeatability/validity) - 4/4 pass. Note: the migrations were written before the test, so there was no RED run; the test was checked against the finished files.
- Not verified: V81-V83 against a full production dump or the Supabase schema (constraint/column names for `location_inventory`, `shipments.site_id` assumed from earlier migrations).

## Test plan

- AC-1: PostgreSQL migration/uniqueness/backfill checks.
- AC-2: MockMvc/Testcontainers foreign-site and system-admin policy tests.
- AC-3: Event/realtime payload tests.
- AC-4–5: ArchUnit, OpenAPI/client and web tests.

## Post-review fixes (mixed-site audit history, kuji notifications)

- V82 now raises when an audit log groups movements of more than one site, instead of assigning it to MAIN. The linked production database had none (checked with a read-only count).
- Audit detail and the activity feed load or keep only movements of the caller's site (`findByAuditLogIdWithItem(auditLogId, siteId)`; feed drops movements whose site differs). Kuji undo uses the same scoped query.
- Kuji draw and undo notifications carry `box.getSiteId()`.
- Tests added first: migration refusal IT, detail IT with a foreign-site movement, activity-feed mixed-log test, kuji draw/undo notification site assertions. Backend now 1218 tests, 0 failures.
- Dev seed (`DevSeedController.seedReviews`) built `Review` rows without `siteId`; fixed with a test. Backend now 1219 tests. Added `scripts/dev-up.sh` (compose up, `setup_dev_db.py`, seeds each table only while empty).

## NotificationController removed

- Deleted `NotificationController`, its security IT, `NotificationMapper`, the response and filter DTOs, `NotificationSpecifications`, and every `NotificationService` method except `createNotification`. `NotificationRepository` keeps only the two site-scoped finders the activity feed uses.
- Web: deleted `lib/api/notifications.ts`, `use-notifications.ts`, `use-dashboard-metrics.ts` (no consumers), `components/notifications`, and the archived notifications tab.
- Test first: RBACAlignmentIT now asserts the retired endpoints return 404 (failed with 200 before the delete). OpenAPI and client regenerated; ArchUnit frozen store pruned.
- Backend 1191 tests, 0 failures; web tsc clean, vitest 483 passed.
