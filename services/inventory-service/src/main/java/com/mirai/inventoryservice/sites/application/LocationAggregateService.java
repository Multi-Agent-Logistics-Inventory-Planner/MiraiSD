package com.mirai.inventoryservice.sites.application;

import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.sites.api.LocationWithCountsDTO;
import com.mirai.inventoryservice.sites.infrastructure.LocationAggregateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for fetching aggregated location data with inventory counts.
 * Provides optimized endpoints to reduce N+1 query problems.
 */
@Service
@Transactional(readOnly = true)
public class LocationAggregateService {

    private final LocationAggregateRepository locationAggregateRepository;
    private final LocationService locationService;

    public LocationAggregateService(
            LocationAggregateRepository locationAggregateRepository,
            LocationService locationService) {
        this.locationAggregateRepository = locationAggregateRepository;
        this.locationService = locationService;
    }

    /**
     * Get all locations across all types with their inventory counts, for the caller's
     * default site.
     *
     * <p>(.specs/phase-6-inventory 6e independent review, B-1, 2026-09-15) Previously called
     * the genuinely site-blind {@code findAllLocationsWithCounts()} directly, mixing every
     * site's locations/quantities into one response -- a live cross-site data leak, not merely
     * a missing scope. Now resolves the default site (same pattern
     * {@link com.mirai.inventoryservice.inventory.application.LocationInventoryService
     * #listInventoryByStorageLocationCode} already uses) and delegates to the site-scoped
     * overload, closing the leak while keeping this route's response shape unchanged.
     *
     * @deprecated site-blind in name only now; prefer {@link #getAllLocationsWithCounts(UUID)}
     * with an explicit site once a caller needs anything other than the default site.
     */
    @Deprecated
    public List<LocationWithCountsDTO> getAllLocationsWithCounts() {
        return getAllLocationsWithCounts(locationService.getDefaultSiteId());
    }

    /**
     * Get locations of a specific type with their inventory counts, for the caller's default
     * site. See {@link #getAllLocationsWithCounts()}'s Javadoc for why this delegates to the
     * site-scoped overload rather than calling the repository directly.
     *
     * @param locationType The type of location to filter by (e.g., BOX_BIN, RACK)
     * @deprecated site-blind in name only now; prefer {@link #getLocationsByTypeWithCounts(LocationType, UUID)}.
     */
    @Deprecated
    public List<LocationWithCountsDTO> getLocationsByTypeWithCounts(LocationType locationType) {
        return getLocationsByTypeWithCounts(locationType, locationService.getDefaultSiteId());
    }

    /**
     * Site-scoped: get all locations across all types with their inventory counts, for
     * {@code siteId} only (.specs/phase-6-inventory 6e, T-6e-be-2/3).
     */
    public List<LocationWithCountsDTO> getAllLocationsWithCounts(UUID siteId) {
        return locationAggregateRepository.findAllLocationsWithCounts(siteId);
    }

    /**
     * Site-scoped: get locations of a specific type with their inventory counts, for
     * {@code siteId} only (.specs/phase-6-inventory 6e, T-6e-be-2/3).
     *
     * @param locationType The type of location to filter by (e.g., BOX_BIN, RACK)
     */
    public List<LocationWithCountsDTO> getLocationsByTypeWithCounts(LocationType locationType, UUID siteId) {
        if (locationType == LocationType.NOT_ASSIGNED) {
            return List.of();
        }
        String storageLocationCode = mapLocationTypeToStorageCode(locationType);
        return locationAggregateRepository.findLocationsByTypeWithCounts(storageLocationCode, siteId);
    }

    /**
     * Maps LocationType enum values to storage_locations.code values.
     * The storage location codes use slightly different naming conventions.
     */
    private String mapLocationTypeToStorageCode(LocationType locationType) {
        return switch (locationType) {
            case BOX_BIN -> "BOX_BINS";
            case RACK -> "RACKS";
            case CABINET -> "CABINETS";
            case SHELF -> "SHELVES";
            case WINDOW -> "WINDOWS";
            case SINGLE_CLAW_MACHINE -> "SINGLE_CLAW";
            case DOUBLE_CLAW_MACHINE -> "DOUBLE_CLAW";
            case FOUR_CORNER_MACHINE -> "FOUR_CORNER";
            case PUSHER_MACHINE -> "PUSHER";
            case GACHAPON -> "GACHAPON";
            case KEYCHAIN_MACHINE -> "KEYCHAIN";
            case NOT_ASSIGNED -> "NOT_ASSIGNED";
        };
    }
}
