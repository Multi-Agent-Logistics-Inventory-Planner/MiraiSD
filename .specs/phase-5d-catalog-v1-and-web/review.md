# Review

## Scope reviewed

T-1 only: global catalog controllers, product creation/update, request rejection,
response mapping, and generated contracts. Independent Standards and Spec passes
ran in parallel. T-2 through T-6 remain outside this checkpoint.

## Findings

- [Standards] P2: `CatalogProductController.getCatalogProducts` delegates search to
  `searchProducts`, whose query requires `isActive = true`. New global identities
  start inactive, so a newly created product disappears when searched by its name
  or SKU. Use a global search without the legacy activity predicate. **act on** —
  **fixed**: added `ProductRepository.searchAllWithCategories`/
  `ProductService.searchAllProducts` (no `isActive` predicate) and switched
  `CatalogProductController` to it.
- [Spec] P2 (AC-1): `CatalogProductMapper` maps the read-only shadow `parentId`
  instead of `parent.id`. Creating a child returns `parentId: null`; mapping the
  current association also avoids stale IDs after reparenting. **act on** —
  **fixed**: `CatalogProductMapper` now maps `parentId` from `product.parent.id`.

## Residual risk

Both findings reproduced with focused integration tests and are now fixed, with
permanent regression tests added to `CatalogProductControllerIT`
(`searchCatalogProducts_findsNewlyCreatedInactiveProduct`,
`createCatalogProduct_childProduct_returnsCorrectParentId`) — 13/13 pass. Full
`*IT` suite re-verified: 353/353 (351 + 2 new), no regressions. None identified.

## T-2 independent review checkpoint

Standards and Spec passes ran independently in parallel. Scope: site product
controller, settings wire semantics, role/site gates, generated contract, and
the catalog -> shared dependency-baseline addition. T-3 onward excluded.

- [Spec] P2 (AC-4): `SiteProductController.updateSettings` exposes JsonNode as
  its request schema. Generated `schema.d.ts` defines it as
  `Record<string, never>`, preventing typed callers from sending expectedVersion
  or any settings field. Provide an explicit request schema with required
  version and optional nullable overrides; regenerate the contracts. **act on** —
  **fixed**: added `SiteProductSettingsRequest` (typed `Long expectedVersion` plus
  five `FieldUpdate<T>` override fields, each with `@Schema(implementation = ...)`)
  backed by a new `FieldUpdateDeserializer` (`ContextualDeserializer`) that
  preserves the omitted/explicit-null/value tri-state at the wire level. Verified
  directly against the regenerated `openapi.json`: the schema now names every
  field with its real type (`expectedVersion: integer(int64)`, `unitCost`/`msrp:
  number`, etc.), and `schema.d.ts` types the request body accordingly instead of
  `Record<string, never>`.
- [Standards] P2: `expectedVersion` uses permissive `JsonNode.asLong()`. A request
  with `expectedVersion: "nonsense"` and an MSRP update returns 200 on a newly
  assorted version-zero row. Reject malformed/nonintegral/out-of-range version
  values before invoking the service. **act on** — **fixed** as a side effect of
  the schema fix above: `expectedVersion` is now a plain typed `Long`, so normal
  Jackson deserialization throws on a non-numeric value (mapped to 400 by the
  existing `HttpMessageNotReadableException` handler) instead of silently
  coercing it to `0`.
- [Spec] P2 (AC-2b): the claimed full mutation permission matrix is incomplete.
  ASSISTANT_MANAGER success, missing-membership rejection, and revoked-membership
  rejection tests exercise assortment only, not settings. Cover settings with
  an existing row and valid version so assertions isolate authorization. **act on** —
  **fixed**: `assistantManager_canMutate`, `admin_canMutate`,
  `noMembership_rejectedOnReadsAndMutations`, and
  `revokedMembership_rejectedOnReadsAndMutations` each now also exercise the
  settings PUT (200/200/403/403 respectively).

The catalog -> shared addition is consistent with the required authorized-site
context pattern and introduces no cycle (shared has no outgoing module edges).
Both selected architecture test classes pass. No reason found to revert this edge.

Residual validation gap noted at the time: retained de-assorted rows and
explicit-null versus omitted settings fields lacked HTTP-level tests. Closed in
the fix pass: `updateSettings_omittedLeavesUnchanged_explicitNullClearsOverride`
proves both halves of the tri-state contract at the HTTP layer (an omitted field
leaves the stored value/inherited fallback untouched; an explicit JSON `null`
clears an override back to global inheritance), and
`updateSettings_malformedVersion_returns400` is the new regression test for the
version-coercion finding. Disposition: findings resolved;
`SiteProductControllerIT` is 16/16 with the review's exact reproduction cases
now permanent tests.

## T-2 typed-request follow-up review

