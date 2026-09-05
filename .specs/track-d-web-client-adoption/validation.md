# Validation

## Command and scope

Changed deployables/boundaries: `apps/web` (frontend only — no
`inventory-service` Java code changed), `packages/api-client` (one-line
type export), and CI/CD mechanics (`apps/web/Dockerfile`,
`apps/web/next.config.mjs`, `.dockerignore`, `.gitignore`,
`.github/workflows/{ci,pr-gate,deploy}.yml`,
`.github/actions/detect-changes/action.yml`, `infra/docker-compose.dev.yml`).
No migrations, no OpenAPI contract change, no event-schema change — so
`services/inventory-service`'s Maven suite (needs JDK 21) and
`tests/contracts` (Python, Kafka event-envelope schemas) are out of scope
for this feature and were not run.

All commands below were run from a genuinely clean install — both
`apps/web/node_modules` and `packages/api-client/node_modules` removed and
reinstalled via `npm ci` (in `packages/api-client` first, then `apps/web`),
exactly mirroring the fixed `ci.yml` jobs — not against a dev environment
with stray local installs that could mask a real resolution failure.

```
rm -rf apps/web/node_modules packages/api-client/node_modules
cd packages/api-client && npm ci
cd ../../apps/web && npm ci
npx tsc --noEmit
npx vitest run
npm run lint
```

```
docker build -f apps/web/Dockerfile \
  --build-arg NEXT_PUBLIC_SUPABASE_URL=https://placeholder.supabase.co \
  --build-arg NEXT_PUBLIC_SUPABASE_ANON_KEY=placeholder \
  --build-arg NEXT_PUBLIC_BACKEND_URL=http://placeholder \
  --build-arg NEXT_PUBLIC_API_URL=http://placeholder \
  --build-arg NEXT_PUBLIC_R2_PUBLIC_BASE_URL=http://placeholder \
  -t mirai-web-validate:latest .
docker run -d -p <port>:3000 -e NEXT_PUBLIC_BACKEND_URL=http://localhost:4000 mirai-web-validate:latest
curl -sI http://localhost:<port>/
```

(Build args match `pr-gate.yml`'s `docker-build-check` job exactly — these
prove the build/runtime wiring, not real backend integration.)

```
python3 -c "import yaml; yaml.safe_load(open(f))" for each of:
  .github/workflows/ci.yml
  .github/workflows/pr-gate.yml
  .github/workflows/deploy.yml
  .github/actions/detect-changes/action.yml
```

## Result

| Check | Result |
| --- | --- |
| `tsc --noEmit` (clean install) | Pass — 0 errors |
| `vitest run` (clean install) | Pass — 292/292 tests, 36 files |
| `eslint` (clean install) | Pass — 0 errors, 50 pre-existing warnings (none in files this feature touched) |
| `docker build` (repo-root context, placeholder args) | Pass — build, typecheck, and static generation for all 19 routes succeed |
| `docker run` + `curl /` | Pass — container starts (`✓ Ready in 0ms`), serves `307` redirect to `/login` (expected unauthenticated behavior), no module-resolution or tracing-root warnings in logs |
| YAML syntax (4 changed workflow/action files) | Pass — all parse |
| `.dockerignore` secrets exclusion | Pass — verified with a synthetic `alpine` image (`COPY . /ctx` + `find`): no `.env*`, `*.pem`, `.mcp.json`, or `.claude` reach the build context |
| AC-7 (Locations page tab bar against a live `inventory-service`) | **Blocked — see below** |

Three full end-to-end Docker build/run/curl passes were done across the
review rounds (T-9, T-10, T-11), each after a distinct set of fixes; the
one recorded above is the final state.

### AC-7 blocker (environment, not a failure)

Verifying the tab bar against a live backend requires
`infra/docker-compose.dev.yml`'s `postgres-dev`, `kafka`, and
`inventory-service` services, which need real Supabase project credentials
and a database connection string (`infra/.env`, `apps/web/.env.local`).
Those files exist in the main checkout (`/Users/mjpark019/code/MiraiSD/`,
not this worktree) and point at what appears to be the project's actual
Supabase project and other live-adjacent infrastructure — not a disposable
local-only credential set. Spinning up an authenticated backend with those
credentials from this session, without the user's explicit authorization to
use them, is not something this validation pass did.

This is a concrete, named blocker, not a skipped step: the placeholder-arg
Docker build/run above proves the image builds and the process starts, but
does not exercise `GET /api/v1/sites/{siteId}/storage-locations` against
real data — that is exactly what AC-7 asks for and it remains open.

Two ways to close it, either requiring the user:
1. Explicit authorization to use the existing `infra/.env`/
   `apps/web/.env.local` credentials to run
   `docker compose -f infra/docker-compose.dev.yml up` locally and hit
   `/storage` in a browser or via a Playwright script.
2. A disposable test Supabase project's credentials, if the existing ones
   are not meant to be used from an agent session.

## Acceptance criteria evidence

- AC-1: `auth-token.test.ts` + `generated-client.test.ts` pass under the
  clean install above; both `client.ts` and `generated-client.ts` import
  the same `getAuthToken`/`redirectToLogin` (confirmed by direct read of
  the diff, not test output alone).
- AC-2: `use-current-site.test.ts` passes; no switcher component exists
  anywhere in the diff (confirmed by `grep` for new component files); the
  fail-closed fix (no `?? memberships[0]`) is exercised by the "fails closed"
  test case.
- AC-3: `locations.test.ts` passes, asserting the real
  `/api/v1/sites/{siteId}/storage-locations` path and params; consumed only
  by `storage/page.tsx` and `location-tabs.tsx` (confirmed by `grep`, no
  other importer).
- AC-4: query key assertion in `locations.test.ts`/`use-storage-locations.test.ts`;
  `skipToken` gating confirmed by the "stays disabled" test case.
- AC-5: `git diff --stat main -- <the six legacy-path files>` is empty,
  re-confirmed at the end of this validation pass (not just earlier in the
  session).
- AC-6: `git diff --stat -- packages/contracts packages/api-client` shows
  only the one documented `index.ts` line; `tests/contracts` unaffected
  (no file under it touched).
- AC-7: **Open — see blocker above.**
- AC-8: `docker build`/`docker run`/`curl` above; CI wiring
  (`web_or_packages_changed`) verified by direct reading of the final
  `detect-changes/action.yml`/`ci.yml`/`pr-gate.yml`/`deploy.yml` diffs and
  by YAML-syntax validation — not by an actual GitHub Actions run, since
  this session has no CI access. Recommend watching the first real PR's
  `docker-build-check / web` job to confirm it actually runs (not skipped)
  on both an `apps/web`-only and a `packages/`-only test change.
