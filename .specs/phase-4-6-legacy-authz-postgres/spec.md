# PostgreSQL legacy-route authorization proof

- Tier: Full
- Status: In progress
- Plan: `docs/plans/phase-4-6-closeout.md`, Slice B

## Problem

The legacy MAIN-scoped HTTP authorization tests run only against H2. They do not
exercise the revoked/absent-membership, foreign-resource, wrong-parent,
actor-spoofing, and explicit system-admin cases on PostgreSQL.

## Acceptance criteria

1. A MockMvc integration base starts a shared PostgreSQL Testcontainer in a
   static block and supplies its datasource properties without Kafka.
2. Every retained stock-movement read requires MAIN authorization and returns only
   MAIN or null-site compatibility rows; foreign-site rows are excluded.
3. A Postgres-backed legacy-route test covers all six scenarios named in Slice B
   across the retained location, inventory, and stock-movement routes.
4. The test establishes that the system-admin bypass does not create a membership.
5. Validation records that the integration profile uses Hibernate create-drop and
   therefore does not prove Flyway migration parity.

## Non-goals

- Changing any retained legacy route beyond the three unguarded stock-movement reads.
- Adding Flyway or applying a migration.
