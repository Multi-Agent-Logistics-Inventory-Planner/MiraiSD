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
    public static class DestinationAllocationDTO {
        @NotNull(message = "Location type is required")
        private LocationType locationType;

        private UUID locationId;  // null for NOT_ASSIGNED

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemReceiptDTO {
        @NotNull(message = "Shipment item ID is required")
        private UUID shipmentItemId;

        // Multi-destination allocations (preferred)
        private List<DestinationAllocationDTO> allocations;

        // Damaged quantity - items that arrived damaged (not added to inventory)
        @Min(value = 0, message = "Damaged quantity must be non-negative")
        private Integer damagedQuantity;

        // Display quantity - items for display use (not added to inventory)
        @Min(value = 0, message = "Display quantity must be non-negative")
        private Integer displayQuantity;

        // Shop quantity - items for shop use (not added to inventory)
        @Min(value = 0, message = "Shop quantity must be non-negative")
        private Integer shopQuantity;

        // Legacy fields for backward compatibility
        @Min(value = 0, message = "Received quantity must be non-negative")
        private Integer receivedQuantity;

        private LocationType destinationLocationType;

        private UUID destinationLocationId;
    }
}
