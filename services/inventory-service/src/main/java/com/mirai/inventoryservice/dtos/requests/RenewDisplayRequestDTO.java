package com.mirai.inventoryservice.dtos.requests;

import com.mirai.inventoryservice.models.enums.LocationType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for renewing display records.
 * Renewing ends the current display record and creates a new one
 * with fresh startedAt timestamp for the same product.
 * Used when restocking the same product and wanting to reset tracking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenewDisplayRequestDTO {
    @NotNull(message = "Location type is required")
    private LocationType locationType;

    @NotNull(message = "Machine ID is required")
    private UUID machineId;

    @NotEmpty(message = "Display IDs are required")
    private List<UUID> displayIds;

    private UUID actorId;
}
