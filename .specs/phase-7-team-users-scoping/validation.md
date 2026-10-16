# Validation - Phase 7 team users scoping

| Check | Command | Result |
| --- | --- | --- |
| inventory-service (unit and all ITs) | `./mvnw -o clean test -Dtest='*'` | 1199 tests, 0 failures, 0 errors, BUILD SUCCESS |
| Architecture | included above (`ArchitectureTest`) | passes; no new module edges, `archunit.properties` unchanged |
| Web types | `npx tsc --noEmit` | clean |
| Web tests | `npx vitest run` | 490 passed |
| Contract | `./mvnw -o -Dtest=OpenApiContractExportTest test`, then `npm run generate` in `packages/api-client` | regenerated; v1 users routes present |

Not run: messaging and contract-event suites (no change in those services), Playwright, `tests/e2e`, the independent PR gate.
