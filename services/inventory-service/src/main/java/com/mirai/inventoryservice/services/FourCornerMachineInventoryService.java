package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.InvalidInventoryOperationException;
import com.mirai.inventoryservice.exceptions.FourCornerMachineInventoryNotFoundException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.inventory.FourCornerMachineInventory;
import com.mirai.inventoryservice.repositories.FourCornerMachineInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class FourCornerMachineInventoryService {
    private final FourCornerMachineInventoryRepository fourCornerMachineInventoryRepository;
    private final FourCornerMachineService fourCornerMachineService;
    private final ProductService productService;
    private final StockMovementService stockMovementService;

    public FourCornerMachineInventoryService(
            FourCornerMachineInventoryRepository fourCornerMachineInventoryRepository,
            FourCornerMachineService fourCornerMachineService,
            ProductService productService,
            StockMovementService stockMovementService) {
        this.fourCornerMachineInventoryRepository = fourCornerMachineInventoryRepository;
        this.fourCornerMachineService = fourCornerMachineService;
        this.productService = productService;
        this.stockMovementService = stockMovementService;
    }

    public FourCornerMachineInventory addInventory(UUID fourCornerMachineId, UUID productId, Integer quantity, UUID actorId) {
        fourCornerMachineService.getFourCornerMachineById(fourCornerMachineId); // Validate machine exists
        Product product = productService.getProductById(productId);

        Optional<FourCornerMachineInventory> existing = fourCornerMachineInventoryRepository
                .findByFourCornerMachine_IdAndItem_Id(fourCornerMachineId, productId);
        if (existing.isPresent()) {
            throw new InvalidInventoryOperationException(
                    "Inventory for product " + product.getSku() + " already exists in this machine");
        }

        UUID inventoryId = stockMovementService.createInventoryWithTracking(
                LocationType.FOUR_CORNER_MACHINE, fourCornerMachineId, product, quantity,
                StockMovementReason.INITIAL_STOCK, actorId, null);

        return fourCornerMachineInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new FourCornerMachineInventoryNotFoundException("Failed to create inventory"));
    }

    public FourCornerMachineInventory getInventoryById(UUID inventoryId) {
        return fourCornerMachineInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new FourCornerMachineInventoryNotFoundException(
                        "FourCornerMachine inventory not found with id: " + inventoryId));
    }

    public List<FourCornerMachineInventory> listInventory(UUID fourCornerMachineId) {
        fourCornerMachineService.getFourCornerMachineById(fourCornerMachineId);
        return fourCornerMachineInventoryRepository.findByFourCornerMachine_Id(fourCornerMachineId);
    }

    public List<FourCornerMachineInventory> findByProduct(UUID productId) {
        return fourCornerMachineInventoryRepository.findByItem_Id(productId);
    }

    public FourCornerMachineInventory updateInventory(UUID inventoryId, Integer quantity) {
        FourCornerMachineInventory inventory = getInventoryById(inventoryId);
        if (quantity != null) {
            inventory.setQuantity(quantity);
        }
        return fourCornerMachineInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        getInventoryById(inventoryId); // Validate exists
        stockMovementService.removeInventoryWithTracking(
                LocationType.FOUR_CORNER_MACHINE, inventoryId,
                StockMovementReason.REMOVED, null, null);
    }
}
