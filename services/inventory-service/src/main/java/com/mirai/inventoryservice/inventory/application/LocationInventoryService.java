package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.inventory.domain.InvalidInventoryOperationException;
import com.mirai.inventoryservice.inventory.domain.InventoryNotFoundException;
import com.mirai.inventoryservice.sites.domain.LocationNotFoundException;
import com.mirai.inventoryservice.sites.domain.StorageLocationNotFoundException;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.application.CatalogEntityAccess;
import com.mirai.inventoryservice.sites.application.LocationService;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.sites.infrastructure.LocationRepository;
import com.mirai.inventoryservice.sites.infrastructure.StorageLocationRepository;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
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
    private final LocationService locationService;
    private final CatalogEntityAccess catalogEntityAccess;
    private final StockMovementService stockMovementService;

    public LocationInventoryService(
            LocationInventoryRepository locationInventoryRepository,
            LocationRepository locationRepository,
            StorageLocationRepository storageLocationRepository,
            LocationService locationService,
            CatalogEntityAccess catalogEntityAccess,
            StockMovementService stockMovementService) {
        this.locationInventoryRepository = locationInventoryRepository;
        this.locationRepository = locationRepository;
        this.storageLocationRepository = storageLocationRepository;
        this.locationService = locationService;
        this.catalogEntityAccess = catalogEntityAccess;
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
        return addInventory(locationId, productId, quantity, actorId, reason, null, null);
    }

    /**
     * Site-scoped counterpart to {@link #addInventory(UUID, UUID, Integer, UUID, StockMovementReason, String, Integer)}
     * (.specs/phase-6-inventory 6d, T-6d-be-1, R-9/AC-3): validates {@code locationId} belongs to
     * {@code siteId} via {@link LocationService#getLocationById(UUID, UUID)} (404 for an
     * unknown/foreign-site location) before any write, then delegates to the existing un-scoped
     * method -- unchanged, so the legacy controller keeps working until 6e deletes it.
     * <p>
     * {@code actorId} MUST be the authenticated principal's backend user id
     * ({@code AuthorizedSiteContext.backendUserId()}), never a client-supplied value -- the v1
     * mutation route is the only caller and always passes this.
     */
    public LocationInventory addInventory(UUID siteId, UUID actorId, UUID locationId, UUID productId,
                                          Integer quantity, StockMovementReason reason,
                                          String intakeUnit, Integer intakeQty) {
        locationService.getLocationById(siteId, locationId);
        return addInventory(locationId, productId, quantity, actorId, reason, intakeUnit, intakeQty);
    }

    public LocationInventory addInventory(UUID locationId, UUID productId, Integer quantity,
                                          UUID actorId, StockMovementReason reason,
                                          String intakeUnit, Integer intakeQty) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new LocationNotFoundException("Location not found: " + locationId));

        // Check if storage location allows inventory
        if (location.getStorageLocation().getIsDisplayOnly()) {
            throw new InvalidInventoryOperationException(
                    location.getStorageLocation().getName() + " is display-only and does not support inventory");
        }

        Product product = catalogEntityAccess.requireManagedProduct(productId);

        stockMovementService.rejectIfCustomKujiParent(product);

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
                effectiveReason, actorId, null, intakeUnit, intakeQty);

        return locationInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new InventoryNotFoundException("Failed to create inventory"));
    }

    /**
     * Get inventory by ID. Public again (.specs/phase-6-inventory 6e, R-3 revert, 2026-09-15):
     * the legacy {@code LocationInventoryController} that calls this was restored after the
     * documented compatibility-removal gate (docs/baseline/api-v1-map.md) turned out to be
     * unsatisfiable this checkpoint -- see log.md's "Review-driven fix: 6e independent review
     * findings" section.
     */
    public LocationInventory getInventoryById(UUID inventoryId) {
        return locationInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new InventoryNotFoundException(
                        "Inventory not found with id: " + inventoryId));
    }

    /** Compatibility-read counterpart which never resolves an inventory row outside MAIN. */
    public LocationInventory getInventoryById(UUID siteId, UUID inventoryId) {
        return locationInventoryRepository.findByIdAndSite_Id(inventoryId, siteId)
                .orElseThrow(() -> new InventoryNotFoundException(
                        "Inventory not found with id: " + inventoryId));
    }

    /**
     * List all inventory at a specific location. Restored alongside {@link #getInventoryById}
     * (R-3 revert) -- see that method's Javadoc.
     */
    public List<LocationInventory> listInventoryAtLocation(UUID locationId) {
        locationRepository.findById(locationId)
                .orElseThrow(() -> new LocationNotFoundException("Location not found: " + locationId));
        return locationInventoryRepository.findByLocation_Id(locationId);
    }

    /** Site-qualified compatibility read for a location's rows. */
    public List<LocationInventory> listInventoryAtLocation(UUID siteId, UUID locationId) {
        locationService.getLocationById(siteId, locationId);
        return locationInventoryRepository.findByLocation_IdAndSite_Id(locationId, siteId);
    }

    /**
     * List all inventory for a specific storage location type (e.g., all box bins). Restored
     * alongside {@link #getInventoryById} (R-3 revert) -- see that method's Javadoc. Delegates
     * to {@link LocationInventoryRepository#findByStorageLocation_Id}, which now applies the
     * same kuji-child/CUSTOM-kuji-parent exclusion its {@code findByLocation_Id} sibling always
     * has -- the 6d-recorded "trap" (a caller reaching the unfiltered method) is closed by
     * fixing the query itself this time, not by leaving the trap for whichever caller reappears.
     */
    public List<LocationInventory> listInventoryByStorageLocation(UUID storageLocationId) {
        storageLocationRepository.findById(storageLocationId)
                .orElseThrow(() -> new StorageLocationNotFoundException(
                        "Storage location not found: " + storageLocationId));
        return locationInventoryRepository.findByStorageLocation_Id(storageLocationId);
    }

    /** Site-qualified compatibility read for a storage category's rows. */
    public List<LocationInventory> listInventoryByStorageLocation(UUID siteId, UUID storageLocationId) {
        storageLocationRepository.findByIdAndSite_Id(storageLocationId, siteId)
                .orElseThrow(() -> new StorageLocationNotFoundException(
                        "Storage location not found: " + storageLocationId));
        return locationInventoryRepository.findByStorageLocation_Id(storageLocationId);
    }

    /**
     * List all inventory by storage location code (e.g., "BOX_BINS")
     */
    public List<LocationInventory> listInventoryByStorageLocationCode(String storageLocationCode) {
        UUID siteId = locationService.getDefaultSiteId();
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
     * Site-scoped counterpart to {@link #deleteInventory(UUID, UUID, StockMovementReason)}
     * (.specs/phase-6-inventory 6d, T-6d-be-1, R-9/AC-3): resolves {@code inventoryId} through
     * {@link LocationInventoryRepository#findByIdAndSite_Id} (404 for an unknown/foreign-site
     * inventory row), then confirms it actually belongs to the path's {@code locationId} (400 on a
     * path/row mismatch) before delegating to the existing un-scoped method.
     * <p>
     * {@code actorId} MUST be the authenticated principal's backend user id -- see
     * {@link #addInventory(UUID, UUID, UUID, UUID, Integer, StockMovementReason, String, Integer)}.
     */
    public void deleteInventory(UUID siteId, UUID actorId, UUID locationId, UUID inventoryId,
                                 StockMovementReason reason) {
        LocationInventory inventory = locationInventoryRepository.findByIdAndSite_Id(inventoryId, siteId)
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found: " + inventoryId));
        if (!java.util.Objects.equals(inventory.getLocation().getId(), locationId)) {
            throw new InvalidInventoryOperationException(
                    "Inventory " + inventoryId + " does not belong to location " + locationId);
        }
        deleteInventory(inventoryId, actorId, reason);
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
        return storageLocationRepository.findBySite_CodeOrderByDisplayOrder(LocationService.DEFAULT_SITE_CODE);
    }

    /**
     * Get a storage location by code
     */
    public StorageLocation getStorageLocationByCode(String code) {
        return storageLocationRepository.findByCodeAndSite_Code(code, LocationService.DEFAULT_SITE_CODE)
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
        UUID siteId = locationService.getDefaultSiteId();
        return locationRepository.findByLocationCodeAndStorageLocationCodeAndSiteId(
                        locationCode, storageLocationCode, siteId)
                .orElseThrow(() -> new LocationNotFoundException(
                        "Location not found: " + storageLocationCode + ":" + locationCode));
    }

    // ========= Helper Methods =========

    /**
     * Maps storage location code to LocationType enum for backward compatibility.
     */
    private LocationType mapStorageLocationCodeToLocationType(String storageLocationCode) {
        return switch (storageLocationCode) {
            case "BOX_BINS" -> LocationType.BOX_BIN;
            case "RACKS" -> LocationType.RACK;
            case "CABINETS" -> LocationType.CABINET;
            case "SHELVES" -> LocationType.SHELF;
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
