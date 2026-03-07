package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.DoubleClawMachineInventoryNotFoundException;
import com.mirai.inventoryservice.exceptions.InvalidInventoryOperationException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.inventory.DoubleClawMachineInventory;
import com.mirai.inventoryservice.repositories.DoubleClawMachineInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class DoubleClawMachineInventoryService {
    private final DoubleClawMachineInventoryRepository doubleClawMachineInventoryRepository;
    private final DoubleClawMachineService doubleClawMachineService;
    private final ProductService productService;
    private final StockMovementService stockMovementService;

    public DoubleClawMachineInventoryService(
            DoubleClawMachineInventoryRepository doubleClawMachineInventoryRepository,
            DoubleClawMachineService doubleClawMachineService,
            ProductService productService,
            StockMovementService stockMovementService) {
        this.doubleClawMachineInventoryRepository = doubleClawMachineInventoryRepository;
        this.doubleClawMachineService = doubleClawMachineService;
        this.productService = productService;
        this.stockMovementService = stockMovementService;
    }

    public DoubleClawMachineInventory addInventory(UUID doubleClawMachineId, UUID productId, Integer quantity, UUID actorId, StockMovementReason reason) {
        doubleClawMachineService.getDoubleClawMachineById(doubleClawMachineId); // Validate machine exists
        Product product = productService.getProductById(productId);

        Optional<DoubleClawMachineInventory> existing = doubleClawMachineInventoryRepository
                .findByDoubleClawMachine_IdAndItem_Id(doubleClawMachineId, productId);
        if (existing.isPresent()) {
            throw new InvalidInventoryOperationException(
                    "Inventory for product " + product.getSku() + " already exists in this machine");
        }

        // Default to INITIAL_STOCK if no reason provided
        StockMovementReason effectiveReason = reason != null ? reason : StockMovementReason.INITIAL_STOCK;

        UUID inventoryId = stockMovementService.createInventoryWithTracking(
                LocationType.DOUBLE_CLAW_MACHINE, doubleClawMachineId, product, quantity,
                effectiveReason, actorId, null);

        return doubleClawMachineInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new DoubleClawMachineInventoryNotFoundException("Failed to create inventory"));
    }

    public DoubleClawMachineInventory getInventoryById(UUID inventoryId) {
        return doubleClawMachineInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new DoubleClawMachineInventoryNotFoundException(
                        "DoubleClawMachine inventory not found with id: " + inventoryId));
    }

    public List<DoubleClawMachineInventory> listInventory(UUID doubleClawMachineId) {
        doubleClawMachineService.getDoubleClawMachineById(doubleClawMachineId);
        return doubleClawMachineInventoryRepository.findByDoubleClawMachine_Id(doubleClawMachineId);
    }

    public List<DoubleClawMachineInventory> findByProduct(UUID productId) {
        return doubleClawMachineInventoryRepository.findByItem_Id(productId);
    }

    public DoubleClawMachineInventory updateInventory(UUID inventoryId, Integer quantity) {
        DoubleClawMachineInventory inventory = getInventoryById(inventoryId);
        if (quantity != null) {
            inventory.setQuantity(quantity);
        }
        return doubleClawMachineInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        getInventoryById(inventoryId); // Validate exists
        stockMovementService.removeInventoryWithTracking(
                LocationType.DOUBLE_CLAW_MACHINE, inventoryId,
                StockMovementReason.REMOVED, null, null);
    }
}
