package com.pm.inventoryservice.dtos.requests;

import com.pm.inventoryservice.models.enums.StockMovementReason;
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
    @NotNull
    private Integer quantityChange; // Can be positive (restock) or negative (sale)

    @NotNull
    private StockMovementReason reason; //SALE, DAMAGE, RESTOCK, ADJUSTMENT, etc

    private String notes; // Optional metadata/description

    @NotNull
    private UUID actorId; //User performing the action
}
