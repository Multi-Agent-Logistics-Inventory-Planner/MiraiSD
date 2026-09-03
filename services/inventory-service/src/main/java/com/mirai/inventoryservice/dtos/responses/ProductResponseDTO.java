package com.mirai.inventoryservice.dtos.responses;

import com.mirai.inventoryservice.models.enums.KujiType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponseDTO {
    private UUID id;
    private String sku;
    private CategoryResponseDTO category;

    // Parent-child relationship fields
    private UUID parentId;
    private String parentName;
    private String letter;
    private Integer templateQuantity;
    private KujiType kujiType;
    private String kujiSlackWebhookUrl;
    private Integer packsPerBox;
    private String parentSku;
    private List<ProductSummaryDTO> children;
    private Integer totalChildStock;
    private Boolean hasChildren;

    private String name;
    private String description;
    private Integer reorderPoint;
    private Integer targetStockLevel;
    private Integer leadTimeDays;
    private UUID preferredSupplierId;
    private String preferredSupplierName;
    private Boolean preferredSupplierAuto;
    // Last delivered supplier for "Use Auto" feature (only populated for single product fetch)
    private UUID lastDeliveredSupplierId;
    private String lastDeliveredSupplierName;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Omitted when the caller lacks the costs:view permission - redacted server-side.")
    private BigDecimal unitCost;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Omitted when the caller lacks the msrp:view permission - redacted server-side.")
    private BigDecimal msrp;
    private Boolean isActive;
    private Boolean forecastingEnabled;
    private Integer quantity;
    private String imageUrl;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
