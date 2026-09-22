# Review

## Scope
Independent Standards and Spec passes reviewed the frontend diff against AC-1–5. No backend/security/schema changes were included.

## Findings
- Standards P2: dropping legacy location-list invalidation breaks Phase 7 picker freshness after MAIN CRUD — **act on** (AC-4). Fixed by capturing origin site code/type and refreshing the legacy key only for MAIN-origin writes. Added failure-first populated-cache tests and a pending site-switch assertion. Independent re-review: clear.
- Spec: no blocking implementation findings within AC-1–4. Finalize validation records for AC-5 — **act on**, completed with final 62-file/441-test, typecheck and lint evidence.

## Result
Both independent passes clear; no outstanding blockers for this slice.

## Residual risk
Backend legacy authorization, other frontend refresh/error/retry audit debt, Phase 7 API migration and full dialog/cart site-switch behavior remain separately scoped. No production or live-browser verification was performed; site transitions are simulated in regression tests. PR gate remains independent proof after push.

## Lazy display-transfer follow-up
- Independent Standards: no blockers; closed queries disabled, scoped lookup correct. In-flight requests are not canceled (not required).
- Independent Spec: AC-6 satisfied, no blockers. Selected-target close branch is verified by inspection; rendered test covers closed mount, open and close/invalidation, without selecting a destination.

## Hidden UI and realtime cleanup
- Standards and Spec independently flagged pending pre-event detail reuse as required AC-8 fix. **Acted on:** cancel exact query, invalidate without automatic refetch, fetch shared detail, ignore cancellation in older handler. Deferred-response test covers final detail/list convergence. Both rereviews clear.
- AC-7 matches scope. Sheet closed-mount test does not provide browser-level reopening/animation/draft-reset proof; reset follows inner component unmount. Product form/Add Display gate tests pass.
