package com.mirai.inventoryservice.catalog.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierResponseDTO {
    private UUID id;
    private String displayName;
    private String contactEmail;
    private Boolean isActive;
    private Long shipmentCount;
    private Long productCount;
    private BigDecimal avgLeadTimeDays;
    private BigDecimal sigmaL;
    private OffsetDateTime createdAt;
}
