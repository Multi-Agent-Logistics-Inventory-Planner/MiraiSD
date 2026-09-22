# Dev admin account seed

Tier: Full (local role/membership seeding).
Context: docs/specs/authentication-and-authorization.md (backend roles and MAIN membership),
existing @Profile("dev") DevSeedController and user-provided ADMIN_EMPLOYEE_EMAIL.

AC-1: seed/all creates the designated admin with ADMIN role and MAIN membership, including
when products already exist. Existing identity/name/Supabase link remain intact.
AC-2: Repeat calls reuse the account, restore designated ADMIN role, and reuse the existing
membership authorizer. Existing employee seeding remains unchanged.
AC-3: Dev-only profile remains; no production user changes or endpoint response/contract changes.

Plan: regression tests first; scoped helper and invocation; JDK 21 wrapper tests; independent
Standards and Spec review; commit only this task's controller/test/record.
