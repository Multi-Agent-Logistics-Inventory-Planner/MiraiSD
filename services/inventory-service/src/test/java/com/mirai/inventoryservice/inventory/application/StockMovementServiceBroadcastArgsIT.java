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

import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

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

    @Test
    void batchTransferInventory_mixedSites_emitsOneNotificationPerAffectedSite() throws Exception {
        // Follow-up review finding, P2: the legacy, unscoped batchTransferInventory(request)
        // overload (unlike its site-scoped sibling, which requires every transfer's source to
        // already belong to the caller's siteId before this method ever runs) accepts
        // independent transfers whose sources belong to different sites in one request. It must
        // not stamp a single combined broadcast with only the first transfer's site -- every
        // other affected site's clients would silently discard it.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Site siteA = newSite(suffix + "-A");
        Site siteB = newSite(suffix + "-B");
        Location sourceA = newLocation(siteA, "BOX_BINS", suffix + "-srcA");
        Location destA = newLocation(siteA, "RACKS", suffix + "-dstA");
        Location sourceB = newLocation(siteB, "BOX_BINS", suffix + "-srcB");
        Location destB = newLocation(siteB, "RACKS", suffix + "-dstB");

        Product productA = newProduct(suffix + "-A");
        Product productB = newProduct(suffix + "-B");
        LocationInventory invA = locationInventoryRepository.save(LocationInventory.builder()
                .location(sourceA).site(siteA).product(productA).quantity(10).build());
        LocationInventory invB = locationInventoryRepository.save(LocationInventory.builder()
                .location(sourceB).site(siteB).product(productB).quantity(8).build());

        TransferInventoryRequestDTO transferA = new TransferInventoryRequestDTO();
        transferA.setSourceLocationType(LocationType.BOX_BIN);
        transferA.setSourceInventoryId(invA.getId());
        transferA.setDestinationLocationType(LocationType.RACK);
        transferA.setDestinationLocationId(destA.getId());
        transferA.setQuantity(3);

        TransferInventoryRequestDTO transferB = new TransferInventoryRequestDTO();
        transferB.setSourceLocationType(LocationType.BOX_BIN);
        transferB.setSourceInventoryId(invB.getId());
        transferB.setDestinationLocationType(LocationType.RACK);
        transferB.setDestinationLocationId(destB.getId());
        transferB.setQuantity(2);

        BatchTransferInventoryRequestDTO batchRequest = new BatchTransferInventoryRequestDTO();
        batchRequest.setTransfers(List.of(transferA, transferB));

        stockMovementService.batchTransferInventory(batchRequest);

        verify(spiedBroadcastService, times(1)).broadcastInventoryUpdated(
                eq(siteA.getId()), any(), argThatContainsExactly(productA.getId()), isNull());
        verify(spiedBroadcastService, times(1)).broadcastInventoryUpdated(
                eq(siteB.getId()), any(), argThatContainsExactly(productB.getId()), isNull());
    }

    @Test
    void forcedRollback_neverReachesTheNetworkDispatchLayer() throws Exception {
        // .specs/phase-6-inventory 6e independent review, R-4: proves AfterCommitRunner's
        // deferral actually depends on a real commit, not just "some transaction was active" --
        // a transaction that starts, does the write, then rolls back (never commits) must never
        // reach the actual dispatch. `broadcastInventoryUpdated` itself is always called (it's
        // the synchronous wrapper that decides whether to defer, called unconditionally by the
        // mutation) -- what must never fire is the deferred, @Async `dispatchInventoryUpdated`
        // this wrapper schedules. That method is package-private to
        // com.mirai.inventoryservice.services, so its own dedicated proof
        // (SupabaseBroadcastServiceAfterCommitIT, same package) is the permanent test for this;
        // here we additionally confirm the rollback actually happened at the data level.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Site site = newSite(suffix);
        Location location = newLocation(site, "BOX_BINS", suffix);
        Product product = newProduct(suffix);
        LocationInventory inv = locationInventoryRepository.save(LocationInventory.builder()
                .location(location).site(site).product(product).quantity(10).build());

        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            stockMovementService.removeInventoryWithTracking(
                    LocationType.BOX_BIN, inv.getId(), StockMovementReason.REMOVED, UUID.randomUUID(), null);
            status.setRollbackOnly();
            return null;
        });

        // The rollback actually happened (the row is still there), not just that the call
        // returned without throwing.
        assertThat(locationInventoryRepository.findById(inv.getId())).isPresent();
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
