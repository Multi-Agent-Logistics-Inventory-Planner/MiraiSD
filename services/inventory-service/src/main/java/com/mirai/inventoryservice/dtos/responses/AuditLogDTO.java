package com.mirai.inventoryservice.dtos.responses;

import com.mirai.inventoryservice.models.enums.StockMovementReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for audit log list view (one row per action)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {
    private UUID id;
    private UUID actorId;
    private String actorName;
    private StockMovementReason reason;
    private String primaryFromLocationCode;
    private String primaryToLocationCode;
    private Integer itemCount;
    private Integer totalQuantityMoved;
    private String notes;
    private OffsetDateTime createdAt;

    // Summary display field (e.g., "Product A" or "3 products")
    private String productSummary;

    // Shipment-event fields (null for inventory-only rows)
    private UUID shipmentId;
    private String shipmentNumber;
    private List<Map<String, Object>> fieldChanges;
    private String previousStatus;
    private String newStatus;
    private String overrideReason;
}
