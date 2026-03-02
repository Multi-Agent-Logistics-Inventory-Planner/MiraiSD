package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.InvalidInventoryOperationException;
import com.mirai.inventoryservice.exceptions.RackInventoryNotFoundException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.inventory.RackInventory;
import com.mirai.inventoryservice.repositories.RackInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class RackInventoryService {
    private final RackInventoryRepository rackInventoryRepository;
    private final RackService rackService;
    private final ProductService productService;
    private final StockMovementService stockMovementService;

    public RackInventoryService(
            RackInventoryRepository rackInventoryRepository,
            RackService rackService,
            ProductService productService,
            StockMovementService stockMovementService) {
        this.rackInventoryRepository = rackInventoryRepository;
        this.rackService = rackService;
        this.productService = productService;
        this.stockMovementService = stockMovementService;
    }

    public RackInventory addInventory(UUID rackId, UUID productId, Integer quantity) {
        rackService.getRackById(rackId); // Validate rack exists
        Product product = productService.getProductById(productId);

        Optional<RackInventory> existing = rackInventoryRepository
                .findByRack_IdAndItem_Id(rackId, productId);
        if (existing.isPresent()) {
            throw new InvalidInventoryOperationException(
                    "Inventory for product " + product.getSku() + " already exists in this rack");
        }

        UUID inventoryId = stockMovementService.createInventoryWithTracking(
                LocationType.RACK, rackId, product, quantity,
                StockMovementReason.INITIAL_STOCK, null, null);

        return rackInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new RackInventoryNotFoundException("Failed to create inventory"));
    }

    public RackInventory getInventoryById(UUID inventoryId) {
        return rackInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new RackInventoryNotFoundException(
                        "Rack inventory not found with id: " + inventoryId));
    }

    public List<RackInventory> listInventory(UUID rackId) {
        rackService.getRackById(rackId);
        return rackInventoryRepository.findByRack_Id(rackId);
    }

    public List<RackInventory> findByProduct(UUID productId) {
        return rackInventoryRepository.findByItem_Id(productId);
    }

    public RackInventory updateInventory(UUID inventoryId, Integer quantity) {
        RackInventory inventory = getInventoryById(inventoryId);
        if (quantity != null) {
            inventory.setQuantity(quantity);
        }
        return rackInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        getInventoryById(inventoryId); // Validate exists
        stockMovementService.removeInventoryWithTracking(
                LocationType.RACK, inventoryId,
                StockMovementReason.REMOVED, null, null);
    }
}
