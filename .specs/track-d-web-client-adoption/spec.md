# Phase 3 Track D — adopt the generated API client in one read-only web workflow

## Tier

Full (reclassified — see below)

Original rationale (superseded): web-only change; no `inventory-service` code,
migration, contract, or RBAC/auth rule changes. The site-selection layer only
*reads* an already-shipped Phase 4 endpoint (`GET /api/v1/me/sites`); it does
not add or change authorization behavior.

**Reclassified to Full (post-review).** Making `@mirai/api-client` a real
dependency of `apps/web` required widening the web Docker build context from
`apps/web` to the repo root, changing: `apps/web/Dockerfile`, its standalone
output tracing boundary (`next.config.mjs`'s `outputFileTracingRoot`), the PR
Docker gate (`pr-gate.yml`'s `docker-build-check`), the production deploy
build trigger (`deploy.yml`'s `build-push-web`), the CI test/typecheck/lint
triggers (`ci.yml`), and the dev Compose build (`infra/docker-compose.dev.yml`).
An initial pass judged this "how the image is built, not what gets deployed"
and kept the tier at Standard — a second review correctly rejected that
distinction: `AGENTS.md`'s Full-tier trigger is "deployment changes" without
a topology/secrets carve-out, and this changes what the PR gate builds, what
triggers a production image rebuild, and what CI validates before either
happens. Reclassified to Full; `review.md` and `validation.md` added.

## Problem and outcome

`docs/plans/enterprise-modernization.md` Phase 3 left "adopt the generated
client in one read-only web workflow" (Track D) explicitly blocked: the
contract had no `/api/v1/sites/{siteId}/...` routes for a real client to
adopt. Phase 4 (merged) shipped those routes — `GET /api/v1/me/sites`,
`GET /api/v1/sites/{siteId}/locations`, `GET /api/v1/sites/{siteId}/storage-locations`
— which unblocks Track D.

Today `apps/web` has no site-selection concept at all: every API call goes
through a hand-rolled fetch wrapper (`apps/web/src/lib/api/client.ts`) hitting
unscoped legacy routes (`/api/locations`, `/api/storage-locations`), which the
backend silently resolves to the `MAIN` site via a hardcoded
`DEFAULT_SITE_CODE` constant (see `LocationService`). There is currently no
way for the frontend to address any other site, and no site-qualified query
cache.

Outcome: the web app gains (1) an instantiated, platform-neutral generated
API client (`packages/api-client`) sharing the existing Supabase-token and
401-redirect behavior, (2) a `useCurrentSite()` hook that silently resolves to
the caller's site with **no visible switcher UI** (locked product decision —
every current user is mapped to `MAIN` only), and (3) one migrated read
workflow that reaches real UI end to end: the storage-location category tab
bar on the Locations page (`useStorageLocations`), now calling the real
`GET /api/v1/sites/{siteId}/storage-locations` with a site-qualified
TanStack Query key, instead of the legacy site-blind endpoint.

**Revised scope (post-review).** The original plan also targeted the plain
locations list (`getLocations`/`getLocationsByType`, consumed via
`useLocations`/`useLocationsOnly`). Investigation after an initial pass
showed two things that narrowed this: (a) the Locations page's actual row
table renders from `useLocationsWithCounts` → legacy
`getLocationsWithCounts`, which has no `/api/v1` equivalent yet (out of
scope, needs backend work) — so migrating `useLocations`/`useLocationsOnly`
would not have moved that page's real data onto the new path at all; and
(b) those two hooks' actual consumers are three unrelated workflows —
shipment receiving, stock location selection, and machine-display transfer
(`shipment-receive-dialog.tsx`, `location-selector.tsx`,
`transfer-display-dialog.tsx`) — none of which were in scope or
smoke-tested. Migrating them would have been undeclared scope creep into
untested workflows for no UI benefit. Decision: `useLocations`/
`useLocationsOnly`/`getLocations`/`getLocationsByType` are fully reverted to
legacy, unscoped behavior (zero diff from `main`). The storage-location
category tab bar remains the one workflow migrated all the way to real,
verified UI.

This is intentionally narrow. It does not migrate location mutations, the
plain locations list, or any other feature module, and does not change
backend behavior — it proves the generated-client adoption pattern on the
smallest safe slice that actually reaches production UI, so later phases
(5+) can repeat it on a real backend-backed "with counts" v1 endpoint.

## Durable context

- Specs: [Client applications](../../docs/specs/client-applications.md) §4-5,
  [Multi-site data and API](../../docs/specs/multi-site-data-and-api.md)
- ADRs: None directly (no architecture change; consumes ADR-0001's existing
  module boundaries as-is)
- Plan: [Enterprise modernization](../../docs/plans/enterprise-modernization.md)
  §6 (Phase 3, Track D)

## Product decision (recorded, not open)

Site resolution is silent — no visible site switcher in this feature.
`useCurrentSite()` resolves to the user's site from `GET /api/v1/me/sites`
(effectively always `MAIN` today, since no user currently holds a `SECOND`
membership) and exposes only `{ siteId, siteCode, isLoading, error }`. A
visible switcher is out of scope until `SECOND` has real members/inventory.

## Out of scope

- Location create/edit/delete (`use-location-mutations.ts`) — stays on legacy
  `/api/locations` mutations.
- `getLocationsWithCounts` (`/api/locations/with-counts`) and the Locations
  page's row table (`useLocationsWithCounts`) — no `/api/v1` equivalent
  exists yet; the page's primary data stays on the legacy, site-blind route.
- The plain locations list (`getLocations`/`getLocationsByType`,
  `useLocations`/`useLocationsOnly`) and its real consumers — shipment
  receiving, stock location selection, machine-display transfer
  (`shipment-receive-dialog.tsx`, `location-selector.tsx`,
  `transfer-display-dialog.tsx`) — reverted to legacy after review found
  they were the actual (undeclared) blast radius of migrating those hooks.
- Every other feature module (products, inventory, shipments, kuji, etc.) —
  untouched, still on the legacy wrapper.
- Any backend, migration, or contract/schema regeneration — this consumes an
  already-generated, already-merged contract (`packages/contracts/openapi.json`,
  `packages/api-client/src/schema.d.ts` must show zero diff).

## Acceptance criteria

- AC-1: A single generated `ApiClient` instance exists for the web app,
  sharing the same Supabase-session token retrieval and 401→`/login` redirect
  behavior as the legacy wrapper (no divergence in auth/redirect behavior
  between the two paths).
- AC-2: `useCurrentSite()` resolves a site ID with no rendered switcher UI
  anywhere in the app; it exposes a loading and error state and never falls
  back to an implicit/hardcoded site ID client-side.
- AC-3: The Locations page's storage-location category tab bar is fetched via
  `GET /api/v1/sites/{siteId}/storage-locations` (not the legacy unscoped
  route), with `siteId` sourced from `useCurrentSite()`. Implemented as a new
  function (`getSiteStorageLocations`) alongside the untouched legacy
  `getStorageLocations`/`getStorageLocationByCode`.
- AC-4: The TanStack Query key for the migrated read includes `siteId`
  (`["storageLocations", siteId]`), and the query stays disabled until
  `siteId` resolves.
- AC-5: Location mutations, `getLocationsWithCounts`, and the plain locations
  list (`getLocations`/`getLocationsByType`, `useLocations`/
  `useLocationsOnly`, and their shipment/stock-selector/machine-display
  consumers) are unchanged — zero diff from `main` — and continue to work
  against legacy endpoints.
- AC-6: `packages/contracts/openapi.json` and
  `packages/api-client/src/schema.d.ts` are unmodified by this feature
  (`git diff --stat` empty for both paths — proves no backend/contract drift).
  `packages/api-client/src/index.ts` may gain a one-line type re-export
  (`components`, alongside the existing `paths`) since it wasn't previously
  exported and callers need it to type generated-client responses; this is
  the one accepted exception and is not contract drift. `tests/contracts`
  (Kafka event-envelope schema tests) is unrelated to this REST-only change
  and is unaffected.
- AC-7: The Locations page renders correctly end-to-end (manual or
  Playwright smoke check) with the tab bar on the new read path, and its row
  table (still legacy) is unaffected.
- AC-8: The web Docker image builds and runs with the new
  `@mirai/api-client` dependency — verified by an actual local
  `docker build` + `docker run` (not just config review) — and every
  build-context reference to `apps/web` (`Dockerfile`, `pr-gate.yml`'s
  `docker-build-check`, `deploy.yml`'s `build-push-web`,
  `infra/docker-compose.dev.yml`'s `frontend` service) is updated
  consistently to the repo root, with `packages/**` changes correctly
  triggering the web build/deploy path alongside `apps/web/**` changes.

## Tasks

- T-1: Extract shared auth-token + 401-redirect logic out of
  `apps/web/src/lib/api/client.ts` into a shared module
  (`apps/web/src/lib/api/auth-token.ts`), with no behavior change to the
  legacy wrapper. Verified: existing `client.test.ts` passes unmodified; add a
  unit test for the extracted module.
- T-2: Instantiate the generated client
  (`apps/web/src/lib/api/generated-client.ts`) via `createApiClient`, wired to
  T-1's token/redirect helpers and `BACKEND_BASE_URL`. Verified: unit test
  confirming the Authorization header is attached and `onUnauthorized` fires
  on a 401.
- T-3: Add `useCurrentSite()` (`apps/web/src/hooks/use-current-site.ts`)
  calling `GET /api/v1/me/sites` through the T-2 client, silently resolving to
  the caller's site with no UI, query key `["me", "sites"]`. This adds one
  request per session that the legacy path never needed (it silently assumed
  MAIN), so set a long `staleTime` (e.g. `Infinity` or session-lifetime,
  matching that membership essentially never changes mid-session) to avoid
  refetching it on every navigation/component mount — a caching choice, not an
  N+1, since the query itself is already two fixed lookups server-side
  regardless of list size. Verified: unit test with mocked response resolving
  `siteId`, plus an empty/error-response case; assert the query does not
  refire on remount within the same session.
- T-4: **Dropped after review.** Originally "add
  `getSiteLocations`/`getSiteLocationsByType`". Removed once review found
  their only real consumers (`useLocations`/`useLocationsOnly`) serve
  unrelated, out-of-scope workflows — see the revised-scope note above and
  `log.md`.
- T-5: Add `getSiteStorageLocations` (`apps/web/src/lib/api/locations.ts`)
  calling `GET /api/v1/sites/{siteId}/storage-locations` via the T-2 client,
  alongside the untouched legacy `getStorageLocations`/
  `getStorageLocationByCode`. `getLocationsWithCounts` untouched.
  `getSiteStorageLocationByCode` was dropped — added speculatively for
  parity but had no real caller. Verified: unit test asserting the
  site-scoped path and response mapping.
- T-6: Wire `siteId` through `use-storage-locations.ts` via
  `useCurrentSite()`, calling `getSiteStorageLocations`; site-qualify the
  query key; gate `enabled` on `siteId` resolution. `use-locations.ts` is
  fully reverted (zero diff from `main`) — see the revised-scope note.
  Verified: `use-current-site.test.ts` covers `siteId` resolution;
  full-suite green covers the hook wiring.
- T-7: Consumer smoke check — confirm `location-table.tsx`, `location-tabs.tsx`,
  `location-form.tsx`, and the three unrelated `useLocations`/
  `useLocationsOnly` consumers (`shipment-receive-dialog.tsx`,
  `location-selector.tsx`, `transfer-display-dialog.tsx`) still
  typecheck/render unchanged. Verified: typecheck passes; the latter three
  are additionally verified by zero diff on `use-locations.ts`, not by a new
  test. Playwright smoke check for the Locations page tab bar still
  outstanding.
- T-8: Contract-drift checklist — confirm zero diff under
  `packages/contracts/` and `packages/api-client/`; `tests/contracts` passes
  unchanged. Verified: `git diff --stat` empty; contracts suite green.
- T-9: **Added after review (P1).** Fix the web Docker build, broken by the
  new `@mirai/api-client` local package dependency: widen build context to
  the repo root (`apps/web/Dockerfile`, `pr-gate.yml`'s `docker-build-check`
  matrix, `deploy.yml`'s `build-push-web`, `infra/docker-compose.dev.yml`),
  add `outputFileTracingRoot` to `next.config.mjs` so standalone-output
  tracing includes the local package, add a root `.dockerignore` (none
  existed), and have `packages/api-client` install its own dependencies
  (`openapi-fetch`) in the image since it isn't part of an npm workspace and
  a plain `file:` dependency doesn't hoist a linked package's own
  dependencies anywhere Node would resolve them from that package's actual
  location. Verified: real `docker build` succeeds and a `docker run`
  container starts cleanly and serves a request (`307` to `/login`, no
  module-resolution errors) — see `log.md` for the exact failures hit and
  fixed along the way.
