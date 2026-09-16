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

## 6e — Targeted refresh and exit proof (AC-7/AC-8), self-review pending independent review

**Process note (read before the findings below):** this checkpoint's implementation was carried
out by an execution session running without access to spawn the `mirai-spring-reviewer`/
`mirai-next-reviewer` independent-review agents (a tooling constraint of that session, not a
process decision). Every prior checkpoint in this record (6a-6d) went through implement ->
independent agent review -> fix; 6e instead went through implement -> a rigorous self-review by
the same session, applying the same Standards/Spec checklist those agents use -> fix. The
findings below are real (each is revert-verified with a new failing-then-passing test) but this
is not a substitute for an independent reviewer with no stake in the implementation choices. The
coordinating session should decide whether to still run `mirai-spring-reviewer`/
`mirai-next-reviewer` over this checkpoint's diff before treating it as equivalent in rigor to
6a-6d, and should treat this section's disposition as provisional until that happens (or a
documented decision not to).

### Self-review findings and disposition

- **[Spec, backend] Missing test coverage for the productIds-threading fix (P2).** The commit
  that threaded `siteId`/`productIds` through `StockMovementService`'s five broadcast call sites
  had no test asserting the actual arguments passed to
  `SupabaseBroadcastService.broadcastInventoryUpdated` -- specifically no coverage of the exact
  gap the worksheet flagged (`batchAdjustInventory`/`batchTransferInventory` previously sent
  `itemId=null` despite already holding every affected product ID). **Fixed:** new
  `StockMovementServiceBroadcastArgsIT` (real Postgres, not `@Transactional`, so the after-commit
  dispatch actually fires) asserting both batch paths emit exactly one notification carrying the
  correct `siteId` and the full affected-product-ID set. Revert-verified: reverting the
  `productIds` argument to `null` made the batch-adjust case fail for the predicted reason
  (payload built with `productIds=null`); restored, both pass.
- **[Spec, web] Missing test coverage for the site-scoped locations-with-counts hooks (P2).**
  `useLocationsWithCounts`/`useAllLocationsWithCounts` had zero test coverage after their 6e
  site-scoping, including the worksheet's own required case ("site-switch test asserting counts
  rebind to the new site's values, distinct per-site fixtures"). **Fixed:** new
  `use-locations-with-counts.test.ts` (5 cases) covering the disabled states (no site, no real
  type, NOT_ASSIGNED), the site-switch rebind (a second site's fixture returns a different
  `totalQuantity`/`id`, confirmed the hook reflects it after switching, not the first site's
  cached data), and that `useAllLocationsWithCounts` shares the site-scoped key family.
