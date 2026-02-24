package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.audit.EventDeadLetter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventDeadLetterRepository")
class EventDeadLetterRepositoryTest {

    @Mock
    private EventDeadLetterRepository eventDeadLetterRepository;

    @Test
    @DisplayName("should save and retrieve EventDeadLetter")
    void shouldSaveAndRetrieve() {
        // Given
        UUID id = UUID.randomUUID();
        EventDeadLetter deadLetter = createDeadLetter("inventory-changes");

        EventDeadLetter savedDeadLetter = EventDeadLetter.builder()
                .id(id)
                .eventType(deadLetter.getEventType())
                .entityType(deadLetter.getEntityType())
                .entityId(deadLetter.getEntityId())
                .payload(deadLetter.getPayload())
                .topic(deadLetter.getTopic())
                .originalAttempts(deadLetter.getOriginalAttempts())
                .lastError(deadLetter.getLastError())
                .originalCreatedAt(deadLetter.getOriginalCreatedAt())
                .movedToDeadLetterAt(deadLetter.getMovedToDeadLetterAt())
                .build();

        when(eventDeadLetterRepository.save(any(EventDeadLetter.class))).thenReturn(savedDeadLetter);
        when(eventDeadLetterRepository.findById(id)).thenReturn(Optional.of(savedDeadLetter));

        // When
        EventDeadLetter saved = eventDeadLetterRepository.save(deadLetter);

        // Then
        assertThat(saved.getId()).isNotNull();

        EventDeadLetter retrieved = eventDeadLetterRepository.findById(id).orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getEventType()).isEqualTo("CREATED");
        assertThat(retrieved.getEntityType()).isEqualTo("stock_movement");
        assertThat(retrieved.getTopic()).isEqualTo("inventory-changes");
        assertThat(retrieved.getOriginalAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("should find by topic")
    void shouldFindByTopic() {
        // Given
        String targetTopic = "inventory-changes";
        EventDeadLetter deadLetter1 = createDeadLetter(targetTopic);
        EventDeadLetter deadLetter2 = createDeadLetter(targetTopic);

        when(eventDeadLetterRepository.findByTopic(targetTopic))
                .thenReturn(List.of(deadLetter1, deadLetter2));

        // When
        List<EventDeadLetter> found = eventDeadLetterRepository.findByTopic(targetTopic);

        // Then
        assertThat(found).hasSize(2);
        assertThat(found).allMatch(dl -> dl.getTopic().equals(targetTopic));
    }

    private EventDeadLetter createDeadLetter(String topic) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("test", "data");

        return EventDeadLetter.builder()
                .eventType("CREATED")
                .entityType("stock_movement")
                .entityId(UUID.randomUUID())
                .payload(payload)
                .topic(topic)
                .originalAttempts(3)
                .lastError("Test error")
                .originalCreatedAt(OffsetDateTime.now().minusMinutes(30))
                .movedToDeadLetterAt(OffsetDateTime.now())
                .build();
    }
}
