package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.*;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.Site;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.inventory.LocationInventory;
import com.mirai.inventoryservice.models.storage.Location;
import com.mirai.inventoryservice.models.storage.StorageLocation;
import com.mirai.inventoryservice.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Unified service for managing inventory across all storage location types.
 * Replaces the 9 individual inventory services (BoxBinInventoryService, etc.)
 */
@Service
@Transactional
public class LocationInventoryService {
    private final LocationInventoryRepository locationInventoryRepository;
    private final LocationRepository locationRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final SiteRepository siteRepository;
    private final ProductRepository productRepository;
    private final StockMovementService stockMovementService;

    private static final String DEFAULT_SITE_CODE = "MAIN";

    public LocationInventoryService(
            LocationInventoryRepository locationInventoryRepository,
            LocationRepository locationRepository,
            StorageLocationRepository storageLocationRepository,
            SiteRepository siteRepository,
            ProductRepository productRepository,
            StockMovementService stockMovementService) {
        this.locationInventoryRepository = locationInventoryRepository;
        this.locationRepository = locationRepository;
        this.storageLocationRepository = storageLocationRepository;
        this.siteRepository = siteRepository;
        this.productRepository = productRepository;
        this.stockMovementService = stockMovementService;
    }

    /**
     * Add inventory to a location
     *
     * @param locationId The location UUID (preserved from old location tables)
     * @param productId The product UUID
     * @param quantity The quantity to add
     * @param actorId The actor performing the action
     * @param reason The reason for adding inventory
     * @return The created LocationInventory
     */
    public LocationInventory addInventory(UUID locationId, UUID productId, Integer quantity,
                                          UUID actorId, StockMovementReason reason) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new LocationNotFoundException("Location not found: " + locationId));

        // Check if storage location allows inventory
        if (location.getStorageLocation().getIsDisplayOnly()) {
            throw new InvalidInventoryOperationException(
                    location.getStorageLocation().getName() + " is display-only and does not support inventory");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));

        // Check if inventory already exists at this location for this product
        Optional<LocationInventory> existing = locationInventoryRepository
                .findByLocation_IdAndProduct_Id(locationId, productId);
        if (existing.isPresent()) {
            throw new InvalidInventoryOperationException(
                    "Inventory for product " + product.getSku() + " already exists at this location");
        }

        StockMovementReason effectiveReason = reason != null ? reason : StockMovementReason.INITIAL_STOCK;

        // Derive LocationType from storage location code for backward compatibility
        LocationType locationType = mapStorageLocationCodeToLocationType(
                location.getStorageLocation().getCode());

        UUID inventoryId = stockMovementService.createInventoryWithTracking(
                locationType, locationId, product, quantity,
                effectiveReason, actorId, null);

        return locationInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new InventoryNotFoundException("Failed to create inventory"));
    }

    /**
     * Get inventory by ID
     */
    public LocationInventory getInventoryById(UUID inventoryId) {
        return locationInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new InventoryNotFoundException(
                        "Inventory not found with id: " + inventoryId));
    }

    /**
     * List all inventory at a specific location
     */
    public List<LocationInventory> listInventoryAtLocation(UUID locationId) {
        locationRepository.findById(locationId)
                .orElseThrow(() -> new LocationNotFoundException("Location not found: " + locationId));
        return locationInventoryRepository.findByLocation_Id(locationId);
    }

    /**
     * List all inventory for a specific storage location type (e.g., all box bins)
     */
    public List<LocationInventory> listInventoryByStorageLocation(UUID storageLocationId) {
        storageLocationRepository.findById(storageLocationId)
                .orElseThrow(() -> new StorageLocationNotFoundException(
                        "Storage location not found: " + storageLocationId));
        return locationInventoryRepository.findByStorageLocation_Id(storageLocationId);
    }

    /**
     * List all inventory by storage location code (e.g., "BOX_BINS")
     */
    public List<LocationInventory> listInventoryByStorageLocationCode(String storageLocationCode) {
        UUID siteId = getDefaultSiteId();
        return locationInventoryRepository.findByStorageLocationCodeAndSiteId(storageLocationCode, siteId);
    }

    /**
     * Find all inventory for a specific product
     */
    public List<LocationInventory> findByProduct(UUID productId) {
        return locationInventoryRepository.findByProduct_Id(productId);
    }

    /**
     * Find inventory for a product at a specific location
     */
    public Optional<LocationInventory> findByLocationAndProduct(UUID locationId, UUID productId) {
        return locationInventoryRepository.findByLocation_IdAndProduct_Id(locationId, productId);
    }

    /**
     * Update inventory quantity directly (without tracking)
     * Use with caution - prefer adjustInventory for tracked changes
     */
    public LocationInventory updateInventoryQuantity(UUID inventoryId, Integer quantity) {
        LocationInventory inventory = getInventoryById(inventoryId);
        inventory.setQuantity(quantity);
        return locationInventoryRepository.save(inventory);
    }

    /**
     * Delete inventory with tracking
     */
    public void deleteInventory(UUID inventoryId, UUID actorId, StockMovementReason reason) {
        LocationInventory inventory = getInventoryById(inventoryId);

        LocationType locationType = mapStorageLocationCodeToLocationType(
                inventory.getLocation().getStorageLocation().getCode());

        StockMovementReason effectiveReason = reason != null ? reason : StockMovementReason.REMOVED;

        stockMovementService.removeInventoryWithTracking(
                locationType, inventoryId,
                effectiveReason, actorId, null);
    }

    /**
     * Get total quantity for a product across all locations
     */
    public int getTotalQuantityForProduct(UUID productId) {
        Integer total = locationInventoryRepository.sumQuantityByProductId(productId);
        return total != null ? total : 0;
    }

    /**
     * Get total quantity for a product at a specific site
     */
    public int getTotalQuantityForProductAtSite(UUID productId, UUID siteId) {
        Integer total = locationInventoryRepository.sumQuantityByProductIdAndSiteId(productId, siteId);
        return total != null ? total : 0;
    }

    // ========= Storage Location Management =========

    /**
     * Get all storage locations for the default site, ordered by display order
     */
    public List<StorageLocation> getStorageLocations() {
        return storageLocationRepository.findBySite_CodeOrderByDisplayOrder(DEFAULT_SITE_CODE);
    }

    /**
     * Get a storage location by code
     */
    public StorageLocation getStorageLocationByCode(String code) {
        return storageLocationRepository.findByCodeAndSite_Code(code, DEFAULT_SITE_CODE)
                .orElseThrow(() -> new StorageLocationNotFoundException(
                        "Storage location not found: " + code));
    }

    /**
     * Get all locations within a storage location type
     */
    public List<Location> getLocationsForStorageLocation(UUID storageLocationId) {
        return locationRepository.findByStorageLocation_Id(storageLocationId);
    }

    /**
     * Get a specific location by code within a storage location type
     */
    public Location getLocationByCode(String storageLocationCode, String locationCode) {
        UUID siteId = getDefaultSiteId();
        return locationRepository.findByLocationCodeAndStorageLocationCodeAndSiteId(
                        locationCode, storageLocationCode, siteId)
                .orElseThrow(() -> new LocationNotFoundException(
                        "Location not found: " + storageLocationCode + ":" + locationCode));
    }

    // ========= Helper Methods =========

    private UUID getDefaultSiteId() {
        return siteRepository.findByCode(DEFAULT_SITE_CODE)
                .orElseThrow(() -> new SiteNotFoundException("Default site not found: " + DEFAULT_SITE_CODE))
                .getId();
    }

    /**
     * Maps storage location code to LocationType enum for backward compatibility.
     */
    private LocationType mapStorageLocationCodeToLocationType(String storageLocationCode) {
        return switch (storageLocationCode) {
            case "BOX_BINS" -> LocationType.BOX_BIN;
            case "RACKS" -> LocationType.RACK;
            case "CABINETS" -> LocationType.CABINET;
            case "WINDOWS" -> LocationType.WINDOW;
            case "SINGLE_CLAW" -> LocationType.SINGLE_CLAW_MACHINE;
            case "DOUBLE_CLAW" -> LocationType.DOUBLE_CLAW_MACHINE;
            case "FOUR_CORNER" -> LocationType.FOUR_CORNER_MACHINE;
            case "PUSHER" -> LocationType.PUSHER_MACHINE;
            case "GACHAPON" -> LocationType.GACHAPON;
            case "KEYCHAIN" -> LocationType.KEYCHAIN_MACHINE;
            case "NOT_ASSIGNED" -> LocationType.NOT_ASSIGNED;
            default -> throw new IllegalArgumentException("Unknown storage location code: " + storageLocationCode);
        };
    }
}
