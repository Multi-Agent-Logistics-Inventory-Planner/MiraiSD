package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.catalog.application.ProductStockStateWriter;
import com.mirai.inventoryservice.inventory.application.StockMovementService;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.inventory.api.BatchAdjustLineDTO;
import com.mirai.inventoryservice.inventory.api.BatchAdjustStockRequestDTO;
import com.mirai.inventoryservice.models.audit.EventOutbox;
import com.mirai.inventoryservice.inventory.domain.StockMovement;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.repositories.EventOutboxRepository;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * AC-5's required atomic-commit proof (docs: .specs/phase-5b-catalog-facade/spec.md) for T-4's
 * {@code StockMovementService} migration: {@code batchAdjustInventory} creates the
 * {@code StockMovement} row, the {@code EventOutbox} row, and the {@code LocationInventory}
 * quantity change, then calls {@code applyProductActiveStatusFromTotals} — now delegating to the
 * *real* {@link ProductStockStateWriter} bean, not a stand-in — all inside the same
 * {@code @Transactional} method. If the last step's actual database write throws, every earlier
 * write in that same transaction must roll back too, not just the step that failed, and the
 * real writer's own product quantity/{@code isActive} update must roll back with it.
 *
 * <p>Deliberately exercises the real {@link ProductStockStateWriter} bean rather than mocking it
 * away: an earlier version of this test replaced it with {@code @MockBean}, which proved the
 * *shape* of atomicity (something at that call site failing rolls everything back) but never
 * proved the specific claim AC-5 makes — that the product's own {@code quantity}/{@code isActive}
 * commit or roll back together with the outbox row. Injecting the failure one layer deeper, via
 * {@code @SpyBean} on {@link ProductRepository} (the real dependency
 * {@code ProductStockStateWriter} itself uses), lets the writer's real fetch, dirty-check, and
 * derivation logic all run for real; only the final {@code saveAll} throws — the same failure
 * shape a real constraint violation or connection drop would produce at that exact point.
 *
 * <p>Deliberately does NOT extend {@code BaseIntegrationTest} (which wraps every test in one
 * outer rolled-back transaction) — that would prevent the method under test from ever really
 * committing or rolling back on its own, the same reasoning
 * {@code ProductLifecycleTransactionBehaviorIT} recorded for Phase 5a's equivalent check.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class StockMovementOutboxAtomicityIT {

    @Autowired private StockMovementService stockMovementService;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private EventOutboxRepository eventOutboxRepository;
    @Autowired private LocationInventoryRepository locationInventoryRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SiteRepository siteRepository;

    @SpyBean private ProductRepository productRepository;

    private LocationInventory inventory;
    private UUID inventoryId;
    private UUID productId;

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Category category = categoryRepository.save(Category.builder()
                .name("Atomicity Test Category " + suffix)
                .slug("atomicity-test-category-" + suffix)
                .build());

        Product product = productRepository.save(Product.builder()
                .sku("ATOMIC-" + suffix)
                .name("Atomicity Test Product")
                .category(category)
                .isActive(true)
                .quantity(20)
                .build());
        productId = product.getId();

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

        Location location = locationRepository.save(Location.builder()
                .storageLocation(boxBins)
                .locationCode("ATOMIC-" + suffix)
                .build());

        inventory = locationInventoryRepository.save(LocationInventory.builder()
                .location(location)
                .site(site)
                .product(product)
                .quantity(20)
                .build());
        inventoryId = inventory.getId();
    }

    @AfterEach
    void cleanup() {
        // No enclosing test transaction here (deliberately, see class javadoc), so each test's
        // real commits/rollbacks are visible to the next test's queries unless cleared -- matches
        // AdjustToKafkaIT's own cleanup() for the same reason.
        eventOutboxRepository.deleteAll();
        stockMovementRepository.deleteAll();
        locationInventoryRepository.deleteAll();
    }

    private BatchAdjustStockRequestDTO adjustRequest(int quantityChange) {
        return BatchAdjustStockRequestDTO.builder()
                .locationType(LocationType.BOX_BIN)
                .locationId(inventory.getLocation().getId())
                .reason(StockMovementReason.SALE)
                .adjustments(List.of(BatchAdjustLineDTO.builder()
                        .inventoryId(inventoryId)
                        .quantityChange(quantityChange)
                        .build()))
                .build();
    }

    @Test
    void batchAdjust_whenProductRepositorySaveAllFails_rollsBackStockMovementLocationInventoryOutboxAndProductStateToo() {
        // Real ProductStockStateWriter runs for real (fetch, dirty-check, derive isActive) --
        // only its final productRepository.saveAll(...) throws, the same failure shape a real
        // constraint violation or connection drop would produce at that exact point.
        doThrow(new RuntimeException("simulated ProductRepository.saveAll failure"))
                .when(productRepository).saveAll(any());

        assertThatThrownBy(() -> stockMovementService.batchAdjustInventory(adjustRequest(-20)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated ProductRepository.saveAll failure");

        // Nothing from this attempt is visible from a fresh read -- proving the StockMovement
        // save, the outbox row, the LocationInventory quantity change, AND the real
        // ProductStockStateWriter's own product quantity/isActive update all rolled back
        // together, not just the step that actually threw.
        assertThat(stockMovementRepository.findAll()).isEmpty();
        assertThat(eventOutboxRepository.findAll()).isEmpty();
        LocationInventory reloadedInventory = locationInventoryRepository.findById(inventoryId).orElseThrow();
        assertThat(reloadedInventory.getQuantity()).isEqualTo(20);
        Product reloadedProduct = productRepository.findById(productId).orElseThrow();
        assertThat(reloadedProduct.getQuantity()).isEqualTo(20);
        assertThat(reloadedProduct.getIsActive()).isTrue();
    }

    @Test
    void batchAdjust_onSuccess_stockMovementLocationInventoryOutboxAndProductStateAllCommitTogether() {
        // -20 drains the only inventory row to exactly 0, so the real ProductStockStateWriter's
        // shouldBeActive = total > 0 derivation flips isActive false -- proving the *specific*
        // product-state change AC-5 cares about, not just that some write happened.
        stockMovementService.batchAdjustInventory(adjustRequest(-20));

        List<StockMovement> movements = stockMovementRepository.findAll();
        List<EventOutbox> outboxEvents = eventOutboxRepository.findAll();
        Product reloadedProduct = productRepository.findById(productId).orElseThrow();

        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getQuantityChange()).isEqualTo(-20);
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEntityType()).isEqualTo("stock_movement");
        // A LocationInventory row drained to exactly 0 is deleted, not saved at quantity 0 --
        // existing batchAdjustInventory behavior, unrelated to this migration.
        assertThat(locationInventoryRepository.findById(inventoryId)).isEmpty();
        assertThat(reloadedProduct.getQuantity()).isEqualTo(0);
        assertThat(reloadedProduct.getIsActive()).isFalse();
    }
}
