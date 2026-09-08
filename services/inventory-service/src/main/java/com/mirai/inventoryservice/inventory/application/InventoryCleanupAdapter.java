package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.catalog.application.InventoryCleanupPort;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import com.mirai.inventoryservice.services.InventoryAggregateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

/**
 * {@link InventoryCleanupPort} implementation backed by location-inventory and stock-movement
 * repositories.
 */
@Component
@RequiredArgsConstructor
class InventoryCleanupAdapter implements InventoryCleanupPort {

    private final InventoryAggregateService inventoryAggregateService;
    private final StockMovementRepository stockMovementRepository;

    @Override
    @Transactional
    public void deleteInventoryForProduct(UUID productId) {
        inventoryAggregateService.deleteAllInventoryForProduct(productId);
    }

    @Override
    @Transactional
    public void deleteInventoryForProducts(Collection<UUID> productIds) {
        inventoryAggregateService.deleteAllInventoryForProducts(productIds);
    }

    @Override
    @Transactional
    public void deleteStockMovementsForProduct(UUID productId) {
        stockMovementRepository.deleteByItem_Id(productId);
    }

    @Override
    @Transactional
    public void deleteStockMovementsForProducts(Collection<UUID> productIds) {
        stockMovementRepository.deleteAllByItemIdIn(productIds);
    }
}
