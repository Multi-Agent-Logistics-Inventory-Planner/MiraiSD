package com.mirai.inventoryservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Service for broadcasting real-time events to connected frontend clients via Supabase.
 *
 * When the backend makes database changes, call the appropriate broadcast method
 * to notify all connected frontend clients to refresh their data.
 *
 * Usage example:
 * <pre>
 * // After updating inventory
 * broadcastService.broadcastInventoryUpdated("RACK", itemId);
 *
 * // After creating a shipment
 * broadcastService.broadcastShipmentUpdated();
 * </pre>
 */
@Service
public class SupabaseBroadcastService {
    private static final Logger log = LoggerFactory.getLogger(SupabaseBroadcastService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.anon.key}")
    private String anonKey;

    @Value("${supabase.service.key:}")
    private String serviceRoleKey;

    private static final String CHANNEL_NAME = "db-changes";
    private static final String EVENT_NAME = "db_change";

    public SupabaseBroadcastService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Broadcast that inventory has been updated.
     * Call this after any inventory changes (transfers, adjustments, stock movements).
     *
     * @param locationType the type of location affected (e.g., "RACK", "CABINET")
     * @param itemId optional item ID for targeted updates
     */
    @Async
    public void broadcastInventoryUpdated(String locationType, String itemId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "inventory_updated");
        if (locationType != null) {
            payload.put("locationType", locationType);
        }
        if (itemId != null) {
            payload.put("itemId", itemId);
        }
        broadcast(payload);
    }

    /**
     * Broadcast that inventory has been updated (without specific location/item).
     */
    @Async
    public void broadcastInventoryUpdated() {
        broadcastInventoryUpdated(null, null);
    }

    /**
     * Broadcast that a product has been updated.
     * Call this after product creation, updates, or deletion.
     *
     * @param productIds optional list of affected product IDs
     */
    @Async
    public void broadcastProductUpdated(List<String> productIds) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "product_updated");
        if (productIds != null && !productIds.isEmpty()) {
            payload.set("ids", objectMapper.valueToTree(productIds));
        }
        broadcast(payload);
    }

    /**
     * Broadcast that a product has been updated (without specific IDs).
     */
    @Async
    public void broadcastProductUpdated() {
        broadcastProductUpdated(null);
    }

    /**
     * Broadcast that a shipment has been updated.
     * Call this after shipment creation, status changes, or receiving.
     *
     * @param shipmentIds optional list of affected shipment IDs
     */
    @Async
    public void broadcastShipmentUpdated(List<String> shipmentIds) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "shipment_updated");
        if (shipmentIds != null && !shipmentIds.isEmpty()) {
            payload.set("ids", objectMapper.valueToTree(shipmentIds));
        }
        broadcast(payload);
    }

    /**
     * Broadcast that a shipment has been updated (without specific IDs).
     */
    @Async
    public void broadcastShipmentUpdated() {
        broadcastShipmentUpdated(null);
    }

    /**
     * Broadcast that a notification has been created.
     * Call this after creating new notifications.
     */
    @Async
    public void broadcastNotificationCreated() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "notification_created");
        broadcast(payload);
    }

    /**
     * Broadcast that an audit log entry has been created.
     * Call this after stock movements are recorded.
     *
     * @param itemId optional item ID for targeted updates
     */
    @Async
    public void broadcastAuditLogCreated(String itemId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "audit_log_created");
        if (itemId != null) {
            payload.put("itemId", itemId);
        }
        broadcast(payload);
    }

    /**
     * Broadcast that an audit log entry has been created (without specific item).
     */
    @Async
    public void broadcastAuditLogCreated() {
        broadcastAuditLogCreated(null);
    }

    /**
     * Send a broadcast message to the Supabase realtime channel.
     */
    private void broadcast(ObjectNode payload) {
        String baseUrl = supabaseUrl != null ? supabaseUrl.replaceAll("/+$", "") : null;
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("Supabase URL is not configured; skipping broadcast {}", payload.get("type"));
            return;
        }

        String keyToUse = (serviceRoleKey != null && !serviceRoleKey.isBlank()) ? serviceRoleKey : anonKey;
        if (keyToUse == null || keyToUse.isBlank()) {
            log.warn("Supabase key is not configured; skipping broadcast {}", payload.get("type"));
            return;
        }

        String url = baseUrl + "/realtime/v1/api/broadcast";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Broadcast API is server-side; prefer service role key when available.
        headers.set("apikey", keyToUse);
        headers.setBearerAuth(keyToUse);

        // Supabase Realtime REST API expects:
        // {
        //   "messages": [
        //     { "topic": "channel-name", "event": "event-name", "payload": { ... } }
        //   ]
        // }
        ObjectNode message = objectMapper.createObjectNode();
        message.put("topic", CHANNEL_NAME);
        message.put("event", EVENT_NAME);
        message.set("payload", payload);

        com.fasterxml.jackson.databind.node.ArrayNode messages = objectMapper.createArrayNode();
        messages.add(message);

        ObjectNode body = objectMapper.createObjectNode();
        body.set("messages", messages);

        HttpEntity<String> request;
        try {
            request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        } catch (Exception e) {
            log.error("Failed to serialize broadcast payload: {}", e.getMessage());
            return;
        }

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Broadcast sent: {}", payload.get("type"));
            } else {
                log.warn("Broadcast failed with status {}: {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.warn("Failed to send broadcast: {}", e.getMessage());
            // Don't throw - broadcasting is best-effort and shouldn't break the main operation
        }
    }
}
