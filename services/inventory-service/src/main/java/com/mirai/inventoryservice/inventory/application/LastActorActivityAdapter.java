package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.identity.application.LastActorActivityPort;
import com.mirai.inventoryservice.inventory.domain.StockMovement;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link LastActorActivityPort} implementation backed by {@code StockMovementRepository}. Kept
 * as a dedicated adapter (rather than implementing the port directly on
 * {@code StockMovementService}), matching the {@code InitialStockAdapter}/
 * {@code InventoryCleanupAdapter} pattern already used for catalog's ports.
 */
@Component
@RequiredArgsConstructor
class LastActorActivityAdapter implements LastActorActivityPort {

    private final StockMovementRepository stockMovementRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<OffsetDateTime> lastActivityFor(UUID actorId) {
        return stockMovementRepository.findTopByActorIdOrderByAtDesc(actorId)
                .map(StockMovement::getAt);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, OffsetDateTime> lastActivityByActor() {
        List<Object[]> results = stockMovementRepository.findLatestMovementTimestampsByActor();
        Map<UUID, OffsetDateTime> map = new HashMap<>();
        for (Object[] row : results) {
            UUID actorId = (UUID) row[0];
            OffsetDateTime timestamp = (OffsetDateTime) row[1];
            map.put(actorId, timestamp);
        }
        return map;
    }
}
