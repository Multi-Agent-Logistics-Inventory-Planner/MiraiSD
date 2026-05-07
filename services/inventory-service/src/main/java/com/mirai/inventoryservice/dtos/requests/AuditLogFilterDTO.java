package com.mirai.inventoryservice.dtos.requests;

import com.mirai.inventoryservice.models.enums.StockMovementReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogFilterDTO {
    private String search;
    private UUID actorId;
    private StockMovementReason reason;
    /** Optional list filter; when non-empty, takes precedence over the singular `reason`. */
    private List<StockMovementReason> reasons;
    private LocalDate fromDate;
    private LocalDate toDate;
    private UUID productId;
    private UUID locationId;
}
