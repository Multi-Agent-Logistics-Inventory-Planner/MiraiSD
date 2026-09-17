# Finish Phase 6 frontend location migration

## Tier
Full: frontend tenant boundary, existing generated contracts only.

## Durable context
- ../../docs/specs/client-applications.md
- ../../docs/specs/multi-site-data-and-api.md
- ../../docs/plans/enterprise-modernization.md (Phase 6/7 boundary)
- ../phase-6-inventory/spec.md (AC-6)

## Scope
Migrate remaining Phase 6 location selection (Adjust, Transfer, initial stock) and Storage CRUD to existing v1 routes. Backend, schema, security hardening, unrelated refresh/error debt and Phase 7 workflows are excluded. Shared Kuji selectors retain explicit legacy adapters; shipment hooks and display operations stay legacy; the display transfer destination lookup uses v1. No site switcher is introduced.

## Acceptance criteria
- AC-1: Phase 6 location selectors fetch v1 locations for the resolved site, with site-qualified keys; unresolved sites cannot request/select data. Late old-site responses cannot appear in the new site. NOT_ASSIGNED stays virtual until existing scoped resolution.
- AC-2: Storage create/rename/delete use generated v1 calls. Create resolves storage-category identity within the originating site. Missing membership/category and API failures fail explicitly; no legacy fallback.
- AC-3: CRUD requests and post-write invalidation retain their originating site across rerenders/pending requests; cache keys match scoped readers. Site changes clear the shared picker's old selection.
- AC-4: Phase 7 Kuji/shipment/display legacy callers preserve behavior. Remove unused frontend legacy write helpers, retain necessary legacy reads. No backend/contracts modifications.
- AC-5: Meaningful regression tests, full web tests, typecheck and lint pass (existing warning baseline allowed); independent Standards and Spec reviews recorded.

## Tasks
1. Add API/hook/rendered regression tests and observe failure.
2. Implement generated CRUD, scoped query and split site/legacy picker adapters.
3. Run web gates, review, update records and commit only scoped files.

## Follow-up: lazy display transfer queries
- AC-6: A closed TransferDisplayDialog performs no dialog-owned location, product, or display requests. Opening fetches destination locations via the current-site v1 lookup; closing disables those queries. No legacy location fallback. Display operations remain Phase 7.

## Follow-up: hidden UI and product refresh
- AC-7: Closed ProductForm disables its detail lookup; closed AddDisplayDialog disables its product lookup; closed LocationDetailSheet unmounts its query-owning content and nested dialogs. Reopening restores normal fetching and cached data; closing resets sheet-local drafts.
- AC-8: Product broadcasts refresh exact product detail through the shared query cache once, reusing it for list updates. Child/detail variants remain invalidated, with existing failure recovery and list filters preserved.
