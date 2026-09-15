# Review

## 6d P1/P2 findings (external review) — 2026-09-14

**Scope reviewed:** an external review of the already-committed 6d checkpoint (backend `accd2b0`,
web `232a8ca`, both of which had already been through and closed their own independent-review fix
rounds — see the "6d backend slice" and "6d web slice" entries below) surfaced two additional,
real findings: one backend concurrency bug (P1) and one frontend error-swallowing bug (P2).
Neither was previously flagged by this checkpoint's own independent reviews. Both addressed this
session.

### Act on

1. **[P1, Correctness] Concurrent site-scoped transfers can lose source debits — fixed.**
   `StockMovementService.requireInventoryBelongsToSite(UUID siteId, UUID inventoryId)` (called
   from both site-scoped `transferInventory(UUID siteId, ...)` and `batchTransferInventory(UUID
   siteId, ...)`) confirmed site membership via
   `LocationInventoryRepository.findByIdAndSite_Id`, an entity-returning, JOIN-FETCH'd query, run
   *before* `planTransfers`/`lockPlannedRows` ever locked the row. This populated the transaction's
   Hibernate persistence context with an unlocked pre-lock snapshot of the row's quantity; the
   later "locked" read (`findById`/`findAllByIdWithGraph`) does not refresh an already-managed
   entity's scalar state, so the transfer computed its debit from the stale, pre-lock quantity even
   though a real Postgres row lock was, by then, correctly held. Two concurrent site-scoped
   transfers sharing one source row could each read the same unlocked quantity, and whichever
   committed last silently overwrote the other's debit — a lost update. Traced every call site of
   `requireInventoryBelongsToSite`, `findByIdAndSite_Id`, and both site-scoped overloads before
   fixing; confirmed the account exactly as described. **Fixed:** added a scalar-only
   `LocationInventoryRepository.existsByIdAndSite_Id(UUID id, UUID siteId)` (a `boolean`
   projection, never populates the persistence context) and switched
   `requireInventoryBelongsToSite` to use it instead of the entity-returning method. The site check
   now runs before any entity-level touch of the row, matching this file's own established
   principle (already followed correctly by `batchAdjustInventory`, which checks site membership
   via `Location`, a different entity, never `LocationInventory`). New test:
   `StockMovementServiceSiteScopedConcurrentSourceCheckRaceIT` (real Postgres, two genuinely
   concurrent site-scoped `transferInventory` calls sharing one source row, forced to interleave
   around the site-check/lock boundary via an externally held `SELECT ... FOR UPDATE` and
   `pg_stat_activity`-polled lock-wait confirmation, not timing). Revert-verified: with the fix
   reverted, the new test failed with the lost update reproduced exactly as predicted (expected
   `100 - 30 - 47 = 23`, got `53` — only one debit applied); with the fix restored, it passes.
