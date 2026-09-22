package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.models.enums.StockMovementReason;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Application-owned slim projection over one stock movement, for the Product Assistant
 * drill-down callers outside the {@code inventory} module. Mirrors
 * {@code inventory.infrastructure.StockMovementHistoryView} field-for-field, but as a plain
 * record rather than a Spring Data projection interface, so callers outside {@code inventory}
 * never hold an infrastructure-package type (see .specs/phase-6-inventory/log.md T-5 review).
 */
public record StockMovementHistoryEntry(
        Long id,
        OffsetDateTime at,
        StockMovementReason reason,
        Integer quantityChange,
        Integer previousQuantity,
        Integer currentQuantity,
        UUID fromLocationId,
        UUID toLocationId) {

    static StockMovementHistoryEntry from(
            com.mirai.inventoryservice.inventory.infrastructure.StockMovementHistoryView view) {
        return new StockMovementHistoryEntry(
                view.getId(),
                view.getAt(),
                view.getReason(),
                view.getQuantityChange(),
                view.getPreviousQuantity(),
                view.getCurrentQuantity(),
                view.getFromLocationId(),
                view.getToLocationId());
    }
}
