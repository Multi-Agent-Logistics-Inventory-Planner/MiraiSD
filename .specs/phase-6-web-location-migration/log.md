# Implementation log

## Current handoff
- Status: frontend migration, lazy display-transfer and hidden UI/realtime cleanup implemented, validated and independently reviewed.
- Next action: user runtime verification; implementation was committed in `f99a95b`, `1797fc1`, and `2cf2e13`.
- Decisions: destination lookup uses current-site v1; closed transfer dialog disables its product, location and display queries. Display operations remain legacy Phase 7.
- Last verified: full web tests (65 files, 447 tests), TypeScript, ESLint (0 errors, 51 existing warnings), git diff --check pass.
- Limits: no browser runtime verification; disabling queries does not cancel already-running requests. Backend unchanged.

## Implementation
- Added generated v1 create/update/delete location helpers using the shipped SiteLocationDTO contract. Removed unused legacy write/storage-code helpers.
- Added a gated, site-qualified location-options hook. Stock/initial-stock selector now uses it; explicit LegacyLocationSelector preserves Kuji reads. Shipment/display hooks remain unchanged.
- Storage creation resolves category IDs within the requested site. A shared CRUD wrapper captures origin site in mutation variables, uses it for writes and invalidation, and rejects missing site before HTTP.
- Scoped invalidation includes location lists/counts and inventory detail labels after rename/delete. Selector exposes failure/retry and clears old selection on site change.
- Native gates: 62 files/439 tests pass; tsc passes; lint 0 errors/51 baseline warnings. Independent reviews completed; see review.md.

## Independent review fix
Standards found that removing legacy location-list invalidation would leave Phase 7 pickers stale after MAIN CRUD. Added a failure-first populated-cache regression (1 failed/4 passed before fix), then captured origin site code/type in mutation variables and restored MAIN-only legacy invalidation. Pending site-change test verifies original MAIN compatibility cache refresh. All five focused mutation tests pass; full suite now 441 tests. Re-review clear.

## Display transfer follow-up
User authorized lazy fetching and v1 destination lookup. Retain Full tier for this existing slice. Plan: rendered query regression, gate dialog-owned requests, validate and review. Preserve all prior dirty changes; prior staging was canceled.

Follow-up regression failed against the eager legacy lookup, then passed after gating. Standards and Spec reviews found no blockers. Spec noted selected-target close behavior is inspected but not directly exercised by the test.

## Hidden UI follow-up plan
Full-tier continuation using mirai-sdd. Gate ProductForm detail and AddDisplay products by open; conditionally mount location sheet contents to cover nested queries without expanding hook interfaces. Closing resets sheet-local drafts. Consolidate realtime detail refresh through fetchQuery and avoid overlapping prefix/child refetches. No Kafka/backend/config changes.

Hidden UI cleanup completed: edit detail and Add Display products gated by open; LocationDetailSheet contents unmount while closed, resetting local drafts. Product broadcast uses exact cancellation then non-refetching invalidation and one fetchQuery shared with lists. Independent reviewers caught a pre-event pending-read race in the first consolidation; fixed with cancellation and regression proving late old response cannot replace latest caches. Both rereviews clear. Initial detail/duplicate-fetch regressions failed before implementation; final full web tests 447/65, typecheck and lint pass (51 baseline warnings). No deployment/rebuild was performed; implementation is committed.
