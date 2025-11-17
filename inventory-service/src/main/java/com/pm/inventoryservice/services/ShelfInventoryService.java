package com.pm.inventoryservice.services;

import com.pm.inventoryservice.dtos.mappers.ShelfInventoryMapper;
import com.pm.inventoryservice.dtos.requests.ShelfInventoryRequestDTO;
import com.pm.inventoryservice.dtos.responses.ShelfInventoryResponseDTO;
import com.pm.inventoryservice.exceptions.InvalidInventoryOperationException;
import com.pm.inventoryservice.exceptions.ShelfInventoryNotFoundException;
import com.pm.inventoryservice.exceptions.ShelfNotFoundException;
import com.pm.inventoryservice.models.Shelf;
import com.pm.inventoryservice.models.ShelfInventory;
import com.pm.inventoryservice.models.enums.ProductCategory;
import com.pm.inventoryservice.repositories.ShelfInventoryRepository;
import com.pm.inventoryservice.repositories.ShelfRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ShelfInventoryService {

    private final ShelfInventoryRepository shelfInventoryRepository;
    private final ShelfRepository shelfRepository;

    public ShelfInventoryService(
            ShelfInventoryRepository shelfInventoryRepository,
            ShelfRepository shelfRepository) {
        this.shelfInventoryRepository = shelfInventoryRepository;
        this.shelfRepository = shelfRepository;
    }

    @Transactional(readOnly = true)
    public List<ShelfInventoryResponseDTO> getInventoryByShelf(@NonNull UUID shelfId) {
        // Verify shelf exists
        shelfRepository.findById(shelfId)
                .orElseThrow(() -> new ShelfNotFoundException("Shelf not found with ID: " + shelfId));

        return shelfInventoryRepository.findByShelfId(shelfId).stream()
                .map(ShelfInventoryMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShelfInventoryResponseDTO getInventoryItem(@NonNull UUID shelfId, @NonNull UUID inventoryId) {
        ShelfInventory inventory = shelfInventoryRepository.findByIdAndShelfId(inventoryId, shelfId)
                .orElseThrow(() -> new ShelfInventoryNotFoundException(
                        "Inventory item not found with ID: " + inventoryId + " in shelf: " + shelfId));

        return ShelfInventoryMapper.toDTO(inventory);
    }

    @Transactional
    public ShelfInventoryResponseDTO addInventory(@NonNull UUID shelfId, @NonNull ShelfInventoryRequestDTO request) {
        // Validate shelf exists
        Shelf shelf = shelfRepository.findById(shelfId)
                .orElseThrow(() -> new ShelfNotFoundException("Shelf not found with ID: " + shelfId));

        // Validate category/subcategory rules
        validateCategorySubcategory(request.getCategory(), request.getSubcategory());

        ShelfInventory inventory = ShelfInventoryMapper.toEntity(request, shelf);
        ShelfInventory savedInventory = shelfInventoryRepository.save(inventory);

        return ShelfInventoryMapper.toDTO(savedInventory);
    }

    @Transactional
    public ShelfInventoryResponseDTO updateInventory(
            @NonNull UUID shelfId,
            @NonNull UUID inventoryId,
            @NonNull ShelfInventoryRequestDTO request) {

        ShelfInventory inventory = shelfInventoryRepository.findByIdAndShelfId(inventoryId, shelfId)
                .orElseThrow(() -> new ShelfInventoryNotFoundException(
                        "Inventory item not found with ID: " + inventoryId + " in shelf: " + shelfId));

        // Validate category/subcategory rules
        validateCategorySubcategory(request.getCategory(), request.getSubcategory());

        ShelfInventoryMapper.updateEntityFromDTO(inventory, request);
        ShelfInventory updatedInventory = shelfInventoryRepository.save(inventory);

        return ShelfInventoryMapper.toDTO(updatedInventory);
    }

    @Transactional
    public void deleteInventory(@NonNull UUID shelfId, @NonNull UUID inventoryId) {
        ShelfInventory inventory = shelfInventoryRepository.findByIdAndShelfId(inventoryId, shelfId)
                .orElseThrow(() -> new ShelfInventoryNotFoundException(
                        "Inventory item not found with ID: " + inventoryId + " in shelf: " + shelfId));

        shelfInventoryRepository.delete(inventory);
    }

    private void validateCategorySubcategory(ProductCategory category, com.pm.inventoryservice.models.enums.ProductSubcategory subcategory) {
        if (category == ProductCategory.BLIND_BOX && subcategory == null) {
            throw new InvalidInventoryOperationException("Subcategory is required for Blind Box items");
        }
        if (category != ProductCategory.BLIND_BOX && subcategory != null) {
            throw new InvalidInventoryOperationException("Subcategory should only be set for Blind Box items");
        }
    }
}

