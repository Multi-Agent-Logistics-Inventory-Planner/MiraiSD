# Temporary MAIN inventory counts

## Tier

Standard: existing web reads and display only; no API or authorization changes.

## Problem and outcome

Restore legacy inventory totals and the existing product stock breakdown for MAIN
until Phase 6 supplies scoped inventory. Remove the migration message without
adding labels or redesigning the UI, as requested by the user on 2026-09-10.

## Durable context

- [Architecture](../../docs/specs/spring-domain-modular-monolith.md)
- [Plan](../../docs/plans/enterprise-modernization.md)
- [Phase 5d AC-6b](../phase-5d-catalog-v1-and-web/spec.md): this user-authorized
  temporary MAIN-only exception supersedes withholding counts there.

## Acceptance criteria

- AC-1: MAIN shows totals from `/api/inventory/totals` and the existing legacy
  product/location breakdown; catalog quantity is never used as a fallback.
- AC-2: Other/unresolved sites do not fetch or display legacy totals, including
  after switching sites or receiving a late MAIN response.
- AC-3: Site settings, assortment and permissions retain their existing behavior.
- AC-4: Loading/failure is not rendered as zero stock. Successful missing totals
  represent zero stock. Remove the Phase 6 message; add no new UI copy/layout.

## Tasks

- T-1: Update regression tests for MAIN, other sites, loading and failure.
- T-2: Restore the existing stock UI with a MAIN-only legacy totals join.
- T-3: Run native web checks, review and commit the scoped change.

## Status restoration (2026-09-10)

User authorized restoring legacy status on this branch before further refactor testing.
- AC-5: Products table and detail modal display Active/Inactive from global
  `product.isActive`, even when `isStocked` disagrees. Preserve green/red colors.
- AC-6: Status sorting uses the same legacy flag in both directions.
- AC-7: Preserve MAIN quantity restoration, site settings, stored assortment,
  permissions and database schema. This temporary global status is not site availability.

This supersedes Phase 5d's assortment-driven status presentation only.
