# Phase 6 gap audit and proposed closure plan

Date: 2026-09-16. Status: proposed; implementation and deployment are not performed by this audit.

## Scope and evidence

Audit of the current working tree against [Phase 6 AC-1–8](../../.specs/phase-6-inventory/spec.md),
the [modernization plan](enterprise-modernization.md), and the durable
[data/API](../specs/multi-site-data-and-api.md) and
[event](../specs/events-and-replica-readiness.md) specifications.
Includes independent Standards (backend/security) and Spec (frontend/acceptance) passes.
Existing unrelated worktree edits were preserved. Findings describe current code, including
uncommitted changes, not necessarily deployed code. No production database, Realtime policies,
Kafka configuration, live authorization matrix, or current PR gate was inspected in this audit.
This is a comprehensive source-level inventory of identified gaps, not proof that no other defect exists.

Current verification:

- From `apps/web`, `npm run test:run`: **59 files, 424 tests passed**.
- From `apps/web`, `npx tsc --noEmit -p tsconfig.json`: **passed**.
- Earlier focused stock/location/inventory run: **6 files, 31 tests passed**.
- Backend results below are historical evidence from the Phase 6 validation record, not a fresh run.
- Green existing tests do not prove the missing scenarios listed below.

## Confirmed implementation gaps

| ID | Priority | Finding and impact | Evidence | Proposed closure |
| --- | --- | --- | --- | --- |
| G1 | High | Shared stock location picker still reads legacy MAIN locations with a site-blind cache. Used by Adjust, Transfer, and ProductForm initial stock. A future non-MAIN session can select the wrong site's location and then fail the scoped mutation. | `apps/web/src/hooks/queries/use-locations.ts:7`; `components/stock/location-selector.tsx:108`; `components/products/product-form.tsx` | Use existing v1 site locations, site-qualified keys, unresolved-site gating, explicit read errors, and tests with two sites. Preserve separately identified Phase 7 consumers until their migration. |
| G2 | High | Storage reads migrated, but create/rename/delete still use legacy location APIs. Creation additionally resolves a MAIN storage category by legacy code lookup. | `apps/web/src/hooks/mutations/use-location-mutations.ts:134` | Adopt existing `SiteLocationController` v1 CRUD and site-scoped storage-category resolution. Verify all request IDs and invalidation keys belong to the originating site. |
| G3 | High | Retained legacy inventory/location routes do not consistently enforce active site membership or scoped entity lookup. Some return global totals/history; others accept arbitrary resource IDs. Route retention was deliberate, but is not an authorization guarantee. | `identity/infrastructure/SiteAccessAuthorizationFilter.java:33`; `controllers/LocationInventoryController.java`; `inventory/api/InventoryAggregateController.java`; `inventory/application/StockMovementService.java:1110`; `sites/application/LocationService.java:113` (Java paths relative to inventory-service main package) | Inventory supported legacy clients, then adapt compatible HTTP routes to an authenticated, authorized MAIN context. Explicitly classify any legitimate administrative/global read. Preserve URL/DTO compatibility where possible; do not delete routes before the removal gate. |
| G4 | High | Legacy mutation paths still accept caller-supplied actor IDs, unlike v1's trusted actor handling. Audit attribution can be forged by an otherwise authorized caller. | `controllers/LocationInventoryController.java`; `inventory/application/StockMovementService.java:594` | Derive actor from authenticated identity. Keep legacy fields accepted if required for compatibility, but do not trust them for attribution. Test forged/missing actor IDs. |
| G5 | High | Nested legacy inventory item GET/DELETE ignore the parent `locationId`. A URL naming location A can operate on a row from location B. | `controllers/LocationInventoryController.java:42` and DELETE handler | Require row, location, and authorized site to agree; reject wrong-parent IDs with no write. |
| G6 | Medium | Initial stock insertion bypasses shared local refresh. Product-list invalidation finishes before stock creation, then the stock request only produces a toast. Without a broadcast, cached totals can remain zero. | `apps/web/src/components/products/product-form.tsx:478`; `hooks/mutations/use-product-mutations.ts:11` | Refresh totals and affected location/product/count caches after committed initial stock. Test success with realtime absent. Refresh failure must not be reported as failed stock insertion. |
| G7 | Medium | Adjust/transfer local success omits `locationsWithCounts` invalidation. Location cards/utilization depend on a later broadcast to catch up. | `apps/web/src/hooks/mutations/use-stock-mutations.ts:69`; `hooks/realtime/use-realtime-broadcast.ts:156` | Include selected-site count invalidation in the common post-write path; prove correct counts without broadcast delivery. |
| G8 | Medium | Adjust/transfer dialogs do not handle their inventory query error state; missing data falls through to empty-inventory presentation. ProductModal's previous error fix does not cover these dialog reads. | `apps/web/src/components/stock/adjust-stock-dialog.tsx:189,890`; `components/stock/transfer-stock-dialog.tsx:425` | Render failed/unresolved/empty separately, provide retry, and block actions whose required source/destination state is unavailable. Include stale-data-plus-error tests. |

