package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.AdjustStockRequestDTO;
import com.mirai.inventoryservice.exceptions.NotAssignedInventoryNotFoundException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.inventory.NotAssignedInventory;
import com.mirai.inventoryservice.repositories.NotAssignedInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class NotAssignedInventoryService {
    private final NotAssignedInventoryRepository notAssignedInventoryRepository;
    private final ProductService productService;
    private final StockMovementService stockMovementService;

    public NotAssignedInventoryService(
            NotAssignedInventoryRepository notAssignedInventoryRepository,
            ProductService productService,
            StockMovementService stockMovementService) {
        this.notAssignedInventoryRepository = notAssignedInventoryRepository;
        this.productService = productService;
        this.stockMovementService = stockMovementService;
    }

    public NotAssignedInventory addInventory(UUID productId, Integer quantity) {
        Product product = productService.getProductById(productId);

        // Check if entry already exists - if so, adjust existing quantity
        Optional<NotAssignedInventory> existing = notAssignedInventoryRepository.findByItem_Id(productId);
        if (existing.isPresent()) {
            NotAssignedInventory inv = existing.get();
            AdjustStockRequestDTO adjustRequest = AdjustStockRequestDTO.builder()
                    .quantityChange(quantity)
                    .reason(StockMovementReason.RESTOCK)
                    .build();
            stockMovementService.adjustInventory(LocationType.NOT_ASSIGNED, inv.getId(), adjustRequest);
            return notAssignedInventoryRepository.findById(inv.getId())
                    .orElseThrow(() -> new NotAssignedInventoryNotFoundException("Inventory not found after adjustment"));
        }

        // Create new inventory with tracking
        UUID inventoryId = stockMovementService.createInventoryWithTracking(
                LocationType.NOT_ASSIGNED, null, product, quantity,
                StockMovementReason.INITIAL_STOCK, null, null);

        return notAssignedInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new NotAssignedInventoryNotFoundException("Failed to create inventory"));
    }

    public NotAssignedInventory getInventoryById(UUID inventoryId) {
        return notAssignedInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new NotAssignedInventoryNotFoundException(
                        "NotAssigned inventory not found with id: " + inventoryId));
    }

    public List<NotAssignedInventory> listInventory() {
        return notAssignedInventoryRepository.findAll();
    }

    public List<NotAssignedInventory> findByProduct(UUID productId) {
        return notAssignedInventoryRepository.findAllByItem_Id(productId);
    }

    public Optional<NotAssignedInventory> findByProductId(UUID productId) {
        return notAssignedInventoryRepository.findByItem_Id(productId);
    }

    public NotAssignedInventory updateInventory(UUID inventoryId, Integer quantity) {
        NotAssignedInventory inventory = getInventoryById(inventoryId);
        if (quantity != null) {
            inventory.setQuantity(quantity);
        }
        return notAssignedInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        getInventoryById(inventoryId); // Validate exists
        stockMovementService.removeInventoryWithTracking(
                LocationType.NOT_ASSIGNED, inventoryId,
                StockMovementReason.REMOVED, null, null);
    }

    /**
     * Find or create NotAssignedInventory for a product.
     * Used by ShipmentService when receiving items without a destination.
     * Note: This creates without tracking - ShipmentService handles its own tracking.
     */
    public NotAssignedInventory findOrCreate(Product product) {
        return notAssignedInventoryRepository.findByItem_Id(product.getId())
                .orElseGet(() -> {
                    NotAssignedInventory inv = new NotAssignedInventory();
                    inv.setItem(product);
                    inv.setQuantity(0);
                    return inv;
                });
    }
}
