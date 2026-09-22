package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.api.CatalogProductResponse;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-6 (.specs/catalog-product-egress/spec.md): the comprehensive egress proof for the three
 * product-list read paths this fix touched. Each must issue exactly one JDBC statement per call
 * -- both the legacy list (B2's hasChildren fold removed a second, uncached
 * {@code SELECT DISTINCT parent_id} scan) and the two v1 endpoints (B3/B5's slim projections
 * removed a JOIN FETCH across category/parent-category/parent-product) -- and the v1 site-products
 * statement must additionally carry no JOIN at all, since {@code EffectiveProductSettings} needs
 * nothing beyond the {@code products} table itself.
 *
 * <p>Same {@code getPrepareStatementCount()}/{@link CapturingStatementInspector} approach as
 * {@link CatalogQueriesEgressIT}, for the same reason: a query-execution counter alone would miss
 * a lazy-load select, and an equal statement count alone would not prove the site-owned columns
 * ({@code unit_cost}, {@code msrp}, {@code reorder_point}, etc.) are absent from the v1 catalog
 * response's SQL, which is exactly the property AC-4/AC-5 depend on.
 */
@Transactional
class ProductListEgressIT extends BaseKafkaIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @DynamicPropertySource
    static void enableHibernateStatisticsAndSqlCapture(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
        registry.add("spring.jpa.properties.hibernate.session_factory.statement_inspector",
                () -> "com.mirai.inventoryservice.catalog.application.CapturingStatementInspector");
    }

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        CapturingStatementInspector.clear();
    }

    private UUID seedCategoryAndTwoProductsWithAChild() {
        Category category = categoryRepository.save(Category.builder()
                .name("ProductListEgress Category " + System.nanoTime())
                .slug("product-list-egress-category-" + System.nanoTime())
                .build());
        Product parent = productRepository.save(Product.builder()
                .sku("PLE-PARENT-" + System.nanoTime())
                .name("Parent Product")
                .category(category)
                .isActive(true)
                .unitCost(new java.math.BigDecimal("1.23"))
                .msrp(new java.math.BigDecimal("4.56"))
                .build());
        productRepository.save(Product.builder()
                .sku("PLE-CHILD-" + System.nanoTime())
                .name("Child Product")
                .category(category)
                .parent(parent)
                .isActive(true)
                .build());
        return category.getId();
    }

    @Test
    void findAllAsListItems_issuesOneStatement_noSeparateParentIdsScan() {
        seedCategoryAndTwoProductsWithAChild();
        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        List<ProductListItemDTO> items = productRepository.findAllAsListItems();

        assertThat(items.size()).isGreaterThanOrEqualTo(2);
        assertThat(statistics.getPrepareStatementCount())
                .as("findAllAsListItems() must issue exactly one statement -- hasChildren is a "
                        + "correlated EXISTS in this query, not a second SELECT DISTINCT "
                        + "parent_id scan (the pre-fix behavior)")
                .isEqualTo(1L);
    }

    @Test
    void findAllCatalogListItems_issuesOneStatement_excludesSiteOwnedColumns() {
        seedCategoryAndTwoProductsWithAChild();
        entityManager.flush();
        entityManager.clear();
        CapturingStatementInspector.clear();
        statistics.clear();

        List<CatalogProductResponse> items = productRepository.findAllCatalogListItems();

        assertThat(items.size()).isGreaterThanOrEqualTo(2);
        assertThat(statistics.getPrepareStatementCount())
                .as("findAllCatalogListItems() must issue exactly one statement, not a JOIN "
                        + "FETCH across category/parent-category/parent-product")
                .isEqualTo(1L);

        List<String> sql = CapturingStatementInspector.captured();
        assertThat(sql).hasSize(1);
        String statement = sql.get(0).toLowerCase(Locale.ROOT);
        assertThat(statement)
                .as("CatalogProductResponse is master-identity-only -- it must never select "
                        + "site-owned columns, regardless of what EffectiveProductSettings needs "
                        + "them for elsewhere")
                .doesNotContain("unit_cost")
                .doesNotContain("msrp")
                .doesNotContain("reorder_point")
                .doesNotContain("target_stock_level")
                .doesNotContain("lead_time_days")
                .doesNotContain("forecasting_enabled")
                // A plain substring check for "quantity" would also match the legitimate
                // master-identity column template_quantity (kuji prize child sizing) -- a
                // negative lookbehind excludes that one column while still catching the real
                // site-owned quantity column regardless of Hibernate's chosen table alias.
                .doesNotContainPattern("(?<!template_)\\bquantity\\b");
    }

    @Test
    void findAllSettingsDefaults_issuesOneStatement_withNoJoinAtAll() {
        seedCategoryAndTwoProductsWithAChild();
        entityManager.flush();
        entityManager.clear();
        CapturingStatementInspector.clear();
        statistics.clear();

        List<ProductSettingsDefaults> items = productRepository.findAllSettingsDefaults();

        assertThat(items.size()).isGreaterThanOrEqualTo(2);
        assertThat(statistics.getPrepareStatementCount())
                .as("findAllSettingsDefaults() must issue exactly one statement, not the "
                        + "JOIN FETCH across category/parent-category/parent-product that "
                        + "SiteProductController.getSiteProducts used before this fix")
                .isEqualTo(1L);

        List<String> sql = CapturingStatementInspector.captured();
        assertThat(sql).hasSize(1);
        String statement = sql.get(0).toLowerCase(Locale.ROOT);
        assertThat(statement)
                .as("EffectiveProductSettings needs id/sku/name plus 6 settings columns from "
                        + "products alone -- no category, no parent, no preferredSupplier join")
                .doesNotContain("join");
    }
}
