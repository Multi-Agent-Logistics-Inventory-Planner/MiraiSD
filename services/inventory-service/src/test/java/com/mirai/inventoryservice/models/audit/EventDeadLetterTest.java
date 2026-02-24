package com.mirai.inventoryservice.models.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EventDeadLetter")
class EventDeadLetterTest {

    @Test
    @DisplayName("should create EventDeadLetter from EventOutbox")
    void shouldCreateFromOutboxEvent() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("product_id", "test-product");
        payload.put("quantity_change", -5);

        OffsetDateTime createdAt = OffsetDateTime.now().minusHours(1);

        EventOutbox outboxEvent = EventOutbox.builder()
                .id(UUID.randomUUID())
                .eventType("CREATED")
                .entityType("stock_movement")
                .entityId(UUID.randomUUID())
                .payload(payload)
                .topic("inventory-changes")
                .publishAttempts(3)
                .lastError("Connection refused")
                .createdAt(createdAt)
                .build();

        // When
        EventDeadLetter deadLetter = EventDeadLetter.fromOutboxEvent(outboxEvent);

        // Then
        assertThat(deadLetter).isNotNull();
        assertThat(deadLetter.getEventType()).isEqualTo("CREATED");
        assertThat(deadLetter.getEntityType()).isEqualTo("stock_movement");
        assertThat(deadLetter.getEntityId()).isEqualTo(outboxEvent.getEntityId());
        assertThat(deadLetter.getPayload()).isEqualTo(payload);
        assertThat(deadLetter.getTopic()).isEqualTo("inventory-changes");
        assertThat(deadLetter.getOriginalAttempts()).isEqualTo(3);
        assertThat(deadLetter.getLastError()).isEqualTo("Connection refused");
        assertThat(deadLetter.getOriginalCreatedAt()).isEqualTo(createdAt);
        assertThat(deadLetter.getMovedToDeadLetterAt()).isNotNull();
    }

    @Test
    @DisplayName("should build EventDeadLetter with builder")
    void shouldBuildWithBuilder() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", "value");
        UUID entityId = UUID.randomUUID();
        OffsetDateTime originalCreatedAt = OffsetDateTime.now().minusMinutes(30);
        OffsetDateTime movedAt = OffsetDateTime.now();

        // When
        EventDeadLetter deadLetter = EventDeadLetter.builder()
                .eventType("UPDATED")
                .entityType("inventory")
                .entityId(entityId)
                .payload(payload)
                .topic("test-topic")
                .originalAttempts(2)
                .lastError("Timeout")
                .originalCreatedAt(originalCreatedAt)
                .movedToDeadLetterAt(movedAt)
                .build();

        // Then
        assertThat(deadLetter.getEventType()).isEqualTo("UPDATED");
        assertThat(deadLetter.getEntityType()).isEqualTo("inventory");
        assertThat(deadLetter.getEntityId()).isEqualTo(entityId);
        assertThat(deadLetter.getPayload()).isEqualTo(payload);
        assertThat(deadLetter.getTopic()).isEqualTo("test-topic");
        assertThat(deadLetter.getOriginalAttempts()).isEqualTo(2);
        assertThat(deadLetter.getLastError()).isEqualTo("Timeout");
        assertThat(deadLetter.getOriginalCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(deadLetter.getMovedToDeadLetterAt()).isEqualTo(movedAt);
    }

    @Test
    @DisplayName("should handle null lastError when creating from outbox")
    void shouldHandleNullLastError() {
        // Given
        EventOutbox outboxEvent = EventOutbox.builder()
                .id(UUID.randomUUID())
                .eventType("CREATED")
                .entityType("stock_movement")
                .entityId(UUID.randomUUID())
                .payload(new HashMap<>())
                .topic("inventory-changes")
                .publishAttempts(3)
                .lastError(null)
                .createdAt(OffsetDateTime.now())
                .build();

        // When
        EventDeadLetter deadLetter = EventDeadLetter.fromOutboxEvent(outboxEvent);

        // Then
        assertThat(deadLetter.getLastError()).isNull();
    }
}
