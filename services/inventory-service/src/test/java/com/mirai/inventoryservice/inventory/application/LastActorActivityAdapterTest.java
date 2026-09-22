package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.inventory.domain.StockMovement;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Phase 6a T-2 (.specs/phase-6-inventory/log.md, R-3): {@link LastActorActivityAdapter} is the
 * inventory-side implementation of identity's {@code LastActorActivityPort}, so the port/adapter
 * translation logic (StockMovement -> OffsetDateTime, Object[] rows -> a Map) lives here rather
 * than in UserService.
 */
@ExtendWith(MockitoExtension.class)
class LastActorActivityAdapterTest {

    @Mock
    private StockMovementRepository stockMovementRepository;

    @InjectMocks
    private LastActorActivityAdapter adapter;

    @Test
    void lastActivityFor_ReturnsMovementTimestamp() {
        UUID actorId = UUID.randomUUID();
        OffsetDateTime at = OffsetDateTime.now();
        StockMovement movement = StockMovement.builder().at(at).build();
        when(stockMovementRepository.findTopByActorIdOrderByAtDesc(actorId))
                .thenReturn(Optional.of(movement));

        Optional<OffsetDateTime> result = adapter.lastActivityFor(actorId);

        assertEquals(Optional.of(at), result);
    }

    @Test
    void lastActivityFor_NoMovements_ReturnsEmpty() {
        UUID actorId = UUID.randomUUID();
        when(stockMovementRepository.findTopByActorIdOrderByAtDesc(actorId))
                .thenReturn(Optional.empty());

        assertFalse(adapter.lastActivityFor(actorId).isPresent());
    }

    @Test
    void lastActivityByActor_MapsRowsToMap() {
        UUID actorId1 = UUID.randomUUID();
        UUID actorId2 = UUID.randomUUID();
        OffsetDateTime at1 = OffsetDateTime.now();
        OffsetDateTime at2 = at1.minusDays(1);
        when(stockMovementRepository.findLatestMovementTimestampsByActor())
                .thenReturn(List.of(
                        new Object[] {actorId1, at1},
                        new Object[] {actorId2, at2}));

        Map<UUID, OffsetDateTime> result = adapter.lastActivityByActor();

        assertEquals(Map.of(actorId1, at1, actorId2, at2), result);
    }

    @Test
    void lastActivityByActor_NoMovements_ReturnsEmptyMap() {
        when(stockMovementRepository.findLatestMovementTimestampsByActor())
                .thenReturn(List.of());

        assertTrue(adapter.lastActivityByActor().isEmpty());
    }
}
