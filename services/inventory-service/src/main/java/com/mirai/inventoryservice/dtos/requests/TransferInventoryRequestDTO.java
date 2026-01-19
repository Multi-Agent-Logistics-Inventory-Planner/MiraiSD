package com.mirai.inventoryservice.dtos.requests;

import com.mirai.inventoryservice.models.enums.LocationType;
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
public class TransferInventoryRequestDTO {
    @NotNull(message = "Source location type is required")
    private LocationType sourceLocationType;

    @NotNull(message = "Source inventory ID is required")
    private UUID sourceInventoryId;

    @NotNull(message = "Destination location type is required")
    private LocationType destinationLocationType;

    @NotNull(message = "Destination inventory ID is required")
    private UUID destinationInventoryId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    private UUID actorId;
    
    private String notes;
}

