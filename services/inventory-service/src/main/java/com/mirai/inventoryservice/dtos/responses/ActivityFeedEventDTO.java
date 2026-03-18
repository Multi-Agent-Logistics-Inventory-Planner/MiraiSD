package com.mirai.inventoryservice.dtos.responses;

import com.mirai.inventoryservice.models.enums.NotificationSeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityFeedEventDTO {
    private String id;           // Prefixed: "audit-{id}", "shipment-{uuid}", "notification-{uuid}"
    private String type;         // "alert", "restock", "sale", "shipment", "adjustment", "transfer"
    private String title;
    private String description;
    private OffsetDateTime timestamp;
    private NotificationSeverity severity;
    private Map<String, Object> metadata;
}
