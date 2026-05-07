package com.mirai.inventoryservice.dtos.responses.kuji;

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
public class KujiBoxTierResponseDTO {
    private UUID id;
    private String label;
    private String letter;
    private UUID linkedProductId;
    private String linkedProductName;
    private String linkedProductImageUrl;
    /** Current LocationInventory.quantity at the box's location for the linked product. Null when unlinked. */
    private Integer linkedInventoryAtBoxLocation;
    private Integer count;
    private BigDecimal price;
    /** True when the linked product was created inline at open-box for this tier. */
    private Boolean autoCreatedProduct;
}
