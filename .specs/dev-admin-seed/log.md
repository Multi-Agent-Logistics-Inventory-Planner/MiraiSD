# Log

## Current handoff
- Status: implementation starting.
- Next action: prove regression then wire the user-added constant into seed/all.
- Decision: ADMIN_EMPLOYEE_EMAIL designates ADMIN, not SYSTEM_ADMIN; dev-only provisioning.
  Preserve unrelated web/DB setup changes. Keep response shape unchanged.
- Validation: pending JDK 21 native tests.
- Risks: new image must be built before invoking updated seed/all.

## Completed handoff
- Status: implementation and independent reviews complete; 25 native tests pass.
- Next action: commit scoped files; rebuild local inventory-service and rerun seed/all to use it.
- Changed: existing account seeder loops over employee/admin templates, preserves saved identity,
  grants missing MAIN membership, and returns employee for unchanged response.
- Last verified: JDK 21 `./mvnw -q clean test -Dtest=DevSeedControllerTest,UserServiceTest,ProductionSafeConfigurationTest,ArchitectureTest` passed.
- Remaining limitation: existing inactive memberships remain inactive by the existing authorizer's
  design; system-admin flag and Supabase identity are not modified by seeding.
