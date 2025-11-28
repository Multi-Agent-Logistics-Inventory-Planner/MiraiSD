package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.SingleClawMachineInventoryNotFoundException;
import com.mirai.inventoryservice.models.Item;
import com.mirai.inventoryservice.models.inventory.SingleClawMachineInventory;
import com.mirai.inventoryservice.models.storage.SingleClawMachine;
import com.mirai.inventoryservice.repositories.SingleClawMachineInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SingleClawMachineInventoryService {
    private final SingleClawMachineInventoryRepository singleClawMachineInventoryRepository;
    private final SingleClawMachineService singleClawMachineService;
    private final ItemService itemService;

    public SingleClawMachineInventoryService(
            SingleClawMachineInventoryRepository singleClawMachineInventoryRepository,
            SingleClawMachineService singleClawMachineService,
            ItemService itemService) {
        this.singleClawMachineInventoryRepository = singleClawMachineInventoryRepository;
        this.singleClawMachineService = singleClawMachineService;
        this.itemService = itemService;
    }

    public SingleClawMachineInventory addInventory(
            UUID singleClawMachineId,
            UUID itemId,
            Integer quantity) {
        
        SingleClawMachine machine = singleClawMachineService.getSingleClawMachineById(singleClawMachineId);
        Item item = itemService.getItemById(itemId);
        
        SingleClawMachineInventory inventory = SingleClawMachineInventory.builder()
                .singleClawMachine(machine)
                .item(item)
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
        // Verify machine exists
        singleClawMachineService.getSingleClawMachineById(singleClawMachineId);
        return singleClawMachineInventoryRepository.findBySingleClawMachine_Id(singleClawMachineId);
    }

    public SingleClawMachineInventory updateInventory(
            UUID inventoryId,
            UUID itemId,
            Integer quantity) {
        
        SingleClawMachineInventory inventory = getInventoryById(inventoryId);
        
        if (itemId != null) {
            Item item = itemService.getItemById(itemId);
            inventory.setItem(item);
        }
        
        if (quantity != null) inventory.setQuantity(quantity);
        
        return singleClawMachineInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        SingleClawMachineInventory inventory = getInventoryById(inventoryId);
        singleClawMachineInventoryRepository.delete(inventory);
    }
}
