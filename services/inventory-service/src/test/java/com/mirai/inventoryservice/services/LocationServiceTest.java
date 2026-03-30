package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.DuplicateLocationCodeException;
import com.mirai.inventoryservice.exceptions.LocationNotFoundException;
import com.mirai.inventoryservice.exceptions.StorageLocationNotFoundException;
import com.mirai.inventoryservice.models.Site;
import com.mirai.inventoryservice.models.storage.Location;
import com.mirai.inventoryservice.models.storage.StorageLocation;
import com.mirai.inventoryservice.repositories.LocationRepository;
import com.mirai.inventoryservice.repositories.SiteRepository;
import com.mirai.inventoryservice.repositories.StorageLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LocationService.
 * Tests the unified location service that handles all storage location types.
 */
@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private StorageLocationRepository storageLocationRepository;

    @Mock
    private SiteRepository siteRepository;

    @InjectMocks
    private LocationService locationService;

    private Site testSite;
    private StorageLocation testStorageLocation;
    private Location testLocation;
    private UUID siteId;
    private UUID storageLocationId;
    private UUID locationId;

    @BeforeEach
    void setUp() {
        siteId = UUID.randomUUID();
        storageLocationId = UUID.randomUUID();
        locationId = UUID.randomUUID();

        testSite = Site.builder()
                .id(siteId)
                .code("MAIN")
                .name("Main Warehouse")
                .build();

        testStorageLocation = StorageLocation.builder()
                .id(storageLocationId)
                .site(testSite)
                .code("BOX_BINS")
                .name("Box Bins")
                .isDisplayOnly(false)
                .hasDisplay(false)
                .build();

        testLocation = Location.builder()
                .id(locationId)
                .storageLocation(testStorageLocation)
                .locationCode("B1")
                .build();
    }

    @Nested
    @DisplayName("createLocation")
    class CreateLocationTests {

        @Test
        @DisplayName("should create location with valid code")
        void shouldCreateLocationWithValidCode() {
            when(storageLocationRepository.findByCodeAndSite_Code("BOX_BINS", "MAIN"))
                    .thenReturn(Optional.of(testStorageLocation));
            when(locationRepository.existsByLocationCodeAndStorageLocation_Id("B2", storageLocationId))
                    .thenReturn(false);
            when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> {
                Location loc = invocation.getArgument(0);
                loc.setId(UUID.randomUUID());
                return loc;
            });

            Location result = locationService.createLocation("BOX_BINS", "B2");

            assertNotNull(result);
            assertEquals("B2", result.getLocationCode());
            verify(locationRepository).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw DuplicateLocationCodeException when code exists")
        void shouldThrowExceptionWhenCodeExists() {
            when(storageLocationRepository.findByCodeAndSite_Code("BOX_BINS", "MAIN"))
                    .thenReturn(Optional.of(testStorageLocation));
            when(locationRepository.existsByLocationCodeAndStorageLocation_Id("B1", storageLocationId))
                    .thenReturn(true);

            assertThrows(DuplicateLocationCodeException.class, () ->
                    locationService.createLocation("BOX_BINS", "B1"));
        }

        @Test
        @DisplayName("should throw StorageLocationNotFoundException when storage location not found")
        void shouldThrowExceptionWhenStorageLocationNotFound() {
            when(storageLocationRepository.findByCodeAndSite_Code("INVALID", "MAIN"))
                    .thenReturn(Optional.empty());

            assertThrows(StorageLocationNotFoundException.class, () ->
                    locationService.createLocation("INVALID", "X1"));
        }
    }

    @Nested
    @DisplayName("getLocationById")
    class GetLocationByIdTests {

        @Test
        @DisplayName("should return location when found")
        void shouldReturnLocationWhenFound() {
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));

            Location result = locationService.getLocationById(locationId);

            assertEquals(testLocation, result);
        }

        @Test
        @DisplayName("should throw LocationNotFoundException when not found")
        void shouldThrowExceptionWhenNotFound() {
            when(locationRepository.findById(locationId)).thenReturn(Optional.empty());

            assertThrows(LocationNotFoundException.class, () ->
                    locationService.getLocationById(locationId));
        }
    }

    @Nested
    @DisplayName("getLocationByCode")
    class GetLocationByCodeTests {

        @Test
        @DisplayName("should return location when found")
        void shouldReturnLocationWhenFound() {
            when(siteRepository.findByCode("MAIN")).thenReturn(Optional.of(testSite));
            when(locationRepository.findByLocationCodeAndStorageLocationCodeAndSiteId("B1", "BOX_BINS", siteId))
                    .thenReturn(Optional.of(testLocation));

            Location result = locationService.getLocationByCode("BOX_BINS", "B1");

            assertEquals(testLocation, result);
        }

        @Test
        @DisplayName("should throw LocationNotFoundException when not found")
        void shouldThrowExceptionWhenNotFound() {
            when(siteRepository.findByCode("MAIN")).thenReturn(Optional.of(testSite));
            when(locationRepository.findByLocationCodeAndStorageLocationCodeAndSiteId("X1", "BOX_BINS", siteId))
                    .thenReturn(Optional.empty());

            assertThrows(LocationNotFoundException.class, () ->
                    locationService.getLocationByCode("BOX_BINS", "X1"));
        }
    }

    @Nested
    @DisplayName("getLocationsByStorageLocation")
    class GetLocationsByStorageLocationTests {

        @Test
        @DisplayName("should return all locations for storage location")
        void shouldReturnAllLocationsForStorageLocation() {
            Location location2 = Location.builder()
                    .id(UUID.randomUUID())
                    .storageLocation(testStorageLocation)
                    .locationCode("B2")
                    .build();

            when(locationRepository.findByStorageLocation_Id(storageLocationId))
                    .thenReturn(List.of(testLocation, location2));

            List<Location> result = locationService.getLocationsByStorageLocation(storageLocationId);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("should return empty list when no locations exist")
        void shouldReturnEmptyListWhenNoLocations() {
            when(locationRepository.findByStorageLocation_Id(storageLocationId))
                    .thenReturn(List.of());

            List<Location> result = locationService.getLocationsByStorageLocation(storageLocationId);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("updateLocation")
    class UpdateLocationTests {

        @Test
        @DisplayName("should update location code")
        void shouldUpdateLocationCode() {
            Location updatedLocation = Location.builder()
                    .id(locationId)
                    .storageLocation(testStorageLocation)
                    .locationCode("B99")
                    .build();

            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationRepository.existsByLocationCodeAndStorageLocation_Id("B99", storageLocationId))
                    .thenReturn(false);
            when(locationRepository.save(any(Location.class))).thenReturn(updatedLocation);

            Location result = locationService.updateLocation(locationId, "B99");

            assertEquals("B99", result.getLocationCode());
            verify(locationRepository).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw DuplicateLocationCodeException when new code exists")
        void shouldThrowExceptionWhenNewCodeExists() {
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationRepository.existsByLocationCodeAndStorageLocation_Id("B2", storageLocationId))
                    .thenReturn(true);

            assertThrows(DuplicateLocationCodeException.class, () ->
                    locationService.updateLocation(locationId, "B2"));
        }

        @Test
        @DisplayName("should allow updating to same code")
        void shouldAllowUpdatingToSameCode() {
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationRepository.save(any(Location.class))).thenReturn(testLocation);

            Location result = locationService.updateLocation(locationId, "B1");

            assertEquals("B1", result.getLocationCode());
        }
    }

    @Nested
    @DisplayName("deleteLocation")
    class DeleteLocationTests {

        @Test
        @DisplayName("should delete location")
        void shouldDeleteLocation() {
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));

            locationService.deleteLocation(locationId);

            verify(locationRepository).delete(testLocation);
        }

        @Test
        @DisplayName("should throw LocationNotFoundException when location not found")
        void shouldThrowExceptionWhenLocationNotFound() {
            when(locationRepository.findById(locationId)).thenReturn(Optional.empty());

            assertThrows(LocationNotFoundException.class, () ->
                    locationService.deleteLocation(locationId));
        }
    }

    @Nested
    @DisplayName("getAllStorageLocations")
    class GetAllStorageLocationsTests {

        @Test
        @DisplayName("should return all storage locations ordered by display order")
        void shouldReturnAllStorageLocations() {
            StorageLocation racksStorage = StorageLocation.builder()
                    .id(UUID.randomUUID())
                    .site(testSite)
                    .code("RACKS")
                    .name("Racks")
                    .displayOrder(2)
                    .build();

            when(storageLocationRepository.findBySite_CodeOrderByDisplayOrder("MAIN"))
                    .thenReturn(List.of(testStorageLocation, racksStorage));

            List<StorageLocation> result = locationService.getAllStorageLocations();

            assertEquals(2, result.size());
        }
    }
}
