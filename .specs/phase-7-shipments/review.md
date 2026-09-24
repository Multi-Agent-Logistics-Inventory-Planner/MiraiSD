# Review

## Scope reviewed

T-5 (v1 site-scoped shipment/tracking routes, EasyPost webhook v1 alias, legacy route
deprecation, OpenAPI/API-client regeneration, `apps/web` migration, `DevSeedController` site
fix) and T-6 (architecture-edge confirmation, native validation sweep) of phase-7-shipments.
Reviewed as two independent passes — a Standards pass (`mirai-spring-reviewer` for
`services/inventory-service`, `mirai-next-reviewer` for `apps/web`) — plus a Spec pass against
`spec.md`'s AC-1 through AC-6, run separately so neither polluted the other.

## Findings

- [Standards, backend] `/api/v1/webhooks/easypost` was published in the OpenAPI contract but
  `auth.SecurityConfig`/`config.OpenApiConfig` only allowlisted the legacy `/api/webhooks/**`
  prefix, so every unauthenticated EasyPost delivery to the v1 path fell through to
  `anyRequest().authenticated()` and got a 401 before HMAC validation ever ran — **act on**
  (AC-5). Fixed: both allowlists now include `/api/v1/webhooks/**`; contract/client regenerated;
  new `EasyPostWebhookControllerSecurityIT` proves neither path returns 401 with no
  `Authorization` header.
- [Standards, backend] No test proved a v1 shipment route rejects a shipment ID that belongs to a
  real, different site (only "no membership at the path's site" was covered) — **act on** (AC-2).
  Fixed: new `SiteShipmentControllerCrossSiteAccessIT` (GET/DELETE/receive against a foreign-site
  shipment id, all 404, DELETE additionally proven non-destructive). PUT/PATCH/undo-receive route
  through the identical site-scoped lookup per source inspection but aren't separately covered —
  **consider**, left as a residual risk rather than blocking.
- [Standards, backend] The two "v1 routes don't carry legacy deprecation headers" tests hit a site
  the admin persona had no membership in, so `SiteAccessAuthorizationFilter` rejected them with
  403 before the handler ran — the assertion passed but never proved a *successful* v1 response
  lacks the headers — **act on** (test-quality gap in AC-5 evidence). Fixed: both tests now grant
  real membership at the target site first; the shipments one additionally asserts `200`.
- [Standards, backend] `EasyPostWebhookServiceTest`/`LegacyShipmentDeprecationHeadersIT`'s tracking
  test make a real network call to the EasyPost API (predates this slice; `TrackingService` has no
  test seam) — **consider**, not fixed. No assertion depends on EasyPost's response, only that no
  401/403/legacy-header leaks through, so this doesn't invalidate the fixed tests' actual claims.
- [Standards, backend] `sortBy` flows unvalidated into `Sort.by(sortBy)` on the v1 list route,
  same as the legacy route — **consider**, not new, not fixed (matches existing behavior, not a
  regression this slice introduced).
- [Standards, web] The supply-chain dashboard card and `useDashboardMetrics`'s shipment count
  could render a false "empty"/zero state during the `useCurrentSite()` fetch, because
  `skipToken`-gated queries report `isLoading=false` while the site is still resolving, and the
  migrated component/hook didn't fold `isSiteLoading` into their own loading flags — **act on**
  (correctness regression risk from this diff). Fixed in `connected-cards.tsx` and
  `use-dashboard-metrics.ts`.
