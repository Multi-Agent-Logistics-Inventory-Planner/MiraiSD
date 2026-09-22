# Stop the signed-out login refresh loop

## Tier
Full — authentication lifecycle.

## Problem and outcome
The root QueryProvider mounts RealtimeProvider on public auth pages. Its site hook
requests authenticated `/api/v1/me/sites`; a 401 navigates to `/login` even when
already there, remounting the same query. Public auth pages must remain usable
without a session, and dashboard realtime must wait for validated authentication.

## Durable context
- [Authentication](../../docs/specs/authentication-and-authorization.md), sections 3, 7.
- [Clients](../../docs/specs/client-applications.md), sections 5, 8.
- Plan: None. No API, backend authority, or event contract changes.

## Acceptance criteria
- AC-1: Public root query context issues no realtime membership request.
- AC-2: Dashboard realtime starts only after auth validation, and unmounts on sign-out; children remain rendered while auth is pending.
- AC-3: Unauthorized navigation does not reload `/login` (including query strings/trailing slash), but still redirects protected routes.

## Tasks
- T-1: Reproduce with focused regression tests.
- T-2: Scope realtime to authenticated dashboard; guard login self-navigation.
- T-3: Run native checks, independent Standards/Spec reviews, record results and commit.
