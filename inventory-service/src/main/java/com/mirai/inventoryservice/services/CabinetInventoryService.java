package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.CabinetInventoryNotFoundException;
import com.mirai.inventoryservice.models.Item;
import com.mirai.inventoryservice.models.inventory.CabinetInventory;
import com.mirai.inventoryservice.models.storage.Cabinet;
import com.mirai.inventoryservice.repositories.CabinetInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CabinetInventoryService {
    private final CabinetInventoryRepository cabinetInventoryRepository;
    private final CabinetService cabinetService;
    private final ItemService itemService;

    public CabinetInventoryService(
            CabinetInventoryRepository cabinetInventoryRepository,
            CabinetService cabinetService,
            ItemService itemService) {
        this.cabinetInventoryRepository = cabinetInventoryRepository;
        this.cabinetService = cabinetService;
        this.itemService = itemService;
    }

    public CabinetInventory addInventory(
            UUID cabinetId,
            UUID itemId,
            Integer quantity) {
        
        Cabinet cabinet = cabinetService.getCabinetById(cabinetId);
        Item item = itemService.getItemById(itemId);
        
        CabinetInventory inventory = CabinetInventory.builder()
                .cabinet(cabinet)
                .item(item)
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
        // Verify cabinet exists
        cabinetService.getCabinetById(cabinetId);
        return cabinetInventoryRepository.findByCabinet_Id(cabinetId);
    }

    public CabinetInventory updateInventory(
            UUID inventoryId,
            UUID itemId,
            Integer quantity) {
        
        CabinetInventory inventory = getInventoryById(inventoryId);
        
        if (itemId != null) {
            Item item = itemService.getItemById(itemId);
            inventory.setItem(item);
        }
        
        if (quantity != null) inventory.setQuantity(quantity);
        
        return cabinetInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        CabinetInventory inventory = getInventoryById(inventoryId);
        cabinetInventoryRepository.delete(inventory);
    }
}
