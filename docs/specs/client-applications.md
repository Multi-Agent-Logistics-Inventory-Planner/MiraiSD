# Client Applications Specification

- Status: Draft
- Date: 2026-08-11
- Scope: `apps/web`, future `apps/mobile`, and shared TypeScript packages
- API dependency: [Multi-site data and API](multi-site-data-and-api.md)

## 1. Target structure

```text
apps/
├── web/                       Next.js client and required server routes
└── mobile/                    Expo/React Native client
packages/
├── contracts/                 OpenAPI and versioned JSON event schemas
├── api-client/                Generated platform-neutral TypeScript client
└── client-domain/             Shared Zod schemas, query keys and pure utilities
```

Web and mobile are separate deployable applications. They share contracts and pure behavior, not UI
components by default. Micro-frontends are outside this design.

## 2. Business-logic boundary

The backend is authoritative for authorization, site isolation, inventory, money, probabilities,
workflow transitions, idempotency, audit and canonical operational statuses. Clients MAY duplicate
simple validation for feedback but cannot rely on it for correctness.

Clients own rendering, navigation, local form state, safe caching, accessibility, formatting and
temporary view filters. A value used by reporting, permissions, mobile and web workflows SHOULD be
returned canonically by the API instead of independently derived in each client.

## 3. Modular client organization

Both applications organize code by feature: identity/sites, catalog, inventory, transfers,
shipments, displays, Kuji/lootbox, notifications and analytics. Feature modules expose narrow client
entry points and do not import another feature's internal components or state.

Site ID is part of every site-owned query key. Switching sites cancels or invalidates previous-site
queries and removes sensitive transient state.

## 4. Generated API client

OpenAPI generates request/response types and transport functions. The generated package is
platform-neutral and accepts injected behavior:

```ts
createApiClient({ baseUrl, fetch, getAccessToken, onUnauthorized, correlationId })
```

It MUST NOT import browser globals, Next.js routing, Supabase browser clients or React Native secure
storage. Web and mobile provide adapters. Generation is deterministic, checked in CI and protected by
OpenAPI compatibility checks.

`client-domain` may contain Zod schemas, permission display helpers, query-key factories and pure
formatting/domain utilities safe for both platforms. It MUST NOT contain secrets, persistence APIs or
authoritative business decisions.

## 5. Web application

The current Next.js application has a server runtime, including route handlers, and is not assumed to
be a static bucket export. Its deployment MUST either retain a Next.js runtime or explicitly move
server routes before static export.

Web migration includes:

- API v1 and generated-client adoption;
- membership-driven site selection;
- site-aware query and realtime keys;
- backend-returned effective permissions for UI gating;
- correction of lint, typecheck, test and React lifecycle failures;
- review of process-local caches and rate limits in Next.js server routes;
- correlation propagation; optional error-monitoring SaaS requires a separate cost decision.

Client permission checks hide or disable controls only. Backend rejection remains authoritative.

## 6. Expo mobile application

`apps/mobile` uses Expo Router, TypeScript, TanStack Query, Supabase authentication and the generated
API client. Initial workflows are:

- authentication and invitation deep links;
- site selection and secure persistence of the last site;
- inventory lookup, barcode and search;
- stock adjustment;
- shipment receiving;
- inter-site transfers;
- notifications and audit confirmation.

The launch scope SHOULD prioritize inventory lookup/barcode, adjustment, receiving and transfers.
Notifications and audit confirmation MAY follow after the core workflows are validated. Expo starts
on the Free plan; a paid tier requires documented usage and cost justification.

Mutations require connectivity; Wi-Fi and cellular both qualify. The application MUST NOT queue
inventory mutations for later offline replay. Safe reads MAY be cached for responsiveness and clearly
marked stale when offline.

Tokens use platform-secure storage. Roles are never trusted solely from client state. Token expiry,
membership loss and site revocation return the user to a safe authenticated or signed-out state.

## 7. UI sharing policy

React DOM and React Native components are not shared by default. Tokens or semantic design values MAY
be shared if platform behavior remains accessible. Shared packages prioritize contracts, query-key
conventions, validation and pure utilities.

## 8. Realtime behavior

Clients subscribe only to an authorized selected-site channel. Site switching unsubscribes from the
previous channel before using new data. Realtime messages trigger site-qualified cache invalidation;
they do not directly authorize or apply sensitive mutations.

## 9. Required tests

- Generated-client regeneration and API compatibility.
- Web lint, typecheck, unit, component and core Playwright flows.
- Mobile authentication, deep links, site selection and token expiry.
- Wi-Fi/cellular success, connectivity loss and explicit refusal to queue mutations.
- Foreign-site data never remains visible after switching or membership revocation.
- Consistent canonical statuses and permissions across web and mobile.

## 10. Acceptance criteria

- Both clients use the same generated API v1 contract.
- Site-owned caches and realtime subscriptions are site-qualified.
- No client is an authority for permissions or business invariants.
- Mobile core workflows pass online E2E tests and do not queue offline mutations.
- The web deployment model explicitly accounts for its Next.js server routes.
