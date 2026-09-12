package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.inventory.domain.InventoryNotFoundException;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.infrastructure.InventoryTotalsRepository;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * T-6c-2 (.specs/phase-6-inventory/log.md, AC-1/AC-3): unit-level proof that
 * {@link InventoryQueries}'s new site-scoped overloads map a foreign-site/missing row to
 * {@link InventoryNotFoundException} (-> 404 per multi-site-data-and-api.md:63-64), not an
 * authorization error, and pass the site id through to the underlying site-qualified repository
 * method unchanged. The repository-level tenant-isolation proof itself (a foreign-site id
 * genuinely returning nothing at the database) is at
 * {@code LocationInventorySiteScopedQueriesIT} (T-6c-1), per the task list's own split.
 */
@ExtendWith(MockitoExtension.class)
class InventoryQueriesSiteScopedTest {

    @Mock private LocationInventoryRepository locationInventoryRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private InventoryTotalsRepository inventoryTotalsRepository;

    private InventoryQueries inventoryQueries;

    private final UUID siteId = UUID.randomUUID();
    private final UUID inventoryId = UUID.randomUUID();
    private final UUID locationId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        inventoryQueries = new InventoryQueries(
                locationInventoryRepository, stockMovementRepository, inventoryTotalsRepository);
    }

    @Test
    void findInventoryBySiteById_returnsRow_whenFoundAtSite() {
        LocationInventory inventory = LocationInventory.builder().id(inventoryId).build();
        when(locationInventoryRepository.findByIdAndSite_Id(inventoryId, siteId))
                .thenReturn(Optional.of(inventory));

        LocationInventory result = inventoryQueries.findInventoryBySite(siteId, inventoryId);

        assertEquals(inventoryId, result.getId());
    }

    @Test
    void findInventoryBySiteById_throwsNotFound_whenForeignSiteOrMissing() {
        when(locationInventoryRepository.findByIdAndSite_Id(inventoryId, siteId))
                .thenReturn(Optional.empty());

        assertThrows(InventoryNotFoundException.class,
                () -> inventoryQueries.findInventoryBySite(siteId, inventoryId));
    }

    @Test
    void findInventoryBySiteByLocationAndProduct_throwsNotFound_whenForeignSiteOrMissing() {
        when(locationInventoryRepository.findByLocation_IdAndProduct_IdAndSite_Id(locationId, productId, siteId))
                .thenReturn(Optional.empty());

        assertThrows(InventoryNotFoundException.class,
                () -> inventoryQueries.findInventoryBySite(siteId, locationId, productId));
    }

    @Test
    void findInventoryBySiteByLocationAndProduct_returnsRow_whenFound() {
        LocationInventory inventory = LocationInventory.builder().id(inventoryId).build();
        when(locationInventoryRepository.findByLocation_IdAndProduct_IdAndSite_Id(locationId, productId, siteId))
                .thenReturn(Optional.of(inventory));

        LocationInventory result = inventoryQueries.findInventoryBySite(siteId, locationId, productId);

        assertEquals(inventoryId, result.getId());
    }

    @Test
    void findByLocationIdAndSite_delegatesWithSiteId() {
        List<LocationInventory> rows = List.of(LocationInventory.builder().id(inventoryId).build());
        when(locationInventoryRepository.findByLocation_IdAndSite_Id(locationId, siteId)).thenReturn(rows);

        List<LocationInventory> result = inventoryQueries.findByLocationIdAndSite(siteId, locationId);

        assertEquals(rows, result);
    }

    @Test
    void sumQuantityByProductIdAndSite_delegatesWithSiteId() {
        when(locationInventoryRepository.sumQuantityByProductIdAndSiteId(productId, siteId)).thenReturn(42);

        Integer result = inventoryQueries.sumQuantityByProductIdAndSite(siteId, productId);

        assertEquals(42, result);
    }

    @Test
    void findAuditLogPageBySite_buildsSiteScopedSpecification() {
        AuditLogFilterDTO filters = AuditLogFilterDTO.builder().build();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<com.mirai.inventoryservice.inventory.domain.StockMovement> page =
                org.springframework.data.domain.Page.empty();

        when(stockMovementRepository.findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<
                        com.mirai.inventoryservice.inventory.domain.StockMovement>>any(),
                org.mockito.ArgumentMatchers.eq(pageable)))
                .thenReturn(page);

        var result = inventoryQueries.findAuditLogPageBySite(siteId, filters, pageable);

        assertEquals(page, result);
    }

    // ---- T-6c-5: slim site-scoped totals pass-through ----

    @Test
    void findInventoryTotalsBySite_fullCatalog_delegatesWithSiteId() {
        List<com.mirai.inventoryservice.inventory.api.SiteInventoryTotalDTO> rows = List.of(
                com.mirai.inventoryservice.inventory.api.SiteInventoryTotalDTO.builder()
                        .productId(productId).totalQuantity(5).build());
        when(inventoryTotalsRepository.findAllInventoryTotalsBySite(siteId)).thenReturn(rows);

        List<com.mirai.inventoryservice.inventory.api.SiteInventoryTotalDTO> result =
                inventoryQueries.findInventoryTotalsBySite(siteId);

        assertEquals(rows, result);
    }

    @Test
    void findInventoryTotalsBySite_batched_delegatesWithSiteIdAndProductIds() {
        List<UUID> ids = List.of(productId);
        List<com.mirai.inventoryservice.inventory.api.SiteInventoryTotalDTO> rows = List.of(
                com.mirai.inventoryservice.inventory.api.SiteInventoryTotalDTO.builder()
                        .productId(productId).totalQuantity(3).build());
        when(inventoryTotalsRepository.findInventoryTotalsBySiteAndProductIds(siteId, ids)).thenReturn(rows);

        List<com.mirai.inventoryservice.inventory.api.SiteInventoryTotalDTO> result =
                inventoryQueries.findInventoryTotalsBySite(siteId, ids);

        assertEquals(rows, result);
    }
}
