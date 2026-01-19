package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.DoubleClawMachineInventoryNotFoundException;
import com.mirai.inventoryservice.exceptions.InvalidInventoryOperationException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.inventory.DoubleClawMachineInventory;
import com.mirai.inventoryservice.models.storage.DoubleClawMachine;
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

    public DoubleClawMachineInventoryService(
            DoubleClawMachineInventoryRepository doubleClawMachineInventoryRepository,
            DoubleClawMachineService doubleClawMachineService,
            ProductService productService) {
        this.doubleClawMachineInventoryRepository = doubleClawMachineInventoryRepository;
        this.doubleClawMachineService = doubleClawMachineService;
        this.productService = productService;
    }

    public DoubleClawMachineInventory addInventory(UUID doubleClawMachineId, UUID productId, Integer quantity) {
        DoubleClawMachine machine = doubleClawMachineService.getDoubleClawMachineById(doubleClawMachineId);
        Product product = productService.getProductById(productId);

        Optional<DoubleClawMachineInventory> existing = doubleClawMachineInventoryRepository
                .findByDoubleClawMachine_IdAndItem_Id(doubleClawMachineId, productId);
        if (existing.isPresent()) {
            throw new InvalidInventoryOperationException(
                    "Inventory for product " + product.getSku() + " already exists in this machine");
        }

        DoubleClawMachineInventory inventory = DoubleClawMachineInventory.builder()
                .doubleClawMachine(machine)
                .item(product)
                .quantity(quantity)
                .build();

        return doubleClawMachineInventoryRepository.save(inventory);
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
        DoubleClawMachineInventory inventory = getInventoryById(inventoryId);
        doubleClawMachineInventoryRepository.delete(inventory);
    }
}
