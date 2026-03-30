package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.AuditLogEntryDTO;
import com.mirai.inventoryservice.models.Category;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.Site;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.enums.UserRole;
import com.mirai.inventoryservice.models.storage.Location;
import com.mirai.inventoryservice.models.storage.StorageLocation;
import com.mirai.inventoryservice.repositories.LocationRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuditLogMapper.
 * Uses the unified Location table for resolving location codes.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogMapperTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private LocationRepository locationRepository;

    private AuditLogMapper auditLogMapper;

    private Site testSite;
    private StorageLocation boxBinsStorage;
    private StorageLocation racksStorage;

    @BeforeEach
    void setUp() {
        auditLogMapper = new AuditLogMapper(userRepository, locationRepository);

        // Set up test site and storage locations
        testSite = Site.builder()
                .id(UUID.randomUUID())
                .code("MAIN")
                .name("Main Warehouse")
                .build();

        boxBinsStorage = StorageLocation.builder()
                .id(UUID.randomUUID())
                .site(testSite)
                .code("BOX_BINS")
                .name("Box Bins")
                .build();

        racksStorage = StorageLocation.builder()
                .id(UUID.randomUUID())
                .site(testSite)
                .code("RACKS")
                .name("Racks")
                .build();
    }

    @Nested
    @DisplayName("toAuditLogEntryDTOList")
    class ToAuditLogEntryDTOListTests {

        @Test
        @DisplayName("should return empty list for empty input")
        void shouldReturnEmptyListForEmptyInput() {
            List<AuditLogEntryDTO> result = auditLogMapper.toAuditLogEntryDTOList(Collections.emptyList());

            assertTrue(result.isEmpty());
            verifyNoInteractions(userRepository);
            verifyNoInteractions(locationRepository);
        }

        @Test
        @DisplayName("should batch fetch users only once for multiple movements with same actor")
        void shouldBatchFetchUsersOnlyOnceForSameActor() {
            UUID actorId = UUID.randomUUID();
            User actor = createUser(actorId, "John Doe");
            List<StockMovement> movements = List.of(
                    createMovement(actorId, LocationType.BOX_BIN, null, null),
                    createMovement(actorId, LocationType.BOX_BIN, null, null),
                    createMovement(actorId, LocationType.BOX_BIN, null, null)
            );

            when(userRepository.findAllById(anyCollection())).thenReturn(List.of(actor));

            List<AuditLogEntryDTO> result = auditLogMapper.toAuditLogEntryDTOList(movements);

            assertEquals(3, result.size());
            result.forEach(dto -> assertEquals("John Doe", dto.getActorName()));

            // Verify batch fetch was called only ONCE, not 3 times
            verify(userRepository, times(1)).findAllById(anyCollection());
        }

        @Test
        @DisplayName("should batch fetch users only once for multiple movements with different actors")
        void shouldBatchFetchUsersOnlyOnceForDifferentActors() {
            UUID actor1Id = UUID.randomUUID();
            UUID actor2Id = UUID.randomUUID();
            UUID actor3Id = UUID.randomUUID();
            User actor1 = createUser(actor1Id, "Alice");
            User actor2 = createUser(actor2Id, "Bob");
            User actor3 = createUser(actor3Id, "Charlie");

            List<StockMovement> movements = List.of(
                    createMovement(actor1Id, LocationType.BOX_BIN, null, null),
                    createMovement(actor2Id, LocationType.BOX_BIN, null, null),
                    createMovement(actor3Id, LocationType.BOX_BIN, null, null)
            );

            when(userRepository.findAllById(anyCollection())).thenReturn(List.of(actor1, actor2, actor3));

            List<AuditLogEntryDTO> result = auditLogMapper.toAuditLogEntryDTOList(movements);

            assertEquals(3, result.size());
            assertEquals("Alice", result.get(0).getActorName());
            assertEquals("Bob", result.get(1).getActorName());
            assertEquals("Charlie", result.get(2).getActorName());

            // Verify batch fetch was called only ONCE
            verify(userRepository, times(1)).findAllById(anyCollection());
        }

        @Test
        @DisplayName("should batch fetch locations only once")
        void shouldBatchFetchLocationsOnlyOnce() {
            UUID loc1Id = UUID.randomUUID();
            UUID loc2Id = UUID.randomUUID();
            Location loc1 = createLocation(loc1Id, "B1", boxBinsStorage);
            Location loc2 = createLocation(loc2Id, "B2", boxBinsStorage);

            List<StockMovement> movements = List.of(
                    createMovement(null, LocationType.BOX_BIN, loc1Id, loc2Id),
                    createMovement(null, LocationType.BOX_BIN, loc2Id, loc1Id),
                    createMovement(null, LocationType.BOX_BIN, loc1Id, loc1Id)
            );

            when(locationRepository.findAllById(anyCollection())).thenReturn(List.of(loc1, loc2));

            List<AuditLogEntryDTO> result = auditLogMapper.toAuditLogEntryDTOList(movements);

            assertEquals(3, result.size());

            // Verify batch fetch was called only ONCE
            verify(locationRepository, times(1)).findAllById(anyCollection());
        }

        @Test
        @DisplayName("should handle movements with different location types")
        void shouldHandleMovementsWithDifferentLocationTypes() {
            UUID boxBinId = UUID.randomUUID();
            UUID rackId = UUID.randomUUID();
            Location boxBin = createLocation(boxBinId, "B1", boxBinsStorage);
            Location rack = createLocation(rackId, "R1", racksStorage);

            List<StockMovement> movements = List.of(
                    createMovement(null, LocationType.BOX_BIN, boxBinId, null),
                    createMovement(null, LocationType.RACK, rackId, null)
            );

            when(locationRepository.findAllById(anyCollection())).thenReturn(List.of(boxBin, rack));

            List<AuditLogEntryDTO> result = auditLogMapper.toAuditLogEntryDTOList(movements);

            assertEquals(2, result.size());
            assertEquals("B1", result.get(0).getFromLocationCode());
            assertEquals("R1", result.get(1).getFromLocationCode());

            // Verify unified location fetch was called once
            verify(locationRepository, times(1)).findAllById(anyCollection());
        }

        @Test
        @DisplayName("should handle null actor IDs")
        void shouldHandleNullActorIds() {
            List<StockMovement> movements = List.of(
                    createMovement(null, LocationType.BOX_BIN, null, null),
                    createMovement(null, LocationType.BOX_BIN, null, null)
            );

            List<AuditLogEntryDTO> result = auditLogMapper.toAuditLogEntryDTOList(movements);

            assertEquals(2, result.size());
            result.forEach(dto -> assertNull(dto.getActorName()));

            // Should not call userRepository when all actor IDs are null
            verify(userRepository, never()).findAllById(anyCollection());
        }

        @Test
        @DisplayName("should handle null location IDs")
        void shouldHandleNullLocationIds() {
            UUID actorId = UUID.randomUUID();
            User actor = createUser(actorId, "Test User");

            List<StockMovement> movements = List.of(
                    createMovement(actorId, LocationType.BOX_BIN, null, null)
            );

            when(userRepository.findAllById(anyCollection())).thenReturn(List.of(actor));

            List<AuditLogEntryDTO> result = auditLogMapper.toAuditLogEntryDTOList(movements);

            assertEquals(1, result.size());
            assertNull(result.get(0).getFromLocationCode());
            assertNull(result.get(0).getToLocationCode());

            // Should not call locationRepository when all location IDs are null
            verify(locationRepository, never()).findAllById(anyCollection());
        }

        @Test
        @DisplayName("should map all fields correctly")
        void shouldMapAllFieldsCorrectly() {
            UUID actorId = UUID.randomUUID();
            UUID fromLocationId = UUID.randomUUID();
            UUID toLocationId = UUID.randomUUID();
            User actor = createUser(actorId, "Jane Doe");
            Location fromLoc = createLocation(fromLocationId, "B1", boxBinsStorage);
            Location toLoc = createLocation(toLocationId, "B2", boxBinsStorage);

            Category testCategory = Category.builder()
                    .id(UUID.randomUUID())
                    .name("Test Category")
                    .slug("test-category")
                    .build();

            Product product = Product.builder()
                    .id(UUID.randomUUID())
                    .sku("SKU-001")
                    .name("Test Product")
                    .category(testCategory)
                    .build();

            OffsetDateTime timestamp = OffsetDateTime.now();
            StockMovement movement = StockMovement.builder()
                    .id(1L)
                    .locationType(LocationType.BOX_BIN)
                    .item(product)
                    .fromLocationId(fromLocationId)
                    .toLocationId(toLocationId)
                    .previousQuantity(10)
                    .currentQuantity(15)
                    .quantityChange(5)
                    .reason(StockMovementReason.RESTOCK)
                    .actorId(actorId)
                    .at(timestamp)
                    .build();

            when(userRepository.findAllById(anyCollection())).thenReturn(List.of(actor));
            when(locationRepository.findAllById(anyCollection())).thenReturn(List.of(fromLoc, toLoc));

            List<AuditLogEntryDTO> result = auditLogMapper.toAuditLogEntryDTOList(List.of(movement));

            assertEquals(1, result.size());
            AuditLogEntryDTO dto = result.get(0);

            assertEquals(1L, dto.getId());
            assertEquals(LocationType.BOX_BIN, dto.getLocationType());
            assertEquals(product.getId(), dto.getItemId());
            assertEquals("SKU-001", dto.getItemSku());
            assertEquals("Test Product", dto.getItemName());
            assertEquals(fromLocationId, dto.getFromLocationId());
            assertEquals("B1", dto.getFromLocationCode());
            assertEquals(toLocationId, dto.getToLocationId());
            assertEquals("B2", dto.getToLocationCode());
            assertEquals(10, dto.getPreviousQuantity());
            assertEquals(15, dto.getCurrentQuantity());
            assertEquals(5, dto.getQuantityChange());
            assertEquals(StockMovementReason.RESTOCK, dto.getReason());
            assertEquals(actorId, dto.getActorId());
            assertEquals("Jane Doe", dto.getActorName());
            assertEquals(timestamp, dto.getAt());
        }
    }

    @Nested
    @DisplayName("toAuditLogEntryDTOPage")
    class ToAuditLogEntryDTOPageTests {

        @Test
        @DisplayName("should preserve pagination metadata")
        void shouldPreservePaginationMetadata() {
            UUID actorId = UUID.randomUUID();
            User actor = createUser(actorId, "Test User");

            List<StockMovement> movements = List.of(
                    createMovement(actorId, LocationType.BOX_BIN, null, null)
            );
            PageRequest pageRequest = PageRequest.of(2, 20);
            Page<StockMovement> movementPage = new PageImpl<>(movements, pageRequest, 100);

            when(userRepository.findAllById(anyCollection())).thenReturn(List.of(actor));

            Page<AuditLogEntryDTO> result = auditLogMapper.toAuditLogEntryDTOPage(movementPage);

            assertEquals(100, result.getTotalElements());
            assertEquals(5, result.getTotalPages());
            assertEquals(2, result.getNumber());
            assertEquals(20, result.getSize());
            assertEquals(1, result.getContent().size());
        }

        @Test
        @DisplayName("should use batch fetching for page conversion")
        void shouldUseBatchFetchingForPageConversion() {
            UUID actor1Id = UUID.randomUUID();
            UUID actor2Id = UUID.randomUUID();
            User actor1 = createUser(actor1Id, "User 1");
            User actor2 = createUser(actor2Id, "User 2");

            List<StockMovement> movements = List.of(
                    createMovement(actor1Id, LocationType.BOX_BIN, null, null),
                    createMovement(actor2Id, LocationType.BOX_BIN, null, null)
            );
            Page<StockMovement> movementPage = new PageImpl<>(movements, PageRequest.of(0, 20), 2);

            when(userRepository.findAllById(anyCollection())).thenReturn(List.of(actor1, actor2));

            Page<AuditLogEntryDTO> result = auditLogMapper.toAuditLogEntryDTOPage(movementPage);

            assertEquals(2, result.getContent().size());

            // Verify batch fetch was called only ONCE
            verify(userRepository, times(1)).findAllById(anyCollection());
        }
    }

    @Nested
    @DisplayName("N+1 Query Prevention")
    class N1QueryPreventionTests {

        @Test
        @DisplayName("should make exactly 1 user query regardless of number of distinct actors")
        void shouldMakeExactlyOneUserQuery() {
            // Create 20 movements with 10 different actors
            List<UUID> actorIds = new ArrayList<>();
            List<User> actors = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                UUID id = UUID.randomUUID();
                actorIds.add(id);
                actors.add(createUser(id, "User " + i));
            }

            List<StockMovement> movements = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                movements.add(createMovement(actorIds.get(i % 10), LocationType.BOX_BIN, null, null));
            }

            when(userRepository.findAllById(anyCollection())).thenReturn(actors);

            auditLogMapper.toAuditLogEntryDTOList(movements);

            // Critical assertion: only 1 query for users, not 20
            verify(userRepository, times(1)).findAllById(anyCollection());
        }

        @Test
        @DisplayName("should make exactly 1 location query for all location types")
        void shouldMakeExactlyOneLocationQuery() {
            // Create movements across different location types
            UUID boxBinId = UUID.randomUUID();
            UUID rackId = UUID.randomUUID();
            UUID cabinetId = UUID.randomUUID();

            StorageLocation cabinetStorage = StorageLocation.builder()
                    .id(UUID.randomUUID())
                    .site(testSite)
                    .code("CABINETS")
                    .name("Cabinets")
                    .build();

            Location boxBin = createLocation(boxBinId, "B1", boxBinsStorage);
            Location rack = createLocation(rackId, "R1", racksStorage);
            Location cabinet = createLocation(cabinetId, "C1", cabinetStorage);

            List<StockMovement> movements = List.of(
                    createMovement(null, LocationType.BOX_BIN, boxBinId, boxBinId),
                    createMovement(null, LocationType.BOX_BIN, boxBinId, null),
                    createMovement(null, LocationType.BOX_BIN, null, boxBinId),
                    createMovement(null, LocationType.RACK, rackId, rackId),
                    createMovement(null, LocationType.RACK, rackId, null),
                    createMovement(null, LocationType.CABINET, cabinetId, null)
            );

            when(locationRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(boxBin, rack, cabinet));

            auditLogMapper.toAuditLogEntryDTOList(movements);

            // Critical assertion: exactly 1 query for all locations (unified table)
            verify(locationRepository, times(1)).findAllById(anyCollection());
        }

        @Test
        @DisplayName("should handle large batch efficiently - 100 movements with mixed data")
        void shouldHandleLargeBatchEfficiently() {
            // Create 100 movements with 20 actors and various locations
            List<UUID> actorIds = new ArrayList<>();
            List<User> actors = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                UUID id = UUID.randomUUID();
                actorIds.add(id);
                actors.add(createUser(id, "User " + i));
            }

            List<UUID> locationIds = new ArrayList<>();
            List<Location> locations = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                UUID id = UUID.randomUUID();
                locationIds.add(id);
                locations.add(createLocation(id, "B" + i, boxBinsStorage));
            }

            List<StockMovement> movements = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                movements.add(createMovement(
                        actorIds.get(i % 20),
                        LocationType.BOX_BIN,
                        locationIds.get(i % 10),
                        locationIds.get((i + 1) % 10)
                ));
            }

            when(userRepository.findAllById(anyCollection())).thenReturn(actors);
            when(locationRepository.findAllById(anyCollection())).thenReturn(locations);

            List<AuditLogEntryDTO> result = auditLogMapper.toAuditLogEntryDTOList(movements);

            assertEquals(100, result.size());

            // Critical assertion: only 1 user query and 1 location query
            // Without batch fetching, this would be 100 user queries + 200 location queries
            verify(userRepository, times(1)).findAllById(anyCollection());
            verify(locationRepository, times(1)).findAllById(anyCollection());
        }
    }

    // Helper methods

    private User createUser(UUID id, String fullName) {
        return User.builder()
                .id(id)
                .fullName(fullName)
                .email(fullName.toLowerCase().replace(" ", ".") + "@example.com")
                .role(UserRole.EMPLOYEE)
                .build();
    }

    private Location createLocation(UUID id, String code, StorageLocation storageLocation) {
        return Location.builder()
                .id(id)
                .locationCode(code)
                .storageLocation(storageLocation)
                .build();
    }

    private StockMovement createMovement(UUID actorId, LocationType locationType, UUID fromLocationId, UUID toLocationId) {
        Category testCategory = Category.builder()
                .id(UUID.randomUUID())
                .name("Test Category")
                .slug("test-category")
                .build();

        Product product = Product.builder()
                .id(UUID.randomUUID())
                .sku("SKU-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Test Product")
                .category(testCategory)
                .build();

        return StockMovement.builder()
                .id((long) (Math.random() * 10000))
                .locationType(locationType)
                .item(product)
                .fromLocationId(fromLocationId)
                .toLocationId(toLocationId)
                .previousQuantity(10)
                .currentQuantity(15)
                .quantityChange(5)
                .reason(StockMovementReason.RESTOCK)
                .actorId(actorId)
                .at(OffsetDateTime.now())
                .build();
    }
}
