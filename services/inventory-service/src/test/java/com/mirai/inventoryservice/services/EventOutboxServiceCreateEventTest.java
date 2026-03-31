package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.kafka.KafkaProducer;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.audit.EventOutbox;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.repositories.EventDeadLetterRepository;
import com.mirai.inventoryservice.repositories.EventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventOutboxService - createStockMovementEvent")
class EventOutboxServiceCreateEventTest {

    @Mock
    private EventOutboxRepository eventOutboxRepository;

    @Mock
    private EventDeadLetterRepository eventDeadLetterRepository;

    @Mock
    private KafkaProducer kafkaProducer;

    @Mock
    private StockMovementService stockMovementService;

    @Captor
    private ArgumentCaptor<EventOutbox> outboxCaptor;

    private EventOutboxService eventOutboxService;

    @BeforeEach
    void setUp() {
        eventOutboxService = new EventOutboxService(
                eventOutboxRepository,
                kafkaProducer,
                stockMovementService,
                eventDeadLetterRepository
        );
        ReflectionTestUtils.setField(eventOutboxService, "inventoryChangesTopic", "inventory-changes");
    }

    @Test
    @DisplayName("should use deterministic UUID from movement ID as entityId")
    void createStockMovementEvent_usesDeterministicEntityId_fromMovementId() {
        // Given
        Long movementId = 42L;
        StockMovement movement = buildMovement(movementId);
        when(stockMovementService.resolveLocationCode(any(), any())).thenReturn("B1");
        when(stockMovementService.calculateTotalInventory(any())).thenReturn(100);
        when(eventOutboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        eventOutboxService.createStockMovementEvent(movement);

        // Then
        verify(eventOutboxRepository).save(outboxCaptor.capture());
        EventOutbox saved = outboxCaptor.getValue();

        UUID expectedEntityId = UUID.nameUUIDFromBytes(movementId.toString().getBytes());
        assertThat(saved.getEntityId()).isEqualTo(expectedEntityId);
    }

    @Test
    @DisplayName("should produce same entityId for same movement ID (deterministic)")
    void createStockMovementEvent_producesSameEntityId_sameMovementId() {
        // Given
        Long movementId = 99L;
        StockMovement movement1 = buildMovement(movementId);
        StockMovement movement2 = buildMovement(movementId);
        when(stockMovementService.resolveLocationCode(any(), any())).thenReturn("R1");
        when(stockMovementService.calculateTotalInventory(any())).thenReturn(50);
        when(eventOutboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        eventOutboxService.createStockMovementEvent(movement1);
        eventOutboxService.createStockMovementEvent(movement2);

        // Then
        verify(eventOutboxRepository, org.mockito.Mockito.times(2)).save(outboxCaptor.capture());
        EventOutbox first = outboxCaptor.getAllValues().get(0);
        EventOutbox second = outboxCaptor.getAllValues().get(1);

        assertThat(first.getEntityId()).isEqualTo(second.getEntityId());
    }

    @Test
    @DisplayName("should produce different entityId for different movement IDs")
    void createStockMovementEvent_producesDifferentEntityId_differentMovementIds() {
        // Given
        StockMovement movement1 = buildMovement(1L);
        StockMovement movement2 = buildMovement(2L);
        when(stockMovementService.resolveLocationCode(any(), any())).thenReturn("C1");
        when(stockMovementService.calculateTotalInventory(any())).thenReturn(10);
        when(eventOutboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        eventOutboxService.createStockMovementEvent(movement1);
        eventOutboxService.createStockMovementEvent(movement2);

        // Then
        verify(eventOutboxRepository, org.mockito.Mockito.times(2)).save(outboxCaptor.capture());
        EventOutbox first = outboxCaptor.getAllValues().get(0);
        EventOutbox second = outboxCaptor.getAllValues().get(1);

        assertThat(first.getEntityId()).isNotEqualTo(second.getEntityId());
    }

    private StockMovement buildMovement(Long id) {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder()
                .id(productId)
                .name("Test Product")
                .sku("SKU-001")
                .reorderPoint(10)
                .build();

        return StockMovement.builder()
                .id(id)
                .item(product)
                .quantityChange(5)
                .reason(StockMovementReason.SALE)
                .locationType(LocationType.BOX_BIN)
                .fromLocationId(UUID.randomUUID())
                .toLocationId(UUID.randomUUID())
                .previousQuantity(20)
                .currentQuantity(15)
                .actorId(UUID.randomUUID())
                .at(OffsetDateTime.now())
                .build();
    }
}
