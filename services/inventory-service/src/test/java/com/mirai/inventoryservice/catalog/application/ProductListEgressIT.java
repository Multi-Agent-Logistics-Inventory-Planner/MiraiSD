package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.api.CatalogProductResponse;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.Supplier;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC-6 (.specs/catalog-product-egress/spec.md): the comprehensive egress proof for the three
 * product-list read paths this fix touched, plus the v1 catalog detail path (AC-4/AC-5's
 * preferredSupplierName and flat-category regression).
 *
 * <p>Two layers of proof, deliberately not just one: a repository-level statement count proves
 * the query itself is slim, but not that a caller stays slim -- a service method can add its own
 * extra query around a perfectly slim repository call without any repository-level test noticing.
 * <ul>
 *   <li>The {@code findAll*}-prefixed tests call the repository query directly and assert its own
 *   statement count / column shape in isolation.</li>
 *   <li>The {@code *_throughController}/{@code *_endToEnd}-suffixed tests fire real HTTP requests
 *   through {@code mockMvc} and inspect the SQL actually emitted for the whole request, including
 *   any additional query a service method adds around the repository call -- e.g. the legacy list
 *   endpoint's separate {@code openKujiBoxPort.findProductIdsWithOpenBox()} lookup (needed for
 *   {@code hasActiveBox}, a real second statement, not a regression) and the site-products
 *   endpoint's separate {@code site_products} assortment query.</li>
 * </ul>
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

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    /**
     * Supplier.canonicalName is populated by a Postgres trigger this Testcontainers instance's
     * Hibernate ddl-auto=create-drop schema does not replicate -- insert directly instead (same
     * workaround as CatalogTransactionPropagationIT.createSupplier).
     */
    private UUID createSupplier(String displayName) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO suppliers (id, display_name, canonical_name, is_active, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, displayName, displayName.toLowerCase(Locale.ROOT), true, now, now);
        return id;
    }

    @Test
    void legacyProductList_throughController_addsExactlyOneStatementForHasActiveBox() throws Exception {
        seedCategoryAndTwoProductsWithAChild();
        entityManager.flush();
        entityManager.clear();
        String token = adminToken();
        CapturingStatementInspector.clear();
        statistics.clear();

        mockMvc.perform(get("/api/products").header("Authorization", "Bearer " + token));

        // Real HTTP also incurs one JWT-auth user lookup ("from users") ahead of the endpoint's
        // own work -- filtered out below, since it's unrelated to this endpoint's own query
        // shape and would make the assertion brittle against unrelated auth-path changes.
        List<String> sql = withoutAuthPreamble(CapturingStatementInspector.captured());

        // The endpoint's own service method now issues two statements: the slim list query
        // (proven independently above) and the separate open-Kuji-box lookup that populates
        // hasActiveBox. Two, not one, is correct here -- the earlier "exactly one statement"
        // framing described the repository query in isolation, not everything the controller's
        // service method actually does with it.
        assertThat(sql)
                .as("GET /api/products must issue the slim list query plus exactly one "
                        + "hasActiveBox lookup -- not a JOIN FETCH, and not zero (which would "
                        + "mean hasActiveBox silently stopped being populated)")
                .hasSize(2);
        // No "no JOIN" check here, unlike the site-products test below: LIST_ITEM_SELECT's
        // LEFT JOIN to categories/suppliers for one row's worth of columns is correct, existing
        // behavior (proven slim at the repository level above) -- the site-products endpoint is
        // the one whose query must have zero joins at all.
        assertThat(sql).anySatisfy(statement ->
                assertThat(statement.toLowerCase(Locale.ROOT)).contains("kuji_boxes"));
    }

    @Test
    void catalogProductList_throughController_issuesExactlyOneStatement() throws Exception {
        seedCategoryAndTwoProductsWithAChild();
        entityManager.flush();
        entityManager.clear();
        String token = adminToken();
        CapturingStatementInspector.clear();
        statistics.clear();

        mockMvc.perform(get("/api/v1/catalog/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Unlike the legacy list (hasActiveBox lookup) and site-products list (separate
        // assortment query), CatalogProductController.getCatalogProducts does nothing beyond the
        // single repository call -- applyKujiWebhookVisibility is a pure in-memory permission
        // check, no DB access. One statement past the auth preamble is the correct total here,
        // not just the repository query's own shape (which the repository-level test above
        // already proved in isolation).
        List<String> sql = withoutAuthPreamble(CapturingStatementInspector.captured());
        // No "no JOIN" check here, same reason as the legacy list test above:
        // CATALOG_LIST_SELECT's LEFT JOIN to categories/suppliers for one row's worth of columns
        // is correct, existing behavior (proven slim, and free of site-owned columns, at the
        // repository level above).
        assertThat(sql)
                .as("GET /api/v1/catalog/products must issue exactly the one slim list query -- "
                        + "no extra statement, unlike the legacy and site-products endpoints")
                .hasSize(1);
    }

    @Test
    void siteProductsList_throughController_isTheSlimQueryPlusTheSeparateAssortmentQuery() throws Exception {
        Site mainSite = siteRepository.findByCode("MAIN").orElseThrow();
        seedCategoryAndTwoProductsWithAChild();
        entityManager.flush();
        entityManager.clear();
        String token = adminToken();
        CapturingStatementInspector.clear();
        statistics.clear();

        mockMvc.perform(get("/api/v1/sites/" + mainSite.getId() + "/products")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // A /api/v1/sites/{siteId}/** request also incurs SiteAccessAuthorizationFilter's own
        // user/site/membership lookups ahead of the endpoint's own work -- filtered out below,
        // same rationale as the legacy list test above.
        List<String> sql = withoutAuthPreamble(CapturingStatementInspector.captured());

        // Two statements, not one: the slim products query (findAllSettingsDefaults) and the
        // site_products assortment lookup (SiteAssortment.rowsForSite) are genuinely separate
        // queries against separate tables -- the fix here was replacing a JOIN FETCH with a slim
        // projection, not eliminating the assortment query, which was never the egress problem.
        assertThat(sql).hasSize(2);
        assertThat(sql).anySatisfy(statement ->
                assertThat(statement.toLowerCase(Locale.ROOT)).contains("site_products"));
        assertThat(sql).noneSatisfy(statement ->
                assertThat(statement.toLowerCase(Locale.ROOT)).contains("join"));
    }

    /**
     * Strips the queries SiteAccessAuthorizationFilter/JWT auth issue ahead of any endpoint's own
     * work (user lookup by email, and for site-scoped routes, site + membership lookups) so each
     * test's assertions describe only the endpoint's own query shape, not the shared auth path.
     */
    private List<String> withoutAuthPreamble(List<String> captured) {
        return captured.stream()
                .filter(statement -> {
                    String lower = statement.toLowerCase(Locale.ROOT);
                    return !lower.contains("from users")
                            && !lower.contains("from sites")
                            && !lower.contains("from user_site_memberships");
                })
                .toList();
    }

    @Test
    void catalogProductDetail_endToEnd_hasNoRecursiveCategoryQueryAndIncludesSupplierName() throws Exception {
        String supplierName = "Egress IT Supplier " + System.nanoTime();
        UUID supplierId = createSupplier(supplierName);
        Category parentCategory = categoryRepository.save(Category.builder()
                .name("Egress Detail Parent Category " + System.nanoTime())
                .slug("egress-detail-parent-category-" + System.nanoTime())
                .build());
        Category category = categoryRepository.save(Category.builder()
                .name("Egress Detail Category " + System.nanoTime())
                .slug("egress-detail-category-" + System.nanoTime())
                .parent(parentCategory)
                .build());
        // A grandchild under the same category the requested product uses -- if CatalogProductMapper
        // regressed to an implicit recursive Category -> CategoryResponseDTO sub-mapper, mapping
        // this product would traverse category.getChildren() (and each child's own children),
        // issuing extra SELECTs this test would catch.
        categoryRepository.save(Category.builder()
                .name("Egress Detail Child Category " + System.nanoTime())
                .slug("egress-detail-child-category-" + System.nanoTime())
                .parent(category)
                .build());
        Product product = productRepository.save(Product.builder()
                .sku("PLE-DETAIL-" + System.nanoTime())
                .name("Detail Product")
                .category(category)
                .preferredSupplier(Supplier.builder().id(supplierId).build())
                .preferredSupplierAuto(true)
                .isActive(true)
                .build());
        entityManager.flush();
        entityManager.clear();
        String token = adminToken();
        CapturingStatementInspector.clear();
        statistics.clear();

        mockMvc.perform(get("/api/v1/catalog/products/" + product.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredSupplierId").value(supplierId.toString()))
                .andExpect(jsonPath("$.preferredSupplierName").value(supplierName))
                .andExpect(jsonPath("$.category.name").value(category.getName()))
                .andExpect(jsonPath("$.category.children").isEmpty());

        // The single-product detail path (findByIdWithCategories) already JOIN FETCHes the
        // product's own category and that category's parent in one statement, by design (see
        // ProductRepository) -- that statement legitimately references the categories table
        // twice via two different aliases. What must NOT happen is a SEPARATE, additional
        // statement querying categories on its own -- that would mean CatalogProductMapper
        // regressed to an implicit recursive Category sub-mapper walking category.children,
        // which the explicit flatCategory mapping (CatalogProductMapper) exists to prevent.
        List<String> sql = withoutAuthPreamble(CapturingStatementInspector.captured());
        long statementsReferencingCategories = sql.stream()
                .filter(statement -> statement.toLowerCase(Locale.ROOT).contains("categories"))
                .count();
        assertThat(statementsReferencingCategories)
                .as("exactly one statement should reference the categories table at all -- the "
                        + "product detail fetch's own JOIN. A second, separate statement "
                        + "referencing categories would mean the mapper is walking "
                        + "category.children recursively")
                .isEqualTo(1L);
    }
}
