package com.mirai.inventoryservice.dtos.requests.kuji;

import jakarta.validation.constraints.Min;
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
public class PatchKujiTierRequestDTO {
    @NotNull
    private UUID actorId;

    @Size(max = 120)
    private String label;

    @Size(max = 50)
    private String letter;

    /** When set with clearLetter=true, the letter is removed. */
    private Boolean clearLetter;

    /** New linked product. Switching the linked product re-points the tier; bringing the new product's inventory in is a separate Transfer-In More step. */
    private UUID linkedProductId;

    /** When clearLinkedProduct=true, the linked product is removed. Requires destinationLocationId if there's existing linked-product inventory at the box location. */
    private Boolean clearLinkedProduct;

    /** Destination location for transferring out the old linked product. Required when linkedProductId changes and old product has inventory at the box location. */
    private UUID linkedProductDestinationLocationId;

    @Min(value = 0, message = "count must be 0 or greater")
    private Integer count;

    private BigDecimal price;

    /** When clearPrice=true, price is removed. */
    private Boolean clearPrice;
}
