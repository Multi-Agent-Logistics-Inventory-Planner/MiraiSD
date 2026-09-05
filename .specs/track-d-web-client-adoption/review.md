# Review

## Scope reviewed

Web-only feature (locations-list scope narrowed to the storage-location
category tab bar) plus the deployment/build-mechanics change that
reclassified this feature to Full: the web Docker build context, its
Next.js standalone-output tracing boundary, the PR Docker gate, the
production deploy build trigger, CI test/typecheck/lint triggers, the dev
Compose build, and the `packages/api-client` local-dependency wiring
(`file:` protocol, no npm workspace). No `inventory-service`, migration,
public-contract, or RBAC/auth changes are in scope.

Three independent Standards-pass reviews ran in parallel against the full
diff, none seeing the acceptance criteria below: `mirai-next-reviewer`
(web/full-stack conventions), `code-reviewer` (general code quality,
correctness, CI/Docker risk), and `security-reviewer` (security). A
separate Spec pass (this author) checked every `AC-N` in `spec.md` against
the diff directly - reading the actual `git diff`, not `log.md`'s claims
about it.

All findings below were fixed before this record was written; see `log.md`
("Review response 2" through "Review response 4", tasks T-9 through T-11)
for the exact diffs, and for each fix's re-verification (real `docker
build`/`docker run`/`curl`, a genuinely clean `npm ci` install mirroring
CI, and the full test suite). This document is the disposition record, not
a restatement of `log.md`'s narrative.

## Findings

### Standards pass — code-reviewer

- [Standards] `npm ci` in `apps/web` alone does not install `openapi-fetch`
  (no npm workspace, so `packages/api-client`'s own dependencies never
  hoist anywhere Node resolves them from that package's location); three CI
  jobs (`unit-test-web`, `build-web`, the web ESLint step) would have failed
  on a clean checkout — **act on** (fixed: T-9/T-10, each job now runs
  `npm ci` in `packages/api-client` first).
- [Standards] `useStorageLocations` misreports loading/error state: a
  disabled TanStack Query reports `isLoading: false`, so `LocationTabs`
  showed the "run the database seeder" message during the `/api/v1/me/sites`
  round-trip, and permanently on a site-resolution error — **act on** (fixed:
  T-10, `isLoading`/`error` now propagate from `useCurrentSite`).
- [Standards] `getStorageLocations()` (legacy, no-arg) became dead once its
  only caller (`useStorageLocations`) was migrated — **act on** (fixed:
  T-10, removed).
- [Standards] `pr-gate.yml`'s web matrix row passed a boolean expression
  where every sibling row passes a string, risking a silent skip — **act
  on** (fixed: T-10, centralized into `detect-changes`'
  `web_or_packages_changed` string output; independently confirmed as an
  actual bug, not just a risk, by `security-reviewer`'s type-coercion
  analysis).
- [Standards] The `web_changed || packages_changed` predicate was
  duplicated five times across three workflow files — **act on** (fixed:
  T-10, centralized into one `detect-changes` output).
- [Standards] `.dockerignore` omitted `services/`'s build output
  (`**/target`, `.venv`, `__pycache__`) — cosmetic today (context transfer
  measured at 38.59 kB on a clean checkout) but real for a local `docker
  compose build` after a Maven/Python build — **act on** (fixed: T-9,
  patterns added; superseded by the larger `.dockerignore` rewrite in
  T-10/`security-reviewer`'s HIGH-1).
- [Standards] `siteId!` non-null assertion in `useStorageLocations`'s
  `queryFn`, relying on `enabled` for safety in a way the type checker can't
  see — **act on** (fixed: T-10, `skipToken` instead).
- [Standards] `toStorageLocationSummary` defaulted missing `id`/`code` to
  `""` rather than dropping the record — **act on** (fixed: T-10, filters
  out records missing either).
- [Standards] Test coverage gap: `useStorageLocations` (the file this
  feature actually rewired) had no dedicated test — **act on** (fixed:
  T-10, new `use-storage-locations.test.ts`).
- [Standards] `@mirai/api-client` broke `package.json`'s alphabetical
  dependency ordering — **act on** (fixed: T-10, reordered; lockfile
  regenerated to match).
- [Standards] `useNotAssignedStorageLocation` is dead code, though confirmed
  (via `git show main`) to already be dead before this diff — **dismissed**
  as out of this feature's scope (a `refactor-cleaner` task); a warning
  comment was added instead (T-11) since this diff does change what its
  `storageLocationId` now means.

### Standards pass — security-reviewer

- [Standards] Root `.dockerignore`'s `.env`/`.env.local`/`.env.*.local`
  patterns only matched repo-root files; every nested `.env*` (including
  `infra/.env` — Supabase JWT secret, service-role key, Slack token —
  `infra/certs/*.pem` — a private TLS key — and `apps/web/.env.local`/
  `.env.prod.local` — R2 credentials, and these two actually land in a
  builder layer) passed through un-excluded once the build context widened
  to the repo root. Mitigated by: gitignored (clean CI checkouts don't have
  them), the Dockerfile only `COPY`s `apps/web`/`packages/api-client` so
  most never reach a layer, and Next's standalone output doesn't carry
  `.env*` forward regardless — but still a real, verified new exposure on
  developer machines / anywhere running the dev Compose build — **act on**
  (HIGH; fixed: T-10, `.dockerignore` rewritten to `**/.env`/`**/.env.*`
  plus `.claude`/`.mcp.json`/`infra/certs`; re-verified with a synthetic
  build proving none of these reach the image).
- [Standards] `packages_changed` was referenced in `pr-gate.yml` and
  `deploy.yml` without either workflow's `changes` job ever exposing it as
  an output (only `ci.yml`'s did) — the P2-fix from an earlier review round
  was silently dead in the two files it was meant to fix, and combined with
  the boolean/string bug above, meant the web Docker build check was
  permanently skipped while reporting success — **act on** (HIGH; fixed:
  T-10, both workflows now expose and use `web_or_packages_changed`).
- [Standards] `openapi-fetch` unresolvable in the CI jobs the diff widened
  — same root cause as `code-reviewer`'s finding above, confirmed
  independently — **act on** (fixed: T-9/T-10, see above).
- [Standards] A developer's `apps/web/.env.local` is copied into the
  builder stage and read by `next build` (non-`NEXT_PUBLIC_*` values like
  R2 credentials get loaded into the build, though not inlined into client
  bundles or carried into standalone output) — **act on** (MEDIUM; fixed by
  the same `.dockerignore` fix as the HIGH finding above).
- [Standards] Fork-PR install-script surface: with `context: .`, a fork PR
  can now influence the build via `packages/api-client/package-lock.json`
  in addition to `apps/web/package-lock.json` — same capability class as
  before, one more file, no secrets reachable in that job (`pull_request`,
  not `pull_request_target`; read-only token; `push: false`) — **dismissed**,
  noted for completeness only, no action required beyond the existing
  fork-PR posture.
- [Standards] `packages/api-client/src/index.ts`, `auth-token.ts`,
  `use-current-site.ts`, `generated-client.ts` — no findings (auth/token
  handling, SSRF/path-traversal, site-resolution direction all reviewed
  clean).

### Standards pass — mirai-next-reviewer

- [Standards] R1: an unresolved site (no error, just no membership) still
  rendered "run the database seeder" via `LocationTabs`' empty-state
  branch — **act on** (fixed: T-11, `useStorageLocations` synthesizes an
  error for this case).
- [Standards] R2: non-ok responses with an empty body (204/HEAD/
  `Content-Length: 0`) read as success in both `getSiteStorageLocations`
  and `fetchCurrentSite`, since openapi-fetch returns `{ error: undefined }`
  for those — **act on** (fixed: T-11, shared `unwrapGeneratedResponse`
  helper checks `response.ok`).
- [Standards] R3: both call sites `throw error` where `error` is a plain
  object/string, not an `Error` — **act on** (fixed: T-11, same helper
  throws `GeneratedApiError`, a real `Error` subclass with `status`).
- [Standards] R4: `outputFileTracingRoot` and `turbopack.root` disagreed,
  producing a build warning and letting Next silently widen the Turbopack
  root anyway — **act on** (fixed: T-11, `turbopack.root` now matches).
- [Standards] R5: `["me","sites"]`'s `staleTime: Infinity` plus a
  browser-singleton `QueryClient` and a `signOut` that only navigates
  (no cache clear) meant a second user signing in in the same tab would
  inherit the first user's cached `siteId` — **act on** (fixed: T-11,
  `signOut` calls `queryClient.clear()`; **found incomplete by a follow-up
  review** — the `onAuthStateChange` `SIGNED_OUT` handler and
  `validateAndSetUser`'s two failure branches, the only paths for an
  *externally* triggered sign-out, still didn't clear the cache; completed
  in T-12 with a new regression test, `auth-provider.test.tsx`, confirmed
  to actually catch the gap by reverting the fix and re-running).
- [Standards] Advisory: inconsistent `npm ci`-for-`packages/api-client`
  invocation style across the three widened `ci.yml` jobs — **act on**
  (fixed: T-11, standardized).
- [Standards] Advisory: `deploy.yml`'s path filter and `detect-changes`'
  `WEB_CHANGED` detection didn't cover the root `.dockerignore`, even
  though it's now load-bearing for the image's contents — **act on**
  (fixed: T-11).
- [Standards] Advisory: no correlation-ID propagation in
  `createWebApiClient` (`docs/specs/client-applications.md` §4/§5 lists it
  as part of web migration) — **consider**, deferred: this feature's scope
  is one read-only workflow, not full client-applications-spec compliance;
  worth picking up when a mutation or a second workflow adopts the
  generated client.
- [Standards] Advisory: no Playwright coverage for the Locations tab bar
  — **consider**, tracked as the feature's known-open item (AC-7, see Spec
  pass below), not new information.
- [Standards] Cleared, with proof: the image actually runs (`/login`,
  `/api/health`, static chunks all 200 in a real container); `@mirai/api-client`
  is bundled into Next's server/client chunks, not resolved at runtime from
  `node_modules`, which is why its absence from the standalone
  `node_modules` is fine; the deliberate `use-locations.ts` revert is real
  (confirmed via `git diff`/`git status`); site scoping is enforced
  server-side, not decorative (`SiteAccessAuthorizationFilter` checked
  directly); the query key is site-qualified per `client-applications.md`
  §3; `packages/api-client` still imports no browser/Next/Supabase globals
  per §4; the `.gitignore` `!/.dockerignore` negation works.

### Spec pass (against `spec.md`'s AC-1 through AC-8)

- [Spec] AC-1 through AC-4, AC-6, AC-8: verified directly against the diff
  (not `log.md`'s claims) — shared auth-token/401-redirect module imported
  by both `client.ts` and `generated-client.ts`; `useCurrentSite` has no
  rendered switcher and (post-fix) never falls back to a non-MAIN
  membership or an implicit ID; `getSiteStorageLocations` hits the real
  `/api/v1/sites/{siteId}/storage-locations` path; the `["storageLocations",
  siteId]` query key and `skipToken` gating confirmed in the hook itself;
  `packages/contracts`/`packages/api-client` diff is exactly the one
  documented `components` export line; real `docker build`/`docker run`
  confirmed. **No gaps.**
- [Spec] AC-5: confirmed via `git diff --stat` that `use-locations.ts`,
  `use-location-mutations.ts`, `use-location-inventory.ts`,
  `use-not-assigned-inventory.ts`, and the three real consumers
  (`shipment-receive-dialog.tsx`, `location-selector.tsx`,
  `transfer-display-dialog.tsx`) show zero diff from `main`. **No gaps.**
- [Spec] AC-7: **gap, act on before merge (not blocking this review, but
  blocking "fully validated").** No Playwright smoke check exists, and the
  Locations page's tab bar was not manually verified against a running
  `inventory-service` backend — only a placeholder-build-arg Docker image
  serving an unauthenticated `307` was verified, which does not exercise
  the actual `/api/v1/sites/{siteId}/storage-locations` read path against
  real data. Tracked as the feature's one remaining open item; see
  `validation.md`.

## Residual risk

- AC-7 (Playwright/manual verification of the tab bar against a live
  backend) is still open — the CI/Docker/security fixes in this round are
  independently verified, but the feature's actual read-path behavior
  against a real `inventory-service` has not been exercised end-to-end.
- The `packages_changed`/`web_or_packages_changed` CI wiring is verified by
  local reasoning, a real Docker build, and YAML-syntax validation, but not
  by an actual GitHub Actions run (no CI access from this session) — worth
  confirming the first real PR shows `docker-build-check / web` actually
  running (not skipped) on both an `apps/web`-only and a `packages/`-only
  change.
- `createWebApiClient` still lacks `correlationId` propagation
  (`docs/specs/client-applications.md` §4/§5) — deferred as out of this
  narrow slice's scope, not forgotten.
- `useNotAssignedStorageLocation`'s pre-existing dead-code status and its
  now-mismatched site-scoped-vs-legacy ID semantics are documented in a
  comment but not resolved — acceptable since it has zero callers, but a
  future caller must read that comment before using it.
