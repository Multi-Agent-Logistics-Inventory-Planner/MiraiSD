package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.KeychainMachineInventoryNotFoundException;
import com.mirai.inventoryservice.models.Item;
import com.mirai.inventoryservice.models.inventory.KeychainMachineInventory;
import com.mirai.inventoryservice.models.storage.KeychainMachine;
import com.mirai.inventoryservice.repositories.KeychainMachineInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class KeychainMachineInventoryService {
    private final KeychainMachineInventoryRepository keychainMachineInventoryRepository;
    private final KeychainMachineService keychainMachineService;
    private final ItemService itemService;

    public KeychainMachineInventoryService(
            KeychainMachineInventoryRepository keychainMachineInventoryRepository,
            KeychainMachineService keychainMachineService,
            ItemService itemService) {
        this.keychainMachineInventoryRepository = keychainMachineInventoryRepository;
        this.keychainMachineService = keychainMachineService;
        this.itemService = itemService;
    }

    public KeychainMachineInventory addInventory(
            UUID keychainMachineId,
            UUID itemId,
            Integer quantity) {
        
        KeychainMachine machine = keychainMachineService.getKeychainMachineById(keychainMachineId);
        Item item = itemService.getItemById(itemId);
        
        KeychainMachineInventory inventory = KeychainMachineInventory.builder()
                .keychainMachine(machine)
                .item(item)
                .quantity(quantity)
                .build();
        
        return keychainMachineInventoryRepository.save(inventory);
    }

    public KeychainMachineInventory getInventoryById(UUID inventoryId) {
        return keychainMachineInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new KeychainMachineInventoryNotFoundException(
                        "KeychainMachine inventory not found with id: " + inventoryId));
    }

    public List<KeychainMachineInventory> listInventory(UUID keychainMachineId) {
        // Verify machine exists
        keychainMachineService.getKeychainMachineById(keychainMachineId);
        return keychainMachineInventoryRepository.findByKeychainMachine_Id(keychainMachineId);
    }

    public KeychainMachineInventory updateInventory(
            UUID inventoryId,
            UUID itemId,
            Integer quantity) {
        
        KeychainMachineInventory inventory = getInventoryById(inventoryId);
        
        if (itemId != null) {
            Item item = itemService.getItemById(itemId);
            inventory.setItem(item);
        }
        
        if (quantity != null) inventory.setQuantity(quantity);
        
        return keychainMachineInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        KeychainMachineInventory inventory = getInventoryById(inventoryId);
        keychainMachineInventoryRepository.delete(inventory);
    }
}
