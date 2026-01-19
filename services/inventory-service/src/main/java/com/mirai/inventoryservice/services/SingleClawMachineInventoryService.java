package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.InvalidInventoryOperationException;
import com.mirai.inventoryservice.exceptions.SingleClawMachineInventoryNotFoundException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.inventory.SingleClawMachineInventory;
import com.mirai.inventoryservice.models.storage.SingleClawMachine;
import com.mirai.inventoryservice.repositories.SingleClawMachineInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class SingleClawMachineInventoryService {
    private final SingleClawMachineInventoryRepository singleClawMachineInventoryRepository;
    private final SingleClawMachineService singleClawMachineService;
    private final ProductService productService;

    public SingleClawMachineInventoryService(
            SingleClawMachineInventoryRepository singleClawMachineInventoryRepository,
            SingleClawMachineService singleClawMachineService,
            ProductService productService) {
        this.singleClawMachineInventoryRepository = singleClawMachineInventoryRepository;
        this.singleClawMachineService = singleClawMachineService;
        this.productService = productService;
    }

    public SingleClawMachineInventory addInventory(UUID singleClawMachineId, UUID productId, Integer quantity) {
        SingleClawMachine machine = singleClawMachineService.getSingleClawMachineById(singleClawMachineId);
        Product product = productService.getProductById(productId);

        Optional<SingleClawMachineInventory> existing = singleClawMachineInventoryRepository
                .findBySingleClawMachine_IdAndItem_Id(singleClawMachineId, productId);
        if (existing.isPresent()) {
            throw new InvalidInventoryOperationException(
                    "Inventory for product " + product.getSku() + " already exists in this machine");
        }

        SingleClawMachineInventory inventory = SingleClawMachineInventory.builder()
                .singleClawMachine(machine)
                .item(product)
                .quantity(quantity)
                .build();

        return singleClawMachineInventoryRepository.save(inventory);
    }

    public SingleClawMachineInventory getInventoryById(UUID inventoryId) {
        return singleClawMachineInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new SingleClawMachineInventoryNotFoundException(
                        "SingleClawMachine inventory not found with id: " + inventoryId));
    }

    public List<SingleClawMachineInventory> listInventory(UUID singleClawMachineId) {
        singleClawMachineService.getSingleClawMachineById(singleClawMachineId);
        return singleClawMachineInventoryRepository.findBySingleClawMachine_Id(singleClawMachineId);
    }

    public List<SingleClawMachineInventory> findByProduct(UUID productId) {
        return singleClawMachineInventoryRepository.findByItem_Id(productId);
    }

    public SingleClawMachineInventory updateInventory(UUID inventoryId, Integer quantity) {
        SingleClawMachineInventory inventory = getInventoryById(inventoryId);
        if (quantity != null) {
            inventory.setQuantity(quantity);
        }
        return singleClawMachineInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        SingleClawMachineInventory inventory = getInventoryById(inventoryId);
        singleClawMachineInventoryRepository.delete(inventory);
    }
}
