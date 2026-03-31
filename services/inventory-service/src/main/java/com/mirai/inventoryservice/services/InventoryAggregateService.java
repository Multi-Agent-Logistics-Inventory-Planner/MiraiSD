package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.responses.ProductInventoryEntryDTO;
import com.mirai.inventoryservice.dtos.responses.ProductInventoryResponseDTO;
import com.mirai.inventoryservice.exceptions.ProductNotFoundException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.inventory.LocationInventory;
import com.mirai.inventoryservice.repositories.LocationInventoryRepository;
import com.mirai.inventoryservice.repositories.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Service for fetching aggregated inventory data across all location types.
 * Uses the unified location_inventory table for optimal performance.
 */
@Service
public class InventoryAggregateService {

    private final ProductRepository productRepository;
    private final LocationInventoryRepository locationInventoryRepository;

    public InventoryAggregateService(
            ProductRepository productRepository,
            LocationInventoryRepository locationInventoryRepository) {
        this.productRepository = productRepository;
        this.locationInventoryRepository = locationInventoryRepository;
    }

    /**
     * Get all inventory entries for a specific product across all location types.
     * Uses unified location_inventory table for single-query performance.
     *
     * @param productId The product ID to look up
     * @return ProductInventoryResponseDTO containing all inventory entries
     */
    public ProductInventoryResponseDTO getInventoryByProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));

        List<LocationInventory> inventories = locationInventoryRepository.findByProduct_Id(productId);

        List<ProductInventoryEntryDTO> entries = inventories.stream()
                .map(this::mapToEntryDTO)
                .sorted(Comparator.comparing(ProductInventoryEntryDTO::getLocationLabel, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int totalQuantity = entries.stream()
                .mapToInt(ProductInventoryEntryDTO::getQuantity)
                .sum();

        return ProductInventoryResponseDTO.builder()
                .productId(product.getId())
                .productSku(product.getSku())
                .productName(product.getName())
                .totalQuantity(totalQuantity)
                .entries(entries)
                .build();
    }

    /**
     * Delete all inventory records for a product across all locations.
     * Required before deleting a product to avoid FK constraint violations.
     */
    @Transactional
    public void deleteAllInventoryForProduct(UUID productId) {
        locationInventoryRepository.deleteByProduct_Id(productId);
    }

    /**
     * Maps a LocationInventory entity to ProductInventoryEntryDTO.
     * Uses storage location code for backward compatibility with LocationType enum values.
     */
    private ProductInventoryEntryDTO mapToEntryDTO(LocationInventory inv) {
        String storageLocationCode = inv.getLocation().getStorageLocation().getCode();
        String storageLocationName = inv.getLocation().getStorageLocation().getName();
        String locationCode = inv.getLocation().getLocationCode();

        // Map storage location code to LocationType name for backward compatibility
        String locationType = mapStorageLocationCodeToLocationType(storageLocationCode);

        return ProductInventoryEntryDTO.builder()
                .inventoryId(inv.getId())
                .locationType(locationType)
                .locationId(inv.getLocation().getId())
                .locationCode(locationCode)
                .locationLabel(storageLocationName + " " + locationCode)
                .quantity(inv.getQuantity())
                .updatedAt(inv.getUpdatedAt())
                .build();
    }

    /**
     * Maps storage location code to LocationType enum name for backward compatibility.
     */
    private String mapStorageLocationCodeToLocationType(String storageLocationCode) {
        return switch (storageLocationCode) {
            case "BOX_BINS" -> "BOX_BIN";
            case "RACKS" -> "RACK";
            case "CABINETS" -> "CABINET";
            case "SHELVES" -> "SHELF";
            case "WINDOWS" -> "WINDOW";
            case "SINGLE_CLAW" -> "SINGLE_CLAW_MACHINE";
            case "DOUBLE_CLAW" -> "DOUBLE_CLAW_MACHINE";
            case "FOUR_CORNER" -> "FOUR_CORNER_MACHINE";
            case "PUSHER" -> "PUSHER_MACHINE";
            case "GACHAPON" -> "GACHAPON";
            case "KEYCHAIN" -> "KEYCHAIN_MACHINE";
            case "NOT_ASSIGNED" -> "NOT_ASSIGNED";
            default -> storageLocationCode; // Pass through for new location types
        };
    }
}
