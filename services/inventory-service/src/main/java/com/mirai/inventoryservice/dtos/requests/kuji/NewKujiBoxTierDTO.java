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

    @NotNull
    @Min(value = 0, message = "count must be 0 or greater")
    private Integer count;

    /**
     * Additional units placed at the box location that do NOT enter slips. Total
     * units transferred from source (or born at box for auto-created) = count + heldBackQuantity.
     * Defaults to 0 when null.
     */
    @Min(value = 0, message = "heldBackQuantity must be 0 or greater")
    private Integer heldBackQuantity;

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
