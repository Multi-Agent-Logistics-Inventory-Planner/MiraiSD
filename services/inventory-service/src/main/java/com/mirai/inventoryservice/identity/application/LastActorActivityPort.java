package com.mirai.inventoryservice.identity.application;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a user's last recorded stock-movement activity for {@code UserService}'s
 * last-activity display, without {@code identity} depending on inventory's tracked-movement
 * storage directly.
 *
 * <p>Declared here (port-in-consumer, per the {@code InitialStockPort}/{@code InventoryCleanupPort}
 * precedent in {@code catalog.application}) and implemented outside {@code identity} by the
 * module that owns stock-movement history, so the dependency edge is {@code inventory ->
 * identity} rather than {@code identity -> inventory}. See docs:
 * .specs/phase-6-inventory/log.md (R-3) — the alternative, an {@code identity -> inventory} edge,
 * would combine with inventory's existing need for a managed {@code identity.domain.User} (R-4)
 * into a module cycle that {@code moduleDependencyEdgesMatchApprovedBaseline} does not itself
 * detect once both directions are individually approved.
 */
public interface LastActorActivityPort {

    /** The timestamp of {@code actorId}'s most recent stock movement, if any. */
    Optional<OffsetDateTime> lastActivityFor(UUID actorId);

    /** The most recent stock-movement timestamp for every actor that has one. */
    Map<UUID, OffsetDateTime> lastActivityByActor();
}