The authorization-matrix finding is resolved by the added settings assertions.
The typed request and contextual deserializer preserve null/omitted/value behavior
at runtime, but two P2 issues remain:

- [Spec] P2 (AC-4 and 5c AC-6): exported OpenAPI 3.1 override properties contain
  only number/integer types, despite `@Schema(nullable = true)`. Generated
  `SiteProductSettingsRequest` fields are `number` rather than `number | null`;
  typed clients cannot clear an override. Export explicit null unions supported
  by the configured generator and verify the regenerated TypeScript null types.
  **act on** — **fixed**: root cause was that this project's springdoc emits
  OpenAPI 3.1, and this swagger-core version's 3.1 output silently drops
  `@Schema(nullable = true)` (confirmed empirically - it doesn't take effect with
  or without `implementation`, and regardless of whether the annotation sits on a
  Lombok-generated or hand-written getter). OpenAPI 3.1 expresses nullability as a
  JSON Schema `type` array instead of the 3.0 `nullable` keyword, and
  swagger-annotations 2.2.x's `@Schema` has a `types()` array attribute for
  exactly this. Switched every override field to
  `@Schema(types = {"<type>", "null"}, implementation = <Wrapper>.class)` -
  `types` alone produces the null union but also lets swagger-core additionally
  resolve `FieldUpdate<T>`'s own `present`/`value` shape as a stray sibling `$ref`
  plus a bogus `FieldUpdateBoolean`/`FieldUpdateBigDecimal`/`FieldUpdateInteger`
  component schema; `implementation` is still needed alongside it to suppress
  that. Verified directly against the regenerated `openapi.json`: every override
  field is now `{"type": ["<type>", "null"]}` with no stray `$ref` or leaked
  `FieldUpdate*` component schemas, and `schema.d.ts` now types every field as
  `T | null` (e.g. `msrp?: number | null`).
