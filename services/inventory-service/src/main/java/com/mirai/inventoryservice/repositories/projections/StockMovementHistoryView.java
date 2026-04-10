package com.mirai.inventoryservice.repositories.projections;

import com.mirai.inventoryservice.models.enums.StockMovementReason;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Spring Data projection used by the Product Assistant drill-down endpoints.
 * Exposes only the columns the UI / LLM needs so Hibernate never hydrates the
 * {@code item}, {@code auditLog}, or location entity graphs - eliminating the
 * N+1 risk of {@code findByItem_IdOrderByAtDesc} and roughly halving the wire
 * payload.
 */
public interface StockMovementHistoryView {
    Long getId();
    OffsetDateTime getAt();
    StockMovementReason getReason();
    Integer getQuantityChange();
    Integer getPreviousQuantity();
    Integer getCurrentQuantity();
    UUID getFromLocationId();
    UUID getToLocationId();
}
