# Phase 0 Scope and Engineering Baseline

- Status: Complete
- Captured: 2026-08-12
- Environment: macOS 15.5 arm64
- Source baseline: branch `refactor/structure`, commit `02df79ad0c07`, plus the uncommitted
  architecture-document set listed in the working tree
- Cost ceiling: USD 300 per month for the recurring production stack

## 1. Approved scope decisions

- The second physical store is committed and multi-site isolation is an active requirement.
- MiraiSD remains a monorepo.
- The Spring application becomes a domain-modular monolith one vertical slice at a time.
- Web and the focused Expo client use the same versioned backend API.
- GHCR and one lean Hetzner host remain active scope.
- Redis, API replicas, a second host/load balancer, microservices, micro-frontends, paid observability
  tiers and Kafka HA remain trigger-based deferred work.
- [ADR-0001](../adr/0001-spring-domain-modular-monolith.md) is accepted.

## 2. Site and role baseline

| Item | Baseline decision |
| --- | --- |
| Existing site | `MAIN`; confirmed in code and seed behavior |
| Second site | Committed; stable code and editable display name are selected before its Phase 4 record is created |
| Current roles | `ADMIN`, `ASSISTANT_MANAGER`, `EMPLOYEE` |
| Target assignment | One or more active memberships per user, with one role per site |
| System administrator | Global override stored by the backend; never derived from user-editable metadata |
| Initial roster | Every current user is assigned to `MAIN` with their existing backend role |

The second-site code is an immutable database identifier chosen when the site is created, not a
hardcoded application constant. Its display name is editable. Neither value is needed to backfill
existing data and users to `MAIN`.

## 3. Local toolchain

| Component | Version/state |
| --- | --- |
| Node.js | 24.3.0 |
| pnpm | 10.19.0 |
| Java | Eclipse Temurin 21.0.8 |
| Maven wrapper | 3.9.11 |
| System Python | 3.9.6; below both Python projects' declared `>=3.11` requirement |
| Forecasting virtual environment | Present; contains its test/lint/type tools |
| Messaging virtual environment | Absent |

## 4. Measured quality and build baseline

Times are local warm-worktree wall-clock measurements and are not CI service-level objectives.

| Check | Result | Time | Evidence summary |
| --- | --- | ---: | --- |
| Web ESLint (`pnpm lint`) | Fail | 16.45 s | 100 findings: 38 errors and 62 warnings |
| Web TypeScript (`pnpm exec tsc --noEmit`) | Pass | 3.00 s | No type errors |
| Web Vitest (`pnpm test:run`) | Fail | 8.13 s | 255 passed, 3 failed; role permissions and stale-display threshold expectations drifted |
| Web production build (`pnpm build`) | Inconclusive | 226.75 s | Stayed at optimized-build stage and was manually stopped; exit 130 |
| Spring tests (`./mvnw test`) | Environment-blocked/fail | not reliable | 167 tests reported before termination: 157 errors caused primarily by Mockito/Byte Buddy being unable to self-attach in this managed environment; H2 also logged schema-order failures |
| Spring package (`./mvnw -DskipTests package`) | Pass | 3.48 s warm | Boot JAR created successfully |
| Forecasting tests (`.venv/bin/pytest -q`) | Pass | 4.65 s | 355 passed |
| Forecasting Ruff (`.venv/bin/ruff check .`) | Fail | 0.39 s | 351 findings across source, tests and experiments |
| Forecasting mypy (`.venv/bin/mypy src`) | Fail | 4.69 s | 31 errors in 15 of 26 checked source files; missing stubs plus application typing errors |
| Messaging tests | Not runnable | 0.08 s | System Python has no pytest and no project virtual environment exists |
| Messaging lint | Not runnable | n/a | Ruff is not installed outside the missing project environment |
| Contract tests | Not runnable | 0.02 s | Root Python environment has no pytest |

## 5. Database and delivery baseline

- Production uses Supabase PostgreSQL and authentication.
- Spring production sets `spring.jpa.hibernate.ddl-auto=none`, but development uses `update`.
- SQL migrations `V1` through `V49` exist, but `pom.xml` does not currently declare Flyway; the
  repository therefore does not prove that the application runs them.
- Legacy `infra/init-db` SQL overlaps with mapped entities and migration SQL.
- Spring tests primarily use H2 and currently emit PostgreSQL/schema-order incompatibilities.
- The only GitHub workflow deploys on `main` by SSH and rebuilds from a mutable checkout on the
  DigitalOcean host. It does not run required PR quality gates.
- Current production deploy duration is not measured by the workflow. Recording it is useful before
  changing deployment machinery, but it is not a Phase 0 exit requirement.

## 6. Current cost baseline

| Cost item | Current monthly cost | Evidence/state |
| --- | ---: | --- |
| DigitalOcean compute | USD 24 | Owner-supplied current charge |
| Supabase | USD 0 | Free plan |
| Vercel web hosting | USD 0 | Free plan |
| Other integrations | USD 0 | Owner confirmed all current integrations use free plans |
| GitHub Actions/GHCR | USD 0 incremental today | GHCR not implemented yet; future usage reviewed per phase |
| Total current recurring stack | USD 24 | Excludes domain registration if billed separately |
| Approved ceiling | USD 300 | User-supplied program constraint |

The expected DigitalOcean-to-Hetzner saving is a projection, not a baseline fact. Phase 10 MUST
compare like-for-like server capacity and include the temporary two-provider overlap.

## 7. Known baseline risks handed to later phases

1. Authorization trusts user-editable metadata and lacks complete issuer/audience validation.
2. Flyway files are present without a demonstrated Flyway runtime path.
3. Current web and Python lint gates fail; messaging and contract environments are not reproducible.
4. Spring tests are not PostgreSQL-faithful and are not runnable in this managed local environment.
5. The event publisher and schedulers are process-local.
6. Forecasting and messaging directly access inventory-owned PostgreSQL tables.
7. Messaging SQL refers to `machine_displays`, while Spring maps `machine_display`; this naming
   contract must be verified against production before changing either side.

## 8. Phase 0 completion record

- [x] Confirm the second store is committed; defer its identifier/display-name selection to Phase 4.
- [x] Define the initial membership backfill: all current users and roles go to `MAIN`.
- [x] Record current recurring costs: USD 24/month total, excluding any separately billed domain.
- [x] Record current hosting plans: DigitalOcean paid; Supabase, Vercel and integrations free.
- [x] Treat deployment duration as an optional pre-Phase-2/10 metric, not a Phase 0 gate.
- [x] Accept ADR-0001.
- [x] Classify current tables and controller mappings.
- [x] Approve the active/deferred scope and USD 300 ceiling.
