package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.inventory.domain.StockMovement;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.repositories.EventOutboxRepository;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.sites.infrastructure.LocationRepository;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import com.mirai.inventoryservice.sites.infrastructure.StorageLocationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * .specs/phase-6-inventory 6b (AC-2): proves every {@link InventoryOperations} write path that
 * persists a {@link StockMovement} sets a non-null {@code site} on it, and that the site is
 * derived correctly — not just present. Complements
 * {@link InventoryOperationsCallerTransactionIT} (transaction propagation) and
 * {@link InventoryOperationsSharedCallerPathIT} (find-or-create/delete-on-zero), which predate
 * the {@code site} column and don't assert on it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class InventoryOperationsSiteIT {

    @Autowired private InventoryOperations inventoryOperations;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private LocationInventoryRepository locationInventoryRepository;
    @Autowired private EventOutboxRepository eventOutboxRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SiteRepository siteRepository;

    @AfterEach
    void cleanup() {
        eventOutboxRepository.deleteAll();
        stockMovementRepository.deleteAll();
        locationInventoryRepository.deleteAll();
    }

    private Product newProduct(String label) {
        String suffix = label + "-" + UUID.randomUUID().toString().substring(0, 8);
        Category category = categoryRepository.save(Category.builder()
                .name("Site IT Category " + suffix)
                .slug("site-it-category-" + suffix.toLowerCase())
                .build());
        return productRepository.save(Product.builder()
                .sku("SITEIT-" + suffix)
                .name("Site IT Product " + suffix)
                .category(category)
                .isActive(true)
                .quantity(0)
                .build());
    }

    private Location newLocation(String siteCode, String label) {
        Site site = siteRepository.findByCode(siteCode)
                .orElseGet(() -> siteRepository.save(Site.builder().code(siteCode).name(siteCode).build()));
        String suffix = label + "-" + UUID.randomUUID().toString().substring(0, 8);
        StorageLocation storage = storageLocationRepository.save(StorageLocation.builder()
                .site(site)
                .code("SITEIT-" + suffix)
                .name("Site IT Storage " + suffix)
                .hasDisplay(false)
                .isDisplayOnly(false)
                .displayOrder(1)
                .build());
        return locationRepository.save(Location.builder()
                .storageLocation(storage)
                .locationCode("SITEIT-" + suffix)
                .build());
    }

    @Test
    void applyDelta_setsSiteFromLocation() {
        Location location = newLocation("MAIN", "apply-delta");
        Product product = newProduct("apply-delta");

        StockMovement saved = inventoryOperations.applyDelta(
                location, product, 5, LocationType.BOX_BIN, null, StockMovementReason.SALE, null, Map.of());

        assertThat(saved.getSite()).isNotNull();
        assertThat(saved.getSite().getId()).isEqualTo(location.getStorageLocation().getSite().getId());
    }

    @Test
    void recordMovement_fieldForm_persistsTheSuppliedSite() {
        Location location = newLocation("MAIN", "field-form");
        Product product = newProduct("field-form");
        Site site = location.getStorageLocation().getSite();

        StockMovement saved = inventoryOperations.recordMovement(
                null, product, LocationType.BOX_BIN, null, location.getId(),
                0, 5, 5, StockMovementReason.SHIPMENT_RECEIPT, null, Map.of(), site);

        StockMovement reloaded = stockMovementRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getSite()).isNotNull();
        assertThat(reloaded.getSite().getId()).isEqualTo(site.getId());
    }

    @Test
    void locationLessMovement_persistsTheExplicitlySuppliedSite() {
        Product product = newProduct("location-less");
        Site site = siteRepository.findByCode("MAIN")
                .orElseGet(() -> siteRepository.save(Site.builder().code("MAIN").name("MAIN").build()));

        StockMovement kujiLedgerRow = StockMovement.builder()
                .item(product)
                .locationType(LocationType.NOT_ASSIGNED)
                .previousQuantity(0)
                .currentQuantity(0)
                .quantityChange(0)
                .reason(StockMovementReason.KUJI_PRIZE_WON)
                .site(site)
                .build();

        StockMovement saved = inventoryOperations.recordMovement(kujiLedgerRow);

        StockMovement reloaded = stockMovementRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getFromLocationId()).isNull();
        assertThat(reloaded.getToLocationId()).isNull();
        assertThat(reloaded.getSite()).isNotNull();
        assertThat(reloaded.getSite().getId()).isEqualTo(site.getId());
    }

    @Test
    void concurrentApplyDeltaCallsEachDeriveTheirOwnCorrectSite() throws Exception {
        Location mainLocation = newLocation("MAIN", "concurrent-main");
        Location secondLocation = newLocation("SECOND", "concurrent-second");
        Product mainProduct = newProduct("concurrent-main");
        Product secondProduct = newProduct("concurrent-second");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<StockMovement> mainFuture = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return inventoryOperations.applyDelta(
                        mainLocation, mainProduct, 3, LocationType.BOX_BIN, null,
                        StockMovementReason.SALE, null, Map.of());
            });
            Future<StockMovement> secondFuture = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return inventoryOperations.applyDelta(
                        secondLocation, secondProduct, 4, LocationType.BOX_BIN, null,
                        StockMovementReason.SALE, null, Map.of());
            });

            start.countDown();
            StockMovement mainSaved = mainFuture.get(10, TimeUnit.SECONDS);
            StockMovement secondSaved = secondFuture.get(10, TimeUnit.SECONDS);

            assertThat(mainSaved.getSite().getId())
                    .isEqualTo(mainLocation.getStorageLocation().getSite().getId());
            assertThat(secondSaved.getSite().getId())
                    .isEqualTo(secondLocation.getStorageLocation().getSite().getId());
            assertThat(mainSaved.getSite().getId()).isNotEqualTo(secondSaved.getSite().getId());

            List<StockMovement> all = stockMovementRepository.findAll();
            assertThat(all).allSatisfy(m -> assertThat(m.getSite()).isNotNull());
        } finally {
            executor.shutdownNow();
        }
    }
}
