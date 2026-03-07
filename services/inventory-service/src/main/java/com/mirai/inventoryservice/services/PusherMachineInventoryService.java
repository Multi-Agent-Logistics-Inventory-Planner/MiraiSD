package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.InvalidInventoryOperationException;
import com.mirai.inventoryservice.exceptions.PusherMachineInventoryNotFoundException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.inventory.PusherMachineInventory;
import com.mirai.inventoryservice.repositories.PusherMachineInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class PusherMachineInventoryService {
    private final PusherMachineInventoryRepository pusherMachineInventoryRepository;
    private final PusherMachineService pusherMachineService;
    private final ProductService productService;
    private final StockMovementService stockMovementService;

    public PusherMachineInventoryService(
            PusherMachineInventoryRepository pusherMachineInventoryRepository,
            PusherMachineService pusherMachineService,
            ProductService productService,
            StockMovementService stockMovementService) {
        this.pusherMachineInventoryRepository = pusherMachineInventoryRepository;
        this.pusherMachineService = pusherMachineService;
        this.productService = productService;
        this.stockMovementService = stockMovementService;
    }

    public PusherMachineInventory addInventory(UUID pusherMachineId, UUID productId, Integer quantity, UUID actorId, StockMovementReason reason) {
        pusherMachineService.getPusherMachineById(pusherMachineId); // Validate machine exists
        Product product = productService.getProductById(productId);

        Optional<PusherMachineInventory> existing = pusherMachineInventoryRepository
                .findByPusherMachine_IdAndItem_Id(pusherMachineId, productId);
        if (existing.isPresent()) {
            throw new InvalidInventoryOperationException(
                    "Inventory for product " + product.getSku() + " already exists in this machine");
        }

        // Default to INITIAL_STOCK if no reason provided
        StockMovementReason effectiveReason = reason != null ? reason : StockMovementReason.INITIAL_STOCK;

        UUID inventoryId = stockMovementService.createInventoryWithTracking(
                LocationType.PUSHER_MACHINE, pusherMachineId, product, quantity,
                effectiveReason, actorId, null);

        return pusherMachineInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new PusherMachineInventoryNotFoundException("Failed to create inventory"));
    }

    public PusherMachineInventory getInventoryById(UUID inventoryId) {
        return pusherMachineInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new PusherMachineInventoryNotFoundException(
                        "PusherMachine inventory not found with id: " + inventoryId));
    }

    public List<PusherMachineInventory> listInventory(UUID pusherMachineId) {
        pusherMachineService.getPusherMachineById(pusherMachineId);
        return pusherMachineInventoryRepository.findByPusherMachine_Id(pusherMachineId);
    }

    public List<PusherMachineInventory> findByProduct(UUID productId) {
        return pusherMachineInventoryRepository.findByItem_Id(productId);
    }

    public PusherMachineInventory updateInventory(UUID inventoryId, Integer quantity) {
        PusherMachineInventory inventory = getInventoryById(inventoryId);
        if (quantity != null) {
            inventory.setQuantity(quantity);
        }
        return pusherMachineInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        getInventoryById(inventoryId); // Validate exists
        stockMovementService.removeInventoryWithTracking(
                LocationType.PUSHER_MACHINE, inventoryId,
                StockMovementReason.REMOVED, null, null);
    }
}
