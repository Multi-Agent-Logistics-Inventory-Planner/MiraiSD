package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.InvalidInventoryOperationException;
import com.mirai.inventoryservice.exceptions.WindowInventoryNotFoundException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.inventory.WindowInventory;
import com.mirai.inventoryservice.repositories.WindowInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class WindowInventoryService {
    private final WindowInventoryRepository windowInventoryRepository;
    private final WindowService windowService;
    private final ProductService productService;
    private final StockMovementService stockMovementService;

    public WindowInventoryService(
            WindowInventoryRepository windowInventoryRepository,
            WindowService windowService,
            ProductService productService,
            StockMovementService stockMovementService) {
        this.windowInventoryRepository = windowInventoryRepository;
        this.windowService = windowService;
        this.productService = productService;
        this.stockMovementService = stockMovementService;
    }

    public WindowInventory addInventory(UUID windowId, UUID productId, Integer quantity, UUID actorId, StockMovementReason reason) {
        windowService.getWindowById(windowId); // Validate window exists
        Product product = productService.getProductById(productId);

        Optional<WindowInventory> existing = windowInventoryRepository
                .findByWindow_IdAndItem_Id(windowId, productId);
        if (existing.isPresent()) {
            throw new InvalidInventoryOperationException(
                    "Inventory for product " + product.getSku() + " already exists in this window");
        }

        // Default to INITIAL_STOCK if no reason provided
        StockMovementReason effectiveReason = reason != null ? reason : StockMovementReason.INITIAL_STOCK;

        UUID inventoryId = stockMovementService.createInventoryWithTracking(
                LocationType.WINDOW, windowId, product, quantity,
                effectiveReason, actorId, null);

        return windowInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new WindowInventoryNotFoundException("Failed to create inventory"));
    }

    public WindowInventory getInventoryById(UUID inventoryId) {
        return windowInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new WindowInventoryNotFoundException(
                        "Window inventory not found with id: " + inventoryId));
    }

    public List<WindowInventory> listInventory(UUID windowId) {
        windowService.getWindowById(windowId);
        return windowInventoryRepository.findByWindow_Id(windowId);
    }

    public List<WindowInventory> findByProduct(UUID productId) {
        return windowInventoryRepository.findByItem_Id(productId);
    }

    public WindowInventory updateInventory(UUID inventoryId, Integer quantity) {
        WindowInventory inventory = getInventoryById(inventoryId);
        if (quantity != null) {
            inventory.setQuantity(quantity);
        }
        return windowInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        getInventoryById(inventoryId); // Validate exists
        stockMovementService.removeInventoryWithTracking(
                LocationType.WINDOW, inventoryId,
                StockMovementReason.REMOVED, null, null);
    }
}

