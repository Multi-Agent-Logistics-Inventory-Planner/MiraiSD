package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.CabinetInventoryNotFoundException;
import com.mirai.inventoryservice.exceptions.InvalidInventoryOperationException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.inventory.CabinetInventory;
import com.mirai.inventoryservice.repositories.CabinetInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class CabinetInventoryService {
    private final CabinetInventoryRepository cabinetInventoryRepository;
    private final CabinetService cabinetService;
    private final ProductService productService;
    private final StockMovementService stockMovementService;

    public CabinetInventoryService(
            CabinetInventoryRepository cabinetInventoryRepository,
            CabinetService cabinetService,
            ProductService productService,
            StockMovementService stockMovementService) {
        this.cabinetInventoryRepository = cabinetInventoryRepository;
        this.cabinetService = cabinetService;
        this.productService = productService;
        this.stockMovementService = stockMovementService;
    }

    public CabinetInventory addInventory(UUID cabinetId, UUID productId, Integer quantity) {
        cabinetService.getCabinetById(cabinetId); // Validate cabinet exists
        Product product = productService.getProductById(productId);

        Optional<CabinetInventory> existing = cabinetInventoryRepository
                .findByCabinet_IdAndItem_Id(cabinetId, productId);
        if (existing.isPresent()) {
            throw new InvalidInventoryOperationException(
                    "Inventory for product " + product.getSku() + " already exists in this cabinet");
        }

        UUID inventoryId = stockMovementService.createInventoryWithTracking(
                LocationType.CABINET, cabinetId, product, quantity,
                StockMovementReason.INITIAL_STOCK, null, null);

        return cabinetInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new CabinetInventoryNotFoundException("Failed to create inventory"));
    }

    public CabinetInventory getInventoryById(UUID inventoryId) {
        return cabinetInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new CabinetInventoryNotFoundException(
                        "Cabinet inventory not found with id: " + inventoryId));
    }

    public List<CabinetInventory> listInventory(UUID cabinetId) {
        cabinetService.getCabinetById(cabinetId);
        return cabinetInventoryRepository.findByCabinet_Id(cabinetId);
    }

    public List<CabinetInventory> findByProduct(UUID productId) {
        return cabinetInventoryRepository.findByItem_Id(productId);
    }

    public CabinetInventory updateInventory(UUID inventoryId, Integer quantity) {
        CabinetInventory inventory = getInventoryById(inventoryId);
        if (quantity != null) {
            inventory.setQuantity(quantity);
        }
        return cabinetInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        getInventoryById(inventoryId); // Validate exists
        stockMovementService.removeInventoryWithTracking(
                LocationType.CABINET, inventoryId,
                StockMovementReason.REMOVED, null, null);
    }
}
