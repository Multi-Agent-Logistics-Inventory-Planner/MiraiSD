package com.mirai.inventoryservice.dtos.responses;

import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentResponseDTO {
    private UUID id;
    private String shipmentNumber;
    private String supplierName;
    private ShipmentStatus status;
    private LocalDate orderDate;
    private LocalDate expectedDeliveryDate;
    private LocalDate actualDeliveryDate;
    private BigDecimal totalCost;
    private String notes;
    private UserResponseDTO createdBy;
    private UserResponseDTO receivedBy;
    private List<ShipmentItemResponseDTO> items;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
