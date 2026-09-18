# Phase 7 — Notifications, reviews and audit

## Tier

Full — tenant data, public APIs, asynchronous delivery and audit boundaries are involved.

## Problem and outcome

Notifications, reviews and audit records are operational data but currently use legacy persistence and site-less realtime paths. This slice makes ownership, authorization and user-facing reads site-aware without losing cross-module auditability.

## Durable context

- Plan: [Enterprise modernization §10](../../docs/plans/enterprise-modernization.md#10-phase-7--shipments-and-remaining-site-operations); [Stage G](../../docs/plans/spring-domain-modular-monolith-migration.md#9-stage-g-remaining-operational-modules-parent-phase-7)
- Specs: [Multi-site data and API](../../docs/specs/multi-site-data-and-api.md)
- Baseline: [Phase 7 inventory](../../docs/baseline/phase-7-inventory.md)

## Acceptance criteria

- AC-1: `notifications`, `reviews`, `review_daily_counts`, and `audit_logs` have recorded ownership, site sources and expand/backfill/verify/constrain migrations, including site/user/date uniqueness for review counts.
- AC-2: Notification recipient reads, review operations, audit-log listing/detail and stock audit dashboard reads authorize site before resource lookup; intentional system-admin/cross-site reporting is explicit.
- AC-3: Audit context and notification creation preserve site, actor and correlation identity; realtime payloads are site-aware and protected by the Realtime tenancy rollout.
- AC-4: Each module owns repositories and exposes stable contracts to callers rather than retaining legacy/foreign repository access.
- AC-5: Site v1 routes and web consumers replace new legacy use; retained notification/review/audit routes have compatibility and deprecation evidence.
- AC-6: PostgreSQL authorization/migration, async delivery, contract and architecture tests prove behavior.

## Tasks

- T-1: Inventory writers/readers, recipient vs site semantics, dashboard consumers and realtime publishers.
- T-2: Define explicit site source for records lacking a direct operational parent and record system-admin reporting policy.
- T-3: Implement expand/backfill/verify; defer constrain to its own release.
- T-4: Move modules, introduce facades/read projections, and migrate v1/web consumers.
- T-5: Coordinate notification/audit site-aware broadcasts and validate tenant isolation.
