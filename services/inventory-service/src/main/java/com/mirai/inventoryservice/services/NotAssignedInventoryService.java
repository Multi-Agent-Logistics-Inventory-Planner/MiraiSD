package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.NotAssignedInventoryNotFoundException;
import com.mirai.inventoryservice.models.Product;
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

    public NotAssignedInventoryService(
            NotAssignedInventoryRepository notAssignedInventoryRepository,
            ProductService productService) {
        this.notAssignedInventoryRepository = notAssignedInventoryRepository;
        this.productService = productService;
    }

    public NotAssignedInventory addInventory(UUID productId, Integer quantity) {
        Product product = productService.getProductById(productId);

        // Check if entry already exists - if so, add to existing quantity
        Optional<NotAssignedInventory> existing = notAssignedInventoryRepository.findByItem_Id(productId);
        if (existing.isPresent()) {
            NotAssignedInventory inv = existing.get();
            inv.setQuantity(inv.getQuantity() + quantity);
            return notAssignedInventoryRepository.save(inv);
        }

        NotAssignedInventory inventory = NotAssignedInventory.builder()
                .item(product)
                .quantity(quantity)
                .build();

        return notAssignedInventoryRepository.save(inventory);
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
        NotAssignedInventory inventory = getInventoryById(inventoryId);
        notAssignedInventoryRepository.delete(inventory);
    }

    /**
     * Find or create NotAssignedInventory for a product.
     * Used by ShipmentService when receiving items without a destination.
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
