package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.BoxBinInventoryNotFoundException;
import com.mirai.inventoryservice.exceptions.InvalidInventoryOperationException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.inventory.BoxBinInventory;
import com.mirai.inventoryservice.models.storage.BoxBin;
import com.mirai.inventoryservice.repositories.BoxBinInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class BoxBinInventoryService {
    private final BoxBinInventoryRepository boxBinInventoryRepository;
    private final BoxBinService boxBinService;
    private final ProductService productService;

    public BoxBinInventoryService(
            BoxBinInventoryRepository boxBinInventoryRepository,
            BoxBinService boxBinService,
            ProductService productService) {
        this.boxBinInventoryRepository = boxBinInventoryRepository;
        this.boxBinService = boxBinService;
        this.productService = productService;
    }

    public BoxBinInventory addInventory(UUID boxBinId, UUID productId, Integer quantity) {
        BoxBin boxBin = boxBinService.getBoxBinById(boxBinId);
        Product product = productService.getProductById(productId);

        Optional<BoxBinInventory> existing = boxBinInventoryRepository
                .findByBoxBin_IdAndItem_Id(boxBinId, productId);
        if (existing.isPresent()) {
            throw new InvalidInventoryOperationException(
                    "Inventory for product " + product.getSku() + " already exists in this box bin");
        }

        BoxBinInventory inventory = BoxBinInventory.builder()
                .boxBin(boxBin)
                .item(product)
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
        boxBinService.getBoxBinById(boxBinId);
        return boxBinInventoryRepository.findByBoxBin_Id(boxBinId);
    }

    public List<BoxBinInventory> findByProduct(UUID productId) {
        return boxBinInventoryRepository.findByItem_Id(productId);
    }

    public BoxBinInventory updateInventory(UUID inventoryId, Integer quantity) {
        BoxBinInventory inventory = getInventoryById(inventoryId);
        if (quantity != null) {
            inventory.setQuantity(quantity);
        }
        return boxBinInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        BoxBinInventory inventory = getInventoryById(inventoryId);
        boxBinInventoryRepository.delete(inventory);
    }
}

