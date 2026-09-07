package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2 finding (5b T-1 review): {@code CatalogEntityAccess}, {@code CatalogCommands}, and
 * {@code ProductStockStateWriter} originally used the default {@code @Transactional} propagation
 * ({@code REQUIRED}), which silently opens a new transaction when none exists — contradicting
 * "joins the caller's existing transaction, never opens its own" and risking a detached
 * reference. All three now use {@code Propagation.MANDATORY}. This is Spring-AOP-interceptor
 * behavior, invisible to a plain Mockito unit test (no proxy, no interceptor) — it requires a
 * real {@code ApplicationContext} with real transaction management, hence an IT rather than
 * living alongside the other four facade unit tests.
 *
 * <p>Deliberately does NOT extend {@code BaseIntegrationTest} (which wraps every test in one
 * outer rolled-back transaction) — the "no active transaction" cases require there to genuinely
 * be none, and the "joins caller's transaction" cases need a real, distinct transaction under
 * test's control via {@link TransactionTemplate}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class CatalogTransactionPropagationIT {

    @Autowired private CatalogEntityAccess catalogEntityAccess;
    @Autowired private CatalogCommands catalogCommands;
    @Autowired private ProductStockStateWriter productStockStateWriter;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private TransactionTemplate transactionTemplate;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        Category category = categoryRepository.save(Category.builder()
                .name("Tx Propagation Test Category " + System.nanoTime())
                .slug("tx-propagation-test-category-" + System.nanoTime())
                .build());
        categoryId = category.getId();
    }

    /**
     * {@code Supplier.canonicalName} is {@code insertable = false} — populated by a Postgres
     * trigger (V22 migration) that the H2 test schema (plain {@code ddl-auto=create-drop}, no
     * triggers) does not replicate. {@code SupplierService} relies on that trigger in production;
     * under this test's "test" (H2) profile, a plain {@code supplierRepository.save(...)} would
     * violate {@code canonical_name}'s {@code NOT NULL} constraint since Hibernate never sends a
     * value for an {@code insertable = false} column. Insert directly instead.
     */
    private UUID createSupplier(String displayName) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO suppliers (id, display_name, canonical_name, is_active, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, displayName, displayName.toLowerCase(), true, now, now);
        return id;
    }

    private UUID createProduct(int quantity, boolean isActive) {
        Product saved = transactionTemplate.execute(status -> productRepository.save(Product.builder()
                .sku("TXPROP-" + System.nanoTime())
                .name("Widget")
                .category(Category.builder().id(categoryId).build())
                .quantity(quantity)
                .isActive(isActive)
                .build()));
        return saved.getId();
    }

    // ---- No active transaction: every method must reject, not silently open one ----

    @Test
    void catalogEntityAccess_getReference_withNoTransaction_throws() {
        UUID productId = createProduct(0, false);

        assertThatThrownBy(() -> catalogEntityAccess.getReference(productId))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void catalogEntityAccess_requireManagedProduct_withNoTransaction_throws() {
        UUID productId = createProduct(0, false);

        assertThatThrownBy(() -> catalogEntityAccess.requireManagedProduct(productId))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void productStockStateWriter_applyStockState_withNoTransaction_throws() {
        UUID productId = createProduct(0, false);

        assertThatThrownBy(() -> productStockStateWriter.applyStockState(productId, 5, true))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void productStockStateWriter_setActive_withNoTransaction_throws() {
        UUID productId = createProduct(0, false);

        assertThatThrownBy(() -> productStockStateWriter.setActive(productId, true))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void productStockStateWriter_applyStockStateBatch_withNoTransaction_throws() {
        UUID productId = createProduct(0, false);

        assertThatThrownBy(() -> productStockStateWriter.applyStockStateBatch(
                Map.of(productId, new ProductStockStateWriter.StockState(5, true))))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void catalogCommands_assignPreferredSupplierFromDelivery_withNoTransaction_throws() {
        UUID productId = createProduct(0, false);
        UUID supplierId = createSupplier("Acme " + System.nanoTime());

        assertThatThrownBy(() -> catalogCommands.assignPreferredSupplierFromDelivery(
                supplierId, List.of(productId)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    // ---- Active transaction: joins it, and a rollback of the outer transaction rolls the write back too ----

    @Test
    void productStockStateWriter_applyStockState_joinsCallersTransaction_rollsBackWithIt() {
        UUID productId = createProduct(0, false);

        transactionTemplate.execute(status -> {
            boolean changed = productStockStateWriter.applyStockState(productId, 7, true);
            assertThat(changed).isTrue();
            status.setRollbackOnly();
            return null;
        });

        Product reloaded = transactionTemplate.execute(status -> productRepository.findById(productId).orElseThrow());
        assertThat(reloaded.getQuantity()).isEqualTo(0);
        assertThat(reloaded.getIsActive()).isFalse();
    }

    @Test
    void catalogEntityAccess_requireManagedProduct_joinsCallersTransaction_succeeds() {
        UUID productId = createProduct(3, true);

        Boolean found = transactionTemplate.execute(status ->
                catalogEntityAccess.requireManagedProduct(productId) != null);

        assertThat(found).isTrue();
    }

    @Test
    void catalogCommands_assignPreferredSupplierFromDelivery_joinsCallersTransaction_rollsBackWithIt() {
        UUID productId = createProduct(0, false);
        UUID supplierId = createSupplier("Acme " + System.nanoTime());

        transactionTemplate.execute(status -> {
            List<UUID> updated = catalogCommands.assignPreferredSupplierFromDelivery(supplierId, List.of(productId));
            assertThat(updated).containsExactly(productId);
            status.setRollbackOnly();
            return null;
        });

        Product reloaded = transactionTemplate.execute(status -> productRepository.findById(productId).orElseThrow());
        assertThat(reloaded.getPreferredSupplierId()).isNull();
    }
}
