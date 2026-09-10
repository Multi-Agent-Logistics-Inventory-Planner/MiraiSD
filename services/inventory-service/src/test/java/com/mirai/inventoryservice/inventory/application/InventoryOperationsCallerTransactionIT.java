package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import com.mirai.inventoryservice.models.audit.EventOutbox;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * .specs/phase-6-inventory/log.md T-5 (AC-1 "facades preserve caller transactions"): proves
 * {@link InventoryOperations#applyDelta} does not open its own transaction boundary — it joins
 * whatever transaction the caller (e.g. {@code ShipmentService}) already has open, the same way
 * {@code StockMovementOutboxAtomicityIT} proves for {@code StockMovementService}'s own
 * {@code batchAdjustInventory}. {@code @Transactional}'s default propagation is {@code REQUIRED},
 * so a caller-opened transaction that later fails must roll back the {@code LocationInventory}
 * write, the {@code StockMovement} row and the {@code EventOutbox} row {@code applyDelta} made
 * earlier in that same transaction — not just whatever step actually threw.
 *
 * <p>Deliberately does NOT extend {@code BaseIntegrationTest} (which wraps every test in one
 * outer rolled-back transaction) for the same reason {@code StockMovementOutboxAtomicityIT}
 * doesn't: that would prevent the harness's own commit/rollback from ever being real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(InventoryOperationsCallerTransactionIT.CallerTransactionHarness.class)
class InventoryOperationsCallerTransactionIT {

    @Autowired private InventoryOperations inventoryOperations;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private LocationInventoryRepository locationInventoryRepository;
    @Autowired private EventOutboxRepository eventOutboxRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SiteRepository siteRepository;
    @Autowired private CallerTransactionHarness harness;

    private Product product;
    private Location location;

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Category category = categoryRepository.save(Category.builder()
                .name("Caller Tx Test Category " + suffix)
                .slug("caller-tx-test-category-" + suffix)
                .build());

        product = productRepository.save(Product.builder()
                .sku("CALLERTX-" + suffix)
                .name("Caller Tx Test Product")
                .category(category)
                .isActive(true)
                .quantity(0)
                .build());

        Site site = siteRepository.findByCode("MAIN")
                .orElseGet(() -> siteRepository.save(Site.builder().code("MAIN").name("Main").build()));

        StorageLocation boxBins = storageLocationRepository.findByCodeAndSite_Code("BOX_BINS", "MAIN")
                .orElseGet(() -> storageLocationRepository.save(StorageLocation.builder()
                        .site(site)
                        .code("BOX_BINS")
                        .name("Box Bins")
                        .hasDisplay(false)
                        .isDisplayOnly(false)
                        .displayOrder(1)
                        .build()));

        location = locationRepository.save(Location.builder()
                .storageLocation(boxBins)
                .locationCode("CALLERTX-" + suffix)
                .build());
    }

    @AfterEach
    void cleanup() {
        // No enclosing test transaction here (deliberately, see class javadoc), so each test's
        // real commits/rollbacks are visible to the next test's queries unless cleared -- matches
        // StockMovementOutboxAtomicityIT's own cleanup() for the same reason.
        eventOutboxRepository.deleteAll();
        stockMovementRepository.deleteAll();
        locationInventoryRepository.deleteAll();
    }

    @Test
    void applyDelta_whenCallerTransactionFailsAfterward_rollsBackInventoryMovementAndOutboxTogether() {
        assertThatThrownBy(() -> harness.applyDeltaThenFail(location, product, 5))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated caller failure after applyDelta");

        // Nothing from this attempt is visible from a fresh read -- proving applyDelta's
        // LocationInventory write, StockMovement save and outbox publish all rolled back
        // together with the rest of the caller's transaction, not just committed independently
        // because applyDelta is itself @Transactional.
        assertThat(stockMovementRepository.findAll()).isEmpty();
        assertThat(eventOutboxRepository.findAll()).isEmpty();
        assertThat(locationInventoryRepository.findByLocation_IdAndProduct_Id(location.getId(), product.getId()))
                .isEmpty();
    }

    @Test
    void applyDelta_whenCallerTransactionCommits_inventoryMovementAndOutboxAllPersistTogether() {
        harness.applyDeltaThenSucceed(location, product, 5);

        LocationInventory inventory = locationInventoryRepository
                .findByLocation_IdAndProduct_Id(location.getId(), product.getId())
                .orElseThrow();
        assertThat(inventory.getQuantity()).isEqualTo(5);

        assertThat(stockMovementRepository.findAll()).hasSize(1);
        assertThat(stockMovementRepository.findAll().get(0).getQuantityChange()).isEqualTo(5);

        List<EventOutbox> outboxEvents = eventOutboxRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEntityType()).isEqualTo("stock_movement");
    }

    /**
     * Stand-in for a production caller (e.g. {@code ShipmentService.addToInventory}): opens its
     * own transaction, calls {@code applyDelta} partway through, then either returns normally or
     * fails — exactly the shape the AC-1 claim needs proven against a real caller boundary rather
     * than {@code InventoryOperations}'s own transaction.
     */
    @TestComponent
    static class CallerTransactionHarness {
        private final InventoryOperations inventoryOperations;

        CallerTransactionHarness(InventoryOperations inventoryOperations) {
            this.inventoryOperations = inventoryOperations;
        }

        @Transactional
        public void applyDeltaThenFail(Location location, Product product, int quantityDelta) {
            inventoryOperations.applyDelta(
                    location, product, quantityDelta, LocationType.BOX_BIN, null,
                    StockMovementReason.SALE, null, Map.of());
            throw new RuntimeException("simulated caller failure after applyDelta");
        }

        @Transactional
        public void applyDeltaThenSucceed(Location location, Product product, int quantityDelta) {
            inventoryOperations.applyDelta(
                    location, product, quantityDelta, LocationType.BOX_BIN, null,
                    StockMovementReason.SALE, null, Map.of());
        }
    }
}
