# Validation

## Command and scope
- `npm run test:run --workspace=apps/web -- src/components/providers/query-provider.test.tsx src/lib/api/auth-token.test.ts` before implementation: 3 expected failures (public membership request and login self-navigation), 4 passes.
- `npm run test:run --workspace=apps/web -- src/components/providers src/lib/api/auth-token.test.ts src/lib/api/generated-client.test.ts src/hooks/realtime/use-realtime-broadcast.test.ts src/middleware.test.ts`: 62 tests passed.
- `npm run test:run --workspace=apps/web`: 423 tests passed in 59 files before adding one additional query-string fixture.
- `./node_modules/.bin/tsc --noEmit -p apps/web/tsconfig.json`: passed.
- `npm run lint --workspace=apps/web`: passed, 0 errors / 51 warnings.
- `git diff --check`: passed.
- Final URL-fixture check: `npm run test:run --workspace=apps/web -- src/lib/api/auth-token.test.ts`: 7 passed, including protected pathname and login query string.

## Acceptance criteria evidence
- AC-1: Real root query context and realtime/site hooks with mocked API boundary reproduced the unauthenticated membership request before the fix, and no request afterward.
- AC-2: Provider lifecycle tests verify auth-pending suppression, validated-user subscription, sign-out cleanup, disabled state, and child rendering. Existing realtime hook tests pass.
- AC-3: Redirect-helper tests verify no login self-navigation and preserved protected-page redirect.

## Runtime limitation
Browser runtime returned `No browser is available`; discovery returned an empty
list. Live localhost page verification was unavailable. No deployment performed.
