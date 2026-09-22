package com.mirai.inventoryservice.inventory.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.dtos.responses.AuditLogDTO;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import com.mirai.inventoryservice.inventory.api.InventoryTotalDTO;
import com.mirai.inventoryservice.inventory.api.ProductInventoryResponseDTO;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.domain.StockMovement;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.repositories.AuditLogRepository;
import com.mirai.inventoryservice.services.AuditLogService;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.sites.infrastructure.LocationRepository;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import com.mirai.inventoryservice.sites.infrastructure.StorageLocationRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-6c-0 (.specs/phase-6-inventory/log.md): captures the AC-8 "before" measurement for the three
 * read paths T-6c-5/T-6c-11 change — {@code /api/inventory/totals}, {@code
 * /api/inventory/by-product/{id}} and the audit-log page — against a fixed, repeatable catalog
 * shape, at the current (pre-slim-projection, pre-v1) implementation. F-6c-10: this must run
 * before T-6c-5 changes {@code InventoryTotalsRepository}, since after that there is no clean
 * baseline left except by reverting.
 *
 * <p>Extends {@link BaseKafkaIntegrationTest} (real Postgres, not H2) for the same reason
 * {@code CatalogQueriesEgressIT} does: {@code InventoryTotalsRepository.findAllInventoryTotals()}
 * is a native query that casts {@code p.id} straight to {@code UUID} in the row mapper, which H2
 * does not support (it returns a raw {@code byte[]} for a UUID column, not
 * {@code java.util.UUID}) — confirmed by an actual {@code ClassCastException} when this test was
 * first written against the H2 {@code test} profile. Statement counts come from Hibernate
 * {@link Statistics#getPrepareStatementCount()} (every JDBC statement, not just Hibernate "query"
 * executions), matching {@code CatalogQueriesEgressIT}'s established reasoning.
 *
 * <p>Review fix (P2, 2026-09-11): the fixture is isolated ({@link #cleanup()} deletes exactly what
 * {@link #seedFixedCatalog()} created), both previously-empty measurements now target populated
 * data (a stocked product instead of the zero-stock {@code firstProductId}, and one seeded audit
 * log row), and each measurement independently captures AC-8's four dimensions — database rows and
 * projected bytes via a raw JDBC pass over the query the production code actually runs, API bytes
 * and request/query counts via the existing Hibernate-statistics/serialized-response path.
 *
 * <p>Review fix round 2 (P2 x2 + P3, 2026-09-12): the totals measurement previously ran the
 * unfiltered production query but then filtered the *application* result down to this fixture's
 * own product ids before measuring, while the JDBC side filtered inside SQL ({@code WHERE p.id =
 * ANY(?)}) — two different, non-comparable workloads whenever any other fixture's rows were also
 * present in the shared Testcontainers Postgres instance (the real production query has no id
 * filter at all, so its actual executed cost scales with the *whole* table, not with this
 * fixture's 25 rows). Fixed by {@link #clearCatalogTables()}, called before every test, which
 * empties {@code products}/{@code categories}/{@code location_inventory}/{@code stock_movements}
 * so the catalog genuinely contains only this fixture's rows — both the totals measurement and its
 * SQL counterpart now run fully unfiltered and are directly comparable, and the raw-{@code
 * Connection}/{@code java.sql.Array} plumbing that filtering needed is gone entirely (also
 * resolving the P3 leaked-connection/unfreed-array finding by removing the code path, not just
 * closing it). Separately, the by-product measurement only ever queried {@code location_inventory}
 * even though {@code InventoryAggregateService.getInventoryByProduct} also fetches the product row
 * (a second, distinct SQL statement) and the location_inventory query itself joins {@code
 * locations}/{@code storage_locations} (`LocationInventoryRepository.findByProduct_Id`'s {@code
 * JOIN FETCH}) — {@link #measureDbEgress(List)} now runs every query the production call actually
 * issues and sums rows/bytes across all of them, matching the real statement count.
 *
 * <p>The recorded numbers below are not just asserted (regression floors), they are also logged
 * at INFO on every run and were copied from an actual {@code ./mvnw -Dtest='*IT'} run into
 * .specs/phase-6-inventory/log.md's "6c implementation" section, per AC-8's requirement that
 * these numbers be observed, not assumed.
 */
class InventoryEgressBaselineIT extends BaseKafkaIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(InventoryEgressBaselineIT.class);

    /** Fixed catalog shape: deterministic product/location/movement counts for a reproducible baseline. */
    private static final int PRODUCT_COUNT = 25;
    private static final int LOCATION_COUNT = 3;

    @Autowired private InventoryQueries inventoryQueries;
    @Autowired private InventoryAggregateService inventoryAggregateService;
    @Autowired private AuditLogService auditLogService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SiteRepository siteRepository;
    @Autowired private LocationInventoryRepository locationInventoryRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Statistics statistics;
    /** First product created; deliberately zero-stock (i % 3 == 0) per F-6c-3's hazard — not used for the stocked-product measurement. */
    private UUID firstProductId;
    /** First product seeded with actual LocationInventory/StockMovement rows — used by the by-product baseline. */
    private UUID stockedProductId;
    private UUID seededAuditLogId;

    private final List<UUID> createdLocationIds = new ArrayList<>();
    private final List<UUID> createdStorageLocationIds = new ArrayList<>();

    @DynamicPropertySource
    static void enableHibernateStatistics(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        clearCatalogTables();
        seedFixedCatalog();
    }

    @AfterEach
    void cleanup() {
        clearCatalogTables();
        // location_inventory/stock_movements/products/categories are cleared above; locations,
        // storage locations and the seeded audit log aren't part of the totals-isolation problem
        // (F-6c-hazard is scoped to the catalog tables the totals query joins), so they stay on
        // targeted, tracked-id cleanup rather than a blanket table clear.
        if (seededAuditLogId != null) {
            auditLogRepository.deleteById(seededAuditLogId);
        }
        locationRepository.deleteAllById(createdLocationIds);
        storageLocationRepository.deleteAllById(createdStorageLocationIds);
    }

    /**
     * Empties every table the totals query joins, so the catalog genuinely contains only this
     * fixture's rows for the duration of one test — see the class Javadoc's "Review fix round 2"
     * note. FK-safe order: children before parents. Called before AND after each test so this
     * class is isolated regardless of what ran before it in the shared Testcontainers Postgres
     * instance, and leaves the DB clean for whatever runs after it.
     */
    private void clearCatalogTables() {
        stockMovementRepository.deleteAllInBatch();
        locationInventoryRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
    }

    private void seedFixedCatalog() {
        Site main = siteRepository.findByCode("MAIN")
                .orElseGet(() -> siteRepository.save(Site.builder().code("MAIN").name("Main").build()));
        String suffix = "baseline-" + UUID.randomUUID().toString().substring(0, 8);

        Category category = categoryRepository.save(Category.builder()
                .name("Baseline Category " + suffix)
                .slug("baseline-category-" + suffix)
                .build());

        List<Location> locations = new ArrayList<>();
        for (int i = 0; i < LOCATION_COUNT; i++) {
            StorageLocation storage = storageLocationRepository.save(StorageLocation.builder()
                    .site(main)
                    .code("BASELINE-" + suffix + "-L" + i)
                    .name("Baseline Storage " + suffix + " " + i)
                    .hasDisplay(false)
                    .isDisplayOnly(false)
                    .displayOrder(i)
                    .build());
            createdStorageLocationIds.add(storage.getId());
            Location location = locationRepository.save(Location.builder()
                    .storageLocation(storage)
                    .locationCode("BASELINE-" + suffix + "-L" + i)
                    .build());
            createdLocationIds.add(location.getId());
            locations.add(location);
        }

        for (int i = 0; i < PRODUCT_COUNT; i++) {
            Product product = productRepository.save(Product.builder()
                    .sku("BASELINE-" + suffix + "-P" + i)
                    .name("Baseline Product " + suffix + " " + i)
                    .category(category)
                    .isActive(true)
                    .quantity(0)
                    .build());
            if (firstProductId == null) {
                firstProductId = product.getId();
            }

            // Every third product is deliberately zero-stock everywhere (no LocationInventory
            // row at all) -- matches F-6c-3's zero-stock hazard the totals query must handle.
            if (i % 3 == 0) {
                continue;
            }

            Location location = locations.get(i % LOCATION_COUNT);
            LocationInventory inventory = locationInventoryRepository.save(LocationInventory.builder()
                    .location(location)
                    .site(main)
                    .product(product)
                    .quantity(10 + i)
                    .build());

            if (stockedProductId == null) {
                stockedProductId = product.getId();
            }

            StockMovement movement = stockMovementRepository.save(StockMovement.builder()
                    .item(product)
                    .locationType(LocationType.BOX_BIN)
                    .toLocationId(location.getId())
                    .previousQuantity(0)
                    .currentQuantity(inventory.getQuantity())
                    .quantityChange(inventory.getQuantity())
                    .reason(StockMovementReason.SHIPMENT_RECEIPT)
                    .site(main)
                    .at(java.time.OffsetDateTime.now())
                    .build());
            assertThat(movement.getId()).isNotNull();
        }

        // Populate the audit log so its baseline measures a real page, not an empty one.
        seededAuditLogId = auditLogService.createAuditLog(
                null,
                StockMovementReason.SHIPMENT_RECEIPT,
                null,
                null,
                locations.get(0).getId(),
                locations.get(0).getLocationCode(),
                1,
                10,
                "Baseline seed audit entry " + suffix,
                "T-6c-0 baseline fixture"
        ).getId();
    }

    /**
     * AC-8's four distinct dimensions: database rows and projected bytes (measured independently,
     * straight off the database via a raw JDBC pass over every query the production code actually
     * runs — not derived from the mapped response), API bytes and request/query counts (measured
     * from the actual service call). {@code projectedDbBytes} is an explicit label estimate
     * (per-column-value byte length summed per row, not the database's own storage/page size) —
     * exactly the estimate AC-8 permits ("projected bytes (label estimates)"), kept separate from
     * {@code apiBytes} so the two are never conflated.
     */
    private record Measurement(
            String label,
            long statementCount,
            int apiRowCount,
            int apiBytes,
            long dbRowCount,
            long projectedDbBytesEstimate
    ) {
        void logIt() {
            log.info("AC-8 baseline [{}]: statements={} apiRows={} apiBytes={} dbRows={} "
                            + "projectedDbBytesEstimate={}",
                    label, statementCount, apiRowCount, apiBytes, dbRowCount, projectedDbBytesEstimate);
        }
    }

    /** One SQL statement the production call issues, with its bind arguments (if any). */
    private record DbQuery(String sql, Object... args) {}

    private record DbEgress(long rows, long bytesEstimate) {}

    /**
     * Runs every {@code query} directly via JDBC and sums a per-value byte-length estimate across
     * every returned row/column — an independent, database-sourced measurement of rows and
     * projected bytes, deliberately not reusing the application's mapped response. Accepts a list
     * because a single application call can (and, for by-product, does) issue more than one SQL
     * statement; summing across all of them is what makes this comparable to
     * {@code statementCount}.
     */
    private DbEgress measureDbEgress(List<DbQuery> queries) {
        long totalRows = 0;
        long totalBytes = 0;
        for (DbQuery query : queries) {
            DbEgress egress = jdbcTemplate.query(query.sql(), (ResultSet rs) -> {
                long rows = 0;
                long bytes = 0;
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    rows++;
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        bytes += estimateColumnBytes(rs.getObject(i));
                    }
                }
                return new DbEgress(rows, bytes);
            }, query.args());
            totalRows += egress.rows();
            totalBytes += egress.bytesEstimate();
        }
        return new DbEgress(totalRows, totalBytes);
    }

    private static long estimateColumnBytes(Object value) throws SQLException {
        if (value == null) {
            return 1; // NULL marker estimate
        }
        if (value instanceof String s) {
            return s.getBytes(StandardCharsets.UTF_8).length;
        }
        if (value instanceof UUID) {
            return 16;
        }
        if (value instanceof Boolean) {
            return 1;
        }
        if (value instanceof Number) {
            return 8;
        }
        return value.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    private <T> Measurement measure(
            String label,
            java.util.function.Supplier<T> call,
            ToRows<T> toRows,
            List<DbQuery> dbQueries
    ) throws Exception {
        statistics.clear();
        T result = call.get();
        long statements = statistics.getPrepareStatementCount();
        int rows = toRows.rows(result);
        int bytes = objectMapper.writeValueAsBytes(result).length;
        DbEgress dbEgress = measureDbEgress(dbQueries);
        Measurement measurement = new Measurement(
                label, statements, rows, bytes, dbEgress.rows(), dbEgress.bytesEstimate());
        measurement.logIt();
        return measurement;
    }

    @FunctionalInterface
    private interface ToRows<T> {
        int rows(T result);
    }

    @Test
    void baseline_inventoryTotals_fullCatalog() throws Exception {
        // Mirrors InventoryTotalsRepository.INVENTORY_TOTALS_SQL exactly -- unfiltered, matching
        // the production query verbatim, safe to run without a WHERE clause now that
        // clearCatalogTables() guarantees this fixture's 25 products are the entire catalog.
        String dbSql = """
                SELECT
                    p.id, p.sku, p.name, p.image_url,
                    c.id as category_id, c.name as category_name,
                    parent.id as parent_category_id, parent.name as parent_category_name,
                    p.unit_cost, p.is_active,
                    COALESCE(SUM(li.quantity), 0) as total_quantity,
                    MAX(li.updated_at) as last_updated_at
                FROM products p
                LEFT JOIN categories c ON p.category_id = c.id
                LEFT JOIN categories parent ON c.parent_id = parent.id
                LEFT JOIN location_inventory li ON p.id = li.product_id
                GROUP BY p.id, p.sku, p.name, p.image_url, c.id, c.name, parent.id, parent.name, p.unit_cost, p.is_active
                ORDER BY p.name
                """;

        Measurement m = measure(
                "GET /api/inventory/totals (full catalog, " + PRODUCT_COUNT + " products)",
                () -> inventoryQueries.findAllInventoryTotals(),
                (List<InventoryTotalDTO> r) -> r.size(),
                List.of(new DbQuery(dbSql)));

        assertThat(m.apiRowCount()).isEqualTo(PRODUCT_COUNT);
        assertThat(m.dbRowCount()).isEqualTo(PRODUCT_COUNT);
        // Recorded floor from an actual run (see .specs/phase-6-inventory/log.md "6c
        // implementation"): whole-catalog totals is a single native query regardless of size.
        assertThat(m.statementCount()).isEqualTo(1L);
    }

    @Test
    void baseline_inventoryByProduct_stockedProduct() throws Exception {
        // InventoryAggregateService.getInventoryByProduct issues two statements: the product
        // fetch (CatalogQueries.getById -> ProductRepository.findById) and the location_inventory
        // query, which itself JOIN FETCHes locations and storage_locations
        // (LocationInventoryRepository.findByProduct_Id). Both are measured so the DB-side total
        // matches what the production call actually executes, not just its inventory half.
        List<DbQuery> dbQueries = List.of(
                new DbQuery("SELECT * FROM products WHERE id = ?", stockedProductId),
                new DbQuery("""
                        SELECT li.*, l.*, sl.*
                        FROM location_inventory li
                        JOIN locations l ON li.location_id = l.id
                        JOIN storage_locations sl ON l.storage_location_id = sl.id
                        WHERE li.product_id = ?
                        """, stockedProductId)
        );

        Measurement m = measure(
                "GET /api/inventory/by-product/{id} (stocked product, up to " + LOCATION_COUNT + " candidate locations)",
                () -> inventoryAggregateService.getInventoryByProduct(stockedProductId),
                (ProductInventoryResponseDTO r) -> r.getEntries().size(),
                dbQueries);

        assertThat(m.apiRowCount()).isGreaterThan(0);
        assertThat(m.dbRowCount()).isGreaterThan(0L);
        assertThat(m.statementCount()).isEqualTo(2L);
    }

    @Test
    void baseline_auditLogPage_defaultPageSize() throws Exception {
        String dbSql = "SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT 20 OFFSET 0";
        AuditLogFilterDTO filters = AuditLogFilterDTO.builder().build();

        Measurement m = measure(
                "GET /api/audit-logs (page 0, size 20)",
                () -> auditLogService.getAuditLogs(filters, PageRequest.of(0, 20)),
                (Page<AuditLogDTO> r) -> r.getContent().size(),
                List.of(new DbQuery(dbSql)));

        assertThat(m.apiRowCount()).isGreaterThan(0);
        assertThat(m.dbRowCount()).isGreaterThan(0L);
        assertThat(m.apiRowCount()).isLessThanOrEqualTo(20);
    }
}
