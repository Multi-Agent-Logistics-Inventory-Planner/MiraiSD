package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.ProductInUseException;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.catalog.application.ProductService;
import com.mirai.inventoryservice.services.SupabaseBroadcastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Transactional-integrity coverage for the Phase 5a T-4/T-4b extraction (docs:
 * .specs/phase-5a-catalog-module-move/spec.md), which {@code ProductDeletionCoordinatorIT} could
 * not provide: that class extends {@code BaseIntegrationTest}, which is itself
 * {@code @Transactional} and rolls every test back, so it can prove wiring but not commit,
 * rollback, or after-commit-broadcast behavior.
 *
 * <p>This class deliberately does NOT extend {@code BaseIntegrationTest} — no enclosing test
 * transaction — so each {@code @Transactional} service/coordinator call under test runs its own
 * real transaction that actually commits or rolls back, and state is verified from a fresh query
 * afterward. The five catalog ports and {@link SupabaseBroadcastService} are mocked so specific
 * failures can be injected at specific points in an otherwise-real flow (real repositories, real
 * transaction manager).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ProductLifecycleTransactionBehaviorIT {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductDeletionCoordinator productDeletionCoordinator;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @MockBean
    private InitialStockPort initialStockPort;

    @MockBean
    private ForecastPurgePort forecastPurgePort;

    @MockBean
    private MachineDisplayCleanupPort machineDisplayCleanupPort;

    @MockBean
    private InventoryCleanupPort inventoryCleanupPort;

    @MockBean
    private KujiBoxCleanupPort kujiBoxCleanupPort;

    @MockBean
    private ShipmentUsageGuardPort shipmentUsageGuardPort;

    @MockBean
    private SupabaseBroadcastService broadcastService;

    private UUID categoryId;

    @BeforeEach
    void setUp() {
        reset(initialStockPort, forecastPurgePort, machineDisplayCleanupPort,
                inventoryCleanupPort, kujiBoxCleanupPort, shipmentUsageGuardPort, broadcastService);
        // Harmless defaults: a plain create/delete with no injected failure should just work.
        when(shipmentUsageGuardPort.isUsedInShipment(any())).thenReturn(false);
        when(kujiBoxCleanupPort.deleteBoxesAndTiersForProduct(any()))
                .thenReturn(new KujiBoxCleanupPort.Result(0, 0));

        Category category = categoryRepository.save(Category.builder()
                .name("Tx Behavior Test Category " + System.nanoTime())
                .slug("tx-behavior-test-category-" + System.nanoTime())
                .build());
        categoryId = category.getId();
    }

    private UUID createParent(String sku, Integer initialStock) {
        Product product = productService.createProduct(
                sku, categoryId, null, null, null, sku, "desc", null, null, null,
                null, null, null, null, initialStock, null, null, null, null);
        return product.getId();
    }

    // ---- T-1: initial-stock failure rolls back product creation ----

    @Test
    void initialStockFailureRollsBackProductCreation() {
        String sku = "TX-CREATE-FAIL-" + System.nanoTime();
        doThrow(new RuntimeException("simulated stock-tracking failure"))
                .when(initialStockPort).recordInitialStock(any(), eq(5));

        assertThatThrownBy(() -> createParent(sku, 5))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated stock-tracking failure");

        assertThat(productRepository.existsBySku(sku))
                .as("product row must not survive when initial-stock recording fails mid-transaction")
                .isFalse();
    }

    // ---- T-2: forecast-purge failure rolls back the update (including the save) ----

    @Test
    void forecastPurgeFailureRollsBackUpdate() {
        String sku = "TX-UPDATE-FAIL-" + System.nanoTime();
        UUID productId = createParent(sku, null);
        productService.updateProduct(
                productId, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                true); // forecastingEnabled starts true
        // Clear setup-triggered invocations (createProduct/updateProduct broadcast eagerly,
        // unrelated to this test) before the real assertion.
        reset(forecastPurgePort, broadcastService);

        doThrow(new RuntimeException("simulated forecast-purge failure"))
                .when(forecastPurgePort).purgeForecastsForProduct(eq(productId));

        assertThatThrownBy(() -> productService.updateProduct(
                productId, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                false)) // attempt true -> false
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated forecast-purge failure");

        Product reloaded = productRepository.findById(productId).orElseThrow();
        assertThat(reloaded.getForecastingEnabled())
                .as("both the forecastingEnabled save and the purge must roll back together")
                .isTrue();
        verify(broadcastService, never()).broadcastProductUpdated(any());
    }

    // ---- T-3: deletion failure restores a previously deleted dependent row (child product) ----

    @Test
    void deletionFailureRestoresPreviouslyDeletedChildRow() {
        String parentSku = "TX-DEL-PARENT-" + System.nanoTime();
        UUID parentId = createParent(parentSku, null);
        Product child = productService.createProduct(
                "TX-DEL-CHILD-" + System.nanoTime(), null, parentId, null, null,
                "child", "desc", null, null, null, null, null, null, null, null,
                null, null, null, null);
        UUID childId = child.getId();
        reset(broadcastService); // clear the two eager creation broadcasts above

        // Children are batch-cleaned (and productRepository.deleteAll(children) executed, a real
        // delete) BEFORE the parent's own single-item forecastPurgePort call. Making that later
        // call throw proves the earlier real child delete rolls back with it, not just the parent.
        doNothing().when(forecastPurgePort).purgeForecastsForProducts(anyCollection());
        doThrow(new RuntimeException("simulated forecast-purge failure"))
                .when(forecastPurgePort).purgeForecastsForProduct(eq(parentId));

        assertThatThrownBy(() -> productDeletionCoordinator.deleteProduct(parentId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated forecast-purge failure");

        assertThat(productRepository.existsById(parentId))
                .as("parent must survive when a later cleanup step throws")
                .isTrue();
        assertThat(productRepository.existsById(childId))
                .as("child row deleted earlier in the same transaction must be restored on rollback")
                .isTrue();
        verify(broadcastService, never()).broadcastProductUpdated(any());
    }

    // ---- T-4: a successful deletion broadcasts only after commit ----

    @Test
    void successfulDeletionBroadcastsAfterCommit() {
        String sku = "TX-DEL-SUCCESS-" + System.nanoTime();
        UUID productId = createParent(sku, null);
        reset(broadcastService); // clear the eager creation broadcast above

        productDeletionCoordinator.deleteProduct(productId);

        assertThat(productRepository.existsById(productId)).isFalse();
        verify(broadcastService).broadcastProductUpdated(List.of(productId.toString()));
    }

    // ---- Shipment-guard rejection: parent and child, per finding P2 ----

    @Test
    void shipmentGuardRejectsAReferencedParent_noCleanupOrDeletionOccurs() {
        UUID productId = createParent("TX-GUARD-PARENT-" + System.nanoTime(), null);
        reset(broadcastService); // clear the eager creation broadcast above
        when(shipmentUsageGuardPort.isUsedInShipment(productId)).thenReturn(true);

        assertThatThrownBy(() -> productDeletionCoordinator.deleteProduct(productId))
                .isInstanceOf(ProductInUseException.class);

        assertThat(productRepository.existsById(productId)).isTrue();
        verify(forecastPurgePort, never()).purgeForecastsForProduct(any());
        verify(forecastPurgePort, never()).purgeForecastsForProducts(any());
        verify(machineDisplayCleanupPort, never()).deleteDisplaysForProduct(any());
        verify(inventoryCleanupPort, never()).deleteInventoryForProduct(any());
        verify(inventoryCleanupPort, never()).deleteStockMovementsForProduct(any());
        verify(kujiBoxCleanupPort, never()).deleteBoxesAndTiersForProduct(any());
        verify(broadcastService, never()).broadcastProductUpdated(any());
    }

    @Test
    void shipmentGuardRejectsAReferencedChild_protectsSiblingsFromPartialDeletion() {
        UUID parentId = createParent("TX-GUARD-PARENT2-" + System.nanoTime(), null);
        Product child = productService.createProduct(
                "TX-GUARD-CHILD-" + System.nanoTime(), null, parentId, null, null,
                "child", "desc", null, null, null, null, null, null, null, null,
                null, null, null, null);
        UUID childId = child.getId();
        reset(broadcastService); // clear the two eager creation broadcasts above
        when(shipmentUsageGuardPort.isUsedInShipment(childId)).thenReturn(true);

        assertThatThrownBy(() -> productDeletionCoordinator.deleteProduct(parentId))
                .isInstanceOf(ProductInUseException.class);

        // Neither parent nor the (unrelated, non-blocked) sibling-check path should have deleted
        // anything -- discovery of a blocked child must happen before any child is removed.
        assertThat(productRepository.existsById(parentId)).isTrue();
        assertThat(productRepository.existsById(childId)).isTrue();
        verify(forecastPurgePort, never()).purgeForecastsForProducts(any());
        verify(inventoryCleanupPort, never()).deleteInventoryForProducts(any());
        verify(inventoryCleanupPort, never()).deleteStockMovementsForProducts(any());
        verify(machineDisplayCleanupPort, never()).deleteDisplaysForProducts(any());
        verify(kujiBoxCleanupPort, never()).deleteBoxesAndTiersForProduct(any());
        verify(broadcastService, never()).broadcastProductUpdated(any());
    }
}
