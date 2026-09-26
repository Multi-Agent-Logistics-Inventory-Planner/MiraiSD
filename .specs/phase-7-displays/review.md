# Review

## Scope reviewed

Full phase-7-displays diff (T-1 through T-4, uncommitted): schema expand/backfill/verify
(V72-V74), the `displays` module move (T-3), the v1 site-scoped routes/AC-3 authorization/web
migration (T-4). Two independent passes ran in parallel: a Standards + Spec backend pass
(mirai-spring-reviewer, verified by running a throwaway Testcontainers probe against 9 real
cross-site/authorization cases) and a Standards + Spec web pass (mirai-next-reviewer, verified by
running a throwaway vitest probe against the rewritten hooks). Both probes were deleted after use;
findings below are backed by real command output, not just log.md's self-report.

A second pass, requested directly by the user after the first pass's fixes were independently
verified and `validation.md` was first written, reviewed the fixes themselves (not the whole diff
again) by reading the changed source directly and re-running focused tests. See "Findings — second
pass" below.

## Findings — first pass (2026-09-24)

All "act on" items below are now **fixed**, independently reverified by the coordinating session
(not taken on the fixing agent's own report), and recorded in `log.md`'s "Review findings and
fixes"/"Second review pass fixes" sections. Outcome noted inline; `consider`/`dismissed` items are
unchanged (no action required).

- [Standards] `SiteMachineDisplayController` accepts a client-supplied `actorId` (request body
  field and `@RequestParam`) and persists it verbatim into `machine_display.actor_id`, audit logs,
  stock movements and notifications, instead of deriving it from
  `AuthorizedSiteContextHolder.require().backendUserId()` the way `SiteShipmentController` and
  `SiteInventoryMutationController` already do — **act on** (Blocker; violates
  `docs/specs/authentication-and-authorization.md` §4, actor identity must come from the
  authenticated principal, not the client). **Outcome: fixed.** Every v1 mutation handler now
  overrides the client value with `contextActorId()`; the two DELETE routes drop the `actorId`
  query parameter from their method signature entirely. Verified by direct source read of
  `SiteMachineDisplayController`, and by `SiteMachineDisplayControllerAuthorizationIT` (both a POST
  spoofing case and, after the second pass, a DELETE spoofing case asserting the real audit-log
  actor).
- [Spec] AC-3's own test evidence (`SiteMachineDisplayControllerCrossSiteAccessIT`) covers 3 of 19
  routes, has no same-site control case, and mislabels a DELETE-returns-400 case as "GET is 404" —
  **act on** (AC-3 evidence gap; verified behavior itself is correct per the reviewer's 9-case
  probe, but the committed test suite doesn't prove it). **Outcome: fixed.** Expanded to 9 cases
  (same-site control, foreign-machine/foreign-display-id rejection across
  set/batch-swap/swap/renew/batch-clear/by-id); mislabeled test renamed to match its actual
  assertion.
- [Spec] No PostgreSQL migration test exercises V72/V73 against a real Postgres (shipments has
  `ShipmentSiteBackfillIT` as precedent) — **act on** (AC-2/AC-6 evidence gap). **Outcome: fixed.**
  New `MachineDisplaySiteBackfillIT` (4 cases: non-MAIN resolution, orphan-to-MAIN fallback,
  fail-loud with no MAIN site, idempotent re-run).
- [Spec] No test exercises the legacy `/api/machine-displays` deprecation headers, or confirms the
  v1 routes don't get them (shipments has `LegacyShipmentDeprecationHeadersIT` as precedent) —
  **act on** (AC-5 evidence gap). **Outcome: fixed.** New `LegacyMachineDisplayDeprecationHeadersIT`
  (3 cases: bare path, sub-path, v1 routes unaffected).
- [Standards] `set` (POST) and `getCurrentDisplay` (GET) on the v1 controller throw
  `IncorrectResultSizeDataAccessException` (500) for any machine already showing more than one
  product, because both route through a single-result finder inherited unchanged from the legacy
  (already-`@deprecated`) controller — **act on** (real bug, reproduced by the reviewer's probe;
  copied into the new v1 contract rather than left isolated in legacy code). **Outcome: fixed** for
  `getCurrentDisplay` (site-scoped list finder + `findFirst()` instead of a single-result finder).
  `set`'s 500 was fixed the same way in this pass, but a *second*, related bug in `set` (returning
  the wrong display's DTO on success, rather than 500ing) survived this pass undetected and was
  only caught by the second review pass below.
- [Spec] AC-4 asked for the dependency baseline to shrink; it grew net (one edge removed, nine
  added) — **act on** as a documentation correction, not a code change. **Outcome: fixed** (in
  `log.md`): every new edge confirmed real and necessary; the edge-count growth (vs. AC-4's
  "shrunk" wording) is recorded as reviewed and accepted, with `displays -> services` and
  `models -> displays` flagged as temporary.
- [Standards] `use-machine-displays.test.ts`'s "stays disabled" case only asserts `isLoading`,
  which passes against both the old and new implementation — **act on**. **Outcome: fixed.**
- [Spec] No test file exists for the fully-rewritten `use-machine-display-mutations.ts` (9 hooks,
  `use-shipment-mutations.test.tsx` is the precedent) — **act on** (AC-5 evidence gap). **Outcome:
  fixed.** New `use-machine-display-mutations.test.tsx` (4 cases); its `useClearMachineDisplayMutation`
  assertion was itself corrected in the second pass after the `actorId` parameter was dropped.
- [Standards] `use-machine-display-mutations.ts` invalidates the unscoped `["machine-displays"]`
  key rather than `["machine-displays", siteId]` — **act on** (small fix, matters once a second
  site is live; not a correctness bug today with one site). **Outcome: fixed**, verified by direct
  grep of all 9 `invalidateQueries` call sites.
- [Standards] Comment headers in `site-machine-displays.ts` and the mutations file cite
  `.specs/phase-7-displays T-4, AC-3/AC-5` — **act on** (repo rule: comments state the invariant
  directly, not a pointer to a spec document). **Outcome: fixed.**
- [Standards] V73's backfill derives site only from `location_id`; a hypothetical row with a null
  `location_id` but a `machine_id` pointing elsewhere would fall back to MAIN — **consider**
  (defensive-only; zero such rows exist in production per T-1's verification)
- [Standards] Rows written during a deploy overlap (old app version, before V72/V73 ship) get a
  NULL `site_id` and fail closed off the v1 routes — **consider** (re-run the idempotent backfill
  UPDATE as the first statement of the future constrain migration, before adding NOT NULL)
- [Standards] `DisplayOperations.requireActive` doesn't check the display belongs to the kuji box's
  own site/machine — **dismissed for this slice** (pre-existing behavior from kuji's old direct
  repository access, not a regression; flagged for the kuji slice)
- [Standards] `batchClearDisplays` runs one `findByIdAndSite_Id` query per id (N+1) — **consider**
  (correctness is fine; a single `findAllByIdInAndSite_Id` would be one query)
- [Standards] `longest-running-displays-card.tsx`/`product-modal.tsx` show an empty state rather
  than an error state when the site query itself fails — **dismissed for this slice** (identical
  pre-existing pattern in `use-shipments.ts`; not a regression introduced here)
- [Standards] Legacy route/service/entity move preserves behavior byte-for-byte except for the
  intentional `.site(...)` write — **dismissed** (verified by direct diff)
- [Standards] Exception messages, `requireActive`/`endIfActive`/`getReference` semantics, kuji
  transaction propagation, `DisplayQueries`/`DisplayOperations` caller set, V74's `CONCURRENTLY` +
  `.conf` shape, and the deferred constrain step — **dismissed** (all verified correct)

## Findings — second pass (2026-09-25)

Requested directly by the user after the first pass's fixes were independently verified and
`validation.md` was first written. This pass reviewed the *fixes themselves*, not the whole diff
again, and found:

- [Standards] `SiteMachineDisplayController.setSiteDisplay` (v1 POST) discarded the just-saved
  `MachineDisplay` and re-fetched "the" active display for the machine via `getCurrentDisplay`,
  which (after the first pass's fix) returns the oldest active display first. On a machine already
  showing product A, adding product B returned a 201 whose body was still product A's display —
  the first pass's fix eliminated the 500 but left this quieter wrong-data bug in place, and its
  own test only asserted a count of active displays, not which one the response body named —
  **act on** (real bug). **Outcome: fixed.** Added `MachineDisplayService.toResponseDTO` (maps an
  already-loaded entity directly, no re-query); the controller now maps the entity `setDisplay`
  returned instead of re-querying. Test strengthened to assert the response body's `$.productId`
  matches the product just added.
- [Standards] The DELETE actor-spoofing regression test (added in the first pass) asserted
  `MachineDisplay.getActorId()`, but clearing a display only sets `endedAt` — it never rewrites
  that row's `actorId` (set once, at creation). The operation's own actor is recorded on the
  `DISPLAY_REMOVED` audit log instead, so the test would keep passing even if `clearDisplay`
  started trusting a spoofed `actorId` query param again — **act on** (ineffective regression
  test). **Outcome: fixed**, and the fix itself surfaced a second, unrelated bug in the *test*: the
  first fix attempt asserted `AuditLog.getActorId()`, which failed at runtime because that field is
  a read-only shadow column (`insertable=false, updatable=false`) that `AuditLogService` never
  populates directly (only the `user` association is set) and a same-persistence-context repository
  read doesn't re-hydrate. Corrected to assert `getUser().getId()`, confirmed passing.
- [Standards] `apps/web/src/lib/api/site-machine-displays.ts`'s `clearSiteMachineDisplay`/
  `clearSiteDisplayById` and their mutation hooks still accepted and transmitted an `actorId` even
  though the v1 DELETE routes never read one after the first pass's fix (removed from the
  controller's method signature entirely) and the regenerated OpenAPI contract no longer declares
  it — not exploitable (Spring ignored the extra query param), but a misleading client contract —
  **act on** (dead parameter). **Outcome: fixed.** Removed from both API functions, both hooks'
  variable types, and `location-detail-sheet.tsx`'s one call site; left alone on the other hooks
  (set/swap/batch/renew) where `actorId` remains a real, server-overwritten DTO field, not a
  removed-from-the-contract one. Fixing this surfaced one more incidental break: the new
  `use-machine-display-mutations.test.tsx` (from the first pass) asserted a fourth `undefined`
  argument to `clearSiteMachineDisplay` that no longer exists post-fix; corrected.
- [Standards] Three SDD record staleness issues, found by the user's own direct review of the
  record rather than the code — **act on**. **Outcome: fixed**, this edit: (1) `review.md` (this
  file) presented already-fixed first-pass findings as still-open "act on" items with no
  disposition — restructured into first-pass/second-pass sections with outcomes recorded inline;
  (2) `log.md` said constrain "is T-5's job, not done yet," contradicting spec.md's T-2 (which
  defers constrain to a later release) and T-5 (ArchUnit review + validation only, no mention of
  constrain) — corrected in three places in `log.md`; (3) `validation.md`'s AC-5 evidence said "no
  component changes needed" for the web migration, which was true of T-4 itself but stale after
  this second pass's `location-detail-sheet.tsx` edit — qualified to distinguish the T-4 claim from
  the unrelated second-pass follow-on fix.

## Residual risk

- Dev-only seed displays (`DevSeedController`) keep a NULL `site_id` and won't appear on v1 routes
  until that dev-only gap is separately addressed (not a production risk).
- The legacy `/api/machine-displays` routes remain fully unscoped by design until a later removal
  gate.
- `DisplayQueries` analytics reads stay cross-site (unscoped) until the analytics slice adds
  site-scoped variants — this was an explicit, reviewed T-3 design decision, not new risk from T-4.
