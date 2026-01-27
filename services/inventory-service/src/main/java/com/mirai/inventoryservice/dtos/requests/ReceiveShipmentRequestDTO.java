package com.mirai.inventoryservice.dtos.requests;

import com.mirai.inventoryservice.models.enums.LocationType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiveShipmentRequestDTO {
    @NotNull(message = "Actual delivery date is required")
    private LocalDate actualDeliveryDate;

    private UUID receivedBy;

    @NotNull(message = "Item receipts are required")
    private List<ItemReceiptDTO> itemReceipts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemReceiptDTO {
        @NotNull(message = "Shipment item ID is required")
        private UUID shipmentItemId;

        @NotNull(message = "Received quantity is required")
        @Min(value = 0, message = "Received quantity must be non-negative")
        private Integer receivedQuantity;

        private LocationType destinationLocationType;

        private UUID destinationLocationId;
    }
}
