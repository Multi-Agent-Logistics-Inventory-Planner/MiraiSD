package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.BoxBinInventoryNotFoundException;
import com.mirai.inventoryservice.models.Item;
import com.mirai.inventoryservice.models.inventory.BoxBinInventory;
import com.mirai.inventoryservice.models.storage.BoxBin;
import com.mirai.inventoryservice.repositories.BoxBinInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class BoxBinInventoryService {
    private final BoxBinInventoryRepository boxBinInventoryRepository;
    private final BoxBinService boxBinService;
    private final ItemService itemService;

    public BoxBinInventoryService(
            BoxBinInventoryRepository boxBinInventoryRepository,
            BoxBinService boxBinService,
            ItemService itemService) {
        this.boxBinInventoryRepository = boxBinInventoryRepository;
        this.boxBinService = boxBinService;
        this.itemService = itemService;
    }

    public BoxBinInventory addInventory(
            UUID boxBinId,
            UUID itemId,
            Integer quantity) {
        
        BoxBin boxBin = boxBinService.getBoxBinById(boxBinId);
        Item item = itemService.getItemById(itemId);
        
        BoxBinInventory inventory = BoxBinInventory.builder()
                .boxBin(boxBin)
                .item(item)
                .quantity(quantity)
                .build();
        
        return boxBinInventoryRepository.save(inventory);
    }

    public BoxBinInventory getInventoryById(UUID inventoryId) {
        return boxBinInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new BoxBinInventoryNotFoundException(
                        "BoxBin inventory not found with id: " + inventoryId));
    }

    public List<BoxBinInventory> listInventory(UUID boxBinId) {
        // Verify box bin exists
        boxBinService.getBoxBinById(boxBinId);
        return boxBinInventoryRepository.findByBoxBin_Id(boxBinId);
    }

    public BoxBinInventory updateInventory(
            UUID inventoryId,
            UUID itemId,
            Integer quantity) {
        
        BoxBinInventory inventory = getInventoryById(inventoryId);
        
        if (itemId != null) {
            Item item = itemService.getItemById(itemId);
            inventory.setItem(item);
        }
        
        if (quantity != null) inventory.setQuantity(quantity);
        
        return boxBinInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        BoxBinInventory inventory = getInventoryById(inventoryId);
        boxBinInventoryRepository.delete(inventory);
    }
}

