package com.mirai.inventoryservice.dtos.requests.kuji;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewKujiBoxTierDTO {
    @NotBlank
    @Size(max = 120)
    private String label;

    @Size(max = 50)
    private String letter;

    /**
     * Existing product to link. When set, sourceLocationId must also be set for transfer-in.
     * Mutually exclusive with autoCreate=true.
     */
    private UUID linkedProductId;

    /** Source location to transfer linked product from. Required when linkedProductId is set. */
    private UUID sourceLocationId;

    /** Initial active (winnable, in-slip) count for this tier. */
    @NotNull
    @Min(value = 0, message = "activeCount must be 0 or greater")
    private Integer activeCount;

    /**
     * Initial inactive (held back, not on a slip) count for this tier. Total units
     * decremented from source (or created for auto-created) at open-box equals
     * activeCount + inactiveCount. Defaults to 0 when null.
     */
    @Min(value = 0, message = "inactiveCount must be 0 or greater")
    private Integer inactiveCount;

    private BigDecimal price;

    /**
     * When true, the backend creates a fresh child product under the kuji parent
     * and births its inventory at the box location. linkedProductId and
     * sourceLocationId must be null. productName is required.
     */
    private Boolean autoCreate;

    @Size(max = 255)
    private String productName;

    @Size(max = 1024)
    private String productImageUrl;

    private BigDecimal productMsrp;
}
