package com.mirai.inventoryservice.sites.application;

import com.mirai.inventoryservice.sites.domain.DuplicateLocationCodeException;
import com.mirai.inventoryservice.sites.domain.LocationNotFoundException;
import com.mirai.inventoryservice.sites.domain.SiteNotFoundException;
import com.mirai.inventoryservice.sites.domain.StorageLocationNotFoundException;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.sites.infrastructure.LocationRepository;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import com.mirai.inventoryservice.sites.infrastructure.StorageLocationRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Unified service for managing locations across all storage location types.
 * Replaces the individual location services (BoxBinService, RackService, etc.)
 */
@Service
@Transactional
public class LocationService {
    private final LocationRepository locationRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final SiteRepository siteRepository;

    private static final String DEFAULT_SITE_CODE = "MAIN";

    public LocationService(
            LocationRepository locationRepository,
            StorageLocationRepository storageLocationRepository,
            SiteRepository siteRepository) {
        this.locationRepository = locationRepository;
        this.storageLocationRepository = storageLocationRepository;
        this.siteRepository = siteRepository;
    }

    /**
     * Create a new location within a storage location type.
     *
     * @param storageLocationCode The storage location code (e.g., "BOX_BINS", "RACKS")
     * @param locationCode The location code (e.g., "B1", "R1")
     * @return The created Location
     */
    public Location createLocation(String storageLocationCode, String locationCode) {
        StorageLocation storageLocation = getStorageLocationByCode(storageLocationCode);

        if (locationRepository.existsByLocationCodeAndStorageLocation_Id(locationCode, storageLocation.getId())) {
            throw new DuplicateLocationCodeException(
                    "Location with code '" + locationCode + "' already exists in " + storageLocation.getName());
        }

        Location location = Location.builder()
                .storageLocation(storageLocation)
                .locationCode(locationCode)
                .build();

        return locationRepository.save(location);
    }

    /**
     * Create a new location within a storage location by ID.
     */
    public Location createLocation(UUID storageLocationId, String locationCode) {
        StorageLocation storageLocation = storageLocationRepository.findById(storageLocationId)
                .orElseThrow(() -> new StorageLocationNotFoundException(
                        "Storage location not found: " + storageLocationId));

        if (locationRepository.existsByLocationCodeAndStorageLocation_Id(locationCode, storageLocationId)) {
            throw new DuplicateLocationCodeException(
                    "Location with code '" + locationCode + "' already exists in " + storageLocation.getName());
        }

        Location location = Location.builder()
                .storageLocation(storageLocation)
                .locationCode(locationCode)
                .build();

        return locationRepository.save(location);
    }

    /**
     * Create a new location within a storage location by ID, scoped to the given site - the
     * storage location must belong to that site.
     */
    public Location createLocation(UUID siteId, UUID storageLocationId, String locationCode) {
        StorageLocation storageLocation = storageLocationRepository.findByIdAndSite_Id(storageLocationId, siteId)
                .orElseThrow(() -> new StorageLocationNotFoundException(
                        "Storage location not found: " + storageLocationId));

        if (locationRepository.existsByLocationCodeAndStorageLocation_Id(locationCode, storageLocationId)) {
            throw new DuplicateLocationCodeException(
                    "Location with code '" + locationCode + "' already exists in " + storageLocation.getName());
        }

        Location location = Location.builder()
                .storageLocation(storageLocation)
                .locationCode(locationCode)
                .build();

        return locationRepository.save(location);
    }

