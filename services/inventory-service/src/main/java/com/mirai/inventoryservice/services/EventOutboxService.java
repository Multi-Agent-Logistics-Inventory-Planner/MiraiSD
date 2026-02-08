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
        payload.put("product_id", movement.getItem().getId().toString());
        payload.put("sku", movement.getItem().getSku());
        payload.put("item_id", movement.getItem().getId().toString());
        payload.put("quantity_change",  movement.getQuantityChange());
        payload.put("reason", movement.getReason().name().toLowerCase()); // "sale", "restock", etc
        payload.put("at", movement.getAt().toString()); // YYYY-MM-DDTHH:MM:SSZ

        // Location codes (renamed for consistency)
        if (movement.getFromLocationId() != null) {
            String fromCode = stockMovementService.resolveLocationCode(
                    movement.getFromLocationId(),
                    movement.getLocationType()
            );
            payload.put("from_location_code", fromCode); // "B1", "S2", "D1", "K1", "C1", "R3", etc
        } else {
            payload.put("from_location_code", null);
        }

        if (movement.getToLocationId() != null) {
            String toCode = stockMovementService.resolveLocationCode(
                    movement.getToLocationId(),
                    movement.getLocationType()
            );
            payload.put("to_location_code", toCode);
        } else {
            payload.put("to_location_code", null);
        }

        // Location-level quantities for crossing logic (from StockMovement)
        payload.put("previous_location_qty", movement.getPreviousQuantity());
        payload.put("current_location_qty", movement.getCurrentQuantity());

        // Total-level quantities for crossing logic (computed in same transaction)
        UUID productId = movement.getItem().getId();
        int currentTotal = stockMovementService.calculateTotalInventory(productId);
        int previousTotal = currentTotal - movement.getQuantityChange();
        payload.put("previous_total_qty", previousTotal);
        payload.put("current_total_qty", currentTotal);

        // Product config for threshold comparison
        payload.put("reorder_point", movement.getItem().getReorderPoint());

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
     *
     * NOTE: This method is NOT transactional to avoid holding DB connections during Kafka sends.
     * Each event fetch and update uses its own short transaction via helper methods.
     */
    @Scheduled(fixedDelay = 10000)
    public void publishPendingEvents() {
        // Fetch pending events in a short transaction, then release connection
        List<EventOutbox> pendingEvents = fetchPendingEvents();

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Publishing {} pending events to Kafka", pendingEvents.size());

        for (EventOutbox event : pendingEvents) {
            try {
                // Build message matching Python's EventEnvelope schema
                Map<String, Object> message = new HashMap<>();
                message.put("event_id", event.getId().toString());
                message.put("topic", event.getTopic());
                message.put("event_type", event.getEventType());
                message.put("entity_type", event.getEntityType());
                message.put("entity_id", event.getEntityId().toString());
                message.put("payload", event.getPayload());
                message.put("created_at", event.getCreatedAt().toString());

                // Key for Kafka partitioning: item_id
                String key = event.getPayload().get("item_id").toString();

                // Send to Kafka (outside of transaction - no DB connection held)
                kafkaProducer.sendEvent(event.getTopic(), key, message);

                // Mark as published in a short transaction
                markEventAsPublished(event.getId());

                log.info("Published event {} to topic {}", event.getId(), event.getTopic());
            } catch (Exception e) {
                log.error("Failed to publish event {}: {}", event.getId(), e.getMessage());
                // Record failure in a short transaction
                recordEventFailure(event.getId(), e.getMessage());
            }
        }
    }

    /**
     * Fetch pending events in its own short transaction.
     * Connection is released immediately after query completes.
     */
    @Transactional(readOnly = true)
    public List<EventOutbox> fetchPendingEvents() {
        return eventOutboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
    }

    /**
     * Mark an event as published in its own short transaction.
     */
    @Transactional
    public void markEventAsPublished(UUID eventId) {
        eventOutboxRepository.findById(eventId).ifPresent(event -> {
            event.setPublishedAt(OffsetDateTime.now());
            eventOutboxRepository.save(event);
        });
    }

    /**
     * Record event publish failure in its own short transaction.
     */
    @Transactional
    public void recordEventFailure(UUID eventId, String errorMessage) {
        eventOutboxRepository.findById(eventId).ifPresent(event -> {
            event.setPublishAttempts(event.getPublishAttempts() + 1);
            event.setLastError(errorMessage);
            eventOutboxRepository.save(event);
        });
    }
}

