package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.RackInventoryNotFoundException;
import com.mirai.inventoryservice.models.Item;
import com.mirai.inventoryservice.models.inventory.RackInventory;
import com.mirai.inventoryservice.models.storage.Rack;
import com.mirai.inventoryservice.repositories.RackInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RackInventoryService {
    private final RackInventoryRepository rackInventoryRepository;
    private final RackService rackService;
    private final ItemService itemService;

    public RackInventoryService(
            RackInventoryRepository rackInventoryRepository,
            RackService rackService,
            ItemService itemService) {
        this.rackInventoryRepository = rackInventoryRepository;
        this.rackService = rackService;
        this.itemService = itemService;
    }

    public RackInventory addInventory(
            UUID rackId,
            UUID itemId,
            Integer quantity) {
        
        Rack rack = rackService.getRackById(rackId);
        Item item = itemService.getItemById(itemId);
        
        RackInventory inventory = RackInventory.builder()
                .rack(rack)
                .item(item)
                .quantity(quantity)
                .build();
        
        return rackInventoryRepository.save(inventory);
    }

    public RackInventory getInventoryById(UUID inventoryId) {
        return rackInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new RackInventoryNotFoundException(
                        "Rack inventory not found with id: " + inventoryId));
    }

    public List<RackInventory> listInventory(UUID rackId) {
        // Verify rack exists
        rackService.getRackById(rackId);
        return rackInventoryRepository.findByRack_Id(rackId);
    }

    public RackInventory updateInventory(
            UUID inventoryId,
            UUID itemId,
            Integer quantity) {
        
        RackInventory inventory = getInventoryById(inventoryId);
        
        if (itemId != null) {
            Item item = itemService.getItemById(itemId);
            inventory.setItem(item);
        }
        
        if (quantity != null) inventory.setQuantity(quantity);
        
        return rackInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        RackInventory inventory = getInventoryById(inventoryId);
        rackInventoryRepository.delete(inventory);
    }
}
