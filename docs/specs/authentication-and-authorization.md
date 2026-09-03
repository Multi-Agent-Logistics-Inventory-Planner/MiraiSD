# Authentication and Authorization Specification

- Status: Draft
- Date: 2026-08-11
- Scope: Spring API, Supabase identity integration, web and mobile authorization context
- Roadmap: [Enterprise modernization](../roadmap/enterprise-modernization.md)

## 1. Security objectives

- Supabase authenticates the human identity; MiraiSD authorizes business access.
- No user-editable JWT claim may grant backend authority.
- All non-system access to site-owned data requires an active membership.
- Backend decisions remain correct when requests bypass the web or mobile UI.
- Authentication and authorization failures are observable without leaking sensitive information.

## 2. Identity model

The JWT `sub` claim identifies the Supabase account. MiraiSD MUST map it to one backend user record.
Email and display name are profile attributes, not stable authorization identifiers.

Authorization consists of:

- one backend-controlled role per user (`ADMIN`, `ASSISTANT_MANAGER` or `EMPLOYEE`), or the
  separate global `SYSTEM_ADMIN` flag;
- zero or more `user_site_memberships`, each granting access to one site.

Role is a property of the person, not the site: a user's role is the same at every site they can
access. `user_site_memberships` answers a different question — which sites a user may reach at
all, and whether that access is currently active — not what they may do once there. The permission
matrix for each role MUST be defined once in backend code and covered by parameterized tests.
Clients receive effective permissions for presentation but cannot grant them.

## 3. JWT validation

The API MUST validate:

- signature using the configured Supabase verification mechanism;
- expiration and not-before when present;
- exact issuer;
- allowed audience;
- non-empty subject;
- expected token type or role claim where Supabase configuration requires it.

Verification configuration MUST support documented key rotation. Secrets and keys MUST never be
logged. A malformed, expired or invalid token returns `401`; an authenticated user lacking permission
returns `403`.

`user_metadata` MAY be used for non-authoritative display information. `user_metadata.role` MUST be
ignored. Protected `app_metadata` MAY only be used if a later ADR defines its source, refresh and
revocation semantics; database membership remains authoritative for site access.

## 4. Authenticated principal

Spring Security MUST expose an immutable principal containing the backend user ID and Supabase
subject. It MUST NOT place a client-provided actor ID or role into trusted request state.

Mutation actor identity is derived from this principal. Requests that currently accept `actorId`
MUST migrate away from trusting that field; compatibility DTO fields are ignored or verified during
the transition.

## 5. Membership authorization

`user_site_memberships` is unique on `(user_id, site_id)` and contains active status, timestamps
and an optimistic version — no role column. Membership and role changes are audited.

For `/api/v1/sites/{siteId}/...` the backend MUST:

1. Authenticate the JWT.
2. Resolve the backend user by subject, including their role.
3. Load the target site and confirm an active membership exists.
4. Evaluate the required permission against the user's role.
5. Produce `AuthorizedSiteContext`.
6. Execute a site-scoped use case and repository query.

`AuthorizedSiteContext` contains backend user ID, site ID, the user's role/effective permissions,
system-admin indicator and correlation ID. It cannot be constructed solely from headers or DTO
values.

System-administrator bypasses MUST be explicit and audited. They MUST NOT silently create a normal
site membership.

## 6. Membership lifecycle

- Invitation acceptance creates or activates membership through a transactional backend use case.
- Deactivation prevents new authorized requests immediately after the configured cache window.
- Deleted or suspended backend users cannot authorize even with an otherwise valid Supabase token.
- A user with no active sites receives an authenticated response with an empty site list, not access
  to MAIN by default.
- Duplicate invitation acceptance is idempotent.
- Role changes (on the user record) and membership activation/deactivation (per site) each record
  actor, target user, site where applicable, previous value and new value.

Authorization caches, if introduced, MUST use bounded TTLs and explicit invalidation on membership
changes. Security correctness must not depend on a client refreshing its token.

## 7. Client contract

The API provides at least:

```text
GET /api/v1/me
GET /api/v1/me/sites
GET /api/v1/sites/{siteId}/permissions
```

The response includes display-safe profile information and effective permissions. Web and mobile use
it to render navigation and actions, but every protected endpoint independently authorizes.

Tokens are stored using Supabase's appropriate browser session mechanism on web and platform-secure
storage on mobile. Logs, analytics and error reports MUST redact tokens.

## 8. Required tests

- Valid, expired, premature, malformed and incorrectly signed tokens.
- Incorrect issuer, audience and missing subject.
- Forged `user_metadata.role=ADMIN` grants no authority.
- Every permission across each role.
- Active, inactive, absent and foreign-site memberships.
- Suspended/deleted backend user.
- System administrator access and auditing.
- Role change and deactivation cache invalidation.
- Actor spoofing through request fields.
- Invitation replay/idempotency.

## 9. Acceptance criteria

- No backend authority is derived from `user_metadata`.
- All site-owned endpoints use membership-derived authorization.
- `401` and `403` behavior is consistent and contract-tested.
- Security-sensitive membership changes take effect within the documented maximum interval.
- Clients cannot alter actor identity, site identity or effective permissions by changing request data.
