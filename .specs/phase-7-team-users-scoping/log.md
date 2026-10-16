# Phase 7 - Team users scoping log

## Start

- Slice created after phase-7-notifications-reviews-audit (commit 5acac82). Dev DB inspected: `/api/users` ignored site; `last-audits` came from `StockMovementRepository.findLatestMovementTimestampsByActor` over all sites.

## Implementation

- Tests first (all seen failing): `LastActorActivityAdapterTest` and `UserServiceTest` (site-scoped port and member filtering, compile red), `SiteUserControllerAuthorizationIT` (members list, cross-site last audits, non-member caller, legacy deprecation headers), `site-users.test.ts`, `use-users.test.ts`, `use-team-data.test.ts` (including loading while the site resolves).
- Backend: `LastActorActivityPort` now takes the site; `StockMovementRepository.findLatestMovementTimestampsByActor(siteId)` and `findTopBySite_IdAndActorIdOrderByAtDesc` replace the bulk unscoped query. `UserService.getSiteLastAuditDates` keeps only active members. New `SiteUserController`. Legacy list, `last-audits` and `{id}/last-audit` resolve MAIN with `LegacyUserDeprecation` headers; `UserService.getAllUsers` removed. `findTopByActorIdOrderByAtDesc` stays because a legacy-route IT uses it.
- `UserControllerSecurityIT` list tests now use tokens with MAIN membership, since the legacy list requires it.
- Web: `lib/api/site-users.ts`; `useUsers` and `useTeamData` gated on the current site with site-qualified keys; `getUsers`, `getAllLastAudits` and `getUserLastAudit` removed from `lib/api/users.ts`.
- Contract and client regenerated; `api-v1-map.md` updated. No new ArchUnit edges.
- Results: backend `./mvnw -o clean test -Dtest='*'` 1195 tests, 0 failures; web `tsc --noEmit` clean, vitest 489 passed.

## Review fixes

- Added the missing cross-site legacy test, the EMPLOYEE and ASSISTANT_MANAGER test, a 403 assertion for non-members, and a site-change refetch test.
- Deprecation headers moved to `LegacyUserDeprecationFilter` (GET list, `last-audits`, `{id}/last-audit` only) so errors carry them; seen failing first.
- Removed `findTopByActorIdOrderByAtDesc`; the legacy IT now uses the site-scoped finder.
- Final: backend 1198 tests (1199 after the auth-failure header fix below), 0 failures; web tsc clean, vitest 490 passed.

## External review fix: deprecation headers on authentication failures

- Finding (P2): the filter ran after Spring Security, so an unauthenticated `GET /api/users` returned 401 without `Deprecation`/`Link` (AC-3).
- Test first: `legacyRoutes_deprecationHeadersOnAuthFailures` (no token, invalid token) failed, then passed after `LegacyUserDeprecationConfig` set `Ordered.HIGHEST_PRECEDENCE`.
- Follow-up, not changed here: the shipment, catalog, machine-display, audit, inventory and review deprecation configs register their filters without an order and have the same gap on 401 responses.
- Follow-up done: the six other legacy deprecation configs (shipment, catalog, machine-display, audit, inventory with both registrations, review) now set `Ordered.HIGHEST_PRECEDENCE`. Each IT gained an auth-failure test that failed first. Full backend suite: 1205 tests, 0 failures.
