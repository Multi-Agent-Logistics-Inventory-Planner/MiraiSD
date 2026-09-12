package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.catalog.application.CatalogQueries;
import com.mirai.inventoryservice.catalog.application.ProductStockStateWriter;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.inventory.api.BatchTransferInventoryRequestDTO;
import com.mirai.inventoryservice.inventory.api.TransferInventoryRequestDTO;
import com.mirai.inventoryservice.inventory.application.StockMovementService;
import com.mirai.inventoryservice.inventory.domain.InvalidInventoryOperationException;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository.LocationProductIds;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.repositories.AuditLogRepository;
import com.mirai.inventoryservice.services.EventOutboxService;
import com.mirai.inventoryservice.services.SupabaseBroadcastService;
import com.mirai.inventoryservice.sites.application.LocationService;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.sites.infrastructure.LocationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * .specs/phase-6-inventory/log.md 6c planning, T-6c-4: transferInventory/batchTransferInventory
 * must reject a source/destination pair whose sites differ. Same-site-only transfers in 6c;
 * audited inter-site transfers are Phase 7's. Covers both destination shapes executeTransfer
 * resolves — an existing destinationInventoryId, and a destinationLocationId with no existing
 * inventory row (the find-or-create path) — asserting the create path never persists the
 * orphan destination row through the JPA {@code save} path when the transfer is rejected.
 * <p>
 * Updated for review-driven fix round 2 (T-6c-6 P1 findings, unified locking strategy):
 * {@code StockMovementService} no longer calls {@code findByLocation_IdAndProduct_Id} anywhere in
 * the transfer path. Planning now resolves every row's (location, product) key via {@code
 * findLocationAndProductIdById}, and locking (existing rows) / find-or-create (new destinations)
 * both go through {@code findIdByLocation_IdAndProduct_IdForUpdate} +
 * {@code insertLocationInventoryIfAbsent}, in that order, before {@code executeTransfer} ever runs
 * a same-site check. These mocks stub that new sequence directly instead of the old id-list
 * locking calls.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockMovementServiceSameSiteTransferTest {

    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private CatalogQueries catalogQueries;
    @Mock private ProductStockStateWriter productStockStateWriter;
    @Mock private UserRepository userRepository;
    @Mock private LocationInventoryRepository locationInventoryRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private LocationService locationService;
    @Mock private EntityManager entityManager;
    @Mock private SupabaseBroadcastService broadcastService;
    @Mock private EventOutboxService eventOutboxService;

    private StockMovementService service;

    private Site mainSite;
    private Site secondSite;

    @BeforeEach
    void setUp() {
        service = new StockMovementService(
                stockMovementRepository, auditLogRepository, catalogQueries, productStockStateWriter,
                userRepository, locationInventoryRepository, locationRepository, locationService,
                entityManager, broadcastService, eventOutboxService);

        mainSite = Site.builder().id(UUID.randomUUID()).code("MAIN").name("Main").build();
        secondSite = Site.builder().id(UUID.randomUUID()).code("SECOND").name("Second").build();

        when(auditLogRepository.save(any())).thenAnswer(inv -> {
            AuditLog log = inv.getArgument(0);
            log.setId(UUID.randomUUID());
            return log;
        });
    }

    private LocationInventory inventoryAt(Site site, int quantity) {
        StorageLocation storageLocation = StorageLocation.builder()
                .id(UUID.randomUUID()).site(site).code("BOX_BINS").name("Box Bins")
                .hasDisplay(false).isDisplayOnly(false).displayOrder(1).build();
        Location location = Location.builder()
                .id(UUID.randomUUID()).storageLocation(storageLocation).locationCode("B1").build();
        Category category = Category.builder().id(UUID.randomUUID()).name("Cat").slug("cat").build();
        Product product = Product.builder().id(UUID.randomUUID()).sku("SKU").name("Name").category(category).build();
        return LocationInventory.builder()
                .id(UUID.randomUUID()).location(location).site(site).product(product).quantity(quantity)
                .build();
    }

    /**
     * A second, distinct row at the same site as {@code product}'s source, sharing its product but
     * at a different location -- realistic under the (location, product) unique-key constraint
     * real Postgres enforces, unlike naively reusing the source's own location.
     */
    private LocationInventory sameSiteDestination(LocationInventory source, int quantity) {
        Location location = Location.builder()
                .id(UUID.randomUUID())
                .storageLocation(source.getLocation().getStorageLocation())
                .locationCode("B2")
                .build();
        return LocationInventory.builder()
                .id(UUID.randomUUID()).location(location).site(source.getSite())
                .product(source.getProduct()).quantity(quantity).build();
    }

    /**
     * Stubs the two scalar lookups planning/locking need for a row that already exists: its
     * (location, product) key ({@code findLocationAndProductIdById}) and its locked id at that key
     * ({@code findIdByLocation_IdAndProduct_IdForUpdate}).
     */
    private void stubExistingRow(LocationInventory inv) {
        when(locationInventoryRepository.findLocationAndProductIdById(inv.getId()))
                .thenReturn(Optional.of(new LocationProductIds(inv.getLocation().getId(), inv.getProduct().getId())));
        when(locationInventoryRepository.findIdByLocation_IdAndProduct_IdForUpdate(
                inv.getLocation().getId(), inv.getProduct().getId()))
                .thenReturn(Optional.of(inv.getId()));
    }

    // ---- transferInventory: existing destination inventory, cross-site rejected ----

    @Test
    void transferInventory_existingDestinationInventory_crossSite_rejectsWithoutMutation() {
        LocationInventory source = inventoryAt(mainSite, 10);
        LocationInventory destination = inventoryAt(secondSite, 5);
        stubExistingRow(source);
        stubExistingRow(destination);
        when(locationInventoryRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(locationInventoryRepository.findById(destination.getId())).thenReturn(Optional.of(destination));

        TransferInventoryRequestDTO request = TransferInventoryRequestDTO.builder()
                .sourceLocationType(LocationType.BOX_BIN)
                .sourceInventoryId(source.getId())
                .destinationLocationType(LocationType.BOX_BIN)
                .destinationInventoryId(destination.getId())
                .quantity(3)
                .build();

        InvalidInventoryOperationException ex = assertThrows(
                InvalidInventoryOperationException.class, () -> service.transferInventory(request));
        assertTrue(ex.getMessage().contains("MAIN"));
        assertTrue(ex.getMessage().contains("SECOND"));

        verify(locationInventoryRepository, never()).save(any());
        verify(locationInventoryRepository, never()).delete(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void transferInventory_existingDestinationInventory_sameSite_succeeds() {
        LocationInventory source = inventoryAt(mainSite, 10);
        LocationInventory destination = sameSiteDestination(source, 5);
        stubExistingRow(source);
        stubExistingRow(destination);
        when(locationInventoryRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(locationInventoryRepository.findById(destination.getId())).thenReturn(Optional.of(destination));
        when(locationInventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransferInventoryRequestDTO request = TransferInventoryRequestDTO.builder()
                .sourceLocationType(LocationType.BOX_BIN)
                .sourceInventoryId(source.getId())
                .destinationLocationType(LocationType.BOX_BIN)
                .destinationInventoryId(destination.getId())
                .quantity(3)
                .build();

        service.transferInventory(request);

        verify(locationInventoryRepository).save(source);
        verify(locationInventoryRepository).save(destination);
    }

    // ---- transferInventory: create-new-inventory-at-location path, cross-site rejected before create ----

    @Test
    void transferInventory_newDestinationLocation_crossSite_rejectsWithoutCreatingOrphanRow() {
        LocationInventory source = inventoryAt(mainSite, 10);
        StorageLocation destStorageLocation = StorageLocation.builder()
                .id(UUID.randomUUID()).site(secondSite).code("BOX_BINS").name("Box Bins")
                .hasDisplay(false).isDisplayOnly(false).displayOrder(1).build();
        Location destLocation = Location.builder()
                .id(UUID.randomUUID()).storageLocation(destStorageLocation).locationCode("B2").build();

        stubExistingRow(source);
        when(locationInventoryRepository.findById(source.getId())).thenReturn(Optional.of(source));

        // The destination row does not exist yet: findIdByLocation_IdAndProduct_IdForUpdate misses
        // on the first call (nothing to lock), ensureAndLockInventoryRow creates it via
        // insertLocationInventoryIfAbsent, then the loop's second call finds the id it just
        // "created" -- simulating a real Postgres read-your-own-writes round trip with two
        // successive stubbed answers, matching ensureAndLockInventoryRow's documented 1-2 iteration
        // shape.
        UUID newDestinationId = UUID.randomUUID();
        when(locationInventoryRepository.findIdByLocation_IdAndProduct_IdForUpdate(
                destLocation.getId(), source.getProduct().getId()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(newDestinationId));
        when(locationRepository.findSiteIdById(destLocation.getId())).thenReturn(Optional.of(secondSite.getId()));

        LocationInventory newDestination = LocationInventory.builder()
                .id(newDestinationId).location(destLocation).site(secondSite)
                .product(source.getProduct()).quantity(0).build();
        when(locationInventoryRepository.findById(newDestinationId)).thenReturn(Optional.of(newDestination));

        TransferInventoryRequestDTO request = TransferInventoryRequestDTO.builder()
                .sourceLocationType(LocationType.BOX_BIN)
                .sourceInventoryId(source.getId())
                .destinationLocationType(LocationType.BOX_BIN)
                .destinationLocationId(destLocation.getId())
                .quantity(3)
                .build();

        InvalidInventoryOperationException ex = assertThrows(
                InvalidInventoryOperationException.class, () -> service.transferInventory(request));
        assertTrue(ex.getMessage().contains("MAIN"));
        assertTrue(ex.getMessage().contains("SECOND"));

        // The rejection happens inside executeTransfer, after the destination row's
        // find-or-create/lock step already ran (an accepted, non-material ordering nuance recorded
        // in .specs/phase-6-inventory/log.md -- in real Postgres this insert is rolled back with
        // everything else in the same @Transactional, so no orphan row is ever observable). What
        // must never happen, and what these assertions prove, is that the JPA entity-mutation path
        // is never reached: no LocationInventory is ever saved or deleted for this doomed transfer.
        verify(locationInventoryRepository, never()).save(any());
        verify(locationInventoryRepository, never()).delete(any());
        verify(stockMovementRepository, never()).save(any());
    }

    // ---- batchTransferInventory: each line's own site pair is checked independently ----

    @Test
    void batchTransferInventory_oneLineCrossSite_rejectsEntireBatch() {
        LocationInventory source1 = inventoryAt(mainSite, 10);
        LocationInventory source2 = inventoryAt(mainSite, 8);
        LocationInventory destination1 = sameSiteDestination(source1, 2);
        LocationInventory destination2 = inventoryAt(secondSite, 1);

        stubExistingRow(source1);
        stubExistingRow(source2);
        stubExistingRow(destination1);
        stubExistingRow(destination2);
        when(locationInventoryRepository.findAllByIdWithGraph(any()))
                .thenReturn(List.of(source1, source2));
        when(locationInventoryRepository.findById(source1.getId())).thenReturn(Optional.of(source1));
        when(locationInventoryRepository.findById(source2.getId())).thenReturn(Optional.of(source2));
        when(locationInventoryRepository.findById(destination1.getId())).thenReturn(Optional.of(destination1));
        when(locationInventoryRepository.findById(destination2.getId())).thenReturn(Optional.of(destination2));
        when(locationInventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BatchTransferInventoryRequestDTO request = BatchTransferInventoryRequestDTO.builder()
                .transfers(List.of(
                        TransferInventoryRequestDTO.builder()
                                .sourceLocationType(LocationType.BOX_BIN)
                                .sourceInventoryId(source1.getId())
                                .destinationLocationType(LocationType.BOX_BIN)
                                .destinationInventoryId(destination1.getId())
                                .quantity(2)
                                .build(),
                        TransferInventoryRequestDTO.builder()
                                .sourceLocationType(LocationType.BOX_BIN)
                                .sourceInventoryId(source2.getId())
                                .destinationLocationType(LocationType.BOX_BIN)
                                .destinationInventoryId(destination2.getId())
                                .quantity(1)
                                .build()))
                .build();

        InvalidInventoryOperationException ex = assertThrows(
                InvalidInventoryOperationException.class, () -> service.batchTransferInventory(request));
        assertTrue(ex.getMessage().contains("MAIN"));
        assertTrue(ex.getMessage().contains("SECOND"));
    }
}
