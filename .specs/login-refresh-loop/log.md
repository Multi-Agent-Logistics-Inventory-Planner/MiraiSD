# Implementation log

## Current handoff
- Status: Fix implemented; all native checks and independent reviews passed.
- Next action: User reloads localhost login to confirm runtime recovery; PR gate remains independent proof.
- Decisions: Fix the demonstrated unauthenticated membership/reload chain; do not conflate it with the separately reported refresh-token error.
- Last verified: Final redirect-helper suite 7/7 passed; full web suite 423 passing tests; TypeScript passed; lint 0 errors / 51 warnings.
- Open risks: User confirmed localhost before sign-in. No connected browser for live verification; separate refresh-token cause unproven.

## Assumptions and decisions
- Clean worktree before edits. Treat this auth lifecycle fix as Full.
- Preserve existing realtime event handling and backend authorization.
- The middleware cookie propagation defect is separate and outside this patch.
- Unrelated dev-db-setup spec/scripts appeared during work and are preserved untouched.

## Task record
- T-1: Added regression tests before implementation; observed 3 expected failures.
- T-2: Moved realtime from root query context into dashboard AuthProvider, gated
  the subscriber on validated auth, and suppressed login self-navigation.
- T-3: Full suite, lint, TypeScript and diff checks passed. Independent parallel
  Standards/Spec reviews found no code defects. Strengthened URL fixtures per
  optional review suggestion. Live browser unavailable.

## Test plan
- AC-1: Render root QueryProvider with real realtime/site hooks and mocked API boundary.
- AC-2: Test auth pending, authenticated, signed-out transitions and subscription cleanup.
- AC-3: Exercise redirect helper on login and protected URLs.
