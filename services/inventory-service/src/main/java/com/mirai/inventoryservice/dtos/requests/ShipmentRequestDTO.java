package com.mirai.inventoryservice.dtos.requests;

import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentRequestDTO {
    private String shipmentNumber;

    private String supplierName;

    @NotNull(message = "Status is required")
    private ShipmentStatus status;

    @NotNull(message = "Order date is required")
    private LocalDate orderDate;

    private LocalDate expectedDeliveryDate;

    private LocalDate actualDeliveryDate;

    private BigDecimal totalCost;

    private String notes;

    private UUID createdBy;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<ShipmentItemRequestDTO> items;
}
