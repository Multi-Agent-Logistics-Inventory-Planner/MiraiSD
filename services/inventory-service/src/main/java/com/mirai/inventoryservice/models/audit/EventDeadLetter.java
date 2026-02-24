package com.mirai.inventoryservice.models.audit;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Stores events that failed to publish to Kafka after max retry attempts.
 * Mirrors EventOutbox structure for debugging and potential replay.
 */
@Entity
@Table(name = "event_dead_letter", indexes = {
    @Index(name = "idx_event_dead_letter_topic", columnList = "topic"),
    @Index(name = "idx_event_dead_letter_moved_at", columnList = "moved_to_dead_letter_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDeadLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(name = "event_type", nullable = false)
    private String eventType;

    @NotBlank
    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload;

    @NotBlank
    @Column(nullable = false)
    private String topic;

    @NotNull
    @Column(name = "original_attempts", nullable = false)
    private Integer originalAttempts;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @NotNull
    @Column(name = "original_created_at", nullable = false)
    private OffsetDateTime originalCreatedAt;

    @NotNull
    @Column(name = "moved_to_dead_letter_at", nullable = false)
    private OffsetDateTime movedToDeadLetterAt;

    /**
     * Factory method to create EventDeadLetter from a failed EventOutbox event.
     */
    public static EventDeadLetter fromOutboxEvent(EventOutbox outboxEvent) {
        return EventDeadLetter.builder()
                .eventType(outboxEvent.getEventType())
                .entityType(outboxEvent.getEntityType())
                .entityId(outboxEvent.getEntityId())
                .payload(outboxEvent.getPayload())
                .topic(outboxEvent.getTopic())
                .originalAttempts(outboxEvent.getPublishAttempts())
                .lastError(outboxEvent.getLastError())
                .originalCreatedAt(outboxEvent.getCreatedAt())
                .movedToDeadLetterAt(OffsetDateTime.now())
                .build();
    }
}
