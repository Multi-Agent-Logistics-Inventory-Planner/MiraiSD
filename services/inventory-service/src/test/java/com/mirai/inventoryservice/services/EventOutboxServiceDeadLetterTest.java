package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.kafka.KafkaProducer;
import com.mirai.inventoryservice.models.audit.EventDeadLetter;
import com.mirai.inventoryservice.models.audit.EventOutbox;
import com.mirai.inventoryservice.repositories.EventDeadLetterRepository;
import com.mirai.inventoryservice.repositories.EventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventOutboxService - Dead Letter Logic")
class EventOutboxServiceDeadLetterTest {

    @Mock
    private EventOutboxRepository eventOutboxRepository;

    @Mock
    private EventDeadLetterRepository eventDeadLetterRepository;

    @Mock
    private KafkaProducer kafkaProducer;

    @Mock
    private StockMovementService stockMovementService;

    @Captor
    private ArgumentCaptor<EventDeadLetter> deadLetterCaptor;

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

    @Nested
    @DisplayName("recordEventFailure")
    class RecordEventFailureTests {

        @Test
        @DisplayName("should increment attempts when under max retries")
        void shouldIncrementAttemptsWhenUnderMaxRetries() {
            // Given
            UUID eventId = UUID.randomUUID();
            EventOutbox event = createOutboxEvent(eventId, 1);
            when(eventOutboxRepository.findById(eventId)).thenReturn(Optional.of(event));

            // When
            eventOutboxService.recordEventFailure(eventId, "Connection error");

            // Then
            assertThat(event.getPublishAttempts()).isEqualTo(2);
            assertThat(event.getLastError()).isEqualTo("Connection error");
            verify(eventOutboxRepository).save(event);
            verify(eventDeadLetterRepository, never()).save(any());
            verify(eventOutboxRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should move to dead letter after max retries (3)")
        void shouldMoveToDeadLetterAfterMaxRetries() {
            // Given
            UUID eventId = UUID.randomUUID();
            EventOutbox event = createOutboxEvent(eventId, 2); // After increment will be 3
            when(eventOutboxRepository.findById(eventId)).thenReturn(Optional.of(event));

            // When
            eventOutboxService.recordEventFailure(eventId, "Final failure");

            // Then
            assertThat(event.getPublishAttempts()).isEqualTo(3);
            verify(eventDeadLetterRepository).save(deadLetterCaptor.capture());
            verify(eventOutboxRepository).delete(event);

            EventDeadLetter deadLetter = deadLetterCaptor.getValue();
            assertThat(deadLetter.getEventType()).isEqualTo(event.getEventType());
            assertThat(deadLetter.getEntityType()).isEqualTo(event.getEntityType());
            assertThat(deadLetter.getTopic()).isEqualTo(event.getTopic());
            assertThat(deadLetter.getOriginalAttempts()).isEqualTo(3);
            assertThat(deadLetter.getLastError()).isEqualTo("Final failure");
        }

        @Test
        @DisplayName("should handle event not found gracefully")
        void shouldHandleEventNotFound() {
            // Given
            UUID eventId = UUID.randomUUID();
            when(eventOutboxRepository.findById(eventId)).thenReturn(Optional.empty());

            // When
            eventOutboxService.recordEventFailure(eventId, "Some error");

            // Then
            verify(eventOutboxRepository, never()).save(any());
            verify(eventDeadLetterRepository, never()).save(any());
        }

        @Test
        @DisplayName("should move to dead letter on exactly 3 attempts")
        void shouldMoveToDeadLetterOnExactlyThreeAttempts() {
            // Given
            UUID eventId = UUID.randomUUID();
            EventOutbox event = createOutboxEvent(eventId, 2); // Will become 3 after this failure
            when(eventOutboxRepository.findById(eventId)).thenReturn(Optional.of(event));

            // When
            eventOutboxService.recordEventFailure(eventId, "Kafka unavailable");

            // Then
            verify(eventDeadLetterRepository).save(any(EventDeadLetter.class));
            verify(eventOutboxRepository).delete(event);
        }
    }

    @Nested
    @DisplayName("fetchPendingEvents")
    class FetchPendingEventsTests {

        @Test
        @DisplayName("should only fetch events under max retry limit")
        void shouldOnlyFetchEventsUnderMaxRetryLimit() {
            // When
            eventOutboxService.fetchPendingEvents();

            // Then
            verify(eventOutboxRepository).findByPublishedAtIsNullAndPublishAttemptsLessThanOrderByCreatedAtAsc(3);
        }
    }

    private EventOutbox createOutboxEvent(UUID id, int publishAttempts) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("product_id", "test-product");
        payload.put("item_id", UUID.randomUUID().toString());

        return EventOutbox.builder()
                .id(id)
                .eventType("CREATED")
                .entityType("stock_movement")
                .entityId(UUID.randomUUID())
                .payload(payload)
                .topic("inventory-changes")
                .publishAttempts(publishAttempts)
                .createdAt(OffsetDateTime.now().minusMinutes(5))
                .build();
    }
}
