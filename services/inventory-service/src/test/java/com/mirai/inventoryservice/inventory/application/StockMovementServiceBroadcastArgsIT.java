package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.inventory.api.BatchAdjustLineDTO;
import com.mirai.inventoryservice.inventory.api.BatchAdjustStockRequestDTO;
import com.mirai.inventoryservice.inventory.api.BatchTransferInventoryRequestDTO;
import com.mirai.inventoryservice.inventory.api.TransferInventoryRequestDTO;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.services.SupabaseBroadcastService;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.sites.infrastructure.LocationRepository;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import com.mirai.inventoryservice.sites.infrastructure.StorageLocationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * .specs/phase-6-inventory 6e, T-6e-be-5: proves {@code StockMovementService}'s batch call
 * sites actually thread {@code siteId} and the full affected-product-ID set into
 * {@code SupabaseBroadcastService.broadcastInventoryUpdated}, closing the gap the 6e worksheet
 * flagged -- previously {@code batchAdjustInventory}/{@code batchTransferInventory} passed
 * {@code itemId = null} despite already holding every affected product ID locally.
 *
 * <p>Extends {@link BaseKafkaIntegrationTest} (real Postgres), not the plain
 * {@code @Transactional} {@code BaseIntegrationTest} -- that class wraps every test in one
 * outer rolled-back transaction, which would mask the after-commit dispatch this checkpoint
 * added (see {@code SiteInventoryMutationControllerAtomicityIT}'s identical reasoning). Real
 * Postgres is also required here regardless: {@code batchTransferInventory}'s
 * create-new-destination path uses a native {@code ON CONFLICT} upsert H2 cannot run.
 */
class StockMovementServiceBroadcastArgsIT extends BaseKafkaIntegrationTest {

    @Autowired private StockMovementService stockMovementService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SiteRepository siteRepository;
    @Autowired private LocationInventoryRepository locationInventoryRepository;

    @SpyBean private SupabaseBroadcastService spiedBroadcastService;

    private Site newSite(String suffix) {
        return siteRepository.save(Site.builder().code("BCARGS-" + suffix).name("Broadcast Args Site").build());
    }

    private Location newLocation(Site site, String storageLocationCode, String suffix) {
        // Must be a real storage-location code: StockMovementService.
        // mapStorageLocationCodeToLocationType throws on anything else.
        StorageLocation storage = storageLocationRepository.save(StorageLocation.builder()
                .site(site).code(storageLocationCode).name(storageLocationCode)
                .isDisplayOnly(false).hasDisplay(false).build());
        return locationRepository.save(Location.builder().storageLocation(storage).locationCode("L-" + suffix).build());
    }

    private Product newProduct(String suffix) {
        Category category = categoryRepository.save(Category.builder()
                .name("Broadcast Args Category " + suffix).slug("bcargs-category-" + suffix).build());
        return productRepository.save(Product.builder()
                .sku("BCARGS-" + suffix).name("Broadcast Args Product " + suffix).category(category).build());
    }

    @Test
    void batchAdjustInventory_emitsOneNotification_withSiteIdAndAllAffectedProductIds() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Site site = newSite(suffix);
        Location location = newLocation(site, "BOX_BINS", suffix);

        Product productA = newProduct(suffix + "-A");
        Product productB = newProduct(suffix + "-B");
        LocationInventory invA = locationInventoryRepository.save(LocationInventory.builder()
                .location(location).site(site).product(productA).quantity(10).build());
        LocationInventory invB = locationInventoryRepository.save(LocationInventory.builder()
                .location(location).site(site).product(productB).quantity(5).build());

        BatchAdjustStockRequestDTO request = new BatchAdjustStockRequestDTO();
        request.setLocationType(LocationType.BOX_BIN);
        request.setLocationId(location.getId());
        request.setReason(StockMovementReason.SALE);
        request.setAdjustments(List.of(
                lineOf(invA.getId(), -2),
                lineOf(invB.getId(), -1)
        ));

        stockMovementService.batchAdjustInventory(site.getId(), UUID.randomUUID(), request);

        verify(spiedBroadcastService, times(1)).broadcastInventoryUpdated(
                eq(site.getId()), any(), argThatContainsExactly(productA.getId(), productB.getId()), isNull());
    }

    @Test
    void batchTransferInventory_emitsOneNotification_withSiteIdAndAllAffectedProductIds() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Site site = newSite(suffix);
        Location source = newLocation(site, "BOX_BINS", suffix + "-src");
        Location dest = newLocation(site, "RACKS", suffix + "-dst");

        Product productA = newProduct(suffix + "-A");
        Product productB = newProduct(suffix + "-B");
        LocationInventory sourceA = locationInventoryRepository.save(LocationInventory.builder()
                .location(source).site(site).product(productA).quantity(10).build());
        LocationInventory sourceB = locationInventoryRepository.save(LocationInventory.builder()
                .location(source).site(site).product(productB).quantity(8).build());

        TransferInventoryRequestDTO transferA = new TransferInventoryRequestDTO();
        transferA.setSourceLocationType(LocationType.BOX_BIN);
        transferA.setSourceInventoryId(sourceA.getId());
        transferA.setDestinationLocationType(LocationType.RACK);
        transferA.setDestinationLocationId(dest.getId());
        transferA.setQuantity(3);

        TransferInventoryRequestDTO transferB = new TransferInventoryRequestDTO();
        transferB.setSourceLocationType(LocationType.BOX_BIN);
        transferB.setSourceInventoryId(sourceB.getId());
        transferB.setDestinationLocationType(LocationType.RACK);
        transferB.setDestinationLocationId(dest.getId());
        transferB.setQuantity(2);

        BatchTransferInventoryRequestDTO batchRequest = new BatchTransferInventoryRequestDTO();
        batchRequest.setTransfers(List.of(transferA, transferB));

        stockMovementService.batchTransferInventory(batchRequest);

        verify(spiedBroadcastService, times(1)).broadcastInventoryUpdated(
                eq(site.getId()), any(), argThatContainsExactly(productA.getId(), productB.getId()), isNull());
    }

    private static BatchAdjustLineDTO lineOf(UUID inventoryId, int quantityChange) {
        BatchAdjustLineDTO line = new BatchAdjustLineDTO();
        line.setInventoryId(inventoryId);
        line.setQuantityChange(quantityChange);
        return line;
    }

    private static List<String> argThatContainsExactly(UUID... productIds) {
        Set<String> expected = Set.of(productIds).stream().map(UUID::toString).collect(java.util.stream.Collectors.toSet());
        return org.mockito.ArgumentMatchers.argThat(actual ->
                actual != null && new java.util.HashSet<>(actual).equals(expected));
    }
}
