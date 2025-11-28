package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.DoubleClawMachineInventoryNotFoundException;
import com.mirai.inventoryservice.models.Item;
import com.mirai.inventoryservice.models.inventory.DoubleClawMachineInventory;
import com.mirai.inventoryservice.models.storage.DoubleClawMachine;
import com.mirai.inventoryservice.repositories.DoubleClawMachineInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DoubleClawMachineInventoryService {
    private final DoubleClawMachineInventoryRepository doubleClawMachineInventoryRepository;
    private final DoubleClawMachineService doubleClawMachineService;
    private final ItemService itemService;

    public DoubleClawMachineInventoryService(
            DoubleClawMachineInventoryRepository doubleClawMachineInventoryRepository,
            DoubleClawMachineService doubleClawMachineService,
            ItemService itemService) {
        this.doubleClawMachineInventoryRepository = doubleClawMachineInventoryRepository;
        this.doubleClawMachineService = doubleClawMachineService;
        this.itemService = itemService;
    }

    public DoubleClawMachineInventory addInventory(
            UUID doubleClawMachineId,
            UUID itemId,
            Integer quantity) {
        
        DoubleClawMachine machine = doubleClawMachineService.getDoubleClawMachineById(doubleClawMachineId);
        Item item = itemService.getItemById(itemId);
        
        DoubleClawMachineInventory inventory = DoubleClawMachineInventory.builder()
                .doubleClawMachine(machine)
                .item(item)
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
        // Verify machine exists
        doubleClawMachineService.getDoubleClawMachineById(doubleClawMachineId);
        return doubleClawMachineInventoryRepository.findByDoubleClawMachine_Id(doubleClawMachineId);
    }

    public DoubleClawMachineInventory updateInventory(
            UUID inventoryId,
            UUID itemId,
            Integer quantity) {
        
        DoubleClawMachineInventory inventory = getInventoryById(inventoryId);
        
        if (itemId != null) {
            Item item = itemService.getItemById(itemId);
            inventory.setItem(item);
        }
        
        if (quantity != null) inventory.setQuantity(quantity);
        
        return doubleClawMachineInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        DoubleClawMachineInventory inventory = getInventoryById(inventoryId);
        doubleClawMachineInventoryRepository.delete(inventory);
    }
}
