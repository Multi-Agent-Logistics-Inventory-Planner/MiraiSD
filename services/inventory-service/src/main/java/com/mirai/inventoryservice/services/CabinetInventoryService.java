package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.CabinetInventoryNotFoundException;
import com.mirai.inventoryservice.exceptions.InvalidInventoryOperationException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.inventory.CabinetInventory;
import com.mirai.inventoryservice.models.storage.Cabinet;
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

    public CabinetInventoryService(
            CabinetInventoryRepository cabinetInventoryRepository,
            CabinetService cabinetService,
            ProductService productService) {
        this.cabinetInventoryRepository = cabinetInventoryRepository;
        this.cabinetService = cabinetService;
        this.productService = productService;
    }

    public CabinetInventory addInventory(UUID cabinetId, UUID productId, Integer quantity) {
        Cabinet cabinet = cabinetService.getCabinetById(cabinetId);
        Product product = productService.getProductById(productId);

        Optional<CabinetInventory> existing = cabinetInventoryRepository
                .findByCabinet_IdAndItem_Id(cabinetId, productId);
        if (existing.isPresent()) {
            throw new InvalidInventoryOperationException(
                    "Inventory for product " + product.getSku() + " already exists in this cabinet");
        }

        CabinetInventory inventory = CabinetInventory.builder()
                .cabinet(cabinet)
                .item(product)
                .quantity(quantity)
                .build();

        return cabinetInventoryRepository.save(inventory);
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
        CabinetInventory inventory = getInventoryById(inventoryId);
        cabinetInventoryRepository.delete(inventory);
    }
}
