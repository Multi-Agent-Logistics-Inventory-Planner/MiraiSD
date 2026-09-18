# Implementation log

## Current handoff

- Status: T-1 and T-2 complete. T-3 added standalone manifests and a live CI guard for the fast envelope suite. The one permitted E2E preflight failed beyond dependencies, so E2E is recorded as unrunnable locally and deferred to Slice E.
- Next action: proceed to the next closeout slice; Slice E owns repairing/replacing the pre-multi-site E2E stack and deciding its CI gate.
- Decisions that must survive compaction: retain JaCoCo's `argLine` with Surefire late evaluation; use Mockito's managed premain agent because the prescribed dynamic-agent flag alone cannot attach on Homebrew JDK 21; do not exclude AnalyticsControllerSecurityIT or ForecastControllerSecurityIT.
- Last verified: `services/forecasting-service/.venv/bin/pytest -q tests/contracts` — 31 passed; E2E collection is blocked before Docker starts by `ModuleNotFoundError: No module named 'jwt'`.
- Open risks/questions: E2E is pre-multi-site and locally unrunnable: after five minutes, PostgreSQL, Kafka, and inventory were healthy but forecasting stayed unhealthy despite Uvicorn/Kafka worker logs. Its repair and CI wiring are owned by Slice E. No Slice A product/runtime risk remains.

## Assumptions and decisions

- Slice A is Standard per the approved Phase 4–6 closeout plan. The POM-only change has no meaningful isolated application test; the focused failing controller test is the regression proof.
- Surefire retains the required `@{argLine} -XX:+EnableDynamicAgentLoading` prefix: JaCoCo's `prepare-agent` sets `argLine`, and replacing it would silently disable coverage. On Homebrew OpenJDK 21.0.12.1, that flag alone still fails both external and self attachment. The existing managed `mockito-core` artifact is therefore also preloaded as a premain agent, without adding a dependency or suppressing coverage.
- Initial sandboxed Maven runs cannot bind the embedded Tomcat port or access Docker; their unsandboxed local reruns passed. These are execution-environment restrictions, not test failures.
- Maven incremental compilation can retain orphaned `.class` and lambda classes after source methods
  are deleted or moved. ArchUnit scans `target/classes`, so this can surface phantom frozen-rule
  changes (and FreezingArchRule will correctly refuse to update the store). After any deletion or
  move, use `./mvnw -B clean test` and `./mvnw -B clean test -Dtest='*IT'`; do not update the
  pinned caller inventory or enable `allowStoreUpdate`. This was confirmed after Slice B removed
  unscoped stock-movement service overloads: the non-clean run reported stale classes, while the
  clean suites passed.
- The analytics/forecasting H2 risk was reproduced by the required full IT run and did not recur: both controller security classes were included in a 526-test passing suite. The historical standing risk is retired for this baseline.
- `tests/e2e` and `tests/contracts` had no dependency manifest and neither suite runs in a workflow. Their documented bare `pytest` commands could not work on a clean checkout: there is no root Python environment, while E2E imports `jwt`, `requests`, SQLAlchemy/psycopg, and requires pytest-timeout; contracts imports pytest, jsonschema, and Pydantic consumer models. Each suite now owns a minimal `requirements.txt`; E2E will use its dedicated `.venv`, not a service virtual environment.
- The single E2E Compose preflight built and started all four isolated services. PostgreSQL, Kafka, and inventory became healthy, but forecasting remained unhealthy after five minutes. Logs show Uvicorn and the Kafka worker running, so this is a health-check/stack failure beyond missing Python dependencies. Per the approved bounded effort, no test venv was created, no E2E test was run, and no stack debugging was attempted. It is a pre-multi-site Slice E concern.
- `event-envelope-contracts` now runs `pytest tests/contracts` in CI with `tests/contracts/requirements.txt`; it intentionally runs on every CI invocation because it is fast and guards the cross-service envelope before Slice E changes it. It deliberately has no `needs: changes`, so it starts independently while still contributing to the PR gate aggregate. E2E CI wiring remains Slice E-owned.
- Both Python manifests use lower-bound requirements consistently. `pandas` remains in the contract manifest because the imported forecasting consumer model imports it; it is not required by JSON Schema validation itself.
- The new contract manifest installed in a disposable local venv, but the workspace's `/usr/bin/python3` is Python 3.9 and cannot import the Python-3.10 `str | None` annotations in the real forecasting consumer model. The CI job explicitly uses Python 3.11, matching the services' supported syntax; this is an interpreter limitation, not a manifest failure.

## Task record

### T-1 — JDK 21 Mockito unblock

- Changed: `services/inventory-service/pom.xml` adds the minimal Surefire block, preserving JaCoCo late evaluation and preloading the managed Mockito agent.
- Tests: `./mvnw -B test -Dtest=LocationInventoryControllerSecurityIT` before and after change.
- Result: baseline failed before test bodies execute (`Could not self-attach to current VM using external process`); final focused regression passed 14/14 with a 27 MB `target/jacoco.exec`.

### T-2 — Maven gates and analytics/forecasting risk

- Tests: `./mvnw -B test`; `./mvnw -B test -Dtest='*IT'` (JDK 21; Docker-enabled).
- Result: unit/ArchUnit suite passed 481 tests; IT suite passed 526 tests. The sandbox-only attempts failed to bind Tomcat / reach Docker, then passed in permitted local execution. AnalyticsControllerSecurityIT and ForecastControllerSecurityIT ran in the passing IT suite; no exclusion or fixture change was used.

### T-3 — contract, web, and Python gates

- Tests: `./mvnw -B -Dtest=OpenApiContractExportTest test`; contract and generated client freshness checks; `npm run test:run && npx tsc --noEmit && npm run lint`; `services/forecasting-service/.venv/bin/pytest -q tests/contracts`; E2E command using that available project Python environment.
- Result: OpenAPI export passed; both generated outputs were fresh and API-client typecheck passed. Web passed 66 files / 454 tests; TypeScript passed; lint had 0 errors / 53 existing warnings. Contract suite initially passed 31 tests only by incidentally borrowing the forecasting-service environment; it now has a standalone manifest and Python-3.11 CI guard. Its new local venv installed dependencies but cannot execute with the host's Python 3.9, while CI uses 3.11. E2E initially failed at collection because its runner imports undeclared `PyJWT`; its one Compose preflight then failed beyond dependencies, so no dedicated-venv run was attempted.

## Test plan

- AC-1: focused Mockito-backed controller security test passed 14/14 with a nonempty 27 MB JaCoCo execution file.
- AC-2: Maven, contract freshness, generated-client, web, and the initial 31-test contract run passed. Both Python suites now own manifests; contract tests are live in CI. E2E is explicitly unrunnable locally after one bounded preflight and remains a Slice E-owned validation gap.
- AC-3: both named security ITs were included and passed in the 526-test `*IT` suite.
