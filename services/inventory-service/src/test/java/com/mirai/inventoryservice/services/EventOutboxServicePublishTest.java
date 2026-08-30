package com.mirai.inventoryservice.services;

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
}
