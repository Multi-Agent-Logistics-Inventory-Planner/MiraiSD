# Review - Phase 7 team users scoping

## Pass 1: independent Spring and web review (mirai-spring-reviewer)

Verdict: approve with advisories, no blockers. The reviewer ran the real routes against Postgres and mutation-checked the two filters: removing `sm.site.id = :siteId` fails `lastAudits_ignoreOtherSites`, and dropping the active-member filter fails `getSiteLastAuditDates_KeepsOnlyActiveMembersOfTheSite`. The trusted site comes from `AuthorizedSiteContextHolder`, not the path variable.

| Finding | Severity | Resolution |
| --- | --- | --- |
| Legacy routes had no committed cross-site test and `/{id}/last-audit` was missing from the header test | should-fix | Fixed: `legacyRoutes_excludeUsersOutsideMain`, header loop extended |
| v1 routes only tested as ADMIN; non-member check accepted any 4xx | should-fix | Fixed: `nonAdminRoles_readOwnSiteOnly` (EMPLOYEE, ASSISTANT_MANAGER, 401), non-member asserts 403 |
| `findTopByActorIdOrderByAtDesc` (unscoped) still in the repository | nit | Fixed: the legacy IT uses the site-scoped finder, method deleted |
| Deprecation headers missing on error responses | nit | Fixed test-first: `LegacyUserDeprecationFilter` replaces per-response headers; GET list and last-audit paths only |
| Audit-log employee filter now omits deactivated members | nit | Recorded in spec D-1 as a follow-up |
| No web test for a site change refetching | residual | Added to `use-users.test.ts` |

## Not covered

- Pending invitations stay global (no site on invitations); out of scope in the spec.
- Playwright and `tests/e2e` were not run.
