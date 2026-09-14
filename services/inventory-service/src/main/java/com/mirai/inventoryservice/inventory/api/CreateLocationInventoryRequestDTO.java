package com.mirai.inventoryservice.inventory.api;

import com.mirai.inventoryservice.models.enums.StockMovementReason;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/sites/{siteId}/inventory/locations/{locationId}/items}
 * (.specs/phase-6-inventory 6d, T-6d-be-2, R-9). Deliberately carries no {@code actorId} -- the
 * v1 mutation route always derives actor identity from {@code AuthorizedSiteContext}, never a
 * client-supplied value (docs/specs/authentication-and-authorization.md#4).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLocationInventoryRequestDTO {

    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    /** Optional reason for creating inventory. Defaults to INITIAL_STOCK if not provided. */
    private StockMovementReason reason;

    /** Optional intake unit ("pack" or "box") preserved for audit-log readability. */
    private String intakeUnit;

    /** Raw quantity in the user's chosen unit. */
    private Integer intakeQty;
}
