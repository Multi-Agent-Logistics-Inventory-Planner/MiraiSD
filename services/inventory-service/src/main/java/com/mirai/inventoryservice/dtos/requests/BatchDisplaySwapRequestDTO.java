package com.mirai.inventoryservice.dtos.requests;

import com.mirai.inventoryservice.models.enums.LocationType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for batch display swap operations.
 * Supports two modes:
 * 1. Swap with products - remove displays and add new products
 * 2. Swap with another machine - trade displays between two machines
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchDisplaySwapRequestDTO {
    @NotNull(message = "Location type is required")
    private LocationType locationType;

    @NotNull(message = "Machine ID is required")
    private UUID machineId;

    /**
     * Display IDs to remove from the current machine
     */
    private List<UUID> displayIdsToRemove;

    /**
     * Product IDs to add to the current machine
     */
    private List<UUID> productIdsToAdd;

    /**
     * For machine-to-machine swap: the target machine's location type
     */
    private LocationType targetLocationType;

    /**
     * For machine-to-machine swap: the target machine's ID
     */
    private UUID targetMachineId;

    /**
     * For machine-to-machine swap: display IDs to move FROM target machine TO current machine
     */
    private List<UUID> displayIdsFromTarget;

    /**
     * For machine-to-machine swap: display IDs to move FROM current machine TO target machine
     */
    private List<UUID> displayIdsToTarget;

    private UUID actorId;
}
