package com.mirai.inventoryservice.dtos.requests;

import com.mirai.inventoryservice.models.enums.StockMovementReason;
import jakarta.validation.constraints.Min;
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
public class InventoryRequestDTO {
    @NotNull(message = "Product ID is required")
    private UUID itemId;

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity must be at least 0")
    private Integer quantity;

    private UUID actorId;

    /**
     * Optional reason for creating inventory.
     * Defaults to INITIAL_STOCK if not provided.
     * Use RESTOCK when adding inventory via the adjust dialog.
     */
    private StockMovementReason reason;
}