2. **[P2, Correctness] Failed inventory reads render as "no inventory" instead of an error —
   fixed.** `ProductModal` (`apps/web/src/components/products/product-modal.tsx`) destructured
   only `data`/`isLoading` from `useSiteProductInventoryEntries`, discarding the `error` field the
   hook already returns. On a failed request, `isLoading` becomes `false` and `data` stays
   `undefined`, so the derived `locations` array becomes empty and the table's empty-state branch
   ("No inventory at any location") rendered identically to a genuine zero-inventory product, even
   while the `Current Stock (N)` header (a different, still-successful query) showed a nonzero
   total directly above it — no indication anything failed, no way to retry. **Fixed:**
   `ProductModal` now destructures `error`/`refetch` from the hook (added `refetch` to
   `useSiteProductInventoryEntries`'s return, wrapping `query.refetch`) and the inventory table's
   conditional render gained an explicit error branch, checked before the empty-state branch:
   "Couldn't load inventory" plus a Retry button calling `refetch()` — visually and textually
   distinct from the empty state, following this codebase's existing destructive-state text
   convention (`location-detail-sheet.tsx`'s `inventoryQuery.isError` message) while adding the
   retry affordance the finding specifically asked for, since the hook already exposed
   `query.refetch` cheaply. Also decided and implemented: the Adjust and Transfer buttons (both
   desktop and mobile) are now `disabled` while the inventory read has failed, with a title tooltip
   explaining why, so a user can't act on a table that's actually just erroring rather than
   genuinely empty; Transfer's click handler also gained a distinct destructive toast branch for
   the (normally unreachable, since the button is disabled) case where it fires anyway. New test
   file `product-modal.test.tsx` (3 cases): error state renders the retry affordance and not the
   empty state (with the nonzero header total still visible), Adjust/Transfer are disabled during
   the error, and the genuine-empty-state case (`data: {entries: []}`, no error) still renders "No
   inventory at any location" unchanged — a before/after regression guard. Revert-verified: with
   the fix reverted, the error-state and disabled-buttons tests both failed for the right reason
   (empty state rendered instead of the error text; buttons not disabled); with the fix restored,
   all three pass.

**Disposition:** both findings fixed and re-verified, each with a revert-verified new test.
`packages/contracts`/`packages/api-client` were not touched (no contract/route shape change,
consistent with the task's explicit scope limit). No other part of either checkpoint slice's
already-committed scope was touched. The working tree is left uncommitted for the coordinating
session, per instruction.

## 6d web slice (T-6d-1..T-6d-14) — independent review, 2026-09-14

**Scope reviewed:** the full 6d web-adoption slice (T-6d-1 through T-6d-14) as committed to the
working tree ahead of PR — the typed v1 inventory client (`lib/api/site-inventory.ts`), the
shared join helper (`lib/api/inventory-join.ts`), site-qualified query keys and realtime
invalidation across every touched hook, the Products-list/detail quantity restoration (T-6d-4),
`useLocationInventory`/`useNotAssignedInventory`'s NOT_ASSIGNED resolution (T-6d-9), and
`product-form.tsx`'s initial-stock creation flow. Reviewed against spec.md's AC-5, AC-6 (rendered
coverage and site-switch isolation in particular), and the R-9 web-side follow-through.

This is the independent review's disposition list, transcribed verbatim ahead of the fix session
described in "Review-driven fix: 6d web slice findings" below, with findings 1/2/4/5/6/7/8's
dispositions updated to reflect that session's fixes (marked **fixed**), finding 3 marked
**resolved** (documentation-only, per the review's own instruction), and finding 6's numbering
here (`getLocationsWithCounts`/broadcast-channel egress) explicitly out of scope, per the review
itself, deferred to 6e.

### Act on

1. **[Spec] `product-form.tsx` silently drops initial stock when `siteId` is unresolved — fixed.**
   Around line 440-447, `siteId` was a conjunct of the guard deciding whether to create initial
   stock; when falsy, the whole block (including its error toast) was skipped, so the user saw
   only "Product created" with their typed stock silently gone. **Fixed:** `siteId` is now
   checked *inside* the block, with a destructive toast ("Product created, but stock was not
   added" / "No active site.") on the missing-site path, matching the shape of the existing
   stock-creation-failure toast. New test:
   `product-form.test.tsx`'s "surfaces a destructive toast and never calls the create-inventory
   API when no site is active" (and a companion positive-path test proving the create call still
   fires normally when a site is active).

2. **[Standards] The `setQueriesData({queryKey:["products"]})` collision fix only excluded the
   sibling site-scoped key, not per-product `children`/`with-children` keys — fixed.**
   `legacy-products-query-filter.ts`'s predicate (`query.queryKey[2] !== "site"`) excluded only
   `["products", siteId, "site"]`; `["products", productId, "children"]` and
   `["products", productId, "with-children"]` (from `useProductChildren`/`useProductWithChildren`
   in `hooks/queries/use-products.ts`) still matched by prefix, so
   `surgicalProductUpdate`'s `index === -1` branch could append an unrelated product into those
   per-product lists on a realtime event. **Fixed:** narrowed the predicate to
   `query.queryKey.length === 2`, matching the precedent already established in this codebase at
   `use-realtime-broadcast.ts`. Verified the `["products", productId]` detail-entry shape (also
   length 2) stays safe because every write site already guards with
   `Array.isArray(oldData)` before writing, and a bare product object is never an array. New
   test: `use-realtime-inventory.test.ts`'s "never appends into a different product's
   children/with-children cache entry" — seeds both unrelated caches, fires a same-site event,
   and asserts both are byte-for-byte unchanged.

3. **[Spec] NOT_ASSIGNED row-hiding scale check not yet run against real data — resolved via
   production-data check, not a code change.** The coordinating session ran the review's
   specified query directly against the real production database:
   ```sql
   SELECT sl.site_id, count(*) AS hidden_rows
   FROM location_inventory li
   JOIN locations l ON l.id = li.location_id
   JOIN storage_locations sl ON sl.id = l.storage_location_id
   JOIN products p ON p.id = li.product_id
   WHERE sl.code = 'NOT_ASSIGNED'
     AND (p.parent_id IS NOT NULL OR p.kuji_type = 'CUSTOM')
   GROUP BY sl.site_id;
   ```
   Result: **zero rows returned** — no production data is hidden by the NOT_ASSIGNED filter
   change (T-6d-9's kuji-child/CUSTOM-parent exclusion) today. See validation.md's matching entry
   for the recorded evidence. No code change was needed or made for this finding.

4. **[Spec] AC-6's rendered-test list not fully satisfied — fixed.** No `render()`-level test
   drove `AdjustStockDialog`'s or `TransferStockDialog`'s actual submit workflow (only
   hook/client-layer tests), and no test forced an out-of-order ("late old-site result")
   resolution proving site-qualified keys actually reject a stale response. **Fixed:** added
   `components/stock/__tests__/adjust-stock-dialog.test.tsx` (2 cases — a subtract-adjustment
   submit asserting the mutation receives the correct payload and a success toast, and the
   "update an existing row" delta-computation path asserting a computed `+3` delta, not the raw
   absolute value 8, reaches the audited batch-adjust mutation) and
   `components/stock/__tests__/transfer-stock-dialog.test.tsx` (1 case — fills source/destination/
   quantity and asserts the batch-transfer mutation fires with the correct payload). Added a
   late-old-site-result test to `hooks/queries/__tests__/use-site-product-inventory.test.ts`
   ("rejects a late-resolving totals response from the previous site after switching sites") —
   site A's totals request is left unresolved, the hook is rerendered as if the site switched to
   B, B's totals resolve and render, and only then is A's request resolved late; the rendered
   data is asserted to stay B's, proving the site-qualified `["inventoryTotals", siteId]` key (not
   just structural argument) actually isolates the late response into its own orphaned cache
   entry.

### Consider (fixed, cheap per the review's own note)

5. **`useSiteProductInventory` fabricated `totalQuantity: 0`/`status: "out-of-stock"` while
   totals were still loading — fixed.** `hooks/queries/use-product-inventory.ts` computed
   `qty = total?.totalQuantity ?? 0` before checking whether `totalsQuery` itself had resolved.
   Masked on the Products page by its own loading skeleton, but not masked in
   `location-detail-sheet.tsx`'s embedded `ProductModal`, which has no loading gate around this
   hook. **Fixed:** the memo now returns `null` until `totalsQuery.data !== undefined` (once
   `siteId` is known), matching the pattern `useLocationInventory` already used. New test:
   "stays null while totals are still loading, never fabricating totalQuantity: 0".
6. **`useNotAssignedInventory` lost its `staleTime: 30_000` — fixed.** Rewritten as a thin wrapper
   over `useLocationInventory` (which defaults to the app-wide `staleTime: 0`), causing a double
   refetch (location resolution + entries) on every mount/focus. **Fixed:** `staleTime: 30_000`
   set on both queries inside `hooks/queries/use-location-inventory.ts`.
7. **`hooks/mutations/use-stock-mutations.ts` had a `void locationType;` statement keeping an
   unused parameter alive — fixed.** Verified `locationType` was genuinely unused inside
   `invalidateStockQueries` (the site-wide invalidation below it never referenced it) and dropped
   both the parameter and the one caller that passed it.
8. **`lib/api/site-inventory.ts`'s `getSiteProductInventory`/`getSiteMovements` dereferenced
   `data.productId`/`data.content` with no guard — fixed.** Unlike `getSiteInventoryTotals`/
   `getSiteLocationInventory` (which use `data ?? []`), an ok-but-empty body (`unwrapGeneratedResponse`
   can return `undefined`) would throw a raw `TypeError` instead of the project's own
   `GeneratedApiError`. **Fixed:** both functions now throw `GeneratedApiError` on an empty body,
   consistent with the other two. New tests: one per function in `site-inventory.test.ts`.

### Dismissed / out of scope (unchanged, not re-litigated this session)

- Idempotency-key generation, no client-supplied `actorId`, the `Pageable` serializer fix, the
  three dead-code removals (`useUpdateInventoryMutation`, `use-not-assigned-mutations.ts`,
  `cachedNALocationId`), the Kuji gate, RBAC, and consumer return-shape compatibility — all
  settled correct by the original 6d web implementation session and the independent review;
  out of scope for this fix pass per the task's own instruction.
- `getLocationsWithCounts`/the org-wide broadcast-channel egress (T-6d-12's recorded residual) —
  explicitly 6e scope per the review itself, not touched this session.

## Residual risk

- The one-NOT_ASSIGNED-location-per-site invariant (carried from the 6d backend slice's finding 3
  below) is still not schema-enforced; `resolveSiteLocationId`'s `.find() ?? locations[0]`
  fallback would silently pick an arbitrary location if a site ever had more than one. Unchanged
  by this session — monitored debt, not blocking.
- `LocationInventoryRepository.findByStorageLocation_Id`'s missing root-product filter (used only
  by the still-legacy `getStorageLocationInventory`, with no current caller per T-6d-9's own
  note) remains a live trap for any future caller that bypasses the already-migrated
  `useLocationInventory`/`useNotAssignedInventory`. Unchanged by this session.
- Movement history (T-6d-10) still has no rendered UI consumer to exercise end-to-end; the
  data-layer/hook is correctly site-scoped and tested, but this is recorded rather than silently
  treated as full AC-6 coverage for that one piece.

## 6d backend slice (T-6d-be-1..T-6d-be-8) — independent review, 2026-09-14

**Scope reviewed:** the full 6d backend slice (T-6d-be-1 through T-6d-be-8) as committed to the
working tree ahead of PR — site-scoped `LocationInventoryService.addInventory`/`deleteInventory`
overloads, the new v1 create/delete/batch-transfer routes on `SiteInventoryMutationController`,
recursive `actorId` idempotency-fingerprint stripping, the `@Size(max = 50)` batch cap, legacy
deprecation-header coverage for the sites-shaped inventory routes, and the regenerated
`packages/contracts/openapi.json`/`packages/api-client`. Reviewed against spec.md's AC-3, AC-4,
AC-5 and the backend-side portion of R-9's resolution.

This is the independent review's disposition list, transcribed verbatim from the review that ran
ahead of this fix session, with findings 1/2/3's dispositions updated below to reflect the
review-driven fix that landed in this same session (findings 1/2/3 are marked **fixed**, not just
"act on" — finding 4, "review.md/validation.md don't exist," is closed by this entry and
validation.md's matching entry existing).

### Act on

1. **[Standards] Delete route's idempotency fingerprint is non-deterministic across JVM restarts — fixed.**
   `fingerprint(Map.of("locationId", locationId, "inventoryId", inventoryId))` used
   `java.util.Map.of`'s randomized-per-JVM iteration order (seeded from `System.nanoTime()`), so
   the same logical delete command fingerprinted differently across JVM restarts/redeploys — a
   legitimate retry inside the idempotency table's 7-day retention window got a spurious 409
   instead of an idempotent 204. Proven by the reviewer hashing the same map six times across JVM
   runs and getting different SHA-256 values.
   **Fixed:** replaced the `Map.of(...)` carrier with a package-private
   `DeleteInventoryFingerprintKey(UUID locationId, UUID inventoryId)` record — a record's component
   order is fixed by its declaration, so Jackson serializes it identically on every run. Checked
   the rest of the file for the same `Map.of`-as-fingerprint-carrier pattern: this was the only
   instance. New test:
   `SiteInventoryMutationControllerFingerprintTest.deleteFingerprint_forFixedLocationAndInventoryId_matchesPinnedHash`
   pins the fingerprint of a fixed `(locationId, inventoryId)` pair to a hard-coded SHA-256
   literal, plus `deleteFingerprint_isStableAcrossRepeatedCalls_forTheSameLogicalCommand` — a test
   that only re-derived the hash the same way the code does would not have caught this class of
   bug, since it would drift together with the code on every JVM run; the pinned literal makes an
   ordering regression structurally visible.

2. **[Standards] Create route's fingerprint omits `locationId` — fixed.**
   `fingerprint(request)` was body-only; `locationId` is a path variable, not part of the request
   body. The same `Idempotency-Key` + identical body + a *different* `locationId` therefore matched
   on `commandType`+`requestFingerprint` in `CommandIdempotencyService.executeIdempotent` and
   silently returned the first location's stored 201 response without creating anything at the
   second location — a silent no-op masquerading as success.
   **Fixed:** added a package-private `CreateInventoryFingerprintKey(UUID locationId,
   CreateLocationInventoryRequestDTO request)` record and fingerprint that instead of the bare
   request, using the same deterministic-record approach as finding 1. New tests:
   `SiteInventoryMutationControllerFingerprintTest.createFingerprint_includesLocationId_soSameBodyDifferentLocationHashesDifferently`/
   `.createFingerprint_forFixedLocationAndBody_matchesPinnedHash` (unit level), and
   `SiteInventoryMutationControllerSecurityIT.createItem_sameKeySameBodyDifferentLocation_returns409AndDoesNotReuseFirstLocationResponse`
   (HTTP level: same `Idempotency-Key`, identical body, two different `locationId` path values —
   second call asserted 409, and the second location's `location_inventory` row asserted absent).
   Confirmed this HTTP test actually catches the bug by temporarily reverting the fix and
   re-running it: it failed with `Status expected:<409> but was:<201>` against the pre-fix code,
   then passed once the fix was restored.

3. **[Spec] T-6d-be-6's "assumption outcome: confirmed safe" is not supported by the test written — Javadoc corrected; disposition changed to open risk, not blocking.**
   The design's caveat was "exactly one **location** under NOT_ASSIGNED per site."
   `secondNotAssignedStorageLocationForSameSite_violatesUniqueConstraint` only proves
   `storage_locations(site_id, code)` uniqueness, not `locations`-row uniqueness beneath it —
   `locations` only carries `UNIQUE(storage_location_id, location_code)`
   (`infra/init-db/20-unified-locations.sql`), so `LocationService.createLocation` could add a
   second `locations` row under one site's NOT_ASSIGNED storage location without any DB-level
   rejection, and `LocationService.getNotAssignedLocation` resolves it via an unordered
   `.stream().findFirst()`.
   **Fixed to the extent resolvable this session:** corrected `NotAssignedInventoryReadParityIT`'s
   Javadoc so it no longer claims to prove "the web's single-NA-location assumption is enforced" —
   it now states plainly what it actually proves (storage-location-level uniqueness only) and what
   it does not (locations-row uniqueness). Ran a read-only query against the project's real
   Supabase database (session had live `mcp__supabase` access) —
   see validation.md's "Finding 3 evidence" for the exact query and result. **Real-data finding:**
   of the two sites in the live database, one (`MAIN`) has a NOT_ASSIGNED storage location with
   exactly one `locations` row beneath it; the other (`SECOND`) has no NOT_ASSIGNED storage
   location at all yet (not yet seeded). No site was found with more than one NA `locations` row —
   today's real data does not violate the assumption. This is **empirical, not schema-enforced**:
   nothing in the schema stops a future write from creating a second `locations` row under a site's
   NOT_ASSIGNED storage location. Recorded as an explicit, named open risk in log.md's Current
   handoff and validation.md, not silently marked closed — see those files for the
   escalate-or-proceed disposition on T-6d-9.

4. **[Standards] `review.md`/`validation.md` didn't exist for 6d — fixed.** This entry and
   validation.md's matching "Review-driven fix: 6d backend slice findings" entry close this gap for
   the 6d checkpoint, per spec.md's per-checkpoint Full-tier requirement.

### Consider (not acted on this session — recorded as-is from the independent review, unchanged)

5. Audit rows are never asserted in the new atomicity tests — AC-4 names audit explicitly;
   `createInventoryWithTracking`/`removeInventoryWithTracking` do build an `AuditLog`, add one
   assertion per success case.
6. `assertThatThrownBy(...).isInstanceOf(RuntimeException.class)` in new atomicity cases is too
   loose — tighten to the specific exception types; HTTP ITs already assert real status codes so
   this is minor.
7. OpenAPI documents 200/401/403 for all five v1 mutation routes, not actual 201/204/409/404/400 —
   pre-existing from 6c, but the regenerated TS client now types create as 200-with-body vs actual
   201, and the web slice is about to consume these types. Add `@ResponseStatus`/`@ApiResponses`.
8. Fingerprint algorithm's serialization approach changed shape (convertValue→LinkedHashMap→serialize
   vs valueToTree→serialize) — equivalent bytes for these bodies but not guaranteed for every type;
   worth a one-line deployment note about idempotency rows spanning a deploy.
9. No test for a cross-site destination on the new batch route specifically (`requireSameSite` does
   hold per code read, and NOT_ASSIGNED fallback resolves default site's NA location which fails
   closed but with a confusing error for non-MAIN sites) — pre-existing from 6c, one IT would pin
   it.

### Dismissed (checked, no action)

- Recursive `actorId` strip is correct for all fingerprinted shapes in this service, no collateral
  stripping of legitimate fields.
- Foreign-site rejection is query-level (JPQL `site.id` predicate), not a post-load filter,
  IT-confirmed.
- Actor identity is principal-derived on all three new handlers, never client-supplied.
- Atomicity holds — `executeIdempotent` is `@Transactional` wrapping the command supplier; rollback
  proven by test.
- No new lock-order path — batch route is a pure wrapper over already-proven
  `batchTransferInventory`.
- `@Size(max = 50)` reaches both the new v1 route and the legacy route (shared DTO).
- Deprecation filter coverage confirmed correct for every sites-module route (no false positives)
  and every legacy inventory route (no false negatives).
- Site stamping can't diverge — `createInventoryWithTracking` sets site from
  `location.storageLocation.site`.
- OpenAPI diff matches claim: 3 paths added (not 4 as originally estimated — a counting
  correction), 0 removed, 1 schema added, exactly one existing-schema delta
  (`BatchTransferInventoryRequestDTO.maxItems`).
- All new/changed test classes independently re-run, 0 failures (SecurityIT 23, AtomicityIT 15,
  FingerprintTest 4, SiteScopedQueriesIT 12, NotAssignedReadParityIT 2, DeprecationHeadersIT 6,
  LocationInventoryServiceTest 18).

## Residual risk

- ITs run on H2, not Testcontainers Postgres, contrary to what CLAUDE.md implies for this project
  generally — real-PG concurrency/constraint semantics for the new routes aren't proven by this
  slice. (The review-driven fix session separately confirmed finding 3's real-data state via a
  direct query against the project's actual Supabase database, not through the H2-backed IT suite —
  see validation.md.)
- The 50-element batch cap bounds request size, not lock footprint (up to ~100 distinct
  (location,product) rows could still lock in one transaction on the 512MB single node) — no
  measurement taken, AC-8 measurement is 6e's job.
- `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` remain failing under the `*IT`
  sweep — pre-existing debt, degrades that sweep as a regression signal. Reconfirmed unchanged by
  this session's full `*IT` rerun (see validation.md).
- Legacy `PUT /api/locations/{id}/inventory/{inventoryId}` (silent untracked absolute quantity set)
  is still live, now merely marked deprecated; removal is 6e's job.
- **New, from finding 3's real-data check:** the one-NA-location-per-site invariant is empirically
  true today but not schema-enforced — `LocationService.createLocation` could add a second
  `locations` row under a site's NOT_ASSIGNED storage location with no DB-level rejection, and
  `LocationService.getNotAssignedLocation`'s unordered `.stream().findFirst()` would then silently
  pick one of several. Not fixed in this session (out of scope — the finding asked for real-data
  verification and Javadoc correction, not a schema change); recorded as an explicit open risk for
  T-6d-9 in log.md's Current handoff.

**Verdict (independent review, as given): Approve after fixes.** Findings 1/2 were real
idempotency-contract defects, now fixed and re-verified same session (see validation.md). Finding 3
is settled to the extent real data allows — no violation found today, but the invariant remains
unenforced; recorded as an open risk rather than closed, per the review's own instruction not to
mark it "confirmed safe" without evidence. Finding 4 is closed by this entry. No P1 in this
project's established sense — no tenant leak, no data corruption, no contract break.

## Review-driven fix: 6c checkpoint P1 findings (independent review) — 2026-09-13

An independent review of the committed 6c slice (`34dcfea`) found two P1s the self-review pass
above missed. Both confirmed against the actual code before fixing, both fixed and re-verified
same session.

- **[Standards] P1 — v1 mutation routes trusted a client-supplied `actorId`.**
  `SiteInventoryMutationController` passed the raw request body into
  `StockMovementService.batchAdjustInventory(siteId, request)`/`transferInventory(siteId,
  request)`, and the service persisted `request.getActorId()` into `AuditLog`/`StockMovement` rows
  unchanged. An authenticated caller could name any UUID as the actor, or omit one, directly
  violating docs/specs/authentication-and-authorization.md#4 ("Mutation actor identity is derived
  from this principal... compatibility DTO fields are ignored or verified during the transition").
  **Fixed:** the three site-scoped `StockMovementService` overloads
  (`batchAdjustInventory(UUID siteId, UUID actorId, ...)`,
  `transferInventory(UUID siteId, UUID actorId, ...)`,
  `batchTransferInventory(UUID siteId, UUID actorId, ...)`) now take the actor id as an explicit
  parameter and overwrite the request's (possibly spoofed) `actorId` with it before delegating to
  the un-scoped path; the controller passes `context.backendUserId()`. Only these three v1-only
  overloads changed signature — legacy callers (`StockMovementController`) use the un-scoped
  overloads and are untouched, so this is scoped to the new v1 surface, not a blanket fix of the
  legacy `actorId` compatibility field.
  - Fixing this surfaced a second, self-inflicted bug: the idempotency fingerprint was originally
    computed by serializing the whole request including `actorId`, and mutating that same field
    later (inside the guarded command) made a same-object replay's fingerprint diverge from the
    one recorded at the first call, spuriously returning a fingerprint-conflict 409 — caught by
    the existing `adjust_replayWithSameKeyAndBody_doesNotRerunTheCommandOrDuplicateEffects` test
    failing after the actorId fix landed. Fixed by excluding `actorId` from the fingerprint's
    input entirely: it carries no request identity of its own once the service always overwrites
    it, so a client-declared (but now-ignored) actor value must not be able to affect idempotency
    matching either.
  - New tests: `SiteInventoryMutationControllerAtomicityIT
    .adjust_clientSuppliedActorIdIsIgnoredInFavorOfTheAuthenticatedPrincipal`/
    `.transfer_clientSuppliedActorIdIsIgnoredInFavorOfTheAuthenticatedPrincipal` — both submit a
    request with a spoofed `actorId` different from the authenticated principal's id and assert
    the persisted `StockMovement.actorId` is the principal's id, not the spoofed one.
- **[Standards] P1 — `GET .../movements?itemId=...` hid null-site legacy movements, contradicting
  Q-6c-5.** The per-product branch used `findByItem_IdAndSite_IdOrderByAtDesc` (a strict
  `site_id = :siteId` predicate, T-6c-1's general tenant-isolation primitive); only the
  no-`itemId` audit-log branch used `StockMovementSpecifications.withSiteFilter`'s "site or null"
  contract. Q-6c-5 requires both branches of this one endpoint to include and label unattributed
  rows, not silently exclude them during the pre-backfill compatibility window. **Fixed:** added
  `StockMovementRepository.findByItem_IdAndSiteOrUnknownOrderByAtDesc` (a `LEFT JOIN`-based JPQL
  query matching `site_id = :siteId OR site_id IS NULL`, foreign-site rows still excluded), and
  `InventoryQueries.findMovementHistoryBySite` now calls it instead of the strict method. The
  original strict method is kept unchanged and undeleted — it remains the primitive
  `LocationInventorySiteScopedQueriesIT` pins for general tenant isolation; only the v1 movements
  endpoint's own query call site changed.
  - New tests: `LocationInventorySiteScopedQueriesIT
    .stockMovementHistory_findByItem_IdAndSiteOrUnknownOrderByAtDesc_includesNullSiteExcludesForeignSite`
    (repository-level: one row per site plus a null-site row; asserts the null-site row is
    included and the foreign-site row is not) and `SiteInventoryControllerSecurityIT
    .movements_byItemId_includesNullSiteRowLabeledUnknown` (HTTP-level: asserts the response
    includes both rows and the null-site one carries `siteAttribution: "UNKNOWN"`).

**Verification:** `./mvnw -q clean test-compile` clean; targeted runs of
`SiteInventoryMutationControllerAtomicityIT` (8/8, including both new actor-spoofing tests),
`SiteInventoryControllerSecurityIT`+`SiteInventoryMutationControllerSecurityIT`+
`LocationInventorySiteScopedQueriesIT`+`StockMovementServiceSameSiteTransferTest` (all green),
`SiteInventoryMutationCrossSiteDestinationIT` (real Postgres, still green) all pass;
`ArchitectureTest` clean with no `archunit_store` diff; full `./mvnw clean test` exit 0; full
`./mvnw test -Dtest='*IT'` — 478 tests (up from 474 by 4 new IT methods), 8 failures, all still
exactly the pre-existing `AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` debt
(confirmed via `./mvnw test -Dtest='*IT,!AnalyticsControllerSecurityIT,!ForecastControllerSecurityIT'`
exit 0, 470/470); `OpenApiContractExportTest` — no diff to `packages/contracts/openapi.json` (both
fixes are internal service-method-signature changes, no route/DTO shape change).

**Disposition:** both P1s fixed and re-verified; no other production-code changes made. The 6c
checkpoint's disposition (approved, T-6c-11..T-6c-17) stands revised by this entry, not
superseded — the underlying task-level work was sound, these were review-caught defects in that
work, now closed.

## 6c — Scoped inventory backend (T-6c-11..T-6c-17 checkpoint) — 2026-09-13

**Methodology note:** this checkpoint's review was performed as a critical self-review pass over
the completed diff (T-6c-11 through T-6c-17), not a separately-invoked review agent — the session
that implemented this slice ran as a background fork with no ability to spawn a reviewer subagent.
It follows the same two-pass discipline (Standards, then Spec, synthesized after) the earlier
checkpoints used, and is held to the same bar: findings below were only accepted after tracing the
actual code path and, where a fix was made, re-running the affected tests against real Postgres to
confirm the fix closes the gap rather than papering over it. This is recorded as a residual-risk
caveat, not hidden — the PR gate remains the authoritative independent proof per AGENTS.md.

**Scope reviewed:** T-6c-11 (v1 read routes), T-6c-12 (v1 mutation routes, idempotency wiring),
T-6c-13 (legacy deprecation), T-6c-14 (contract/client regeneration), T-6c-15 (stock-state
compatibility guard), T-6c-16 (boundary/doc updates), T-6c-17 (AC-8 after-measurement), against
spec.md's AC-1, AC-3, AC-4, AC-5, and the portions of AC-7/AC-8 this slice's backend work touches.

### Findings

- **[Standards] act on — cross-site destination-location gap untested.** T-6c-12's site-scoped
  `StockMovementService.transferInventory(siteId, request)` validates only the *source* inventory's
  site before delegating to the un-scoped method; an implicit `destinationLocationId` at a
  different site was not separately covered by any new test, even though the underlying
  `requireSameSite` check (T-6c-4) still catches it. Tracing the code found that check runs *after*
  `LocationInventoryRepository.insertLocationInventoryIfAbsent` has already speculatively inserted
  the destination row — a real write, rolled back by the enclosing `@Transactional`, not "no write
  at all" in the strictest sense. **Fixed:** added
  `SiteInventoryMutationCrossSiteDestinationIT.transfer_foreignSiteImplicitDestinationLocation_
  rollsBackTheSpeculativeInsert`, proving no permanent `location_inventory` row survives at the
  foreign destination and no `StockMovement`/idempotency row is left behind.
- **[Standards] act on — that same new test was first written as a false positive.** Initially
  added inside `SiteInventoryMutationControllerAtomicityIT` (H2 `test` profile), it "passed," but
  for the wrong reason: H2 rejects `insertLocationInventoryIfAbsent`'s native
  `INSERT ... ON CONFLICT` with a syntax error *before* `requireSameSite` ever runs, so the test
  never exercised the intended rejection path. Caught by asking why it passed, not just that it
  did. **Fixed:** moved to its own class, `SiteInventoryMutationCrossSiteDestinationIT`, extending
  `BaseKafkaIntegrationTest` (real Postgres) — it now genuinely exercises `requireSameSite`.
- **[Standards] act on — unsafe blanket-table assertion under the shared `*IT` Postgres instance.**
  The same new test's first version asserted
  `stockMovementRepository.findAll()).isEmpty()`/`eventOutboxRepository.findAll()).isEmpty()`,
  which surfaced only when the *whole* `*IT` sweep ran (not in isolation): a residual row from a
  same-JVM-run class executing immediately before it made the table non-empty, and AssertJ's own
  failure-message rendering then threw `LazyInitializationException` trying to render a detached
  `AuditLog` association, masking the real cause. **Fixed:** scoped the assertion to this test's
  own product id (`stockMovementRepository.findByItem_IdOrderByAtDesc`), matching the tracked-id
  isolation discipline `InventoryEgressBaselineIT`'s own review fix already established for
  exactly this shared-container hazard.
- **[Standards] act on — `@SneakyThrows` introduced a pattern with no other precedent in this
  codebase.** `SiteInventoryMutationController.fingerprint()` used Lombok's `@SneakyThrows` around
  `ObjectMapper.writeValueAsBytes`/`MessageDigest.getInstance`; grepping confirmed it is used
  nowhere else in `inventory-service`, while `CommandIdempotencyService.serialize`/`deserialize`
  (the class this very method feeds) explicitly catches and wraps in `IllegalStateException`.
  **Fixed:** replaced with an explicit try/catch matching that existing convention.
- **[Spec] No blocking findings.** AC-3 (trusted site context, foreign-site rejection, idempotent
  retries tested) and AC-5 (v1 DTOs, slim/batched totals, zero-stock correctness, contract
  regeneration, legacy compatibility with no unintended break) are satisfied for this slice's
  scope. AC-4's envelope/atomicity requirements are unaffected by this slice except for confirming
  the idempotency wiring integrates correctly through the v1 mutation path (proven by
  `SiteInventoryMutationControllerAtomicityIT`/`...SecurityIT`); AC-4's Kafka partition-key cutover
  (Q-6c-4) remains explicitly open, not claimed complete here. AC-6/AC-7's web/realtime halves
  remain 6d/6e's, per the task list's own scope note — T-6c-17's numbers are backend-side evidence
  feeding those later checkpoints, not a claim that they are closed.
- **[Spec] Consider — recorded, not a blocker:** `InventoryTotalsRepository
  .findAllInventoryTotals()`'s native SQL is order-fragile under the shared H2 `*IT` datasource
  (a `ClassCastException` casting a joined UUID column, confirmed pre-existing and unrelated to
  this slice's changes — see log.md's "New open risk" entry). Carried forward as debt for a future
  session, same disposition as the pre-existing `AnalyticsControllerSecurityIT`/
  `ForecastControllerSecurityIT` debt this checkpoint did not introduce and does not fix.

### Disposition

**6c (T-6c-11..T-6c-17) approved**, all four "act on" findings fixed and re-verified in the same
session (see validation.md's 6c entry for the exact commands/results). No finding required a
production-code behavior change beyond the fingerprint-hashing refactor (style/consistency, not a
functional fix) — the two real gaps found (cross-site destination coverage, the false-positive
test) were test-coverage gaps, not production bugs; the underlying `requireSameSite`/idempotency
mechanisms were already correct. Residual risks: Q-6c-1 (production deploy/backfill status) and
Q-6c-4 (Kafka partition-key cutover) remain open, unchanged, both requiring separate authorization
before AC-4/AC-8 can be called complete against production; the `InventoryTotalsRepository`
H2 order-fragility above; R-9 (`LocationInventoryController` not moved into `inventory.api`)
remains open, out of 6c's scope. This disposition does not extend to 6d (web adoption) or 6e
(targeted refresh/exit proof).

## 6b re-review — 2026-09-10

Reviewed the complete uncommitted 6b slice, including V58–V60, all changed movement writers,
site persistence and display fixtures, against AC-2 and the rollout worksheet. Independent
Standards and Spec passes ran in parallel and were synthesized after completion.

- [Standards] The sign-aware V60 fix agrees with transfer withdrawal/deposit writers. Dev seed
  paths set a real site, display swap assertions distinguish the two sites, and all three
  prebuilt movement entry points reject a null site before saving. **No blocking findings.**
- [Spec] Nullable expansion and deferred enforcement preserve old-writer compatibility within
  the agreed 6b scope. Trusted context, scoped queries and event changes remain 6c work.
  **No blocking findings.**
- [Spec] **Consider — recorded:** the concurrent site test uses different locations/products;
  same-row contention and inventory-row site assertions remain unproven and are explicitly
  carried into 6c's concurrency gate. Existing concurrency behavior was not changed by 6b.

**Disposition: fixed 6b implementation approved for progression to 6c planning.** Focused native
validation passed 40 tests (validation.md). This review did not repeat the previously reported
332-test unit suite or 537-test IT sweep.

Residual risks: V58 SQL execution coverage remains absent; production schema/trigger checks,
backfill counts and MAIN-fallback assumptions still require operator verification. V61 must
remain a separate release after the writer prerequisite and verification are satisfied. This
disposition is not production deployment or constraint-enforcement approval.

## Scope reviewed

Planning structure only: one Phase 6 PR with five ordered implementation/review checkpoints,
the shared Full-tier record, acceptance gates and migration compatibility exception.

## Findings

2026-09-09: independent Standards and Spec passes completed in parallel. No planning findings.
Both plans and this record consistently express the agreed five checkpoints within one PR, permit
fix commits, retain the separate-release exception, and preserve the durable phase exit gates.
Caller/table inventory and rollout decisions are explicitly prerequisites, not claimed evidence.

Disposition: planning structure clear. Implementation review and checkpoint dispositions remain
pending; this review does not approve unspecified schema, contract or stock-state changes.

## Residual risk

Caller/table inventory and concrete tests remain to be specified before dependent implementation.
The migration release sequence and global stock-state compatibility remain unresolved; the spec
requires these decisions before the affected changes. No runtime readiness is claimed.

## 6a — Inventory module boundary (AC-1)

Independent implementation review ran across four rounds as 6a's tasks (T-0–T-8) landed; full
detail is in log.md's per-task Result subsections and its two "Review-driven fix" entries. This
section records the checkpoint's final disposition.

**Findings and disposition:**

- Round 1 (T-5, two P2s): (1) `InventoryQueries.findHistoryByItemId` returned the
  infrastructure-layer `StockMovementHistoryView` projection to callers outside `inventory`,
  which would have failed T-7's then-planned boundary rule. Fixed: a new application-owned
  `StockMovementHistoryEntry` record. (2) The 6a task list's own test plan required a real-transaction
  IT proving `applyDelta` preserves a caller-opened transaction (AC-1) before 6a closes; this had
  been prematurely deferred to 6c. Fixed: `InventoryOperationsCallerTransactionIT`. No
  business-behavior regression found in this round.
- Round 2 (T-5, one gap): the task list's delete-on-zero/find-or-create IT (the
  `ShipmentService`/`KujiBoxService` shared path) was still outstanding after round 1's fixes
  landed, and the log's "Current handoff" summary was stale. Fixed: `InventoryOperationsSharedCallerPathIT`,
  and the summary corrected.
- Round 3 (T-6–T-8 slice, two P3s): (1) `InventoryOperationsCallerSetTest` compared caller origins
  by simple name, which a same-named class in a different package could silently collapse into an
  existing entry. Fixed: switched to fully-qualified-name comparison. (2) This checkpoint's
  `review.md`/`validation.md` still held only planning-stage content, not 6a's actual results,
  ahead of checkpoint closure. Fixed: both files updated with 6a's commands, results and
  disposition (this entry; validation.md's "6a" section).

No runtime regression was found in any of the four rounds. The limited Kuji/display migration
scope (T-5, rewiring call sites without migrating those domains' packages) was confirmed supported
by the spec's own Delivery decisions.

**Disposition: 6a (AC-1) approved.** Inventory owns its entities/persistence/workflows; external
production callers use `InventoryOperations`/`InventoryQueries`, enforced live by ArchUnit
(`noProductionClassOutsideInventoryDependsOnInventoryInfrastructure`, T-7) and pinned by a
caller-set test; facades preserve caller transactions, proven by a real-database IT, not only
Mockito. `packages/contracts/openapi.json` stayed byte-identical through the T-6 endpoint moves.

**Carried-forward, not a 6a blocker:** R-9 (`LocationInventoryController`/`LocationInventoryMapper`/
`LocationInventoryResponseDTO`/`InventoryRequestDTO` not moved into `inventory.api` — their
dependency on `catalog.api` types would violate the live `modulesDoNotDependOnAnotherModulesApi`
rule; resolving it needs a catalog-owned application-layer product-summary contract, real design
work for a later checkpoint, not 6a's mechanical-move scope). R-4 through R-8 (see log.md) also
remain open, each flagged for its owning later phase/checkpoint. This approval does not extend to
6b's schema/rollout worksheet (AC-2) or any later checkpoint's acceptance criteria.
