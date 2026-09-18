package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.inventory.api.ProductInventoryEntryDTO;
import com.mirai.inventoryservice.inventory.api.ProductInventoryResponseDTO;
import com.mirai.inventoryservice.catalog.application.CatalogQueries;
import com.mirai.inventoryservice.catalog.application.ProductRef;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Service for fetching aggregated inventory data across all location types.
 * Uses the unified location_inventory table for optimal performance.
 */
@Service
public class InventoryAggregateService {

    private final CatalogQueries catalogQueries;
    private final LocationInventoryRepository locationInventoryRepository;

    public InventoryAggregateService(
            CatalogQueries catalogQueries,
            LocationInventoryRepository locationInventoryRepository) {
        this.catalogQueries = catalogQueries;
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
        ProductRef product = catalogQueries.getById(productId);

        List<LocationInventory> inventories = locationInventoryRepository.findByProduct_Id(productId);

        List<ProductInventoryEntryDTO> entries = inventories.stream()
                .map(this::mapToEntryDTO)
                .sorted(Comparator.comparing(ProductInventoryEntryDTO::getLocationLabel, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int totalQuantity = entries.stream()
                .mapToInt(ProductInventoryEntryDTO::getQuantity)
                .sum();

        return ProductInventoryResponseDTO.builder()
                .productId(product.id())
                .productSku(product.sku())
                .productName(product.name())
                .totalQuantity(totalQuantity)
                .entries(entries)
                .build();
    }

    /**
     * Site-scoped counterpart to {@link #getInventoryByProduct} (.specs/phase-6-inventory 6c,
     * T-6c-11): only inventory rows at {@code siteId}, for the v1
     * {@code GET .../inventory/products/{productId}} route. {@code entries} is empty (not a 404)
     * for a product with no inventory rows at this site -- absence-of-stock is not
     * absence-of-product (matches the zero-stock contract F-6c-3 established for totals).
     */
    public ProductInventoryResponseDTO getInventoryByProductAndSite(UUID siteId, UUID productId) {
        ProductRef product = catalogQueries.getById(productId);

        List<LocationInventory> inventories = locationInventoryRepository.findByProduct_IdAndSite_Id(productId, siteId);

        List<ProductInventoryEntryDTO> entries = inventories.stream()
                .map(this::mapToEntryDTO)
                .sorted(Comparator.comparing(ProductInventoryEntryDTO::getLocationLabel, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int totalQuantity = entries.stream()
                .mapToInt(ProductInventoryEntryDTO::getQuantity)
                .sum();

        return ProductInventoryResponseDTO.builder()
                .productId(product.id())
                .productSku(product.sku())
                .productName(product.name())
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
     * Batch delete all inventory records for multiple products across all locations.
     * Optimized to prevent N+1 queries when deleting parent products with children.
     */
    @Transactional
    public void deleteAllInventoryForProducts(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        locationInventoryRepository.deleteAllByProductIdIn(productIds);
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
