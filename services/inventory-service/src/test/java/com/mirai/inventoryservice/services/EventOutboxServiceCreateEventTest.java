package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.inventory.application.StockMovementService;
import com.mirai.inventoryservice.kafka.KafkaProducer;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.models.audit.EventOutbox;
import com.mirai.inventoryservice.inventory.domain.StockMovement;
import com.mirai.inventoryservice.catalog.domain.KujiType;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.repositories.EventDeadLetterRepository;
import com.mirai.inventoryservice.repositories.EventOutboxRepository;
import com.mirai.inventoryservice.shared.correlation.IdempotencyKeyContext;
import com.mirai.inventoryservice.sites.domain.Site;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;
import com.mirai.inventoryservice.shared.correlation.CorrelationIdContext;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
        when(eventOutboxRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        eventOutboxService.createStockMovementEvent(movement);

        // Then
        verify(eventOutboxRepository).saveAndFlush(outboxCaptor.capture());
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
        when(eventOutboxRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        eventOutboxService.createStockMovementEvent(movement1);
        eventOutboxService.createStockMovementEvent(movement2);

        // Then
        verify(eventOutboxRepository, org.mockito.Mockito.times(2)).saveAndFlush(outboxCaptor.capture());
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
        when(eventOutboxRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        eventOutboxService.createStockMovementEvent(movement1);
        eventOutboxService.createStockMovementEvent(movement2);

        // Then
        verify(eventOutboxRepository, org.mockito.Mockito.times(2)).saveAndFlush(outboxCaptor.capture());
        EventOutbox first = outboxCaptor.getAllValues().get(0);
        EventOutbox second = outboxCaptor.getAllValues().get(1);

        assertThat(first.getEntityId()).isNotEqualTo(second.getEntityId());
    }

    @Test
    @DisplayName("skips outbox event when item is a kuji prize child (parent has kujiType)")
    void createStockMovementEvent_skips_forKujiPrizeChild() {
        // Given
        Product kujiParent = Product.builder()
                .id(UUID.randomUUID())
                .name("Pokemon Kuji Box")
                .sku("KUJI-BOX-001")
                .kujiType(KujiType.CUSTOM)
                .build();
        Product child = Product.builder()
                .id(UUID.randomUUID())
                .name("alakazam ex 151")
                .sku("KUJI-PRIZE-001")
                .parent(kujiParent)
                .reorderPoint(0)
                .build();
        StockMovement movement = StockMovement.builder()
                .id(7L)
                .item(child)
                .quantityChange(-1)
                .reason(StockMovementReason.KUJI_PRIZE_WON)
                .locationType(LocationType.BOX_BIN)
                .previousQuantity(1)
                .currentQuantity(0)
                .at(OffsetDateTime.now())
                .build();

        // When
        eventOutboxService.createStockMovementEvent(movement);

        // Then — outbox is never written, and we never query totals/locations for the child
        verify(eventOutboxRepository, never()).saveAndFlush(any());
        verify(stockMovementService, never()).calculateTotalInventory(any());
        verify(stockMovementService, never()).resolveLocationCode(any(), any());
    }

    @Test
    @DisplayName("publishes event when item has a non-kuji parent (parent.kujiType is null)")
    void createStockMovementEvent_publishes_whenParentIsNotKuji() {
        // Given — a parented product whose parent is not a kuji root
        Product nonKujiParent = Product.builder()
                .id(UUID.randomUUID())
                .name("Some Bundle")
                .sku("BUNDLE-001")
                .build();
        Product child = Product.builder()
                .id(UUID.randomUUID())
                .name("Bundle Child")
                .sku("BUNDLE-CHILD-001")
                .parent(nonKujiParent)
                .reorderPoint(5)
                .build();
        StockMovement movement = StockMovement.builder()
                .id(8L)
                .item(child)
                .quantityChange(-1)
                .reason(StockMovementReason.SALE)
                .locationType(LocationType.BOX_BIN)
                .fromLocationId(UUID.randomUUID())
                .toLocationId(UUID.randomUUID())
                .previousQuantity(3)
                .currentQuantity(2)
                .at(OffsetDateTime.now())
                .build();
        when(stockMovementService.resolveLocationCode(any(), any())).thenReturn("B1");
        when(stockMovementService.calculateTotalInventory(any())).thenReturn(2);
        when(eventOutboxRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        eventOutboxService.createStockMovementEvent(movement);

        // Then — outbox was written exactly once
        verify(eventOutboxRepository).saveAndFlush(any());
    }

    @Test
    @DisplayName("carries the request's correlation ID into the payload when present")
    void createStockMovementEvent_carriesCorrelationId_whenPresentInMdc() {
        // Given
        MDC.put(CorrelationIdContext.MDC_KEY, "req-123");
        try {
            StockMovement movement = buildMovement(11L);
            when(stockMovementService.resolveLocationCode(any(), any())).thenReturn("B1");
            when(stockMovementService.calculateTotalInventory(any())).thenReturn(5);
            when(eventOutboxRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            eventOutboxService.createStockMovementEvent(movement);

            // Then
            verify(eventOutboxRepository).saveAndFlush(outboxCaptor.capture());
            assertThat(outboxCaptor.getValue().getPayload()).containsEntry("correlation_id", "req-123");
        } finally {
            MDC.remove(CorrelationIdContext.MDC_KEY);
        }
    }

    @Test
    @DisplayName("stores a null correlation ID when created outside a request (e.g. a scheduled job)")
    void createStockMovementEvent_storesNullCorrelationId_outsideARequest() {
        // Given
        StockMovement movement = buildMovement(12L);
        when(stockMovementService.resolveLocationCode(any(), any())).thenReturn("B1");
        when(stockMovementService.calculateTotalInventory(any())).thenReturn(5);
        when(eventOutboxRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        eventOutboxService.createStockMovementEvent(movement);

        // Then
        verify(eventOutboxRepository).saveAndFlush(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getPayload()).containsEntry("correlation_id", null);
    }

    @Test
    @DisplayName("populates the AC-4 envelope: site_id from the movement, a fixed event_version, null causation_id")
    void createStockMovementEvent_populatesEnvelope_siteAndVersionAndCausation() {
        // Given
        Site site = Site.builder().id(UUID.randomUUID()).code("MAIN").name("Main").build();
        StockMovement movement = buildMovement(20L);
        movement.setSite(site);
        when(stockMovementService.resolveLocationCode(any(), any())).thenReturn("B1");
        when(stockMovementService.calculateTotalInventory(any())).thenReturn(5);
        when(eventOutboxRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        eventOutboxService.createStockMovementEvent(movement);

        // Then
        verify(eventOutboxRepository).saveAndFlush(outboxCaptor.capture());
        EventOutbox saved = outboxCaptor.getValue();
        assertThat(saved.getSiteId()).isEqualTo(site.getId());
        assertThat(saved.getEventVersion()).isEqualTo(1);
        assertThat(saved.getCausationId()).isNull();
    }

    @Test
    @DisplayName("stores a null site_id when the movement carries no site")
    void createStockMovementEvent_storesNullSiteId_whenMovementHasNoSite() {
        // Given
        StockMovement movement = buildMovement(21L);
        movement.setSite(null);
        when(stockMovementService.resolveLocationCode(any(), any())).thenReturn("B1");
        when(stockMovementService.calculateTotalInventory(any())).thenReturn(5);
        when(eventOutboxRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        eventOutboxService.createStockMovementEvent(movement);

        // Then
        verify(eventOutboxRepository).saveAndFlush(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getSiteId()).isNull();
    }

    @Test
    @DisplayName("carries the request's idempotency key from MDC into the envelope when present")
    void createStockMovementEvent_carriesIdempotencyKey_whenPresentInMdc() {
        MDC.put(IdempotencyKeyContext.MDC_KEY, "idem-abc");
        try {
            StockMovement movement = buildMovement(22L);
            when(stockMovementService.resolveLocationCode(any(), any())).thenReturn("B1");
            when(stockMovementService.calculateTotalInventory(any())).thenReturn(5);
            when(eventOutboxRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            eventOutboxService.createStockMovementEvent(movement);

            verify(eventOutboxRepository).saveAndFlush(outboxCaptor.capture());
            assertThat(outboxCaptor.getValue().getIdempotencyKey()).isEqualTo("idem-abc");
        } finally {
            MDC.remove(IdempotencyKeyContext.MDC_KEY);
        }
    }

    @Test
    @DisplayName("stores a null idempotency key when none was set (e.g. before T-6c-10 wires the v1 command layer)")
    void createStockMovementEvent_storesNullIdempotencyKey_whenNotSet() {
        StockMovement movement = buildMovement(23L);
        when(stockMovementService.resolveLocationCode(any(), any())).thenReturn("B1");
        when(stockMovementService.calculateTotalInventory(any())).thenReturn(5);
        when(eventOutboxRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        eventOutboxService.createStockMovementEvent(movement);

        verify(eventOutboxRepository).saveAndFlush(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getIdempotencyKey()).isNull();
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
