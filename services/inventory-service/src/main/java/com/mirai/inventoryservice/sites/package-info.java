/**
 * Sites and physical location topology.
 *
 * <p>{@code sites.api.LocationAggregateController} (and its {@code LocationAggregateService}/
 * {@code LocationAggregateRepository}) is an approved cross-module read projection (R-1,
 * .specs/phase-6-inventory/log.md T-6): its native query reads {@code locations}/
 * {@code storage_locations} (owned here), {@code location_inventory} (owned by {@code inventory}),
 * and {@code machine_display} (owned by {@code displays}) in one statement, per
 * docs/specs/spring-domain-modular-monolith.md §7.4, rather than being decomposed across three
 * modules or reassigned to any single one of them.
 */
package com.mirai.inventoryservice.sites;
