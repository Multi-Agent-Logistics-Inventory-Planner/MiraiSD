package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.catalog.application.InitialStockPort;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.inventory.application.StockMovementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link InitialStockPort} implementation backed by {@code StockMovementService}'s tracked
 * inventory creation. Kept as a dedicated adapter (rather than implementing the port directly on
 * {@code StockMovementService}, as an earlier draft did) because {@code StockMovementService} is
 * depended on, by its concrete type, by many other beans (e.g. {@code AuditLogDTOMapper}) —
 * {@code @MockBean InitialStockPort} in a test would silently remove the entire
 * {@code StockMovementService} bean from the context, breaking unrelated wiring. A single-purpose
 * adapter can be mocked in isolation, matching every other port's pattern.
 */
@Component
@RequiredArgsConstructor
class InitialStockAdapter implements InitialStockPort {

    private final StockMovementService stockMovementService;

    @Override
    @Transactional
    public void recordInitialStock(Product product, int quantity) {
        stockMovementService.createInventoryWithTracking(
                LocationType.NOT_ASSIGNED,
                null,
                product,
                quantity,
                StockMovementReason.INITIAL_STOCK,
                null,
                "Initial stock on product creation");
    }
}