- **[ArchUnit process near-miss, not a shipped bug]** The first attempt at the after-commit
  dispatch fix (`AfterCommitRunner`'s deferral logic) was written inline inside
  `SupabaseBroadcastService` as an anonymous `TransactionSynchronization` class. That failed
  `ArchitectureTest.legacyTechnicalLayerPackagesDoNotGrow` -- the anonymous class counted as a
  *new* class in the legacy `services` package, which the freezing rule forbids growing. Caught
  before commit by running the full suite; fixed by moving the logic into a new
  `shared.transaction.AfterCommitRunner` (see log.md's "T-6e-be-1..T-6e-be-7" entry for detail).
  Recorded here because it is exactly the class of mistake an independent reviewer's own
  `ArchitectureTest` run would have caught if this session's self-review had missed it.

No correctness, tenant-isolation, or security findings were found in the self-review beyond the
two coverage gaps above -- both were coverage gaps (the underlying implementation was already
correct in both cases), not behavioral bugs. In particular: the `SiteLocationAggregateController`/
`LocationAggregateRepository` site-scoping was checked against a real mismatched-`site_id`
`location_inventory` row (excluded correctly, per `LocationAggregateEgressIT`); the coalescing
buffer's site-switch behavior was checked against a live mid-flight switch (per
`use-realtime-broadcast.test.ts`'s dedicated case) and never contaminates the new site's cache;
and the `@Lazy` self-injection pattern in `SupabaseBroadcastService` was confirmed to work at
real Spring-context startup, not just in isolated unit tests, by the full IT suite's repeated
clean passes.

**Disposition (superseded by the independent review below): fixed 6e implementation,
self-reviewed with two real coverage gaps found and closed, both revert-verified.** Full
verification commands and counts from this pass are in validation.md's "6e" section.

## 6e -- independent review (mirai-spring-reviewer, mirai-next-reviewer): BLOCK, then fixed

The coordinating session ran both independent reviewers this checkpoint's earlier self-review
flagged as still owed. Both returned a **block** verdict. Every Blocker and Required finding was
fixed, each with a revert-verified test (the fix removed, the new test confirmed to fail for the
predicted reason, the fix restored, the test confirmed green). Advisories were fixed where cheap
and clear; the rest are noted with disposition below.

### Backend findings (mirai-spring-reviewer)

- **R-3 (material scope decision, user-directed, not a code fix): revert the legacy
  inventory-at-location route deletion; ship deprecation-only.** T-6e-be-9's deletion of
  `LocationInventoryController`/`LocationInventoryMapper`/`LocationInventoryResponseDTO`/
  `InventoryRequestDTO` violated the project's own documented compatibility-removal gate
  (`docs/baseline/api-v1-map.md`): removal requires access-log evidence of no legacy traffic plus
  a stabilization window on a *released* version, which is unsatisfiable when the routes'
  deprecation headers only just landed on this same unmerged branch. **Fixed:** restored all four
  classes, the four `LocationInventoryService` methods (except `updateInventoryQuantity`/the PUT
  route -- see A-5), `LocationInventoryRepository.findByStorageLocation_Id`, their tests
  (`LocationInventoryControllerSecurityIT` minus the by-product `ProductTests` nested class,
  which now permanently lives in `InventoryAggregateControllerSecurityIT` to avoid duplicate
  coverage across two files; the three `LocationInventoryServiceTest` nested classes; the
  T-6d-be-7 deprecation-header cases), and the restored-classes' `module-dependency-edges-baseline.txt`
  note -- from git history at `c8cfcd8^`. Unlike the original T-6d-be-6 version, the restored
  `findByStorageLocation_Id` now applies the same kuji-child/CUSTOM-kuji-parent filter its
  `findByLocation_Id` sibling always had, closing the trap for real rather than re-shipping it;
  `NotAssignedInventoryReadParityIT` rewritten again to prove read *parity* between the legacy
  and v1 reads (there is no longer a delta, since both filter identically). Regenerated
  contracts/client (3 paths, 2 schemas restored, confirmed by scripted set-diff). ArchUnit's
  frozen store regenerated back to its pre-deletion 7-line-larger content directly from git
  history (`git show c8cfcd8^:...`) rather than via the `allowStoreUpdate` flag, since that flag
  only prunes obsolete entries -- it cannot re-add violations that were previously frozen out;
  verified stable across two independent clean rebuilds with the flag back at `false`/`false`.
- **B-1 (Blocker): the legacy `GET /api/locations/with-counts` route
  (`LocationAggregateController`, a *different* legacy route from R-3's, in the `sites` module)
  still called the site-blind `findAllLocationsWithCounts()`/`findLocationsByTypeWithCounts(String)`
  directly, genuinely mixing every site's data.** **Fixed:** `LocationAggregateService`'s two
  deprecated no-arg methods now resolve the caller's default site (via `LocationService
  .getDefaultSiteId()`, the same pattern `LocationInventoryService.listInventoryByStorageLocationCode`
  already uses) and delegate to the already-built site-scoped overload -- the route's response
  shape is unchanged. New test in `LocationAggregateEgressIT`
  (`legacyRoute_serviceLayerNowResolvesDefaultSite_closingTheCrossSiteLeak`) calls the service
  method the controller actually calls (not just the already-scoped repository method) and
  confirms a 555-quantity row seeded under a different site never appears. Revert-verified.
- **R-4 (Required): a permanent real-Postgres IT proving a forced-rollback transaction emits zero
  broadcasts, plus proof `@Async` still applies through the `@Lazy` self-proxy.** The reviewer
  verified this by hand with a throwaway test context; no permanent test existed. **Fixed:** new
  `SupabaseBroadcastServiceAfterCommitIT` (package `com.mirai.inventoryservice.services`, same
  package as `SupabaseBroadcastService` so it can observe its package-private
  `dispatchInventoryUpdated` -- a Mockito spy on that method proved too fragile to stub reliably
  through the class's existing Spring AOP `@Async` CGLIB proxy, so both proofs instead observe
  the dispatch's own "Failed to send broadcast" WARN log line via a Logback `ListAppender`: a
  forced-rollback transaction (via `TransactionTemplate` + `setRollbackOnly()`) produces zero log
  lines within a real wait window; a committed transaction produces exactly one, on a thread
  different from the calling test thread.
- **A-5 (Advisory, fixed): `LocationInventoryService.updateInventoryQuantity` is an untracked,
  unaudited absolute-quantity setter with zero callers -- delete it.** Confirmed by re-reading
  the commit history: T-6e-be-9's own commit message claimed to delete this method, but the
  actual diff never touched it -- a real oversight, caught by this finding. **Fixed:** deleted
  the method (and, per the same rationale, did not restore its PUT route/test cases during the
  R-3 revert above -- the controller's PUT endpoint and its Javadoc now explicitly record that
  this stays removed even though the rest of the controller came back).
- **A-7 (Advisory, fixed): delete `apps/web/src/lib/api/locations.ts`'s dead `getLocationsWithCounts`
  function.** Zero callers post-T-6e-9; its own comment's "silently resolves to MAIN" claim was
  the exact claim B-1 found to be false. Deleted; the backend route stays present (deprecated) per
  R-3.
- **A-10 (Advisory, fixed): `AfterCommitRunner` should guard on `isSynchronizationActive() &&
  isActualTransactionActive()`, not synchronization-active alone.** Synchronization can be active
  without a real, commit-capable transaction underneath it. Fixed; updated
  `SupabaseBroadcastServiceTest`'s three "active transaction" cases to also call
  `TransactionSynchronizationManager.setActualTransactionActive(true)` (they were manually
  initializing synchronization without a real transaction manager, which would otherwise now
  dispatch immediately instead of deferring), and added a new case proving the guard itself:
  synchronization active but no actual transaction still dispatches immediately. Revert-verified.
- **A-6 (Advisory): re-check `docs/baseline/api-v1-map.md` for accuracy after the R-3 revert.**
  Checked -- the document was never edited during T-6e-be-9's deletion (confirmed via `git log`),
  so it already correctly described `LocationInventoryController` as present. No change needed.
- A-8, A-9, A-11, A-12: informational, no action needed (A-8 moot once R-2/B-1 were fixed; A-9/
  A-11/A-12 pre-existing or Phase-7 debt).

### Web findings (mirai-next-reviewer)

- **Blocker 1: `flushInventorySiteRefresh`'s merge never zeroed a requested product ID absent
  from the response, contradicting `InventoryQueries.java`'s documented batched-mode contract
  (absence means quantity 0).** A product that just went out of stock would show its old
  non-zero quantity forever. **Fixed:** every requested ID is now seeded to
  `{productId, totalQuantity: 0}` before applying whatever the response actually returned.
  Revert-verified with a new test (`inventory-refresh.test.ts`).
- **Blocker 2: the totals refresh added inside `onSuccess` in `use-stock-mutations.ts`/
  `use-location-mutations.ts` could reject, and an `onSuccess` rejection makes `mutateAsync`
  report the whole, already-committed mutation as failed** -- `adjust-stock-dialog.tsx` would
  show a false "Adjustment failed" toast, and a user retry would mint a fresh idempotency key,
  risking a real double-adjustment. **Fixed:** both call sites now `.catch()` a refresh failure
  into a plain `invalidateQueries` fallback instead of letting it propagate. Revert-verified
  (`use-stock-mutations.test.ts`); the identical fix in `use-location-mutations.ts` was applied
  by the same pattern but not independently test-covered -- that hook has no existing test file
  at all (a pre-existing gap, not introduced by this fix), and adding one from scratch was out of
  this fix round's scope. Noted, not silently skipped.
- **Required 4: the Kuji dialogs' legacy `useProductInventoryEntries` two-element key
  (`["productInventoryEntries", productId]`) was never invalidated by either the coalescing
  executor or the broadcast handler after 6e's site-scoping.** **Fixed:** both
  `flushInventorySiteRefresh` and the broadcast handler's direct per-product branch now also
  invalidate the legacy two-element key alongside the site-qualified one, kept until Kuji's own
  Phase 7 site migration. Revert-verified in both files.
- **Required 5: reconnect recovery only refreshed `inventoryTotals`, not AC-7's "full
  selected-site recovery."** **Fixed:** broadened to also invalidate `locationInventory`,
  `locationsWithCounts` (site-qualified) and `productInventoryEntries` (the bare prefix, since a
  recovery-path refresh is rare enough that the minor cross-site over-invalidation cost is
  acceptable there, unlike the regular coalesced path). Revert-verified.
- **Advisory 6 (fixed): attach `.catch()` to the fire-and-forget `flushInventorySiteRefresh`
  calls** in `use-coalesced-inventory-refresh.ts` and the reconnect-recovery call, so a failed
  refresh never becomes an unhandled promise rejection.
- **Advisory 7 (fixed): the broadcast effect's cleanup called `flushNow()` on unmount, contradicting
  `useCoalescedInventoryRefresh`'s own documented/tested "cancels without flushing" behavior.**
  Picked the coalescing hook's existing behavior (no flush on unmount) and removed the
  contradictory call; added a composed-hook unmount test in `use-realtime-broadcast.test.ts`
  (not just the isolated coalescing hook's existing test). Revert-verified.
- **Advisory 8 (fixed): guard the merge with a `lastUpdatedAt` comparison** so two overlapping
  flushes for the same product can't have an older response win just by resolving second. Keeps
  whichever of the cached vs. fetched row is newer; prefers the fetched candidate when either
  side lacks a timestamp. Revert-verified with two new tests (the race case, and the normal
  newer-wins case).
- **Advisory 9 (fixed): relabeled the AC-8 web measurement's byte figures in `validation.md`**
  as derived estimates carried over from 6c's backend-measured per-row costs, explicitly not an
  independent web-side measurement -- the request-count columns are the real web-side evidence.
- **R-2/Required-3 (same finding from both reviewers, fixed): `use-realtime-broadcast.ts`'s
  `locationsWithCounts` type-qualified invalidation was dead code** -- `data.locationType` is the
  backend's `storage_locations.code` vocabulary (`"RACKS"`), not the frontend `LocationType` enum
  the cache key uses, so it could never match, and Kuji/Shipment producers send no `locationType`
  at all so even the "ALL" branch never fired for those. **Fixed:** dropped the type-qualified
  keys entirely; every `inventory_updated` event now unconditionally invalidates the whole
  `["locationsWithCounts", siteId]` prefix, matching the pattern `use-location-mutations.ts`
  already used. Revert-verified.
- Advisory 10: left to judgment -- not independently actioned this round; no specific finding
  text was carried in the reviewer's report to act on beyond what's covered above.

### Final verification after all fixes (this round)

Backend: `./mvnw -q clean test-compile` clean. `./mvnw -q clean test` -- 380 run (up from 379:
+1, `SupabaseBroadcastServiceTest`'s new A-10 case), 0 failures/errors. `./mvnw test -Dtest='*IT'`
-- 522 run (up from 502: the R-3-restored `LocationInventoryControllerSecurityIT` minus its moved
`ProductTests`, plus `SupabaseBroadcastServiceAfterCommitIT` and the new `LocationAggregateEgressIT`
case), 8 failures, name-for-name identical to the pre-existing
`AnalyticsControllerSecurityIT`/`ForecastControllerSecurityIT` set, no new failure. `ArchitectureTest`
stable across two independent clean rebuilds; frozen store restored to its pre-T6e-be-9 content
exactly, confirmed by `git diff`. Contracts regenerated and confirmed stable (scripted set-diff:
the R-3 revert's 3 paths + 2 schemas restored, nothing else changed).

Web: `npx tsc --noEmit` clean. `npx vitest run` -- 57 files/404 tests (up from 395: +9 new cases
across `inventory-refresh.test.ts`, `use-realtime-broadcast.test.ts`,
`use-stock-mutations.test.ts`), 0 failed. `npx eslint .` -- 0 errors/51 warnings, identical to
every prior checkpoint's baseline.

**Disposition: both independent reviews' Blocker and Required findings fixed and revert-verified;
Advisories fixed where cheap, one (use-location-mutations.ts's Blocker-2 test coverage) explicitly
noted as skipped with reason, one (Advisory 10) left to judgment for lack of specific text to
action. 6e is now reviewed to the same independent-agent standard as 6a-6d.** The coordinating
session still owns running the complete phase exit gate (AC-1-8 together) and closing the
checkpoint.

## Follow-up review: four further findings after 6e's independent-review fix round (2026-09-15)

A fourth review pass (user-reported, against `69fc6fe^..HEAD`) found four more findings the two
independent-agent reviews above had not caught. All fixed by the coordinating session directly,
each revert-verified (fix removed, new/existing test confirmed to fail for the predicted reason,
fix restored, confirmed green).

- **P1: known-ID `inventory_updated` events never invalidated `locationInventory`; unknown-ID
  events never invalidated `productInventoryEntries`.** `use-realtime-broadcast.ts`'s two
  branches were mutually exclusive when they should not have been: `locationInventory` (read by
  location sheets and stock dialogs, keyed by location rather than product) was only invalidated
  in the unknown-ID `else` branch, and `productInventoryEntries` only in the known-ID branch --
  so a known-ID event left open location views stale, and an unknown-ID event left open
  product-inventory views stale. **Fixed:** both families are now invalidated unconditionally on
  every `inventory_updated` event, in addition to whichever ID-specific invalidation already
  applies. Two new tests in `use-realtime-broadcast.test.ts` (one per direction), both revert-
  verified against the pre-fix code.
- **P1: the merge guard in `flushInventorySiteRefresh` compared each fetched row's own
  `lastUpdatedAt` -- a value that is not monotonic with correctness.** `lastUpdatedAt` is
  `MAX(location_inventory.updated_at)` across a product's *remaining* rows
  (`InventoryTotalsRepository.INVENTORY_TOTALS_SQL`): deleting the newest row legitimately
  *decreases* it, so the guard was rejecting a correct, lower total; and the zero-quantity
  default seeded for a requested ID absent from the response carries no timestamp at all, so an
  older, empty response could bypass the guard entirely and overwrite a newer restocked quantity
  with zero. **Fixed:** replaced the timestamp comparison with request-issuance sequencing -- a
  module-level monotonic counter claims a sequence number for every requested product ID at the
  moment a flush *starts* (before its fetch is even issued), and a flush's write is applied only
  if no later-started flush has since claimed that same product ID, regardless of which resolves
  first. This depends on nothing in either response, so it is correct however
  `lastUpdatedAt` (or its absence) behaves. The two prior Advisory-8 tests (which asserted the
  old timestamp-based behavior) were replaced with one test that drives two overlapping calls
  through controlled, independently-resolvable promises and proves the later-started one always
  wins even when its response arrives first.
- **P2: the coalescing buffer had no cap, and the backend's `MAX_PRODUCT_IDS_BATCH_SIZE` (500)
  rejects an oversized request outright.** Enough rapid, individually-valid events can
  accumulate more than 500 buffered IDs; the resulting request would 400, and every caller of
  `flushInventorySiteRefresh` already wraps it in a bare `.catch()` (by earlier review design),
  so the failure was silently swallowed with no cache update for any of the batched IDs.
  **Fixed:** `flushInventorySiteRefresh` now chunks the unique ID list into batches of 500,
  issuing one request per chunk and merging every chunk's results before writing the cache once.
  New test asserts a 501-ID flush issues exactly two requests (500 + 1) and every ID ends up
  correctly refreshed.
- **P2: the legacy, unscoped `batchTransferInventory(BatchTransferInventoryRequestDTO)` stamps
  a combined broadcast with only the first transfer's site.** Unlike its site-scoped sibling
  (which requires every transfer's source to already belong to the caller's `siteId` before this
  method ever runs), the legacy overload has no such precondition and genuinely accepts
  independent A-to-A and B-to-B transfers in one request -- confirmed by re-reading
  `planTransfers`/`lockPlannedRows`, which apply no site check anywhere. The prior code computed
  one `batchTransferSiteId` from `firstSource` alone and broadcast every affected product under
  it, so site B's clients would receive a notification stamped for site A and discard it as
  foreign. **Fixed:** product IDs are now grouped by each transfer's own source site while
  looping (`productIdsBySite`), and one `broadcastInventoryUpdated`/`broadcastAuditLogCreated`
  pair is emitted per affected site instead of one combined pair. New IT
  (`StockMovementServiceBroadcastArgsIT.batchTransferInventory_mixedSites_
  emitsOneNotificationPerAffectedSite`) seeds two sites' worth of transfers in one legacy batch
  request and asserts one notification per site, each carrying only that site's product IDs.

### Final verification after this round

Backend: `./mvnw -q clean test-compile` clean. `./mvnw -q clean test` -- 479 run, 0 failures.
`./mvnw test -Dtest='*IT'` -- 523 run (up 1: the new mixed-site broadcast test), 8 failures,
name-for-name identical to the pre-existing `AnalyticsControllerSecurityIT`/
`ForecastControllerSecurityIT` set, no new failure. `ArchitectureTest` clean, frozen store
unchanged.

Web: `npx tsc --noEmit` clean. `npx vitest run` -- 57 files/406 tests (up 2: the two new
`use-realtime-broadcast.test.ts` cases; `inventory-refresh.test.ts`'s two replaced tests keep
the file's count net-even), 0 failed. `npx eslint .` -- 0 errors/51 warnings, baseline-identical.

**Disposition: all four findings fixed and revert-verified by the coordinating session.**

## Second follow-up review: two sequencing gaps in the P1/P2 fix itself (2026-09-15)

A fifth review pass (user-reported, against the round above) found that the request-issuance
sequencing mechanism the prior round introduced did not itself cover every path that writes the
`inventoryTotals` cache. Both fixed by the coordinating session, each revert-verified.

- **P1: full refreshes (reconnect recovery, unknown-ID batches, and the "nothing cached yet"
  fallback) bypassed sequencing entirely.** They called a bare `invalidateQueries` instead of
  going through the claim/apply mechanism the prior round built for targeted flushes, so they
  raced with targeted flushes in both directions: an older full read arriving late (React
  Query's own refetch, unsequenced) could overwrite a newer targeted write for the same product,
  and -- because the full path never claimed anything -- an older targeted flush that resolved
  late could just as easily overwrite a newer full read. **Fixed:** replaced both bare-invalidate
  call sites with a new `refreshAllInventoryTotals` helper that participates in the same
  sequencing scheme: it claims every currently-cached product ID up front (mirroring how a
  targeted flush claims its own ids), fetches the full site totals, and per-id, only applies a
  fetched row if no strictly newer flush has since claimed that id -- falling back to whatever is
  currently cached for it otherwise, so a superseded full read doesn't regress an id a newer
  flush already corrected. New tests cover both directions: an older full refresh cannot
  overwrite a newer targeted write, and an older targeted response cannot overwrite a newer full
  refresh, each driven through controlled, independently-resolvable promises the same way the
  first round's P1 sequencing test was.
- **P2: a failing flush that had superseded an earlier, successful one left the cache
  permanently stale with nothing scheduled to correct it.** Claiming happens before the fetch is
  issued (by design, so ordering is determined by issuance time, not resolution time) -- but the
  prior round's fix let a flush's own failure just propagate with no consequence for the claim it
  had already taken. If flush B superseded flush A's claim on product X and then B's fetch
  failed, A's result (even a correct one, already in flight) was discarded on arrival because A
  no longer owned the claim, and nothing else was scheduled to reconcile X - the cache stayed
  wrong indefinitely. **Fixed:** added `recoverOnFailure`, invoked from both the targeted and
  full-refresh paths' catch blocks: if the failing flush is still the *current* claim holder for
  at least one of its ids when it fails (i.e. no even-newer flush has since superseded it too),
  a best-effort `invalidateQueries` on the site's totals key triggers a corrective refetch.
  Deliberately scoped to only fire when the failing flush is still the latest claimant -- if an
  even-newer flush already superseded the failing one before it failed, that newer flush already
  owns reconciling the id, and a redundant recovery would just race it. Two new tests: recovery
  fires and reproduces the reported scenario (cache would otherwise stay stuck at the old value
  even though an earlier, superseded fetch had already returned the correct one), and recovery
  does *not* fire when a still-later flush had already taken over before the failure.

Two existing tests (`ac8-web-measurement.test.ts`'s scenario harness) detected a "full refresh"
by counting `invalidateQueries` calls against the totals key -- no longer valid once the
unknown-ID path fetches directly instead of bare-invalidating. Updated to detect a full refresh
by the absence of a `productIds` argument on the `getSiteInventoryTotals` mock call instead;
the scenario assertions themselves (request counts, byte estimates) are unchanged.

### Final verification after this round

Backend: unaffected by this round (web-only fix); re-ran `StockMovementServiceBroadcastArgsIT`
directly to confirm continued green (4/4) after the prior round's backend fix, since the user's
own attempt was blocked locally by Docker permissions.

Web: `npx tsc --noEmit` clean. `npx vitest run` -- **57 files/410 tests** (up 4 net: two existing
full-refresh tests rewritten for the new fetch-based behavior, four new tests added -- two P1
ordering-direction tests and two P2 recovery tests), 0 failed. `npx eslint .` -- 0 errors/51
warnings, baseline-identical. All new/changed tests individually revert-verified: with the fix
reverted, 7 of the file's 13 tests failed for the predicted reasons (wrong value applied, no
ordering guard, recovery not triggered, or a timeout from the old code's non-deterministic mock
consumption under the new test's controlled-promise setup); fix restored, all 13 green.

**Disposition: both sequencing gaps fixed and revert-verified.**

## Third follow-up review: sequencing gaps in the second round's own fix (2026-09-16)

A sixth review pass (user-reported, with independent reproduction tests) found two more gaps in
the sequencing mechanism the second round built. Both fixed by the coordinating session,
revert-verified against 5 tests that failed for the predicted reasons on the pre-fix code.

- **P1: recovery and the real `useQuery` behind this cache key still bypassed sequencing.**
  The second round's `recoverOnFailure` called a bare `invalidateQueries`, and the actual
  `useQuery` in `use-product-inventory.ts` (`queryFn: () => getSiteInventoryTotals(siteId)`)
  never claimed or checked sequence at all - so *either* path's own refetch (recovery's
  corrective invalidate, or the query's own mount/window-focus/staleTime/manual refetch) could
  land after a newer targeted flush and overwrite it unconditionally, reproduced as a quantity
  regressing from 10 back to 2. **Fixed:** split the merge logic into a pure `mergeFullTotals`
  function and a claim-then-fetch `fetchAndMergeFullTotals` helper that takes an
  already-claimed sequence number (never claims twice for the same attempt, which would mint a
  spurious higher number that could wrongly supersede a genuinely concurrent flush). Three
  callers now share it, each claiming exactly once: `fetchSequencedInventoryTotals` (a new
  exported function, now the actual `queryFn` for `["inventoryTotals", siteId]` in
  `use-product-inventory.ts` - the query's own lifecycle refetches are ordered against explicit
  flushes for the first time), `recoverOnFailure` (issues its own freshly-claimed recovery fetch
  instead of a bare invalidate), and `refreshAllInventoryTotals` (the unknown-ID/no-cache path,
  unchanged in spirit but rewired onto the shared helper). Also removed two now-doubly-risky
  bare-`invalidateQueries` fallbacks in `use-stock-mutations.ts`/`use-location-mutations.ts`
  (added in the *first* follow-up round for Blocker 2) - `flushInventorySiteRefresh` already
  attempts its own sequenced recovery internally on failure; an unsequenced fallback stacked on
  top of that could undo it.
- **P2: an older full response deleted a newer product entirely absent from it.** The second
  round's full-refresh merge built its result exclusively from `fetched` rows; any id in the old
  cache but absent from that response was silently dropped, on the assumption absence always
  means deletion. But a product created after an older full read's server-side snapshot - and
  already cached by a newer targeted flush that started after that read began - is *also* absent
  from the older response, for an entirely different reason (it didn't exist yet when that
  read's query ran), and was being deleted right along with genuine deletions. **Fixed:**
  `mergeFullTotals` now walks the old cache's ids that are absent from `fetched` first: an id is
  only dropped if nothing newer than this flush's own sequence has claimed it since; otherwise
  the newer-owned entry is preserved into the result before the fetched rows are applied.

Two new tests target these directly: `fetchSequencedInventoryTotals` (the real queryFn's
replacement) losing a race to a targeted flush that started after it but resolved first, and a
brand-new product surviving an older, in-flight full read that never saw it exist. The existing
P2 recovery tests were rewritten to assert the new mechanism (a fresh sequenced fetch + direct
`setQueryData`, not `invalidateQueries`) rather than the old one.

### Final verification after this round

Web only (backend untouched): `npx tsc --noEmit` clean; `npx vitest run` -- **57 files/412
tests** (up 2 net: two new tests, two rewritten P2 recovery tests kept at the same count), 0
failed; `npx eslint .` -- 0 errors/51 warnings, baseline-identical. Revert-verified: with the fix
reverted, 5 of `inventory-refresh.test.ts`'s 15 tests failed for the predicted reasons (missing
export, timeouts from the old recovery path never issuing its corrective fetch, and the wrong
error propagating from stale mock-queue ordering); fix restored, all 15 green, full suite green.

**Disposition: both sequencing gaps fixed and revert-verified. No further findings outstanding.**
