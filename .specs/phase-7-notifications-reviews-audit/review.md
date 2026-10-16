# Review

## Scope reviewed

Full implementation diff on `refactor/phase-7` (uncommitted), 2026-10-16: migrations V81-V83, the `audit` and `reviews` module moves, site stamping at every audit/notification writer, v1 and legacy controllers, messaging-service writes, web consumers, regenerated contracts, ArchUnit baseline. Three independent passes: Spring/Standards (backend), Next/web, security. Reviewer reports are model output; each finding below was checked against the code before acting.

## Findings

- [Spring, Required R1] Contract test `tests/contracts` asserted the messaging envelope does not model `site_id` -- **fixed** (test now asserts the new contract; 32 passed).
- [Spring, Required R2] A deactivated member stayed in the monthly summary but 404'd on stats/reviews -- **fixed**: read paths accept any membership row (`UserService.findSiteMember`), tracking updates still require an active one; `SiteReviewControllerAuthorizationIT.formerMember_isReadableButNotEditable`.
- [Spring, Required R3] `AuditLogService` injected another module's `UserRepository` -- **fixed**: uses `UserService.findById`; tests updated.
- [Spring, Required R4] V82 sent non-MAIN shipment-tracking notifications to MAIN -- **fixed**: backfill by `metadata.shipment_id` before the fallback; migration IT covers it. Stale-display (`machine_id`) already covered.
- [Spring, Required R5] Audit detail mapping path (the `@Lazy` mapper) was not exercised -- **fixed**: the detail IT seeds a movement and asserts it is returned.
- [Spring, Advisory A1] NULL-site rows are invisible to scoped reads -- **recorded**: the V84 constrain migration must first repeat V82's body (idempotent) before `SET NOT NULL`.
- [Spring, Advisory A2] `AuditLogDTOMapper` uses `@Lazy StockMovementService` only for `resolveLocationCode` -- **deferred**: move that lookup to `InventoryQueries`/`LocationService` to drop the cycle.
- [Spring, Advisory A3] Misplaced javadoc and a spec-reference comment -- **fixed**.
- [Web, Required R1] Reviews tab and Manage Employees dialog spun forever when no site resolved -- **fixed** with a failing-first component test (`manage-employees-dialog.test.tsx`) and a "no site available" state; the reviews tab clears its loading flag once the site settles.
- [Web, Advisory A1/A2] Hooks return empty data without an error when the site is unresolved; `isError`/`status` are not overridden -- **deferred**: matches the existing `use-kuji-box` convention; revisit for all site hooks together.
- [Web, Advisory A3] No component tests for `tab-reviews`, `user-stats-dialog`, `coins-tab` -- **deferred** (typecheck covers the call sites).
- [Security, Medium] Review tracking fields are global user attributes, so a manager of site B could set a name that matches another site's employee -- **mitigated**: the messaging lookup now requires an active membership of the review site (`test_review_site_membership.py`); per-site tracking remains out of scope.
- [Security, Low] Alert dedupe key ignored the site -- **fixed**. Backfill other-site history under MAIN -- **fixed** (R4). Unbounded page size -- **fixed** (`PageLimits`, 200). No validation on the tracking body -- **fixed** (`@Size`, `@Valid`). Unused unscoped audit repository methods -- **removed**. Email visible to employees on `/users/tracked` -- **deferred**.
- [Security] `.env` is not tracked or ever committed -- no action; rotate the tokens if they were shared.

## Residual risk

- Messaging deploy ordering: the review/notification INSERTs name `site_id`; Flyway must run before the new messaging image (the deploy gate in `production-platform.md` already orders it). `create_review` swallows INSERT errors, so a reversed order would silently drop reviews.
- V81-V83 were not run against a production dump; the stale-display query was not run against the live schema.
- The independent PR gate remains the authoritative proof.