G3–G5 are security follow-ups to the compatibility boundary, not an instruction to remove legacy
support or silently redefine intentionally global internal facades. Audit the HTTP adapters separately
from legacy domain callers that remain scheduled for Phase 7.

## Proof gaps and hardening decisions

| ID | Classification | Finding | Proposed closure |
| --- | --- | --- | --- |
| P1 | AC-6 coverage / latent multi-site behavior | Transfer's rendered test stubs the real picker. Stock dialogs retain local selection/cart state without an explicit site-change reset. Mutation variables do not capture the originating site, while callbacks read hook site context. No deferred-response/site-switch test proves these interactions. | Test the real picker and open dialogs across site changes, late reads, and pending mutations. Bind an operation's site to its variables; reset or close old-site drafts. Treat callback misrouting as a risk until reproduced, not a demonstrated corrupt write. |
| P2 | AC-6 delivery qualification | `useMovementHistory` is scoped but has no rendered consumer. The planned visible UNKNOWN-site label has no UI surface/proof. | Either implement a small product movement-history view using the existing hook, or explicitly defer that UI and narrow the closure claim. Do not migrate grouped audit logs implicitly. |
| P3 | AC-7/8 performance qualification | Known-ID local mutations still refetch the entire site-product view via `["products", siteId, "site"]`. Local refresh and the later broadcast share an executor but are not deduplicated into a single request. | Measure the real workflow first. Remove unnecessary broad assortment/settings refresh or replace it with targeted reads if semantics require it. Deduplicate local-plus-realtime refresh only where correctness can be preserved. Describe existing savings as totals savings. |
| P4 | AC-8 measurement limit | `ac8-web-measurement.test.ts` counts a stubbed totals API, reconstructs the before workload, and estimates web bytes. It omits all surrounding product/detail/count requests and live websocket delivery. This is disclosed in the validation record. | Add a controlled integrated workload for initial load, local write plus broadcast, batch, duplicates, reconnect and site switch; record every relevant request and measured/estimated bytes separately. Keep current projection measurements as valid narrower evidence. |
| P5 | Idempotency UX hardening | Every new user `mutateAsync` invocation gets a new key. Internal retries preserve keys, but manual resubmission after a committed request's response is lost can repeat its business effect. The existing per-invocation strategy is documented. | Define an explicit retry of an uncertain operation that retains the original key, payload and site. Separate it from starting a new operation. Test committed-but-response-lost behavior before claiming user-visible retry safety. |
| P6 | Gate evidence | Historical backend full IT evidence has eight acknowledged analytics/forecasting failures. Current PR-gate evidence was not checked. | Fix the pre-existing tests or isolate their database fixtures in a separate slice (do not simply exclude failing tests), then run JDK 21 wrapper unit and IT suites, architecture and contract checks, web checks and the actual PR gate. Report baseline exceptions honestly until eliminated. |
| P7 | Documentation | Phase 6 spec still says implementation has not started, while log says all ACs closed. API map still calls location-inventory v1 unimplemented although it exists. Some client comments describe only the old Track D scope. | Reconcile current status, route inventory, AC evidence and explicit deferral ownership. Preserve historical entries but add a concise authoritative current handoff. |

## Deliberate deferrals and release prerequisites

These are outstanding work, but should not be represented as newly discovered Phase 6 regressions.

| ID | Remaining work | Owner / timing | Completion condition |
| --- | --- | --- | --- |
| D1 | Exactly one canonical NOT_ASSIGNED location per site is not enforced. Backend chooses first row; frontend prefers `NA`, then first row. Existing location-code uniqueness does not enforce a single row for the category. | Inventory/sites integrity follow-up, before broader site use | Verify missing/duplicate rows without deleting data; define canonical identity; add compatible guard/constraint and concurrent tests. Resolve duplicates through an explicit reviewed data plan. |
| D2 | V61 movement-site NOT NULL/FK enforcement and outbox-envelope constraints remain deferred. | Separate Full-tier rollout slice | Verify writer rollout, null/orphan/cross-site checks and rollback compatibility before constraining. Define which legacy UNKNOWN-site event rows can legitimately remain. |
| D3 | Flyway is not canonical; migration files do not self-apply and development Hibernate update is not migration parity. Dev setup script is a guarded local bridge. | Earlier Phase 1/platform debt; prerequisite to repeatable releases | Reconcile real schemas and migration history, establish baseline and tracked apply/check mechanism, then remove Hibernate update. Do not enable Flyway against an unreconciled production database. |
| D4 | `kafka.partitioning.site-scoped-key.enabled` defaults false in shipped profiles. Implemented site/product partitioning is not proof of active cutover. | Coordinated event rollout | Verify target partitions/consumer assumptions, drain old-key traffic, cut over with offset evidence and a rollback plan. Preserve global stock-payload semantics until consumers migrate. |
| D5 | Browser subscribes to shared `db-changes`; backend publishes the same topic. Client inventory filtering is not server-side subscription authorization. Live Supabase settings/policies were not inspected. | Realtime tenant-boundary work before independent site access | Implement/verify private site-authorized channels and denied foreign-site subscriptions. Keep global catalog events separately classified. Test on the configured service, not only a JS predicate. |
| D6 | Site-less legacy domain broadcasts and unbounded async execution remain. Broadcast is intentionally best effort. | Phase 7 producers / platform reliability | Migrate producer site context, bound executor/network work, and verify failure observability and recovery. Do not confuse durable Kafka outbox guarantees with realtime delivery guarantees. |
| D7 | Current-site resolver chooses MAIN; no site picker or complete second-site onboarding. | Explicit current product limitation | Introduce selection/onboarding only after legacy security and UI isolation gates pass. Ensure memberships, category rows and canonical NOT_ASSIGNED row exist. No site switcher is required merely to repair G1–G8. |

