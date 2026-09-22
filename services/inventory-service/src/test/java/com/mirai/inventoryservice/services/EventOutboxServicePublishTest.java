package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.inventory.application.StockMovementService;
import com.mirai.inventoryservice.kafka.KafkaProducer;
import com.mirai.inventoryservice.models.audit.EventOutbox;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventOutboxService - publishPendingEvents")
class EventOutboxServicePublishTest {

    @Mock
    private EventOutboxRepository eventOutboxRepository;

    @Mock
    private EventDeadLetterRepository eventDeadLetterRepository;

    @Mock
    private KafkaProducer kafkaProducer;

    @Mock
    private StockMovementService stockMovementService;

    @Captor
    private ArgumentCaptor<Map<String, Object>> messageCaptor;

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
    @DisplayName("promotes the stored correlation ID from payload to a top-level envelope field")
    void publishPendingEvents_promotesCorrelationId_toEnvelope() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("item_id", UUID.randomUUID().toString());
        payload.put("correlation_id", "req-abc");

        EventOutbox event = EventOutbox.builder()
                .id(UUID.randomUUID())
                .topic("inventory-changes")
                .eventType("CREATED")
                .entityType("stock_movement")
                .entityId(UUID.randomUUID())
                .payload(payload)
                .createdAt(OffsetDateTime.now())
                .build();

        when(eventOutboxRepository.findByPublishedAtIsNullAndPublishAttemptsLessThanOrderByCreatedAtAsc(anyInt()))
                .thenReturn(List.of(event));
        when(eventOutboxRepository.findById(event.getId())).thenReturn(Optional.of(event));

        // When
        eventOutboxService.publishPendingEvents();

        // Then
        verify(kafkaProducer).sendEvent(any(), any(), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).containsEntry("correlation_id", "req-abc");
    }

    @Test
    @DisplayName("Q-6c-4: uses the legacy item_id key when the site-scoped flag is off, even if the event carries a site")
    void publishPendingEvents_usesLegacyItemIdKey_whenFlagDisabled() {
        UUID productId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("item_id", productId.toString());

        EventOutbox event = EventOutbox.builder()
                .id(UUID.randomUUID())
                .topic("inventory-changes")
                .eventType("CREATED")
                .entityType("stock_movement")
                .entityId(UUID.randomUUID())
                .payload(payload)
                .siteId(siteId)
                .createdAt(OffsetDateTime.now())
                .build();

        when(eventOutboxRepository.findByPublishedAtIsNullAndPublishAttemptsLessThanOrderByCreatedAtAsc(anyInt()))
                .thenReturn(List.of(event));
        when(eventOutboxRepository.findById(event.getId())).thenReturn(Optional.of(event));

        eventOutboxService.publishPendingEvents();

        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafkaProducer).sendEvent(any(), keyCaptor.capture(), any());
        assertThat(keyCaptor.getValue()).isEqualTo(productId.toString());
    }

    @Test
    @DisplayName("Q-6c-4: uses site_id:product_id when the cutover flag is on and the event carries a site")
    void publishPendingEvents_usesSiteScopedKey_whenFlagEnabledAndSitePresent() {
        UUID productId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("item_id", productId.toString());

        EventOutbox event = EventOutbox.builder()
                .id(UUID.randomUUID())
                .topic("inventory-changes")
                .eventType("CREATED")
                .entityType("stock_movement")
                .entityId(UUID.randomUUID())
                .payload(payload)
                .siteId(siteId)
                .createdAt(OffsetDateTime.now())
                .build();

        when(eventOutboxRepository.findByPublishedAtIsNullAndPublishAttemptsLessThanOrderByCreatedAtAsc(anyInt()))
                .thenReturn(List.of(event));
        when(eventOutboxRepository.findById(event.getId())).thenReturn(Optional.of(event));
        ReflectionTestUtils.setField(eventOutboxService, "siteScopedPartitionKeyEnabled", true);

        eventOutboxService.publishPendingEvents();

        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafkaProducer).sendEvent(any(), keyCaptor.capture(), any());
        assertThat(keyCaptor.getValue()).isEqualTo(siteId + ":" + productId);
    }

    @Test
    @DisplayName("Q-6c-4: falls back to the legacy item_id key when the flag is on but the event has no site (pre-backfill row)")
    void publishPendingEvents_fallsBackToLegacyKey_whenFlagEnabledButNoSite() {
        UUID productId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("item_id", productId.toString());

        EventOutbox event = EventOutbox.builder()
                .id(UUID.randomUUID())
                .topic("inventory-changes")
                .eventType("CREATED")
                .entityType("stock_movement")
                .entityId(UUID.randomUUID())
                .payload(payload)
                .siteId(null)
                .createdAt(OffsetDateTime.now())
                .build();

        when(eventOutboxRepository.findByPublishedAtIsNullAndPublishAttemptsLessThanOrderByCreatedAtAsc(anyInt()))
                .thenReturn(List.of(event));
        when(eventOutboxRepository.findById(event.getId())).thenReturn(Optional.of(event));
        ReflectionTestUtils.setField(eventOutboxService, "siteScopedPartitionKeyEnabled", true);

        eventOutboxService.publishPendingEvents();

        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafkaProducer).sendEvent(any(), keyCaptor.capture(), any());
        assertThat(keyCaptor.getValue()).isEqualTo(productId.toString());
    }
}
