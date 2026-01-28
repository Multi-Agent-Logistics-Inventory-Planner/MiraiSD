package com.mirai.inventoryservice.dtos.requests;

import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.validation.ValidTransferDestination;
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
@ValidTransferDestination
public class TransferInventoryRequestDTO {
    @NotNull(message = "Source location type is required")
    private LocationType sourceLocationType;

    @NotNull(message = "Source inventory ID is required")
    private UUID sourceInventoryId;

    @NotNull(message = "Destination location type is required")
    private LocationType destinationLocationType;

    // Destination inventory ID - required if inventory already exists at destination
    private UUID destinationInventoryId;

    // Destination location ID - required if creating new inventory at destination
    private UUID destinationLocationId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    private UUID actorId;

    private String notes;
}

