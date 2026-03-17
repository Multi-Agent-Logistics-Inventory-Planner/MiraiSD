package com.mirai.inventoryservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirai.inventoryservice.dtos.easypost.EasyPostTrackerResult;
import com.mirai.inventoryservice.dtos.easypost.EasyPostWebhookPayload;
import com.mirai.inventoryservice.models.audit.Notification;
import com.mirai.inventoryservice.models.audit.WebhookEvent;
import com.mirai.inventoryservice.models.enums.NotificationSeverity;
import com.mirai.inventoryservice.models.enums.NotificationType;
import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import com.mirai.inventoryservice.models.shipment.Shipment;
import com.mirai.inventoryservice.repositories.ShipmentRepository;
import com.mirai.inventoryservice.repositories.WebhookEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class EasyPostWebhookService {

    private final ShipmentRepository shipmentRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final NotificationService notificationService;
    private final SupabaseBroadcastService broadcastService;
    private final ObjectMapper objectMapper;

    @Value("${easypost.webhook.secret:}")
    private String webhookSecret;

    public EasyPostWebhookService(
            ShipmentRepository shipmentRepository,
            WebhookEventRepository webhookEventRepository,
            NotificationService notificationService,
            SupabaseBroadcastService broadcastService,
            ObjectMapper objectMapper) {
        this.shipmentRepository = shipmentRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.notificationService = notificationService;
        this.broadcastService = broadcastService;
        this.objectMapper = objectMapper;
    }

    /**
     * Validate EasyPost webhook signature using HMAC-SHA256
     */
    public boolean validateSignature(String payload, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("EasyPost webhook secret not configured - skipping signature validation");
            return true;
        }

        if (signature == null || signature.isBlank()) {
            log.warn("No signature provided in webhook request");
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = "hmac-sha256-hex=" + bytesToHex(hmac);
            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Signature validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Process a webhook event idempotently
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public void processWebhook(EasyPostWebhookPayload webhook) {
        String eventId = webhook.getId();

        // Idempotency check
        if (webhookEventRepository.existsByEventIdAndSource(eventId, "easypost")) {
            log.info("Webhook event {} already processed, skipping", eventId);
            return;
        }

        // Record event for idempotency
        WebhookEvent event = WebhookEvent.builder()
                .eventId(eventId)
                .eventType(webhook.getDescription())
                .source("easypost")
                .payload(objectMapper.convertValue(webhook, Map.class))
                .build();
        webhookEventRepository.save(event);

        // Route based on event type
        String eventType = webhook.getDescription();
        if ("tracker.created".equals(eventType) || "tracker.updated".equals(eventType)) {
            processTrackerEvent(webhook.getResult());
        } else {
            log.debug("Ignoring webhook event type: {}", eventType);
        }
    }

    /**
     * Process tracker status update
     */
    private void processTrackerEvent(EasyPostTrackerResult tracker) {
        if (tracker == null) {
            log.warn("Received tracker event with null result");
            return;
        }

        String trackerId = tracker.getId();
        String trackingCode = tracker.getTrackingCode();
        String status = tracker.getStatus();

        log.info("Processing tracker event: trackerId={}, trackingCode={}, status={}",
                trackerId, trackingCode, status);

        // Find shipment by tracker ID or tracking code
        Optional<Shipment> shipmentOpt = shipmentRepository.findByEasypostTrackerId(trackerId);
        if (shipmentOpt.isEmpty() && trackingCode != null) {
            shipmentOpt = shipmentRepository.findByTrackingId(trackingCode);
        }

        if (shipmentOpt.isEmpty()) {
            log.warn("No shipment found for tracker {} / tracking code {}",
                    trackerId, trackingCode);
            return;
        }

        Shipment shipment = shipmentOpt.get();
        ShipmentStatus newStatus = mapToShipmentStatus(status);
        ShipmentStatus oldStatus = shipment.getStatus();

        // Only update if status actually changed
        if (oldStatus == newStatus) {
            log.debug("Shipment {} status unchanged: {}", shipment.getId(), status);
            return;
        }

        // Don't overwrite DELIVERED status from webhook - only manual receipt should do this
        if (oldStatus == ShipmentStatus.DELIVERED) {
            log.info("Shipment {} already DELIVERED, ignoring webhook status update", shipment.getId());
            return;
        }

        // Update shipment status
        shipment.setStatus(newStatus);

        // Store tracker ID if not already set
        if (shipment.getEasypostTrackerId() == null) {
            shipment.setEasypostTrackerId(trackerId);
        }

        // Update expected delivery date if available
        if (tracker.getEstDeliveryDate() != null) {
            try {
                LocalDate estDate = ZonedDateTime.parse(tracker.getEstDeliveryDate()).toLocalDate();
                shipment.setExpectedDeliveryDate(estDate);
            } catch (Exception e) {
                log.debug("Could not parse est_delivery_date: {}", tracker.getEstDeliveryDate());
            }
        }

        shipmentRepository.save(shipment);

        // Create notifications only for DELIVERED or CANCELLED (failure)
        createTrackingNotification(shipment, oldStatus, newStatus, status);

        // Broadcast update to frontend
        broadcastService.broadcastShipmentUpdated(List.of(shipment.getId().toString()));

        log.info("Updated shipment {} status: {} -> {} (EasyPost: {})",
                shipment.getId(), oldStatus, newStatus, status);
    }

    /**
     * Map EasyPost status to ShipmentStatus
     */
    private ShipmentStatus mapToShipmentStatus(String easyPostStatus) {
        if (easyPostStatus == null) {
            return ShipmentStatus.PENDING;
        }

        return switch (easyPostStatus.toLowerCase()) {
            case "pre_transit", "unknown" -> ShipmentStatus.PENDING;
            case "in_transit", "out_for_delivery", "available_for_pickup" -> ShipmentStatus.IN_TRANSIT;
            case "delivered" -> ShipmentStatus.DELIVERED;
            case "cancelled", "return_to_sender", "failure", "error" -> ShipmentStatus.CANCELLED;
            default -> ShipmentStatus.PENDING;
        };
    }

    /**
     * Create notification for tracking status changes.
     * Only creates notifications for DELIVERED and CANCELLED (failure) statuses.
     */
    private void createTrackingNotification(
            Shipment shipment,
            ShipmentStatus oldStatus,
            ShipmentStatus newStatus,
            String easyPostStatus) {

        NotificationType type;
        NotificationSeverity severity;
        String message;

        if (newStatus == ShipmentStatus.DELIVERED) {
            type = NotificationType.SHIPMENT_COMPLETED;
            severity = NotificationSeverity.INFO;
            message = String.format("Shipment %s has been delivered", shipment.getShipmentNumber());
        } else if (newStatus == ShipmentStatus.CANCELLED) {
            type = NotificationType.SHIPMENT_DAMAGED;
            severity = NotificationSeverity.WARNING;
            message = String.format("Shipment %s delivery failed: %s",
                    shipment.getShipmentNumber(), easyPostStatus);
        } else {
            // No notification for other transitions (PENDING -> IN_TRANSIT)
            return;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("shipment_name", shipment.getShipmentNumber());
        metadata.put("shipment_id", shipment.getId().toString());
        metadata.put("tracking_id", shipment.getTrackingId());
        metadata.put("easypost_status", easyPostStatus);
        metadata.put("old_status", oldStatus.name());
        metadata.put("new_status", newStatus.name());
        metadata.put("category", "tracking");

        Notification notification = Notification.builder()
                .type(type)
                .severity(severity)
                .message(message)
                .metadata(metadata)
                .via(List.of("slack", "app"))
                .build();

        notificationService.createNotification(notification);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
