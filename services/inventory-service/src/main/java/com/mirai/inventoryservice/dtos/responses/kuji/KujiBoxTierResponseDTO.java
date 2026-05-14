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
    /** packs_per_box of the linked product, exposed so the transfer-in dialog can show a pack/box toggle. */
    private Integer linkedProductPacksPerBox;
    /** Slips currently winnable in the box. */
    private Integer activeCount;
    /** Slips held back from the pool (kuji-internal). */
    private Integer inactiveCount;
    /** Convenience: activeCount + inactiveCount. */
    private Integer totalCount;
    private BigDecimal price;
    /** True when the linked product was created inline at open-box for this tier. */
    private Boolean autoCreatedProduct;
}
