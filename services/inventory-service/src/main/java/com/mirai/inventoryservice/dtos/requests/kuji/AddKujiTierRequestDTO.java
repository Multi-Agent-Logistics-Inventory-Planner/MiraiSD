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

/**
 * Request to add a single new tier to an already-open kuji box. Mirrors the
 * per-tier shape used at open-box, plus actorId for the audit trail.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddKujiTierRequestDTO {
    @NotNull
    private UUID actorId;

    @NotBlank
    @Size(max = 120)
    private String label;

    @Size(max = 50)
    private String letter;

    /** Existing product to link. Mutually exclusive with autoCreate=true. */
    private UUID linkedProductId;

    /** Source location to transfer linked product from. Required when linkedProductId is set. */
    private UUID sourceLocationId;

    /** Initial active (winnable, in-slip) count for the new tier. */
    @NotNull
    @Min(value = 0, message = "activeCount must be 0 or greater")
    private Integer activeCount;

    /** Initial inactive (held back) count for the new tier. Defaults to 0 when null. */
    @Min(value = 0, message = "inactiveCount must be 0 or greater")
    private Integer inactiveCount;

    private BigDecimal price;

    /** When true, creates a fresh child product under the kuji parent. */
    private Boolean autoCreate;

    @Size(max = 255)
    private String productName;

    @Size(max = 1024)
    private String productImageUrl;

    private BigDecimal productMsrp;
}
