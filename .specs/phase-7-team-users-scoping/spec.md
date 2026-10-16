# Phase 7 - Team users scoping

## Tier

Full - authorization, public contracts and tenant isolation of user data are involved.

## Problem and outcome

The Team page Members tab and the audit-log user filter call `GET /api/users`, which returns every user in the organization. The "last audit" column calls `GET /api/users/last-audits`, which takes the latest stock movement per actor across all sites. A user who works at one site is shown, with activity from other sites, on every site's pages.

This slice adds site-scoped read routes for members and their last audit time, points the web at them, and makes the legacy list and last-audit routes resolve MAIN with deprecation headers.

## Durable context

- Plan: [Enterprise modernization, Phase 7](../../docs/plans/enterprise-modernization.md#10-phase-7--shipments-and-remaining-site-operations)
- Specs: [Multi-site data and API](../../docs/specs/multi-site-data-and-api.md)
- Route map: [api-v1-map](../../docs/baseline/api-v1-map.md) row for `UserController /api/users`
- Predecessor: [phase-7-notifications-reviews-audit](../phase-7-notifications-reviews-audit/spec.md), which found the gap.

## Acceptance criteria

- AC-1: `GET /api/v1/sites/{siteId}/users` returns only users with an active membership at that site, ordered by name. A caller with no membership at the site gets no data.
- AC-2: `GET /api/v1/sites/{siteId}/users/last-audits` returns, per active member, the time of their latest stock movement at that site. Movements at other sites do not count.
- AC-3: Legacy `GET /api/users`, `/api/users/last-audits` and `/api/users/{id}/last-audit` resolve the MAIN site, return the same scoped data, and carry `Deprecation` and `Link` headers. The unscoped port and repository queries are removed, and the headers are also sent on error responses.
- AC-4: The Team Members tab and the audit-log user filter call the v1 routes, only when a current site is resolved, with site-qualified query keys. Switching sites never shows the previous site's members.
- AC-5: `openapi.json` and the generated client are regenerated; the route map, ArchUnit baseline and `.specs` records are updated.
- AC-6: Backend and web tests prove AC-1 to AC-4, including a cross-site case for each backend route.

## Out of scope

- Pending invitations (`/api/admin/invitations`): invitations carry no site today, so the Members tab keeps showing every pending invitation.
- Global identity lookups by id, email or full name, and the admin write routes (create, update, delete, membership grant and revoke).
- Per-site review tracking and email visibility on review routes.

## Decisions and assumptions

- D-1: The site list returns active members only. A deactivated member no longer appears in the Members tab; reactivating is done through the admin membership route. The audit-log employee filter uses the same list, so a deactivated member's past movements can no longer be picked there. Recorded as a follow-up if admins need to see deactivated members in either place.
- D-2: Roles for the new routes match the legacy ones (`ADMIN`, `ASSISTANT_MANAGER`, `EMPLOYEE`). Emails stay in the response as before.
- D-3: No v1 per-user last-audit route; nothing in the web calls it.

## Tasks

- T-1: Failing tests for the repository query, port, service and both v1 routes (member filtering, cross-site movements, non-member caller).
- T-2: Implement the site-scoped port and adapter query, service methods and `SiteUserController`; scope the legacy routes to MAIN with deprecation headers.
- T-3: Failing web tests, then `lib/api/site-users.ts`, site-gated hooks, and removal of the unscoped client functions.
- T-4: Regenerate contract and client; update the route map and ArchUnit baseline.
- T-5: Run the backend and web gates, record `review.md` and `validation.md`.
