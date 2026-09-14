/**
 * Site stock, stock movements, adjustment/transfer primitives and totals.
 *
 * <p>Public facade for callers outside this module: {@code inventory.application.
 * InventoryOperations} (writes: {@code applyDelta}, {@code adjustQuantity}, {@code recordMovement},
 * {@code syncProductTotals}) and {@code inventory.application.InventoryQueries} (reads). Every
 * production class outside {@code inventory} must reach {@code location_inventory}/
 * {@code stock_movements} through one of these two, never {@code inventory.infrastructure}
 * directly -- enforced live by {@code ArchitectureTest
 * .noProductionClassOutsideInventoryDependsOnInventoryInfrastructure} and pinned by
 * {@code InventoryOperationsCallerSetTest} (.specs/phase-6-inventory/log.md T-5/T-7).
 *
 * <p>{@code StockMovement} lives here (moved from {@code models.audit} in T-3, R-2) while its
 * {@code AuditLog} association stays in {@code models.audit}, not yet migrated -- an accepted
 * existing relationship carried across the module boundary
 * (docs/specs/spring-domain-modular-monolith.md §6.1 rule 8), to be resolved when {@code audit}
 * migrates (Phase 7).
 *
 * <p>{@code inventory -> identity} (R-4/R-3, T-2) is narrow and one-directional:
 * {@code identity.application.LastActorActivityPort} is declared in {@code identity} (the
 * consumer), implemented by {@code inventory.application.LastActorActivityAdapter} (the
 * provider), so {@code identity} carries no edge back into {@code inventory} -- see
 * docs/specs/spring-domain-modular-monolith.md §6.2 for the full dependency-direction rationale.
 */
package com.mirai.inventoryservice.inventory;
