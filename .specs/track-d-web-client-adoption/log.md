# Implementation log

## Review response 5 (R5 was incomplete: only the explicit signOut() path was fixed)

A follow-up review on `review.md`'s R5 fix found it was incomplete: `auth-
provider.tsx`'s `signOut()` callback clears the query cache, but that is
only one of three places the component clears user/session state on a
sign-out. The `onAuthStateChange` `SIGNED_OUT` event handler (the only path
for an *externally* triggered sign-out - token expiry, revocation, or a
sign-out fired from another tab) did not, and neither did
`validateAndSetUser`'s two failure paths (backend session validation
failing, or the request throwing). Since `["me","sites"]` has `staleTime:
Infinity`, any of these three untouched paths would leave that entry
cached for whoever signs in next in the same tab. Fixed: `queryClient.clear()`
added to the `SIGNED_OUT` handler and both `validateAndSetUser` failure
branches; `queryClient` added to `validateAndSetUser`'s `useCallback` deps
and the auth-effect's dependency array (matches `useQueryClient()`'s
singleton stability, so this doesn't cause the effect to re-run).

Verified with a real regression test (`auth-provider.test.tsx`, new file -
this component had no prior test coverage at all): seeds
`["me","sites"]` in a real `QueryClient`, renders `AuthProvider`, captures
the `onAuthStateChange` callback via a mocked Supabase client, fires
`SIGNED_OUT` directly (not via the app's own `signOut()`), and asserts the
cache entry is gone. Confirmed the test actually catches the regression:
temporarily reverted the `SIGNED_OUT` handler's `queryClient.clear()` line
and re-ran - test failed with the exact stale-data assertion, then passed
again once restored. Full suite: 292/292 (up from 291). Re-verified against
a genuinely clean install (`packages/api-client` then `apps/web` `npm ci`,
no leftover local installs): `tsc --noEmit`, `vitest run`, `eslint` all
clean.

## Review response 4 (Standards-pass findings from `mirai-next-reviewer`)

The third Standards-pass agent (`mirai-next-reviewer`, web/full-stack
conventions) independently re-verified the fixes from "Review response 3"
(reproduced the clean-CI `openapi-fetch` failure and confirmed the current
tree's two-stage `npm ci` fixes it; confirmed the `pr-gate.yml` boolean/
string matrix bug and that it's now fixed) and found five more real,
verified issues (R1-R5), all fixed:

- **R1 - an unresolved site (no error, just no membership) still rendered
  "run the database seeder".** `useCurrentSite()`'s fail-closed `null` on no
  `MAIN` membership carries no error, and `useStorageLocations` only
  propagated `siteError`, not this case. Fixed: `useStorageLocations` now
  synthesizes an error when the site query has settled with no `siteId` and
  no error of its own - a membership/authorization outcome must never
  present as a data-seeding problem.
- **R2 - non-ok responses with an empty body silently read as success.**
  openapi-fetch returns `{ error: undefined }` for a non-ok response with no
  body (204, HEAD, `Content-Length: 0` - e.g. a 502/504 through Caddy, or an
  empty 403), so both `getSiteStorageLocations` and `fetchCurrentSite` would
  have treated that as "resolved, no data" rather than a failure. Fixed via
  a shared `unwrapGeneratedResponse` helper (new, in `generated-client.ts`)
  that checks `response.ok` in addition to `error`.
- **R3 - thrown errors weren't real `Error` instances.** Both call sites did
  `throw error` where `error` is openapi-fetch's parsed error-schema value
  (a plain object or string, not an `Error`) - anything doing `.message` or
  relying on error-boundary `Error` handling would have broken. Fixed: the
  same `unwrapGeneratedResponse` helper throws a new `GeneratedApiError`
  (extends `Error`, carries `status`) with a real message, falling back to
  `Request failed with status ${status}` when the parsed error has no
  `.message`.
- **R4 - `outputFileTracingRoot` and `turbopack.root` disagreed**, producing
  a build-time warning on every build (reproduced in a real Docker build)
  and silently letting Next widen Turbopack's project root anyway rather
  than by explicit config. Fixed: `turbopack.root` now points at the same
  monorepo root as `outputFileTracingRoot`. Re-verified with `docker build
  --no-cache`: warning gone.
- **R5 - site identity could survive a same-tab user switch.**
  `["me","sites"]` uses `staleTime: Infinity` and the `QueryClient` is a
  browser-lifetime singleton; `signOut` only navigated (`router.push`, a
  client-side transition, not a reload) without clearing the query cache -
  a second user signing in in the same tab would have inherited the first
  user's cached `siteId` for the rest of the session, since the query would
  never refetch on its own. Fixed: `signOut` (`auth-provider.tsx`) now calls
  `queryClient.clear()` before navigating.

Also acted on (cheap, real, already touching these files):
inconsistent `npm ci` invocation style across the three widened `ci.yml`
jobs (`static-analysis`'s ESLint step used an inline `--prefix` flag while
the other two used a separate `working-directory:` step - standardized on
the latter); `deploy.yml`'s path filter and `detect-changes/action.yml`'s
`WEB_CHANGED` detection didn't cover the root `.dockerignore`, even though
it now governs the web image's contents - a `.dockerignore`-only change
would have silently skipped rebuilding/redeploying web; added a warning
comment on the pre-existing-dead `useNotAssignedStorageLocation` noting its
`storageLocationId` is now site-scoped while sibling hooks
(`use-location-inventory.ts`/`use-location-mutations.ts`) still resolve the
same NOT_ASSIGNED concept through the legacy, unscoped endpoint - a trap for
a future caller, not touched further since the hook itself is still
callerless.

Every fix re-verified: full suite (291/291, up from 283 - 8 new tests for
`unwrapGeneratedResponse` (4) and the R1 regression case (1) plus three from
`use-current-site.test.ts`'s new empty-body-failure case and shape updates),
`tsc --noEmit` clean, `eslint` clean, re-run against a genuinely clean
install (both `node_modules` directories removed, `npm ci` in
`packages/api-client` then `apps/web`), and a `docker build --no-cache` +
`docker run` + `curl` (image builds, no tracing-root warning, container
serves the expected `307`).

## Review response 3 (Full-tier reclassification, review.md/validation.md)

A third review correctly rejected the "Standard" tier judgment call from
"Review response 2": widening the Docker build context, its standalone
tracing boundary, the PR Docker gate, the deploy build trigger, and the dev
Compose build is a deployment change under `AGENTS.md`, full stop - no
carve-out for "didn't change topology/secrets." Reclassified to Full;
`spec.md`'s Tier section updated; this feature now needs `review.md` and
`validation.md`, produced via the project's `/review` skill (three parallel
Standards-pass agents - `mirai-next-reviewer`, `code-reviewer`,
`security-reviewer` - plus a Spec pass checking every AC) and `/validate`.

That Standards pass caught real, verified bugs beyond the tier question
itself - both agents proved their findings by actually running things
(a synthetic Docker context build, `npm ci --dry-run`, a clean-install
`tsc`), not just reading config:

- **CI would fail on a clean checkout** (`code-reviewer`, confirmed
  independently by `security-reviewer`): `unit-test-web`, `build-web`, and
  the web ESLint step all run `npm ci` in `apps/web` only.
  `packages/api-client` isn't an npm workspace member, so that `npm ci`
  symlinks `node_modules/@mirai/api-client` but never installs
  `openapi-fetch` anywhere Node would resolve it from
  `packages/api-client`'s own location - the Dockerfile's two-stage install
  was never mirrored into CI. Verified by reproducing the exact failure
  (`npx tsc --noEmit` → `Cannot find module 'openapi-fetch'`) with both
  directories' stray local installs removed, matching a fresh checkout.
- **`pr-gate.yml` and `deploy.yml` referenced `packages_changed` without ever
  exposing it** (`security-reviewer`): only `ci.yml`'s `changes` job forwarded
  `detect-changes`' `packages_changed` output; the other two workflows
  declare their own `changes` job outputs and never added it, so
  `needs.changes.outputs.packages_changed` evaluated to empty string in
  both - the P2-fix from "Review response 2" was silently dead in both
  files it was supposed to fix.
- **The `pr-gate.yml` matrix `changed` field was a boolean, not a string,
  for the web row only** (`code-reviewer` flagged as a risk;
  `security-reviewer` confirmed via GitHub Actions' type-coercion rules that
  it actually fails): every sibling matrix row passes a raw `'true'`/`'false'`
  string; the web row's `${{ a == 'true' || b == 'true' }}` evaluates to a
  YAML boolean, and `if: matrix.changed == 'true'` never matches a boolean
  against that string - the web Docker build check silently never ran, on
  every PR, reporting success. This is the same class of bug as the missing
  output above, compounding it.
- **`useStorageLocations` misreported loading/error state**
  (`code-reviewer`): TanStack Query v5's `enabled: false` query reports
  `isLoading: false` (idle, not loading), so `LocationTabs` showed "run the
  database seeder" during the `/api/v1/me/sites` round-trip, and
  permanently on a site-resolution error - a real, user-visible regression
  this feature introduced, not merely a missed test.
- **`useCurrentSite`'s `?? memberships[0]` fallback** (`code-reviewer`):
  correct today (every user maps to MAIN) but exactly the line that would
  silently show one site's categories with the rest of the page still
  resolving to MAIN server-side, the moment a user gets a non-MAIN-only
  membership set.
- **Root `.dockerignore` didn't stop nested secrets from entering the
  widened context** (`security-reviewer`, HIGH, proven with a synthetic
  build): `.env`/`.env.local`/`.env.*.local` only match repo-root files:
  `infra/.env` (Supabase JWT secret, service-role key, Slack token),
  `infra/certs/*.pem` (a private TLS key), `apps/web/.env.local`/
  `.env.prod.local` (R2 credentials - and the latter two actually land in a
  builder layer, though not in the final pushed image or standalone
  output), `.mcp.json`, and `.claude/` all passed through un-excluded.

All fixed (see Task record below and the fix commits' diffs); every fix was
re-verified by actually running the thing it fixes, not just re-reading the
config - a real `docker build` + `docker run` + `curl` after the
`.dockerignore` fix (confirmed via a synthetic-context `find` that no
`.env*`/`.pem`/`.mcp.json`/`.claude` reach the image), and a clean-install
`npm ci` (packages/api-client then apps/web, no leftover local installs) +
`tsc --noEmit` + `vitest run` + `eslint` reproducing exactly what the fixed
CI jobs now do.

Two `code-reviewer` suggestions were also acted on since they were real and
cheap to fix while already in these files: `getStorageLocations()` (legacy,
no-arg) was dead after this diff removed its only caller
(`useStorageLocations`) - removed. `toStorageLocationSummary` defaulted
missing `id`/`code` to `""` rather than dropping the record - changed to
filter out storage-location records missing either, since an empty-string
identity silently matching nothing (or colliding with another dropped
record) is worse than the record just not appearing.

One suggestion dismissed: adding `"use client"` to `generated-client.ts` for
clarity - the sibling legacy `client.ts` module doesn't have one either
(neither needs it; it's a plain module, not a component), so adding it only
to the new file would be inconsistent, not clarifying.

One item deliberately left alone: `useNotAssignedStorageLocation` has zero
callers, but confirmed via `git show main` that it was already dead before
this diff - out of scope for this feature to clean up (a `refactor-cleaner`
task, not this SDD record's job).

## Review response (post-implementation)

An independent review of the initial implementation surfaced two real
findings, both fixed:

- **P1 — the Locations page's actual row table never used the migrated
  path.** `storage/page.tsx` renders rows from `useLocationsWithCounts` →
  legacy `getLocationsWithCounts` (no `/api/v1` equivalent exists), not from
  `useLocations`/`useLocationsOnly` (which I had migrated). Only the
  page's storage-location category tab bar (`useStorageLocations`) actually
  reached the new site-scoped path. So AC-3/AC-4 as originally written were
  not met for the page's primary data — only a secondary UI element was.
- **P2 — migrating `useLocations`/`useLocationsOnly` silently widened scope
  into three unrelated, unvalidated workflows.** Those hooks' actual
  consumers are `shipment-receive-dialog.tsx`, `location-selector.tsx`, and
  `transfer-display-dialog.tsx` — shipment receiving, stock location
  selection, and machine-display transfer. None were declared in scope, none
  were smoke-tested, and none had anything to do with the Locations page
  this feature targeted.

**Fix:** reverted `use-locations.ts` to legacy (`git diff` against `main` is
now empty for that file) and removed the now-dead `getSiteLocations`/
`getSiteLocationsByType`/`getSiteStorageLocationByCode` from `locations.ts`
(the last of these had no caller even before the revert — added
speculatively for parity, never used). The feature's verified, real-UI
surface is now exactly the storage-location category tab bar
(`useStorageLocations` → `getSiteStorageLocations`), consumed only by
`storage/page.tsx` and `location-tabs.tsx`. `spec.md` updated (Problem and
outcome, Out of scope, AC-3/4/5/7, T-4/5/6/7) to describe this as the actual
scope rather than the originally-planned broader one. Root cause: T-6 was
implemented by checking `use-locations.ts`'s own signature and its three
"expected" consumers (`location-table.tsx`/`location-tabs.tsx`/
`location-form.tsx`) without first grepping who actually imports
`useLocations`/`useLocationsOnly` themselves — the consumer-discovery step
that caught `getStorageLocationByCode`'s extra callers (see below) wasn't
applied to the hooks layer. Test suite after the fix: 279/279 passing (down
from 284, the 5 removed tests were for the dropped functions).

## Review response 2 (Docker build break + stale log entry)

A second independent review surfaced two more findings:

- **P1 — the new `@mirai/api-client` local package dependency broke the web
  Docker build.** The web image builds with `apps/web` as its context
  (`Dockerfile`, `pr-gate.yml`'s `docker-build-check`, `deploy.yml`'s
  `build-push-web`, `infra/docker-compose.dev.yml`'s `frontend` service), so
  `packages/api-client` — the target of `apps/web/package.json`'s
  `file:../../packages/api-client` — was invisible inside that build,
  breaking `npm ci`.
- **P2 — the implementation log retained a superseded decision as current
  fact.** The original "New site-scoped function names..." bullet in
  "Assumptions and decisions" still described all four `getSite*` functions
  and both hooks as the final state, contradicting the "Review response"
  section above it that correctly said three were removed and
  `use-locations.ts` reverted.

**P2 fix:** the bullet below is now marked `[SUPERSEDED]` with the final
state stated up front, rather than silently correcting the history in
place.

**P1 fix — asked the user to choose between three approaches** (widen
Docker build context to repo root; vendor a tarball inside `apps/web`;
introduce full npm workspaces at the repo root) given the trade-offs and
the "deployment changes" Full-tier proximity. User chose **widen the build
context to the repo root**. Changed:

- `apps/web/Dockerfile`: builds from repo-root context now; copies
  `apps/web` and `packages/api-client` manifests first (layer caching),
  runs `npm ci` in `packages/api-client` *and* `apps/web` (see why below),
  then copies full source and builds; runner stage paths updated for the
  monorepo-relative standalone output structure (`apps/web/server.js`, not
  `server.js`).
- `apps/web/next.config.mjs`: added `outputFileTracingRoot` (repo root) —
  without it, Next's standalone-output file tracing stops at `apps/web` and
  silently omits the local package, which would have failed at container
  *runtime* (`Cannot find module`) rather than build time — the kind of gap
  that's easy to miss without an actual `docker run`.
- `.github/workflows/pr-gate.yml`, `.github/workflows/deploy.yml`,
  `infra/docker-compose.dev.yml`: `context`/`dockerfile` updated to match;
  `packages_changed` (an existing, previously-unused `detect-changes`
  output) now also triggers the web `docker-build-check` and
  `build-push-web` jobs, and `deploy.yml`'s path filter gained
  `packages/**` — otherwise an api-client-only change (e.g. a schema
  regeneration) would silently skip rebuilding/redeploying the web image
  that depends on it.
- New root `.dockerignore` — none existed; without it, a locally-present
  `node_modules` (present on this dev machine, not in a clean CI checkout)
  collides with the image's own `npm ci`-created `node_modules` during
  `COPY`.

**A second, deeper bug found only by actually running the build** (not
caught by config review alone): `npm ci` inside `apps/web` symlinks
`node_modules/@mirai/api-client` to `../../packages/api-client` but does
**not** install that package's own dependencies (`openapi-fetch`) anywhere
Node would resolve them from `packages/api-client`'s actual location — this
project has no npm workspace, so a plain `file:` dependency doesn't hoist a
linked package's transitive dependencies the way a workspace member would.
This first surfaced as a lockfile problem (`npm install --package-lock-only
--offline` had recorded `@mirai/api-client`'s manifest but never resolved
`openapi-fetch` as an installable entry), then as a Turbopack build failure
(`Module not found: Can't resolve 'openapi-fetch'`) once the Docker build
actually ran `npm run build` against a clean install. Fixed by having
`packages/api-client` run its own `npm ci` in the image (it already has its
own `package-lock.json`) rather than relying on hoisting that doesn't
happen. This would not have been caught by typecheck/lint/vitest alone,
since my local dev node_modules had `openapi-fetch` manually copied in
earlier (see "Assumptions and decisions" below) — only the real Docker
build surfaced it.

**Verification:** ran an actual `docker build` (with the same placeholder
build-args `pr-gate.yml` uses) — succeeded, `npm run build`/typecheck/static
generation all completed. Then `docker run` with a mapped port — container
started (`✓ Ready in 0ms`), and `curl` against `/` returned `307` (redirect
to `/login`, expected unauthenticated behavior) with no module-resolution
errors in the logs. Image and container removed after verification.

## Assumptions and decisions

- **New site-scoped function names instead of repurposing legacy ones**
  **[SUPERSEDED — see "Review response" above for the final state].** This
  bullet records the reasoning as it stood immediately after the first
  implementation pass, before the review response above removed three of the
  four functions it describes. It is kept for history, not as current fact —
  do not read the function/consumer list below as accurate; the "Review
  response" section and the Task record's T-4/T-5/T-6 entries are the
  authoritative final state.
  <br>
  Original reasoning: the spec's original T-4/T-5 wording assumed
  `getLocations`, `getLocationsByType`, `getStorageLocations`, and
  `getStorageLocationByCode` could be migrated in place. Investigating actual
  callers showed `getStorageLocationByCode` is also used by
  `use-location-mutations.ts`, `use-location-inventory.ts`, and
  `use-not-assigned-inventory.ts` — all explicitly out of scope per the spec
  (location mutations stay on legacy endpoints). Changing the shared
  function's signature to require `siteId` would have forced those
  out-of-scope files to change too, violating AC-5. Decision at the time:
  added new, distinctly-named functions (`getSiteLocations`,
  `getSiteLocationsByType`, `getSiteStorageLocations`,
  `getSiteStorageLocationByCode`) used only by `use-locations.ts` and
  `use-storage-locations.ts`. **This held only until the next review pass**,
  which found `getSiteLocations`/`getSiteLocationsByType` reached no real UI
  and silently widened scope (P1/P2 above) — those two, plus the never-called
  `getSiteStorageLocationByCode`, were subsequently removed, and
  `use-locations.ts` was fully reverted. Only `getSiteStorageLocations`
  survives, used only by `use-storage-locations.ts`.

- **`StorageLocationCategory` retained as a type alias.** `use-storage-locations.ts`
  exported an inline `StorageLocationCategory` interface with the same shape
  as the new `StorageLocationSummary` in `locations.ts`. Rather than
  duplicating the shape, `StorageLocationCategory` is now `export type
  StorageLocationCategory = StorageLocationSummary` — no consumer outside this
  file imports it, so this is a safe, non-breaking simplification.

- **One-line export addition in `packages/api-client/src/index.ts`.**
  `locations.ts` and `use-current-site.ts` need `components["schemas"][...]`
  types to type generated-client responses, but only `paths` was exported.
  Added `components` to the existing `export type { paths, ... }` line.
  `openapi.json` and `schema.d.ts` are unmodified (verified via
  `git diff --stat -- packages/contracts packages/api-client`); this is the
  one exception to AC-6's "zero diff" and is recorded there.

- **`apps/web` has no npm/pnpm workspace wiring to `packages/api-client`.**
  There's no root `package.json` workspaces field, so `@mirai/api-client` was
  never installed as a dependency anywhere. Added
  `"@mirai/api-client": "file:../../packages/api-client"` to
  `apps/web/package.json` and regenerated `apps/web/package-lock.json` via
  `npm install --package-lock-only --offline` (confirmed the lockfile gained
  the `@mirai/api-client` entry; no network access was needed since it
  resolves to a local path). `node_modules` itself was populated by
  symlinking the already-installed dev environment rather than a full
  `npm install`, purely a local dev-environment shortcut — the committed
  `package.json`/`package-lock.json` are what matters for CI/other machines.

- **Response-shape mapping in `locations.ts`.** The generated client's
  `Location`/`StorageLocation` schema types are nested/optional
  (`storageLocation: { id, code, ... }`, most fields `?`), while the existing
  frontend `Location` type (`@/types/api`) is flat
  (`storageLocationId`/`storageLocationType`) and non-optional. Checked
  actual consumers (`location-table.tsx`, `location-selector.tsx`, etc.) —
  none read `storageLocationId`/`storageLocationType` today, confirming these
  fields are effectively dead on the frontend type. Added `toLocation`/
  `toStorageLocationSummary` mappers so the new site-scoped functions still
  return the exact same declared shape as before, satisfying T-7's "hook
  contracts didn't change shape" expectation.

## Task record

### T-1 — Extract shared auth-token + 401-redirect logic

- Changed: added `apps/web/src/lib/api/auth-token.ts` (`getAuthToken`,
  `redirectToLogin`, moved verbatim/behavior-equivalent from `client.ts`);
  updated `client.ts` to import and use them instead of inlining the logic.
- Tests: added `auth-token.test.ts` (4 tests); existing `client.test.ts`
  passes unmodified (5 tests).
- Result: pass.

### T-2 — Instantiate the generated API client

- Changed: added `apps/web/src/lib/api/generated-client.ts`
  (`createWebApiClient`, `webApiClient` singleton) wired to T-1's helpers and
  `BACKEND_BASE_URL`. Added `@mirai/api-client` as a dependency of `apps/web`
  (see assumptions above).
- Tests: added `generated-client.test.ts` (3 tests: Authorization header
  attached, `onUnauthorized` fires on 401, no header when no token).
- Result: pass.

### T-3 — `useCurrentSite()` silent site-resolution hook

- Changed: added `apps/web/src/hooks/queries/use-current-site.ts`, calling
  `GET /api/v1/me/sites` via the T-2 client, resolving to the `MAIN`
  membership (or the sole membership if `MAIN` is absent), `staleTime:
  Infinity` per the caching decision recorded in the spec.
- Tests: added `use-current-site.test.ts` (5 tests: resolves MAIN, falls back
  to sole membership, empty list resolves `undefined` without throwing,
  surfaces an error without throwing, does not refetch on remount within the
  same `QueryClient`).
- Result: pass.

### T-4 — Dropped after review

- `getSiteLocations`/`getSiteLocationsByType` were implemented, then removed
  once review found their only consumers (`useLocations`/`useLocationsOnly`)
  serve unrelated, out-of-scope workflows. See "Review response" above.
- Result: dropped, not implemented.

### T-5 — Site-scoped storage-locations read

- Changed: `apps/web/src/lib/api/locations.ts` — added
  `getSiteStorageLocations` (generated client, real
  `GET /api/v1/sites/{siteId}/storage-locations`) alongside the untouched
  legacy `getStorageLocations`/`getStorageLocationByCode`/
  `getLocationsWithCounts`/CRUD functions. `getSiteStorageLocationByCode` was
  implemented then dropped — added speculatively for parity, had no real
  caller. Added `packages/api-client/src/index.ts` `components` export (see
  assumptions).
- Tests: `locations.test.ts` (2 tests: site-scoped path/params, error
  propagation) — trimmed from 7 after dropping the unused functions and
  their tests.
- Result: pass.

### T-6 — Wire `siteId` through the storage-locations hook

- Changed: `use-storage-locations.ts` (`useStorageLocations` calls
  `useCurrentSite()` and `getSiteStorageLocations`, query key
  `["storageLocations", siteId]`, `enabled` gated on `siteId`;
  `useNotAssignedStorageLocation` unchanged, derives from
  `useStorageLocations`). `use-locations.ts` fully reverted to its
  pre-feature state (confirmed via `git diff` — empty) after the review
  above found its migration reached no intended UI and silently pulled in
  three unrelated workflows.
- Tests: covered by `locations.test.ts` (function-level) and
  `use-current-site.test.ts` (hook-level `siteId` resolution); no dedicated
  new hook test added for `use-storage-locations.ts` itself since no test
  file existed for it before this change and its query-key/enabled-gating
  logic is thin pass-through — verified instead by full-suite green +
  typecheck.
- Result: pass. Full suite: 279/279 passing (no regressions in the other 277
  pre-existing tests).

### T-7 — Consumer smoke check

- Changed: none. `location-table.tsx`, `location-tabs.tsx`,
  `location-form.tsx` consume `useLocations`/`useLocationsOnly`/
  `useStorageLocations`; since `useLocations`/`useLocationsOnly` are now a
  no-op revert and `useStorageLocations`'s external return shape (`data`,
  `isLoading`, etc.) is unchanged, no edits were needed to any of the three
  files — confirmed via `tsc --noEmit` (clean). The three previously
  at-risk out-of-scope consumers (`shipment-receive-dialog.tsx`,
  `location-selector.tsx`, `transfer-display-dialog.tsx`) are verified
  unaffected by the empty `git diff` on `use-locations.ts`, not by a new
  smoke test.
- Tests: `npx tsc --noEmit -p tsconfig.json` clean; `npx eslint` clean on all
  touched files. No Playwright smoke check added this pass — flagged as
  remaining below.
- Result: pass (typecheck/lint only; Playwright smoke check not yet added).

### T-8 — Contract-drift checklist

- Verified: `git diff --stat -- packages/contracts packages/api-client`
  shows only `packages/api-client/src/index.ts` (1 line), zero diff on
  `openapi.json`/`schema.d.ts`. `tests/contracts` (Python Kafka
  event-envelope schema tests) not run — unrelated to this REST-only change,
  no file under `tests/contracts` was touched.
- Result: pass.

### T-9 — Fix the web Docker build (added after review)

- Changed: `apps/web/Dockerfile` (repo-root context, two-stage `npm ci`,
  monorepo-relative runner paths), `apps/web/next.config.mjs`
  (`outputFileTracingRoot`), `.dockerignore` (new, repo root),
  `.github/workflows/pr-gate.yml` (`docker-build-check` matrix: `context`/
  `dockerfile`/`changed` for `web`), `.github/workflows/deploy.yml`
  (`build-push-web`: `context`/`file`/`if`, plus `packages/**` in the
  top-level path filter), `infra/docker-compose.dev.yml` (`frontend.build`).
  See "Review response 2" above for the why.
- Tests: real `docker build` (succeeded) and `docker run` + `curl` (started
  cleanly, expected `307` redirect, no errors in logs). YAML syntax of all
  three workflow/compose files validated with `python3 -c "import yaml;
  yaml.safe_load(...)"`. Full JS test suite + typecheck re-run after these
  changes (279/279, clean) to confirm `next.config.mjs` didn't regress
  local dev/test behavior.
- Result: pass.

### T-10 — Full-tier review fixes (added after reclassification)

- Changed: `.github/actions/detect-changes/action.yml` (new
  `web_or_packages_changed` output - single source of truth replacing five
  duplicated `web_changed == 'true' || packages_changed == 'true'` copies,
  which also removes the boolean/string type mismatch); `pr-gate.yml` and
  `deploy.yml` (`changes` job now actually exposes `web_or_packages_changed`;
  consumers switched to it); `ci.yml`'s `unit-test-web`/`build-web`/ESLint
  step (each now runs `npm ci` in `packages/api-client` before `apps/web`'s,
  mirroring the Dockerfile, plus `cache-dependency-path` covers both
  lockfiles); `.dockerignore` (rewritten: `**/.env`/`**/.env.*` instead of
  root-only patterns, plus `.claude`, `.mcp.json`, `infra/certs`,
  `**/target`, `**/.venv`, `**/__pycache__`); `use-current-site.ts` (dropped
  `?? memberships[0]`, fails closed to `null`); `use-storage-locations.ts`
  (propagates `useCurrentSite()`'s `isLoading`/`error`; `skipToken` instead
  of a `siteId!` assertion); `location-tabs.tsx` (distinct error branch,
  separate from the "no storage locations" empty state); `locations.ts`
  (removed dead `getStorageLocations()`; `toStorageLocationSummary` now
  drops records missing `id`/`code` instead of defaulting to `""`);
  `package.json` (alphabetical ordering restored).
- Tests: `use-current-site.test.ts`'s "falls back to the only membership"
  case rewritten to assert the new fail-closed behavior (`siteId` stays
  `undefined`) instead of the removed fallback. New
  `use-storage-locations.test.ts` (4 tests: disabled/no-call until `siteId`
  resolves, calls with the resolved `siteId`, `isLoading` reflects site
  resolution before the storage-location query even starts, site-resolution
  `error` surfaces even though the storage-location query never ran).
  Full suite: 283/283 passing. Re-verified against a genuinely clean
  install (both `node_modules` and `packages/api-client/node_modules`
  removed, `npm ci` run in `packages/api-client` then `apps/web`, exactly
  mirroring the fixed CI jobs): `tsc --noEmit`, `vitest run`, `npm run lint`
  all clean (lint: 0 errors, pre-existing warnings only). `.dockerignore`
  fix verified with a synthetic `alpine` image doing `COPY . /ctx` +
  `find` for `.env*`/`.pem`/`.mcp.json`/`.claude` - all absent from the
  built image. Real web `docker build` + `docker run` + `curl` re-run after
  every fix in this round (three full passes total across the round) -
  each succeeded, image serves the expected `307` unauthenticated redirect.
- Result: pass.

### T-11 — `mirai-next-reviewer` findings (R1-R5, added after Standards pass)

- Changed: `generated-client.ts` (new `GeneratedApiError` + shared
  `unwrapGeneratedResponse` helper - checks `response.ok` in addition to
  `error`, throws a real `Error` with `status`); `locations.ts` and
  `use-current-site.ts` (both call sites switched to the shared helper);
  `use-storage-locations.ts` (synthesizes an error when the site query
  settles with no `siteId` and no error of its own; narrowed its return to
  `{data, isLoading, error}` instead of spreading the whole query object);
  `next.config.mjs` (`turbopack.root` now matches `outputFileTracingRoot`);
  `auth-provider.tsx` (`signOut` calls `queryClient.clear()`); `ci.yml`
  (standardized the ESLint step's `packages/api-client` install to a
  separate `working-directory:` step, matching the other two jobs);
  `deploy.yml` + `detect-changes/action.yml` (root `.dockerignore` now
  covered by both the workflow path filter and `WEB_CHANGED` detection);
  `use-storage-locations.ts` (warning comment on the pre-existing-dead
  `useNotAssignedStorageLocation`'s site-scoped-vs-legacy ID mismatch).
- Tests: `generated-client.test.ts` +4 (`unwrapGeneratedResponse`: success,
  parsed-error throw with real `.message`/`.status`, non-ok-with-no-error
  throw, ok-with-no-data no-throw). `locations.test.ts` updated to the new
  mock shape (`response` field) plus a new empty-body-failure case and an
  id/code-filtering case. `use-current-site.test.ts` updated similarly plus
  a new "non-ok response with no parsed error" case. `use-storage-locations.test.ts`
  +1 (site-settled-with-no-membership-and-no-error surfaces as an error).
  Full suite: 291/291. Re-verified against a genuinely clean install
  (`tsc --noEmit`, `vitest run`, `eslint` - all clean) and a real
  `docker build --no-cache` + `docker run` + `curl` (confirmed the R4
  tracing-root warning is gone; image still serves the expected `307`).
- Result: pass.

### T-12 — Complete R5's fix (added after follow-up review)

- Changed: `auth-provider.tsx` — `queryClient.clear()` added to the
  `onAuthStateChange` `SIGNED_OUT` handler and to both `validateAndSetUser`
  failure branches (backend validation failed; request threw); `queryClient`
  added to `validateAndSetUser`'s deps and the auth-effect's deps.
- Tests: new `auth-provider.test.tsx` (first test coverage for this
  component) — seeds `["me","sites"]`, fires a Supabase-originated
  `SIGNED_OUT` event directly, asserts the cache is cleared. Verified the
  test catches the regression by temporarily reverting the fix and
  confirming the test fails with the expected stale-data diff, then passes
  again restored. Full suite: 292/292. Re-verified against a genuinely
  clean install: `tsc --noEmit`, `vitest run`, `eslint` all clean.
- Result: pass.

### T-13 — Convert to a real npm workspace (added on user request, planned separately)

Prompted by a direct question during review: why wasn't this a real npm
workspace from the start, given every problem in T-9/T-10/T-11 traced back
to the same root cause (a `file:` dependency outside a workspace doesn't
hoist its own dependencies anywhere Node resolves them from that package's
location)? Planned via `EnterPlanMode`/`ExitPlanMode` before implementing,
given the repo-wide blast radius (see the approved plan for full rationale).

- Changed: new root `package.json` (`"workspaces": ["apps/web",
  "packages/*"]`); `apps/web/package.json`'s `"@mirai/api-client"` changed
  from `"file:../../packages/api-client"` to `"*"`; `apps/web/package-lock.json`
  and `packages/api-client/package-lock.json` deleted - the root
  `package-lock.json` (previously the empty `{"packages": {}}` stub) is now
  the one real, authoritative lockfile; `apps/web/Dockerfile` collapsed the
  two-stage `npm ci` (`packages/api-client` then `apps/web`) into one `npm
  ci` at the repo root; `ci.yml`'s `unit-test-web`/`build-web`/`static-analysis`
  jobs same collapse. `scripts/` (a one-off-script package, "not part of the
  app build") deliberately left outside the workspace.
- **A real, caught problem along the way**: the first attempt (`rm -rf`
  every `node_modules` + `npm install`) let all 43 of `apps/web`'s directly
  declared dependencies (plus ~280 transitive ones) float to whatever
  satisfied their existing caret ranges on the registry *today* - including
  `next` itself (16.2.3 -> 16.3.4) - and two of those newer versions
  (`eslint-config-next`, `eslint-plugin-react-hooks`) introduced new lint
  errors (React Compiler rule violations) in files this branch never
  touches (`tab-reviews.tsx`, a shipment dialog). Root cause: with the old
  lockfiles and `node_modules` both gone, npm had nothing to prefer and
  re-resolved the entire graph fresh - this is normal `npm install` behavior
  whenever a lockfile is regenerated from scratch, not something specific
  to workspaces, but doing it as part of this PR would have silently bundled
  an unrelated, unreviewed dependency bump (including the app's own
  framework version) into a "convert to workspaces" change.
- **Decision, not a full revert**: reconstructing the entire old dependency
  graph exactly (a full manual lockfile merge preserving all 326 drifted
  entries) was judged not worth the risk of getting the merge subtly wrong
  versus the value - most of that drift is patch/minor-level movement in
  transitive dependencies nobody explicitly pinned or reviewed, and is
  exactly what would happen to any contributor running `npm install` today
  regardless of this PR. Instead: pinned only the two packages that caused
  a concrete, verified failure (`eslint-config-next` back to `16.2.3`,
  `eslint-plugin-react-hooks` back to `7.0.1`) via a root `package.json`
  `overrides` block - restoring the 0-lint-errors baseline without touching
  unrelated files. Everything else, including `next` moving to `16.3.4`, is
  disclosed here rather than silently absorbed; recommend a human decision
  on whether that specific bump needs its own dedicated verification pass
  before merge (flagged to the user directly, not just recorded here).
- Tests: full clean-install verification, twice - once confirming the
  drift/lint-break (diagnostic, not the final state) and once from a
  genuine `rm -rf node_modules && npm ci` at the repo root after the
  `overrides` fix: `tsc --noEmit` clean, `vitest run` 292/292, `eslint` 0
  errors/50 pre-existing warnings (identical baseline to before this task).
  Confirmed `openapi-fetch` now resolves via ordinary Node module
  resolution with one install, no second `npm ci` needed - the actual bug
  this task set out to fix. Real `docker build --no-cache` +
  `docker run` + `curl` with the collapsed single-`npm ci` Dockerfile -
  succeeded, no tracing-root warning, expected `307` response. Confirmed
  `scripts/` untouched (`git status --short scripts/` empty).
- Result: pass, with one disclosed, un-pinned side effect (`next`
  16.2.3 -> 16.3.4) flagged for a human decision rather than resolved
  unilaterally.

### T-14 — `next` pin attempted, then reverted on a security finding; `contracts-check` fix

Asked to pin `next` back to `16.2.3` (full version-parity with pre-migration
state). Did so via the same `overrides` mechanism, then ran `npm audit`
before considering it done - it reported 3 new high-severity findings:
`next@16.2.3` falls inside a documented vulnerable range
(`9.3.4-canary.0 - 16.3.0-preview.10`) covering ~20 CVEs/advisories (DoS,
XSS, SSRF, cache poisoning, middleware bypass), all fixed in `16.3.4`.
Did not leave the pin in place - reverted the override, flagged the finding
to the user instead of silently completing "pin back" as literally asked,
per "never compromise on security." Re-resolving `next` back up to `16.3.4`
needed an explicit `npm update next --workspace=apps/web` - removing the
override alone did not re-trigger resolution, since `16.2.3` still
satisfied the declared `^16.1.6` range and npm had no reason to move it on
its own. Final state: `next` at `16.3.4`, `eslint-config-next`/
`eslint-plugin-react-hooks` still pinned to their pre-migration versions
(pure tooling, not shipped in the app - no security relevance), `npm audit`
clean (0 vulnerabilities), re-verified with a genuine `rm -rf node_modules
&& npm ci`.

Separately, a review caught a real gap T-13 missed: `contracts-check`
(regenerates `packages/contracts/openapi.json` and
`packages/api-client/src/schema.d.ts`, fails on drift or breaking API
changes) still referenced the deleted `packages/api-client/package-lock.json`
in its `cache-dependency-path` and ran `npm ci` from inside
`packages/api-client` - the same per-package-install pattern every other
job had already been fixed to drop. This job wasn't touched in T-13 because
the earlier pass focused only on the three "web" jobs; `contracts-check`
lives in a different part of `ci.yml` and was missed. Fixed the same way:
one `npm ci` at the repo root, `npm run generate`/`npm run typecheck` still
run with `working-directory: packages/api-client` (correct - those are
package-specific scripts whose relative paths depend on that cwd).
Verified by running both commands locally exactly as CI would: `npm run
typecheck` clean, `npm run generate` produced zero diff on
`schema.d.ts` (confirming the checked-in generated client is still fresh
after the pin changes above).
- Result: pass.

## Test plan

- AC-1: `auth-token.test.ts` + `generated-client.test.ts` (9 tests) —
  Authorization header attached, `onUnauthorized` redirect fires on 401,
  same behavior as legacy `client.ts` (`client.test.ts` unmodified, still
  passing).
- AC-2: `use-current-site.test.ts` — `siteId` resolves silently, no UI
  component renders a switcher (none was added), loading/error states
  exposed.
- AC-3: `locations.test.ts` — `getSiteStorageLocations` calls the real
  `GET /api/v1/sites/{siteId}/storage-locations` path with the given
  `siteId`; `storage/page.tsx`/`location-tabs.tsx` consume it via
  `useStorageLocations` (confirmed by grep — the only two consumers).
- AC-4: `locations.test.ts` asserts `params.path.siteId` shape;
  `use-current-site.test.ts` proves `staleTime: Infinity` behavior (no
  refetch on remount) that the query-key wiring in `use-storage-locations.ts`
  depends on. Full-suite green confirms `enabled` gating didn't break
  existing consumers.
- AC-5: Full suite (291/291 passing as of the final round) includes existing tests exercising
  `use-location-mutations.ts` consumers unchanged;
  `getLocationsWithCounts`/legacy CRUD functions untouched by diff; `git diff`
  on `use-locations.ts` is empty, confirming the plain locations list and its
  three real consumers are byte-for-byte unchanged.
- AC-6: `git diff --stat -- packages/contracts packages/api-client` — see
  T-8.
- AC-7: **Not yet done.** No Playwright smoke check was added and the
  locations page was not manually verified against a running backend this
  pass — remaining before this feature is considered fully validated.
- AC-8: Real `docker build` + `docker run` + `curl` (see T-9) — image
  builds, container starts, serves a request with no module-resolution
  errors. `packages_changed`-triggers-web-rebuild wiring verified by
  reading the updated conditions, not by an actual CI run (no CI access
  from this session) — worth confirming on the real PR.

## Remaining before commit/PR

- AC-7 / T-7's Playwright smoke check is outstanding — needs a running
  backend (`inventory-service`) to verify the locations page renders via the
  new read path end-to-end.
- `tests/contracts` was not executed (Python suite, unrelated to this
  change) — worth a quick confirmation run if a full validation pass is
  desired before merge.
- AC-8's CI wiring (`packages_changed` triggering the web
  `docker-build-check`/`build-push-web` jobs) is verified by inspection and
  local Docker build/run, not by an actual GitHub Actions run — worth
  watching the first real PR to confirm the matrix condition and path
  filter behave as written.
