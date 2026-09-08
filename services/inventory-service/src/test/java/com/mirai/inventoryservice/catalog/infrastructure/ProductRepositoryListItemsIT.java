package com.mirai.inventoryservice.catalog.infrastructure;

import com.mirai.inventoryservice.catalog.application.ProductListItemDTO;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

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
}