## Legacy routes intentionally owned elsewhere

- **Phase 7 shipments/receiving/tracking:** `/api/shipments`, tracking/webhook flows and their
  location selection. The stock picker migration must not accidentally claim shipment migration.
- **Phase 7 displays:** `/api/machine-displays`, display transfer/location detail reads.
- **Phase 7 Kuji/lootbox:** `/api/kuji-boxes`, `/api/lootbox`, and
  `/api/inventory/by-product/{productId}` used by Kuji dialogs. Keep the non-MAIN unavailable gate.
- **Phase 7 audit/notifications/reviews:** `/api/audit-logs`, stock audit-log dashboard reads,
  notifications and reviews. Grouped audit logs have a different contract from v1 movement history.
- **Phase 7 analytics/forecasting:** their APIs, projection ownership and global stock compatibility.
- **Catalog compatibility:** Products still joins legacy catalog/Kuji display fields with scoped
  site-products and inventory totals by explicit Phase 5d decision. This is not evidence that
  quantities still use legacy totals. Track remaining catalog cleanup with its owning slice.
- **Phase 8:** audited inter-site transfers. Existing stock transfers remain same-site operations.
- **Compatibility retention:** deprecated inventory endpoints can remain until supported clients
  migrate, logs show no supported legacy traffic, contract checks pass, and a rollback-compatible
  release completes its stabilization window. Security hardening is not deferred by this gate.

## Suggested execution order

1. **Compatibility security (Full).** Address G3–G5 first. Inventory every retained inventory/sites
   HTTP adapter, derive trusted MAIN context and actor, add membership/resource-parent tests, and
   preserve supported contracts. Test with an ordinary MAIN member, a SECOND-only member, no/revoked
   membership and a system admin under its explicit policy. Run PostgreSQL-backed HTTP tests.
2. **Complete web adoption (Full due to tenant boundary).** Address G1–G2 and P1 in one coherent
   slice. Reuse existing v1 routes, site-qualified caches, origin-site mutation variables and real
   dialog tests. Include initial stock and Storage creation/rename/delete. Keep Phase 7 workflows
   separately inventoried. No backend contract expansion is necessary merely for location CRUD.
3. **Local correctness and retry UX.** Address G6–G8 with meaningful failure-first tests. Consolidate
   post-commit refresh so success never depends on a broadcast. Add P5's explicit uncertain-result
   retry as a separate, documented behavior change if included; preserve fresh keys for genuinely
   new operations. Close P2 through an explicit deliver/defer decision.
4. **Integrity and repeatable deployment (Full, separate from UI).** Address D1–D3 with expand,
   backfill, verify, constrain and rollback tests. Make local seeded/fresh/existing databases
   reproducible. Production apply remains a separate explicitly authorized final action.
5. **Realtime/event rollout (Full).** Address D4–D6 with configured-service authorization and ordering
   proof. Do not flip Kafka keys or production realtime policies as incidental cleanup. Prioritize
   D5 alongside step 1 if a second site is to be enabled before the other steps finish.
6. **Close evidence and documentation.** Address P3–P4/P6–P7. Run all relevant native gates, record
   current independent PR results, update the route map and AC dispositions, and assign every
   remaining deferral an owner/trigger. Complete second-site product work only after its gates.

Each implementation slice should receive its own appropriately scoped SDD execution record,
failing regression tests where meaningful, independent Full-tier review where applicable, and
focused commits. This plan is not approval to refactor unrelated domains or deploy changes.

## Acceptance-criteria disposition after audit

- **AC-1:** module boundary evidence exists; no new confirmed ownership defect found in this audit.
- **AC-2:** local schema/compatibility proof exists, with D1–D3 enforcement and rollout obligations.
- **AC-3:** v1 protections have tests; retained HTTP compatibility surfaces require G3–G5 hardening.
- **AC-4:** no new confirmed transaction atomicity defect found; D4 cutover and P5 retry UX remain
  distinct from existing backend idempotency tests.
- **AC-5:** v1 inventory routes exist; remaining browser callers and stale route-map claims require
  G1–G2/P7. Legacy route presence alone is not a violation.
- **AC-6:** G1–G2/G8/P1–P2 prevent an unqualified claim of complete web adoption and rendered proof.
- **AC-7:** G6–G7 require local-refresh fixes; P3/D5 qualify performance and site isolation claims.
- **AC-8:** totals measurements are useful but narrower than whole-workflow savings; current full
  backend/PR proof and reconciled documentation remain P4/P6/P7.
