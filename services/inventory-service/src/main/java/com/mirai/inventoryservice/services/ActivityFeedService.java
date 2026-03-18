package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.responses.ActivityFeedEventDTO;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.audit.Notification;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.enums.NotificationSeverity;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.shipment.Shipment;
import com.mirai.inventoryservice.repositories.AuditLogRepository;
import com.mirai.inventoryservice.repositories.NotificationRepository;
import com.mirai.inventoryservice.repositories.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityFeedService {

    private final AuditLogRepository auditLogRepository;
    private final ShipmentRepository shipmentRepository;
    private final NotificationRepository notificationRepository;

    private static final Set<String> STOCK_MOVEMENT_TYPES = Set.of("restock", "sale", "adjustment", "transfer");

    @Transactional(readOnly = true)
    public List<ActivityFeedEventDTO> getActivityFeed(int limit, List<String> types, boolean includeResolved) {
        List<ActivityFeedEventDTO> events = new ArrayList<>();
        Set<String> typeSet = types != null && !types.isEmpty() ? new HashSet<>(types) : null;

        // Fetch audit logs for stock movements (restock, sale, adjustment, transfer)
        if (typeSet == null || typeSet.stream().anyMatch(STOCK_MOVEMENT_TYPES::contains)) {
            OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minusDays(7);
            var auditLogs = auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                    sevenDaysAgo, OffsetDateTime.now(), PageRequest.of(0, 100));

            for (AuditLog auditLog : auditLogs.getContent()) {
                for (StockMovement movement : auditLog.getMovements()) {
                    String eventType = mapReasonToEventType(movement.getReason());
                    if (typeSet != null && !typeSet.contains(eventType)) continue;

                    events.add(ActivityFeedEventDTO.builder()
                            .id("audit-" + movement.getId())
                            .type(eventType)
                            .title(formatMovementTitle(movement))
                            .description(auditLog.getActorName() != null ? "by " + auditLog.getActorName() : null)
                            .timestamp(auditLog.getCreatedAt())
                            .severity(null)
                            .metadata(Map.of(
                                    "itemId", movement.getItem().getId().toString(),
                                    "itemName", movement.getItem().getName(),
                                    "itemSku", movement.getItem().getSku() != null ? movement.getItem().getSku() : "",
                                    "quantity", movement.getQuantityChange()
                            ))
                            .build());
                }
            }
        }

        // Fetch shipments
        if (typeSet == null || typeSet.contains("shipment")) {
            var shipments = shipmentRepository.findAllWithAssociations(PageRequest.of(0, 50));

            for (Shipment shipment : shipments.getContent()) {
                int itemCount = shipment.getItems().size();
                int totalQty = shipment.getItems().stream()
                        .mapToInt(item -> item.getOrderedQuantity() != null ? item.getOrderedQuantity() : 0)
                        .sum();

                events.add(ActivityFeedEventDTO.builder()
                        .id("shipment-" + shipment.getId())
                        .type("shipment")
                        .title(String.format("Shipment %s: %d items (%d units)",
                                shipment.getShipmentNumber(), itemCount, totalQty))
                        .description(shipment.getSupplierName() != null ? "from " + shipment.getSupplierName() : null)
                        .timestamp(shipment.getUpdatedAt())
                        .severity(null)
                        .metadata(Map.of(
                                "shipmentId", shipment.getId().toString(),
                                "shipmentNumber", shipment.getShipmentNumber()
                        ))
                        .build());
            }
        }

        // Fetch notifications (alerts)
        if (typeSet == null || typeSet.contains("alert")) {
            List<Notification> notifications = notificationRepository.findAll();

            for (Notification notification : notifications) {
                boolean isResolved = notification.getResolvedAt() != null;
                if (isResolved && !includeResolved) continue;

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("notificationId", notification.getId().toString());
                metadata.put("resolved", isResolved);
                if (notification.getItemId() != null) {
                    metadata.put("itemId", notification.getItemId().toString());
                }

                events.add(ActivityFeedEventDTO.builder()
                        .id("notification-" + notification.getId())
                        .type("alert")
                        .title(notification.getMessage())
                        .description(isResolved ? "Resolved" : null)
                        .timestamp(notification.getCreatedAt())
                        .severity(notification.getSeverity())
                        .metadata(metadata)
                        .build());
            }
        }

        // Sort by timestamp descending and limit
        return events.stream()
                .sorted(Comparator.comparing(ActivityFeedEventDTO::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private String mapReasonToEventType(StockMovementReason reason) {
        return switch (reason) {
            case SALE -> "sale";
            case RESTOCK, INITIAL_STOCK, SHIPMENT_RECEIPT, RETURN -> "restock";
            case TRANSFER, DISPLAY_SET, DISPLAY_REMOVED, DISPLAY_SWAP -> "transfer";
            case ADJUSTMENT, DAMAGE, REMOVED, SHIPMENT_RECEIPT_REVERSED -> "adjustment";
        };
    }

    private String formatMovementTitle(StockMovement movement) {
        int absQty = Math.abs(movement.getQuantityChange());
        String qtyStr = absQty == 1 ? "1 unit" : absQty + " units";
        String itemName = movement.getItem().getName();

        return switch (movement.getReason()) {
            case SALE -> "Sold " + qtyStr + " of " + itemName;
            case RESTOCK -> "Restocked " + qtyStr + " of " + itemName;
            case SHIPMENT_RECEIPT -> "Received shipment: " + qtyStr + " of " + itemName;
            case SHIPMENT_RECEIPT_REVERSED -> "Reversed shipment: " + qtyStr + " of " + itemName;
            case INITIAL_STOCK -> "Added initial stock: " + qtyStr + " of " + itemName;
            case RETURN -> "Returned " + qtyStr + " of " + itemName;
            case TRANSFER -> "Transferred " + qtyStr + " of " + itemName;
            case ADJUSTMENT -> (movement.getQuantityChange() >= 0 ? "Adjusted +" : "Adjusted -") + qtyStr + " of " + itemName;
            case DAMAGE -> "Damaged " + qtyStr + " of " + itemName;
            case REMOVED -> "Removed " + qtyStr + " of " + itemName;
            case DISPLAY_SET -> "Set display: " + qtyStr + " of " + itemName;
            case DISPLAY_REMOVED -> "Removed from display: " + qtyStr + " of " + itemName;
            case DISPLAY_SWAP -> "Swapped display: " + qtyStr + " of " + itemName;
        };
    }
}