- [Standards, web] No unit tests were added for `site-shipments.ts`/`site-tracking.ts` or the
  rewritten hooks, unlike the `locations.test.ts`/`site-inventory.test.ts` precedent for
  path/param correctness — **consider**, not fixed in this pass (time-boxed; the reviewer's own
  independent script-based route/contract cross-check substitutes as one-time verification, but
  doesn't replace a regression-guarding test). Recorded as a residual risk.
- [Standards, web] The new `mutate`/`mutateAsync` wrappers in `use-shipment-mutations.ts` dropped
  the second `options` argument the `useMutation` API accepts, unlike `use-location-mutations.ts`'s
  precedent — **act on** (latent bug: a future `mutate(vars, { onSuccess })` caller's callback
  would silently never run). First fix pass only corrected create/update; a follow-up pass of
  this same review incorrectly recorded all seven as fixed when delete/receive/undo-item/
  undo-items were still unfixed. Now genuinely fixed: all seven wrappers (create, update, delete,
  receive, undo-item, undo-items, override) forward `options`, verified by a new
  `use-shipment-mutations.test.tsx` that asserts a per-call `onSuccess` runs for every one of the
  seven hooks (this test caught that `override` itself had also been missed in both earlier
  passes, since a mocked `mutationFn` resolves regardless of whether `options` reaches it).
- [Standards, web] Stale-cache advisory: shipment mutations invalidate `["shipments", siteId]` but
  not `["shipmentsByProduct", siteId, ...]` or `["shipment", siteId, id]` — **consider**, not
  fixed (predates this diff under the old unscoped keys; `useShipment` has no current caller and
  the product modal remounts on open, so impact is low).
- [Standards, web] Using `./client` (plain REST helpers) instead of `webApiClient`
  (generated-schema client) in the two new site-scoped modules deviates from
  `site-inventory.ts`'s pattern — flagged for evaluation, not raised as an objection; the
  Standards (web) reviewer itself assessed this as a reasonable judgment call. **Reviewed,
  accepted**: `lib/api/locations.ts` already establishes the same `./client` pattern for
  site-scoped v1 routes, the v1 shipment/tracking DTOs are byte-identical to the legacy ones
  (nothing to slim/map), and the reviewer's own route-by-route cross-check against
  `openapi.json` found every path correct.
- [Spec] AC-5 ("v1 routes and tracking/webhook contracts are migrated with web consumers; legacy
  routes are protected and deprecated until their documented removal gate") — confirmed satisfied
  once the webhook allowlist finding above was fixed; legacy routes carry `Deprecation`/`Link`
  headers and `docs/baseline/api-v1-map.md` records the phase-7 entry and reuses the existing
  compatibility-removal-gate section.
- [Spec] AC-6 ("...authorization, concurrency/idempotency, contract and module-boundary tests
  prove the behavior") — authorization now has direct v1-route coverage (role gates, foreign-site
  membership rejection, and foreign-site-shipment-id rejection); contract and module-boundary
  (ArchUnit) tests pass; concurrency/idempotency test coverage predates this slice and is
  untouched by it, so no new claim is made there.
- [Spec] AC-1 through AC-4 — unaffected by T-5/T-6's changes; already satisfied by T-3/T-4 and
  re-confirmed by this pass's full native-validation sweep (no regression found).

## Residual risk

- PUT/PATCH `/status`/`undo-receive` v1 routes' foreign-site rejection is proven by source
  inspection (identical `getShipmentById(siteId, id)` lookup path) but not by a dedicated runtime
  test.
- No unit test coverage for `lib/api/site-shipments.ts`/`site-tracking.ts` or most of the
  rewritten query hooks beyond the full `tsc`/`vitest` suite passing.
  `use-shipment-mutations.test.tsx` now covers option-forwarding for all seven mutation hooks
  (added while fixing the finding above), but path-builder/query-param correctness for the two
  new `lib/api` modules and `skipToken` gating on the query hooks remain untested.
- `EasyPostWebhookServiceTest`/tracking-lookup tests depend on a live outbound call to the
  EasyPost API (`APIKEY.INACTIVE` observed in this environment); assertions don't depend on its
  response, but this makes those tests slower and non-hermetic.
- `sortBy` on both legacy and v1 shipment list routes is unvalidated against `Sort.by(...)` and
  could 500 on an unknown property name — pre-existing, not introduced or worsened here.
- Stale-cache gaps on `shipmentsByProduct`/`shipment` query keys after a shipment mutation predate
  this diff and are low-impact given current callers.
- The two intentionally deferred design items from T-4 (`ShipmentMapper`/DTOs staying in legacy
  packages; the V70 pre-constrain verification query, not yet runnable since nothing in this
  migration chain is deployed) remain open, matching precedent, and do not block this slice.