    /**
     * Get a location by ID. No site check - kept only for the legacy, unscoped
     * {@code /api/locations} routes; do not add new callers.
     */
    public Location getLocationById(UUID id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new LocationNotFoundException("Location not found with id: " + id));
    }

    /**
     * Get a location by ID, scoped to the given site - 404s if the location exists but belongs
     * to a different site.
     */
    public Location getLocationById(UUID siteId, UUID id) {
        return locationRepository.findByIdAndSite_Id(id, siteId)
                .orElseThrow(() -> new LocationNotFoundException("Location not found with id: " + id));
    }

    /**
     * Get a location by code within a storage location type.
     */
    public Location getLocationByCode(String storageLocationCode, String locationCode) {
        UUID siteId = getDefaultSiteId();
        return locationRepository.findByLocationCodeAndStorageLocationCodeAndSiteId(
                        locationCode, storageLocationCode, siteId)
                .orElseThrow(() -> new LocationNotFoundException(
                        "Location not found: " + storageLocationCode + ":" + locationCode));
    }

    /**
     * Get all locations. No site check - kept only for the legacy, unscoped
     * {@code /api/locations} routes; do not add new callers.
     */
    public List<Location> getAllLocations() {
        UUID siteId = getDefaultSiteId();
        return locationRepository.findBySite_Id(siteId);
    }

    /** Get all locations for the given site. */
    public List<Location> getAllLocations(UUID siteId) {
        return locationRepository.findBySite_Id(siteId);
    }

    /**
     * Get all locations within a storage location type. No site check - kept only for the
     * legacy, unscoped {@code /api/locations} routes; do not add new callers.
     */
    public List<Location> getLocationsByStorageLocation(UUID storageLocationId) {
        return locationRepository.findByStorageLocation_Id(storageLocationId);
    }

    /**
     * Get all locations within a storage location type, scoped to the given site - 404s if the
     * storage location isn't in this site.
     */
    public List<Location> getLocationsByStorageLocation(UUID siteId, UUID storageLocationId) {
        if (!storageLocationRepository.existsByIdAndSite_Id(storageLocationId, siteId)) {
            throw new StorageLocationNotFoundException("Storage location not found: " + storageLocationId);
        }
        return locationRepository.findByStorageLocation_Id(storageLocationId);
    }

    /**
     * Get all locations within a storage location type by code. No site check - kept only for
     * the legacy, unscoped {@code /api/locations} routes; do not add new callers.
     */
    public List<Location> getLocationsByStorageLocationCode(String storageLocationCode) {
        UUID siteId = getDefaultSiteId();
        return locationRepository.findByStorageLocationCodeAndSiteId(storageLocationCode, siteId);
    }

    /** Get all locations within a storage location type by code, scoped to the given site. */
    public List<Location> getLocationsByStorageLocationCode(UUID siteId, String storageLocationCode) {
        return locationRepository.findByStorageLocationCodeAndSiteId(storageLocationCode, siteId);
    }

    /**
     * Update a location's code. No site check - kept only for the legacy, unscoped
     * {@code /api/locations} routes; do not add new callers.
     */
    public Location updateLocation(UUID id, String locationCode) {
        Location location = getLocationById(id);
        return applyLocationCodeUpdate(location, locationCode);
    }

    /** Update a location's code, scoped to the given site. */
    public Location updateLocation(UUID siteId, UUID id, String locationCode) {
        Location location = getLocationById(siteId, id);
        return applyLocationCodeUpdate(location, locationCode);
    }

    private Location applyLocationCodeUpdate(Location location, String locationCode) {
        if (!locationCode.equals(location.getLocationCode()) &&
                locationRepository.existsByLocationCodeAndStorageLocation_Id(
                        locationCode, location.getStorageLocation().getId())) {
            throw new DuplicateLocationCodeException(
                    "Location with code '" + locationCode + "' already exists in " +
                            location.getStorageLocation().getName());
        }

        location.setLocationCode(locationCode);
        return locationRepository.save(location);
    }

    /**
     * Delete a location. No site check - kept only for the legacy, unscoped
     * {@code /api/locations} routes; do not add new callers.
     */
    public void deleteLocation(UUID id) {
        Location location = getLocationById(id);
        locationRepository.delete(location);
    }

    /** Delete a location, scoped to the given site. */
    public void deleteLocation(UUID siteId, UUID id) {
        Location location = getLocationById(siteId, id);
        locationRepository.delete(location);
    }

    /**
     * Check if a location exists by code within a storage location type.
     */
    public boolean existsByCode(String storageLocationCode, String locationCode) {
        StorageLocation storageLocation = getStorageLocationByCode(storageLocationCode);
        return locationRepository.existsByLocationCodeAndStorageLocation_Id(
                locationCode, storageLocation.getId());
    }

    // ========= Storage Location Queries =========
    // Note: Storage location types are fixed and seeded automatically.
    // Use DevSeedController.seedCoreEntities() to create all standard types.

    /**
     * Get all storage locations for the default site. No site check - kept only for the legacy,
     * unscoped {@code /api/storage-locations} routes; do not add new callers.
     */
    public List<StorageLocation> getAllStorageLocations() {
        return storageLocationRepository.findBySite_CodeOrderByDisplayOrder(DEFAULT_SITE_CODE);
    }

    /** Get all storage locations for the given site. */
    public List<StorageLocation> getAllStorageLocations(UUID siteId) {
        return storageLocationRepository.findBySite_IdOrderByDisplayOrder(siteId);
    }

    /**
     * Get a storage location by code. No site check - kept only for the legacy, unscoped
     * {@code /api/storage-locations} routes; do not add new callers.
     */
    public StorageLocation getStorageLocationByCode(String code) {
        return storageLocationRepository.findByCodeAndSite_Code(code, DEFAULT_SITE_CODE)
                .orElseThrow(() -> new StorageLocationNotFoundException(
                        "Storage location not found: " + code));
    }

    /** Get a storage location by code, scoped to the given site. */
    public StorageLocation getStorageLocationByCode(UUID siteId, String code) {
        return storageLocationRepository.findByCodeAndSite_Id(code, siteId)
                .orElseThrow(() -> new StorageLocationNotFoundException(
                        "Storage location not found: " + code));
    }

    /**
     * Get a storage location by ID. No site check - kept only for the legacy, unscoped
     * {@code /api/storage-locations} routes; do not add new callers.
     */
    public StorageLocation getStorageLocationById(UUID id) {
        return storageLocationRepository.findById(id)
                .orElseThrow(() -> new StorageLocationNotFoundException(
                        "Storage location not found: " + id));
    }

    /**
     * Get a storage location by ID, scoped to the given site - 404s if it exists but belongs to
     * a different site.
     */
    public StorageLocation getStorageLocationById(UUID siteId, UUID id) {
        return storageLocationRepository.findByIdAndSite_Id(id, siteId)
                .orElseThrow(() -> new StorageLocationNotFoundException(
                        "Storage location not found: " + id));
    }

    /**
     * Get storage locations that support inventory (not display-only). No site check - kept
     * only for the legacy, unscoped {@code /api/storage-locations} routes; do not add new
     * callers.
     */
    public List<StorageLocation> getInventoryStorageLocations() {
        UUID siteId = getDefaultSiteId();
        return storageLocationRepository.findInventoryLocationsBySite_Id(siteId);
    }

    /** Get storage locations that support inventory (not display-only) for the given site. */
    public List<StorageLocation> getInventoryStorageLocations(UUID siteId) {
        return storageLocationRepository.findInventoryLocationsBySite_Id(siteId);
    }

    /**
     * Get storage locations that support display tracking. No site check - kept only for the
     * legacy, unscoped {@code /api/storage-locations} routes; do not add new callers.
     */
    public List<StorageLocation> getDisplayStorageLocations() {
        UUID siteId = getDefaultSiteId();
        return storageLocationRepository.findDisplayLocationsBySite_Id(siteId);
    }

    /** Get storage locations that support display tracking for the given site. */
    public List<StorageLocation> getDisplayStorageLocations(UUID siteId) {
        return storageLocationRepository.findDisplayLocationsBySite_Id(siteId);
    }

    // ========= Helper Methods =========

    private UUID getDefaultSiteId() {
        return siteRepository.findByCode(DEFAULT_SITE_CODE)
                .orElseThrow(() -> new SiteNotFoundException("Default site not found: " + DEFAULT_SITE_CODE))
                .getId();
    }
}
