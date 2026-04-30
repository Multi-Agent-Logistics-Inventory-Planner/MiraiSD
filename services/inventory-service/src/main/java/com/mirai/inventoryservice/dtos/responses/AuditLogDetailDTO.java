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
 * DTO for audit log detail view (includes all movements)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDetailDTO {
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

    // Shipment-event fields (null for inventory-only rows)
    private UUID shipmentId;
    private String shipmentNumber;
    private List<Map<String, Object>> fieldChanges;
    private String previousStatus;
    private String newStatus;
    private String overrideReason;

    // List of all movements in this audit log
    private List<MovementDetailDTO> movements;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MovementDetailDTO {
        private Long id;
        private UUID itemId;
        private String itemSku;
        private String itemName;
        private String itemImageUrl;
        private String fromLocationCode;
        private String toLocationCode;
        private Integer previousQuantity;
        private Integer currentQuantity;
        private Integer quantityChange;
    }
}
