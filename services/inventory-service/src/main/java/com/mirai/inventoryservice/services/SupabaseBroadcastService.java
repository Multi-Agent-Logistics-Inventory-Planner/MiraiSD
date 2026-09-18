package com.mirai.inventoryservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import com.mirai.inventoryservice.shared.transaction.AfterCommitRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Service for broadcasting real-time events to connected frontend clients via Supabase.
 *
 * When the backend makes database changes, call the appropriate broadcast method
 * to notify all connected frontend clients to refresh their data.
 *
 * Dispatch is deferred to after the enclosing transaction commits (if one is active),
 * so a rolled-back mutation never emits a notification and a committing mutation's
 * notification always reflects durable state.
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
    private final SupabaseBroadcastService self;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.anon.key}")
    private String anonKey;

    @Value("${supabase.service.key:}")
    private String serviceRoleKey;

    private static final String CHANNEL_NAME = "db-changes";
    private static final String EVENT_NAME = "db_change";

    public SupabaseBroadcastService(@Lazy SupabaseBroadcastService self) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.self = self;
    }

    /**
     * Broadcast that inventory has been updated.
     * Call this after any inventory changes (transfers, adjustments, stock movements).
     *
     * @param locationType the type of location affected (e.g., "RACK", "CABINET")
     * @param itemId optional item ID for targeted updates
     */
    public void broadcastInventoryUpdated(String locationType, String itemId) {
        broadcastInventoryUpdated(null, locationType, null, itemId);
    }

    /**
     * Broadcast that inventory has been updated (without specific location/item).
     */
    public void broadcastInventoryUpdated() {
        broadcastInventoryUpdated(null, null, null, null);
    }

    /**
     * Broadcast that inventory has been updated, site-scoped, with the affected product IDs.
     * Prefer this overload from every inventory-module call site: it lets clients do a bounded,
     * site-qualified targeted refresh instead of a full-catalog invalidation. {@code productIds}
     * may be null/empty for a batch whose affected IDs are not cheaply known; a null
     * {@code siteId} is treated by clients as "possibly relevant to any site" (matching the
     * null-site precedent already established for {@code stock_movements} rows).
     *
     * @param siteId the owning site, or null if not yet migrated to carry one
     * @param locationType the type of location affected (e.g., "RACK", "CABINET")
     * @param productIds the affected product IDs, or null/empty if unknown
     * @param itemId optional single item ID, kept for legacy payload compatibility
     */
    public void broadcastInventoryUpdated(UUID siteId, String locationType, List<String> productIds, String itemId) {
        dispatchAfterCommit(() -> self.dispatchInventoryUpdated(siteId, locationType, productIds, itemId));
    }

    @Async
    void dispatchInventoryUpdated(UUID siteId, String locationType, List<String> productIds, String itemId) {
        broadcast(buildInventoryUpdatedPayload(siteId, locationType, productIds, itemId));
    }

    ObjectNode buildInventoryUpdatedPayload(UUID siteId, String locationType, List<String> productIds, String itemId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "inventory_updated");
        if (siteId != null) {
            payload.put("siteId", siteId.toString());
        }
        if (locationType != null) {
            payload.put("locationType", locationType);
        }
        if (itemId != null) {
            payload.put("itemId", itemId);
        }
        if (productIds != null && !productIds.isEmpty()) {
            payload.set("productIds", objectMapper.valueToTree(productIds));
        }
        return payload;
    }

    /**
     * Broadcast that a product has been updated.
     * Call this after product creation, updates, or deletion. Product identity is global, so
     * this event is deliberately site-less by design, not by omission.
     *
     * @param productIds optional list of affected product IDs
     */
    public void broadcastProductUpdated(List<String> productIds) {
        dispatchAfterCommit(() -> self.dispatchProductUpdated(productIds));
    }

    /**
     * Broadcast that a product has been updated (without specific IDs).
     */
    public void broadcastProductUpdated() {
        broadcastProductUpdated(null);
    }

    @Async
    void dispatchProductUpdated(List<String> productIds) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "product_updated");
        if (productIds != null && !productIds.isEmpty()) {
            payload.set("ids", objectMapper.valueToTree(productIds));
        }
        broadcast(payload);
    }

    /**
     * Broadcast that a shipment has been updated.
     * Call this after shipment creation, status changes, or receiving.
     *
     * @param shipmentIds optional list of affected shipment IDs
     */
    public void broadcastShipmentUpdated(List<String> shipmentIds) {
        dispatchAfterCommit(() -> self.dispatchShipmentUpdated(shipmentIds));
    }

    /**
     * Broadcast that a shipment has been updated (without specific IDs).
     */
    public void broadcastShipmentUpdated() {
        broadcastShipmentUpdated(null);
    }

    @Async
    void dispatchShipmentUpdated(List<String> shipmentIds) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "shipment_updated");
        if (shipmentIds != null && !shipmentIds.isEmpty()) {
            payload.set("ids", objectMapper.valueToTree(shipmentIds));
        }
        broadcast(payload);
    }

    /**
     * Broadcast that a notification has been created.
     * Call this after creating new notifications.
     */
    public void broadcastNotificationCreated() {
        dispatchAfterCommit(self::dispatchNotificationCreated);
    }

    @Async
    void dispatchNotificationCreated() {
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
    public void broadcastAuditLogCreated(String itemId) {
        broadcastAuditLogCreated(null, itemId);
    }

    /**
     * Broadcast that an audit log entry has been created (without specific item).
     */
    public void broadcastAuditLogCreated() {
        broadcastAuditLogCreated(null, null);
    }

    /**
     * Broadcast that an audit log entry has been created, site-scoped.
     *
     * @param siteId the owning site, or null if not yet migrated to carry one
     * @param itemId optional item ID for targeted updates
     */
    public void broadcastAuditLogCreated(UUID siteId, String itemId) {
        dispatchAfterCommit(() -> self.dispatchAuditLogCreated(siteId, itemId));
    }

    @Async
    void dispatchAuditLogCreated(UUID siteId, String itemId) {
        broadcast(buildAuditLogCreatedPayload(siteId, itemId));
    }

    ObjectNode buildAuditLogCreatedPayload(UUID siteId, String itemId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "audit_log_created");
        if (siteId != null) {
            payload.put("siteId", siteId.toString());
        }
        if (itemId != null) {
            payload.put("itemId", itemId);
        }
        return payload;
    }

    /**
     * Defer {@code action} to run after the enclosing transaction commits, if one is active;
     * otherwise run it immediately. Guarantees a rolled-back mutation emits nothing and a
     * committed mutation's broadcast always observes durable state.
     */
    private void dispatchAfterCommit(Runnable action) {
        AfterCommitRunner.run(action);
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
