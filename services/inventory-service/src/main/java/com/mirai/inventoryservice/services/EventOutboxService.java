package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.kafka.KafkaProducer;
import com.mirai.inventoryservice.models.audit.EventOutbox;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.repositories.EventOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class EventOutboxService {
    private final EventOutboxRepository eventOutboxRepository;
    private final KafkaProducer kafkaProducer;
    private final StockMovementService stockMovementService;

    @Value("${kafka.topic.inventory-changes:inventory-changes}")
    private String inventoryChangesTopic;

    public EventOutboxService(
            EventOutboxRepository eventOutboxRepository,
            KafkaProducer kafkaProducer,
            @Lazy StockMovementService stockMovementService)
    {
        this.eventOutboxRepository = eventOutboxRepository;
        this.kafkaProducer = kafkaProducer;
        this.stockMovementService = stockMovementService;
    }

    /**
     * Create outbox event for stock movement
     * Called by StockMovementService after saving a movement
     */
    @Transactional
    public void createStockMovementEvent(StockMovement movement) {
        // Build payload with resolved location codes for ML analytics
        Map<String, Object> payload = new HashMap<>();
        payload.put("item_id", movement.getItemId().toString());
        payload.put("quantity_change",  movement.getQuantityChange());
        payload.put("reason", movement.getReason().name().toLowerCase()); // "sale", "restock", etc
        payload.put("at", movement.getAt().toString()); // YYYY-MM-DDTHH:MM:SSZ

        if (movement.getFromLocationId() != null) {
            String fromCode = stockMovementService.resolveLocationCode(
                    movement.getFromLocationId(),
                    movement.getLocationType()
            );
            payload.put("from_box_id", fromCode); // "B1", "S2", "D1", "M1", "C1", "R3", etc
        } else {
            payload.put("from_box_id", null);
        }

        if (movement.getToLocationId() != null) {
            String toCode = stockMovementService.resolveLocationCode(
                    movement.getToLocationId(),
                    movement.getLocationType()
            );
            payload.put("to_box_id", toCode);
        } else {
            payload.put("to_box_id", null);
        }

        if (movement.getActorId() != null) {
            payload.put("actor_id", movement.getActorId().toString());
        } else {
            payload.put("actor_id", null);
        }
        
        // Add stock movement ID to payload for reference
        payload.put("stock_movement_id", movement.getId().toString());

        EventOutbox event = EventOutbox.builder()
                .topic(inventoryChangesTopic)
                .eventType("CREATED") // Expects "CREATED" or "UPDATED"
                .entityType("stock_movement")
                .entityId(UUID.randomUUID()) // Generate UUID for the outbox event
                .payload(payload)
                .build();

        eventOutboxRepository.save(event);
        log.info("Created outbox event for stock movement: {}", movement.getId());
    }

    /**
     * Scheduled job to publish unpublished events to Kafka
     * Runs every 10 seconds
     */
    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void publishPendingEvents() {
        List<EventOutbox> pendingEvents = eventOutboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Publishing {} pending events to Kafka",  pendingEvents.size());

        for (EventOutbox event : pendingEvents) {
            try {
                // Build message matching Python's EventEnvelope schema
                Map<String, Object> message = new HashMap<>();
                message.put("event_id", event.getId().toString());
                message.put("topic", event.getTopic());
                message.put("event_type", event.getEventType());
                message.put("entity_type", event.getEntityType());
                message.put("entity_id", event.getEntityId().toString());
                message.put("payload", event.getPayload()); // Already structured correctly
                message.put("created_at", event.getCreatedAt().toString());

                // Key for Kafka partitioning: item_id + location_type
                String key = event.getPayload().get("item_id").toString();

                // Send to Kafka
                kafkaProducer.sendEvent(event.getTopic(), key, message);

                // Mark as published
                event.setPublishedAt(OffsetDateTime.now());
                eventOutboxRepository.save(event);

                log.info("Published event {} to topic {}", event.getId(), event.getTopic());
            } catch (Exception e) {
                log.error("Failed to publish event {}: {}", event.getId(), e.getMessage());
                event.setPublishAttempts(event.getPublishAttempts() + 1);
                event.setLastError(e.getMessage());
                eventOutboxRepository.save(event);
            }
        }
    }
}

