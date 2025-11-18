package com.mirai.inventoryservice.dtos.requests;

import com.mirai.inventoryservice.models.enums.StockMovementReason;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjustStockRequestDTO {
    @NotNull(message = "Quantity change is required")
    private Integer quantityChange;

    @NotNull(message = "Reason is required")
    private StockMovementReason reason;

    private UUID actorId;
    
    private String notes;
}

