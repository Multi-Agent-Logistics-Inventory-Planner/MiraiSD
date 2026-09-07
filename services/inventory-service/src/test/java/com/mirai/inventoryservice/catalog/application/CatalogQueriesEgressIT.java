package com.mirai.inventoryservice.catalog.application;

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
 * AC-4's required N+1/pooler-egress regression check for
 * {@code CatalogQueries.allProductRefs()} (docs: .specs/phase-5b-catalog-facade/spec.md) —
 * against real PostgreSQL, not H2, since the behavior under test (Hibernate/JDBC statement
 * counts and the actual SELECT column list) is not something a mock-based unit test can observe.
 *
 * <p><b>Two prior versions of this check were both insufficient, per review — both fixed here,
 * not just documented as gaps:</b>
 * <ul>
 *   <li>{@code Statistics.getQueryExecutionCount()} only counts Hibernate "query" executions
 *   (HQL/JPQL/Criteria), not every JDBC round trip — a lazy-load select triggered by touching an
 *   uninitialized association would not increment it, so a real N+1 could pass this counter at 1.
 *   Replaced with {@code getPrepareStatementCount()}, which counts every JDBC
 *   {@code PreparedStatement} regardless of how Hibernate triggered it.</li>
 *   <li>Equal statement counts don't prove equal egress — {@code allProductRefs()} could still
 *   select more columns per row than the existing slim {@code ProductListItemDTO} projection.
 *   Fixed at the source, not just measured: {@code ProductRepository.findAllProductRefs()} is now
 *   its own JPQL constructor-expression projection (same pattern as {@code LIST_ITEM_SELECT}),
 *   not {@code findAll()} plus in-memory mapping — so it never selects {@code description}/
 *   {@code notes}/other columns {@link ProductRef} doesn't expose. This test captures the actual
 *   SQL via {@link CapturingStatementInspector} and asserts that directly, rather than inferring
 *   it from anything indirect.</li>
 * </ul>
 *
 * <p>Extends {@link BaseKafkaIntegrationTest} for the established real-Postgres Testcontainers
 * setup (same precedent as {@code ProductRepositoryListItemsIT}).
 */
@Transactional
class CatalogQueriesEgressIT extends BaseKafkaIntegrationTest {

    @Autowired
    private CatalogQueries catalogQueries;

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

    private UUID seedCategory(String name) {
        return categoryRepository.save(Category.builder()
                .name(name + " " + System.nanoTime())
                .slug("egress-it-" + name.toLowerCase(Locale.ROOT) + "-" + System.nanoTime())
                .build()).getId();
    }

    private UUID seedProduct(String sku, UUID categoryId) {
        return productRepository.save(Product.builder()
                .sku(sku + "-" + System.nanoTime())
                .name(sku)
                .category(Category.builder().id(categoryId).build())
                .isActive(true)
                .description("egress-it should never see this column")
                .notes("egress-it should never see this column either")
                .build()).getId();
    }

    @Test
    void allProductRefs_issuesOneStatement_notOnePerProductOrCategory() {
        // Multiple products across multiple distinct categories -- if resolving categoryId
        // triggered a select per row (the N+1 this design specifically avoids), the statement
        // count would scale with product count, not stay flat at 1.
        UUID categoryA = seedCategory("EgressCategoryA");
        UUID categoryB = seedCategory("EgressCategoryB");
        UUID categoryC = seedCategory("EgressCategoryC");
        seedProduct("EGRESS-1", categoryA);
        seedProduct("EGRESS-2", categoryA);
        seedProduct("EGRESS-3", categoryB);
        seedProduct("EGRESS-4", categoryB);
        seedProduct("EGRESS-5", categoryC);

        // Force a real round trip: nothing left in the first-level cache to short-circuit on.
        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        List<ProductRef> refs = catalogQueries.allProductRefs();

        assertThat(refs.size()).isGreaterThanOrEqualTo(5);
        assertThat(statistics.getPrepareStatementCount())
                .as("allProductRefs() must prepare exactly one JDBC statement regardless of "
                        + "product/category count -- getPrepareStatementCount() counts every "
                        + "statement Hibernate sends to the driver, including lazy-load selects "
                        + "that getQueryExecutionCount() would miss")
                .isEqualTo(1L);
        assertThat(refs).extracting(ProductRef::categoryId)
                .contains(categoryA, categoryB, categoryC);
    }

    @Test
    void allProductRefs_selectsTheSameSlimColumnShapeAsTheExistingListItemProjection() {
        UUID categoryId = seedCategory("EgressCompareCategory");
        seedProduct("EGRESS-CMP-1", categoryId);
        seedProduct("EGRESS-CMP-2", categoryId);
        seedProduct("EGRESS-CMP-3", categoryId);
        entityManager.flush();
        entityManager.clear();

        CapturingStatementInspector.clear();
        statistics.clear();
        catalogQueries.allProductRefs();
        long productRefStatementCount = statistics.getPrepareStatementCount();
        List<String> productRefSql = CapturingStatementInspector.captured();

        entityManager.clear();
        CapturingStatementInspector.clear();
        statistics.clear();
        productRepository.findAllAsListItems();
        long listItemStatementCount = statistics.getPrepareStatementCount();
        List<String> listItemSql = CapturingStatementInspector.captured();

        assertThat(productRefStatementCount)
                .as("allProductRefs() must not regress to more statements than the existing "
                        + "ProductListItemDTO projection it is meant to match")
                .isEqualTo(listItemStatementCount)
                .isEqualTo(1L);

        // The actual AC-4 claim: same slim column shape, not just the same statement count.
        // findAll()-plus-entity-hydration would select description/notes (seeded above with
        // distinctive values precisely so their presence in the captured SQL is unambiguous);
        // both the list-item projection and the fixed allProductRefs() projection must not.
        assertThat(productRefSql).hasSize(1);
        assertThat(listItemSql).hasSize(1);
        String productRefStatement = productRefSql.get(0).toLowerCase(Locale.ROOT);
        String listItemStatement = listItemSql.get(0).toLowerCase(Locale.ROOT);
        assertThat(productRefStatement).as("allProductRefs()'s SQL")
                .doesNotContain("description").doesNotContain("notes");
        assertThat(listItemStatement).as("findAllAsListItems()'s SQL")
                .doesNotContain("description").doesNotContain("notes");
    }
}
