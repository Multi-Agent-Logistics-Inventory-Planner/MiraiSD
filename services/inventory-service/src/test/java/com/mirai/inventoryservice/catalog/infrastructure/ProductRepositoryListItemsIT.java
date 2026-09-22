package com.mirai.inventoryservice.catalog.infrastructure;

import com.mirai.inventoryservice.catalog.api.CatalogProductResponse;
import com.mirai.inventoryservice.catalog.application.ProductListItemDTO;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for T-6 (docs: .specs/phase-5a-catalog-module-move/spec.md AC-6):
 * {@code ProductRepository.LIST_ITEM_SELECT} hardcodes {@code ProductListItemDTO}'s fully
 * qualified name in a JPQL constructor expression. Moving the DTO to {@code catalog.application}
 * without updating that string would compile clean and fail only at query execution time -- a
 * failure mode a mock-based unit test cannot see. This runs the query against real PostgreSQL
 * (H2 wouldn't exercise the same JPQL-to-SQL translation path with the same fidelity).
 */
class ProductRepositoryListItemsIT extends BaseKafkaIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findAllAsListItems_executesAgainstRealPostgres() {
        Category category = categoryRepository.save(Category.builder()
                .name("List Items IT Category " + System.nanoTime())
                .slug("list-items-it-category-" + System.nanoTime())
                .build());
        Product product = productRepository.save(Product.builder()
                .sku("LIST-ITEMS-IT-" + System.nanoTime())
                .name("List Items IT Product")
                .category(category)
                .isActive(true)
                .unitCost(new BigDecimal("1.23"))
                .msrp(new BigDecimal("4.56"))
                .build());

        var listItems = productRepository.findAllAsListItems();

        ProductListItemDTO found = listItems.stream()
                .filter(item -> item.getId().equals(product.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Product not found in findAllAsListItems()"));
        assertThat(found.getSku()).isEqualTo(product.getSku());
        assertThat(found.getName()).isEqualTo("List Items IT Product");
        assertThat(found.getCategory()).isNotNull();
        assertThat(found.getCategory().getName()).isEqualTo(category.getName());
    }

    @Test
    void findActiveAsListItems_filtersInactiveProducts() {
        Category category = categoryRepository.save(Category.builder()
                .name("List Items Active IT Category " + System.nanoTime())
                .slug("list-items-active-it-category-" + System.nanoTime())
                .build());
        Product active = productRepository.save(Product.builder()
                .sku("LIST-ITEMS-ACTIVE-" + System.nanoTime())
                .name("Active Product")
                .category(category)
                .isActive(true)
                .build());
        Product inactive = productRepository.save(Product.builder()
                .sku("LIST-ITEMS-INACTIVE-" + System.nanoTime())
                .name("Inactive Product")
                .category(category)
                .isActive(false)
                .build());

        var activeItems = productRepository.findActiveAsListItems();

        assertThat(activeItems.stream().map(ProductListItemDTO::getId))
                .contains(active.getId())
                .doesNotContain(inactive.getId());
    }

    /**
     * Regression for the egress-audit fix: hasChildren used to require a separate uncached
     * SELECT DISTINCT parent_id FROM products scan per list call (ProductService.withHasChildren)
     * -- the single most-called query in the database. Folding it into LIST_ITEM_SELECT's
     * projection removes that second query entirely; this proves the folded EXISTS subquery
     * computes the same true/false result the old two-query approach did.
     */
    @Test
    void findAllAsListItems_computesHasChildrenInTheSameQuery() {
        Category category = categoryRepository.save(Category.builder()
                .name("List Items HasChildren IT Category " + System.nanoTime())
                .slug("list-items-haschildren-it-category-" + System.nanoTime())
                .build());
        Product parent = productRepository.save(Product.builder()
                .sku("LIST-ITEMS-PARENT-" + System.nanoTime())
                .name("Parent Product")
                .category(category)
                .isActive(true)
                .build());
        Product child = productRepository.save(Product.builder()
                .sku("LIST-ITEMS-CHILD-" + System.nanoTime())
                .name("Child Product")
                .category(category)
                .parent(parent)
                .isActive(true)
                .build());
        Product leaf = productRepository.save(Product.builder()
                .sku("LIST-ITEMS-LEAF-" + System.nanoTime())
                .name("Leaf Product")
                .category(category)
                .isActive(true)
                .build());

        var listItems = productRepository.findAllAsListItems();
        var byId = listItems.stream()
                .collect(java.util.stream.Collectors.toMap(ProductListItemDTO::getId, i -> i));

        assertThat(byId.get(parent.getId()).getHasChildren()).isTrue();
        assertThat(byId.get(child.getId()).getHasChildren()).isFalse();
        assertThat(byId.get(leaf.getId()).getHasChildren()).isFalse();
    }
    /**
     * Regression for AC-5 (.specs/catalog-product-egress/spec.md): the v1 catalog list projection
     * (CATALOG_LIST_SELECT) must compute hasChildren, build the full nested category object, and
     * resolve preferredSupplierName through the same LEFT JOIN LIST_ITEM_SELECT uses -- all
     * without a full-entity JOIN FETCH. Run against real Postgres for the same JPQL-to-SQL
     * fidelity reason findAllAsListItems_executesAgainstRealPostgres above exists.
     */
    /**
     * {@code Supplier.canonicalName} is {@code insertable = false} -- populated by a Postgres
     * trigger (V22 migration) this Testcontainers instance's Hibernate {@code ddl-auto=create-drop}
     * schema does not replicate (same limitation documented in
     * {@code CatalogTransactionPropagationIT.createSupplier}). Insert directly instead.
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

    @Test
    void findAllCatalogListItems_computesHasChildrenCategoryAndPreferredSupplier() {
        Category category = categoryRepository.save(Category.builder()
                .name("Catalog List Items IT Category " + System.nanoTime())
                .slug("catalog-list-items-it-category-" + System.nanoTime())
                .build());
        String supplierDisplayName = "Catalog List Items IT Supplier " + System.nanoTime();
        UUID supplierId = createSupplier(supplierDisplayName);
        // preferredSupplierId is insertable=false/updatable=false (a read-only FK shadow, same
        // pattern as parentId) -- set the association itself with an unmanaged reference entity
        // so Hibernate persists the FK without a full Supplier load.
        Product parent = productRepository.save(Product.builder()
                .sku("CATALOG-LIST-PARENT-" + System.nanoTime())
                .name("Catalog List Parent Product")
                .category(category)
                .isActive(true)
                .preferredSupplier(com.mirai.inventoryservice.catalog.domain.Supplier.builder().id(supplierId).build())
                .preferredSupplierAuto(true)
                .build());
        Product leaf = productRepository.save(Product.builder()
                .sku("CATALOG-LIST-LEAF-" + System.nanoTime())
                .name("Catalog List Leaf Product")
                .category(category)
                .isActive(true)
                .build());
        productRepository.save(Product.builder()
                .sku("CATALOG-LIST-CHILD-" + System.nanoTime())
                .name("Catalog List Child Product")
                .category(category)
                .parent(parent)
                .isActive(true)
                .build());

        var listItems = productRepository.findAllCatalogListItems();
        var byId = listItems.stream()
                .collect(java.util.stream.Collectors.toMap(CatalogProductResponse::getId, i -> i));

        CatalogProductResponse parentDto = byId.get(parent.getId());
        assertThat(parentDto.getHasChildren()).isTrue();
        assertThat(parentDto.getCategory()).isNotNull();
        assertThat(parentDto.getCategory().getId()).isEqualTo(category.getId());
        assertThat(parentDto.getCategory().getName()).isEqualTo(category.getName());
        assertThat(parentDto.getPreferredSupplierId()).isEqualTo(supplierId);
        assertThat(parentDto.getPreferredSupplierName()).isEqualTo(supplierDisplayName);
        assertThat(parentDto.getPreferredSupplierAuto()).isTrue();

        CatalogProductResponse leafDto = byId.get(leaf.getId());
        assertThat(leafDto.getHasChildren()).isFalse();
        assertThat(leafDto.getPreferredSupplierId()).isNull();
    }
}