- [Standards] P2: `Long expectedVersion` rejects nonsense strings but still accepts
  fractional JSON numbers through Jackson coercion. Replacing the malformed-version
  regression payload with `expectedVersion: 0.9` returns 200 on the asserted
  version-zero row. Require an integral, in-range version without float truncation,
  preferably scoped to this request field. **act on** — **fixed**: added
  `StrictLongDeserializer` (`catalog/api/`), applied via `@JsonDeserialize` to
  just the `expectedVersion` setter (not a global Jackson coercion-config change,
  per the review's "preferably scoped to this request field"). It accepts only a
  `VALUE_NUMBER_INT` JSON token and calls `ctxt.reportInputMismatch(...)` for
  anything else (a float, a string, etc.), so `0.9` is now rejected the same way
  `"nonsense"` already was.

Both fixes verified with a new permanent test each:
`updateSettings_fractionalVersion_returns400` (the review's exact `0.9`
reproduction) and by direct inspection of the regenerated `openapi.json`/
`schema.d.ts` for the nullable-union fix (no HTTP-level test can distinguish a
schema-only change, since the server already accepted `null` for these fields
at runtime before this fix - only the exported contract was wrong).
`SiteProductControllerIT` is 17/17. Disposition: findings resolved.

## T-2 final independent follow-up

Both remaining P2 findings are resolved. Direct contract inspection confirms
OpenAPI 3.1 type/null unions and generated TypeScript `T | null` override fields.
StrictLongDeserializer is scoped to expectedVersion and accepts only integer
tokens, using getLongValue() for range checking. The permanent controller suite
passes 17/17, including the fractional-version regression. No new findings in
this follow-up; T-2 review is clear to proceed to T-3.


## T-3 independent review

Parallel Standards and Spec passes reviewed the legacy deprecation filter,
registration, tests, and AC-3 migration documentation.

- [Standards] P2 (AC-3): `LegacyCatalogDeprecationFilter.java:18` emits an HTTP-date
  for `Deprecation`. RFC 9745 section 2.1 requires a Structured Field Date:
  the selected instant must be `@1788825600`. Standards-compliant consumers cannot
  parse the current value. **act on** — use the structured date and verify its
  format independently of the implementation constant in the integration test.
  Source: https://www.rfc-editor.org/rfc/rfc9745.html#section-2.1
- [Spec] Registration runs after Spring Security, so authentication short-circuits
  may omit these headers. **consider** — no runtime probe was performed, and AC-3
  does not explicitly settle whether authentication failures need deprecation
  metadata. Not promoted to a blocking finding in this review.

The linked migration map covers the three families; omission of an unknown
Sunset is permitted. Disposition: correct the P2 header format before closing T-3.

**Fixed:** `DEPRECATION_DATE` is now `"@1788825600"` (RFC 8941 Structured Fields Date), and
`LegacyCatalogDeprecationHeadersIT` parses the raw header against an independently-written
regex for the `sf-date`/Link syntax and decodes the epoch value, rather than comparing against
the implementation constant. `./mvnw -o test -Dtest='LegacyCatalogDeprecationHeadersIT'` — 4/4;
`-Dtest='*IT'` — 374/374; unit — 404/404. T-3 is closed. The non-blocking "consider" note above
(headers on auth-short-circuited responses) was not acted on, per its own disposition.

## T-3 independent fix follow-up

The P2 is resolved: the emitted structured date is `@1788825600`, and the
permanent test independently checks syntax and the intended UTC instant.
Focused suite passes 4/4; no new blocking findings. T-3 is clear for T-4.
Minor documentation correction: the Date type comes from RFC 9651 (referenced
by RFC 9745), rather than RFC 8941 as the new comments state.

## T-5 independent review — 2026-09-09

Parallel Standards and Spec passes reviewed the dirty-worktree web migration,
the accepted AC-6a data-source split, AC-6b withholding, query invalidation,
detail state, and the AC-6e Kuji gate. Disposition: **changes required**.

- [Standards|Spec] **P2 — act on (AC-6c):**
  `apps/web/src/app/(dashboard)/products/page.tsx:263` passes a selected row
  snapshot to ProductModal. Selection is only updated on a row click; changing
  the site/list does not clear or rebind it. Open detail at MAIN, then change the
  current-site result to SECOND: the table adopts SECOND's settings while the
  open modal still shows MAIN's price/settings. Clear transient dialog state on
  site changes or resolve selection against the current site's rows, including
  the loading/error transition. The present resolver only exposes MAIN, so this
  is a failure of the explicitly required site-switch behavior, not a claim of
  an available production switcher.
- [Standards|Spec] **P2 — act on (AC-6b/AC-6d):**
  `apps/web/src/components/products/product-modal.tsx:304` still renders global
  `p.isActive` even with `showInventory={false}`. For `isStocked=false` and
  `isActive=true`, the table says "Not Stocked" while detail says "Active".
  Withholding the Current Stock section does not withhold this global,
  stock-derived status. Render site assortment from `product.isStocked` on
  this path, or withhold the global status.
- [Spec] **P2 — act on (AC-6/AC-6c):**
  `apps/web/src/hooks/queries/__tests__/use-site-product-inventory.test.ts:124`
  switches mocked hook data to an empty site list. It does not exercise the
  real absent-assortment response (a product row with effective fallback
  settings), the rendered table/modal, role-dependent offered actions, or
  inventory withholding. The implementation log explicitly omits the whole-page
  test and records no completed manual/Playwright smoke. Add the required page
  switch proof using realistic A/B responses, an open detail modal, withheld
  quantity/status, relevant role actions, and the Kuji gate. Typecheck and hook
  tests do not establish those rendered behaviors.

The fixed field-source join, site-qualified query, prefix invalidation audit,
and fail-closed Kuji panel have no additional blocking findings. Existing tests
pass, but do not close the findings above. No implementation edits made during
this review; T-6 has not started.

## T-5 independent fix follow-up — 2026-09-09

Parallel Standards and Spec passes confirm all three P2 findings are resolved.
Selection stores a product ID and resolves the row from current joined items;
an uncached site transition produces a null selection rather than retaining the
old site's row. ProductModal explicitly distinguishes undefined `isStocked`
from false, preserving legacy status only on the unscoped path.

The four new rendered-page tests execute the actual list/join hooks, table,
and detail modal. They cover inventory withholding, role-dependent Edit and
money visibility, and an open modal adopting SECOND's distinct MSRP and
assortment status. Together with the existing Kuji boundary tests, this closes
the missing acceptance-proof finding. No new blocking findings; **T-5 is clear
to proceed to T-6**.

Non-blocking limitations: the switch test supplies a new QueryClient on rerender
rather than retaining one cache, and Kuji is tested separately from the page.
A future extension can retain the same client and assert the intermediate
loading state and all post-switch table fields. No browser smoke was run.
The edit form remains on its existing legacy product query; ID-only edit state
does not itself migrate that form's settings to site-scoped data.

Lint passes with 51 warnings, not 50: the new selection memo adds one warning
about the unstable `items` fallback dependency. This is non-blocking.

## T-6 final web review — 2026-09-09

Parallel Standards and Spec passes reviewed the category API adapter, query
swap, new category API/hook tests, added site-product API tests, and the
Products page test's updated category mock. **No findings; T-6 satisfies AC-7.**

Both category controllers call the same service and mapper. The new client
mapper preserves every Category field and maps children recursively. The
global query key, alphabetical selection, child-category lookup, consumers,
and legacy mutation invalidation remain compatible. The existing published
contract supplies the response type; no regeneration is needed.

T-1 through T-6 implementation tasks are complete with no outstanding blocking
web review findings. Commit and the PR gate remain pending. This final web
review retains earlier backend/contract evidence without rerunning it and
retains T-5's documented limitation that no browser smoke was performed.
