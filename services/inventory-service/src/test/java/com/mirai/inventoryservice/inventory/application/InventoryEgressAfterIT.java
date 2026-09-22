package com.mirai.inventoryservice.inventory.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.inventory.api.SiteInventoryTotalDTO;
import com.mirai.inventoryservice.inventory.api.SiteStockMovementResponseDTO;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.domain.StockMovement;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
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
 * .specs/phase-6-inventory 6c, T-6c-17: the AC-8 "after" measurement, run against the v1
 * slim/batched totals path ({@link InventoryQueries#findInventoryTotalsBySite}) and the v1
 * movements path ({@link InventoryQueries#findAuditLogPageBySite}) at the exact same fixed
 * catalog shape {@code InventoryEgressBaselineIT} (T-6c-0) used — 25 products across 3 MAIN
 * locations, every third product deliberately zero-stock. Diffs recorded here are against the
 * <b>round-2</b> baseline numbers in {@code .specs/phase-6-inventory/log.md}'s "6c implementation
 * (T-6c-0..T-6c-3)" section (the final, corrected set — not the two earlier superseded sets in
 * that same entry).
 *
 * <p>Mirrors {@code InventoryEgressBaselineIT}'s measurement technique exactly (same
 * {@code Measurement}/{@code DbQuery}/{@code measureDbEgress} shape, same isolation via
 * {@code clearCatalogTables()}) so the two are directly comparable. Extends
 * {@link BaseKafkaIntegrationTest} (real Postgres) for the same reason the baseline does — the
 * legacy totals query this compares against is native SQL H2 cannot run; the new slim queries are
 * plain JPQL and would run under H2 too, but sharing one fixture/measurement technique with the
 * baseline matters more here than minimizing container use.
 *
 * <p>Covers the two AC-7/AC-8 levers T-6c-5 added: the full-catalog slim projection (same
 * row-per-product guarantee as the legacy totals, but no catalog metadata) and the bounded,
 * known-ID batch (the "neither full-catalog refreshes nor one request per product" case) — the
 * batch is the largest single lever, since it returns bytes proportional to the affected ids, not
 * the catalog size. The browser/realtime half of AC-8 (coalesced refresh measurement) remains
 * 6e's, per the task list's own scope note.
 */
class InventoryEgressAfterIT extends BaseKafkaIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(InventoryEgressAfterIT.class);

    private static final int PRODUCT_COUNT = 25;
    private static final int LOCATION_COUNT = 3;
    /** Size of the "known affected ids" batch for the targeted-refresh-shaped measurement. */
    private static final int KNOWN_IDS_BATCH_SIZE = 3;

    @Autowired private InventoryQueries inventoryQueries;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SiteRepository siteRepository;
    @Autowired private LocationInventoryRepository locationInventoryRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Statistics statistics;
    private UUID siteId;
    private final List<UUID> stockedProductIds = new ArrayList<>();
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
        locationRepository.deleteAllById(createdLocationIds);
        storageLocationRepository.deleteAllById(createdStorageLocationIds);
    }

    /** Same isolation technique as InventoryEgressBaselineIT's round-2 fix. */
    private void clearCatalogTables() {
        stockMovementRepository.deleteAllInBatch();
        locationInventoryRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
    }

    private void seedFixedCatalog() {
        Site main = siteRepository.findByCode("MAIN")
                .orElseGet(() -> siteRepository.save(Site.builder().code("MAIN").name("Main").build()));
        siteId = main.getId();
        String suffix = "after-" + UUID.randomUUID().toString().substring(0, 8);

        Category category = categoryRepository.save(Category.builder()
                .name("After Category " + suffix)
                .slug("after-category-" + suffix)
                .build());

        List<Location> locations = new ArrayList<>();
        for (int i = 0; i < LOCATION_COUNT; i++) {
            StorageLocation storage = storageLocationRepository.save(StorageLocation.builder()
                    .site(main)
                    .code("AFTER-" + suffix + "-L" + i)
                    .name("After Storage " + suffix + " " + i)
                    .hasDisplay(false)
                    .isDisplayOnly(false)
                    .displayOrder(i)
                    .build());
            createdStorageLocationIds.add(storage.getId());
            Location location = locationRepository.save(Location.builder()
                    .storageLocation(storage)
                    .locationCode("AFTER-" + suffix + "-L" + i)
                    .build());
            createdLocationIds.add(location.getId());
            locations.add(location);
        }

        for (int i = 0; i < PRODUCT_COUNT; i++) {
            Product product = productRepository.save(Product.builder()
                    .sku("AFTER-" + suffix + "-P" + i)
                    .name("After Product " + suffix + " " + i)
                    .category(category)
                    .isActive(true)
                    .quantity(0)
                    .build());

            // Same zero-stock-every-third shape as the baseline (F-6c-3's hazard).
            if (i % 3 == 0) {
                continue;
            }

            Location location = locations.get(i % LOCATION_COUNT);
            LocationInventory inventory = locationInventoryRepository.save(LocationInventory.builder()
                    .location(location).site(main).product(product).quantity(10 + i).build());
            stockMovementRepository.save(StockMovement.builder()
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
            stockedProductIds.add(product.getId());
        }
    }

    private record Measurement(
            String label, long statementCount, int apiRowCount, int apiBytes,
            long dbRowCount, long projectedDbBytesEstimate) {
        void logIt() {
            log.info("AC-8 after [{}]: statements={} apiRows={} apiBytes={} dbRows={} "
                            + "projectedDbBytesEstimate={}",
                    label, statementCount, apiRowCount, apiBytes, dbRowCount, projectedDbBytesEstimate);
        }
    }

    private record DbQuery(String sql, Object... args) {}

    private record DbEgress(long rows, long bytesEstimate) {}

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
            return 1;
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
            String label, java.util.function.Supplier<T> call, ToRows<T> toRows, List<DbQuery> dbQueries)
            throws Exception {
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
    void after_siteInventoryTotals_fullCatalog_slimProjectionIsSmallerThanLegacy() throws Exception {
        String dbSql = """
                SELECT p.id, COALESCE(SUM(li.quantity), 0), MAX(li.updated_at)
                FROM products p LEFT JOIN location_inventory li
                    ON li.product_id = p.id AND li.site_id = ?
                GROUP BY p.id
                """;

        Measurement m = measure(
                "GET /api/v1/sites/{siteId}/inventory/totals (full catalog, " + PRODUCT_COUNT + " products)",
                () -> inventoryQueries.findInventoryTotalsBySite(siteId),
                (List<SiteInventoryTotalDTO> r) -> r.size(),
                List.of(new DbQuery(dbSql, siteId)));

        // Same row-per-product guarantee as the legacy totals baseline (25 rows), but with no
        // catalog metadata (sku/name/image/category/unitCost) -- apiBytes must be smaller than the
        // round-2 baseline's 9445 bytes for the same 25-product/25-row shape.
        assertThat(m.apiRowCount()).isEqualTo(PRODUCT_COUNT);
        assertThat(m.dbRowCount()).isEqualTo(PRODUCT_COUNT);
        assertThat(m.statementCount()).isEqualTo(1L);
        assertThat(m.apiBytes()).isLessThan(9445);
    }

    @Test
    void after_siteInventoryTotals_knownIds_returnsOnlyTheRequestedIds() throws Exception {
        List<UUID> knownIds = stockedProductIds.subList(0, KNOWN_IDS_BATCH_SIZE);
        String dbSql = """
                SELECT li.product_id, SUM(li.quantity), MAX(li.updated_at)
                FROM location_inventory li
                WHERE li.product_id = ANY(?) AND li.site_id = ?
                GROUP BY li.product_id
                """;
        java.sql.Array idsArray = jdbcTemplate.getDataSource().getConnection()
                .createArrayOf("uuid", knownIds.toArray());

        Measurement m = measure(
                "GET /api/v1/sites/{siteId}/inventory/totals?productIds=... (" + KNOWN_IDS_BATCH_SIZE + " known ids)",
                () -> inventoryQueries.findInventoryTotalsBySite(siteId, knownIds),
                (List<SiteInventoryTotalDTO> r) -> r.size(),
                List.of(new DbQuery(dbSql, idsArray, siteId)));

        // AC-7's real ceiling: exactly the requested ids come back, not the full 25-product
        // catalog and not one request per product -- proportional to KNOWN_IDS_BATCH_SIZE.
        assertThat(m.apiRowCount()).isEqualTo(KNOWN_IDS_BATCH_SIZE);
        assertThat(m.dbRowCount()).isEqualTo(KNOWN_IDS_BATCH_SIZE);
        assertThat(m.statementCount()).isEqualTo(1L);
        assertThat(m.apiBytes()).isLessThan(9445);
    }

    /** Same field-by-field mapping SiteInventoryController.getSiteMovements uses (T-6c-11). */
    private static SiteStockMovementResponseDTO toSiteMovementDTO(StockMovement movement) {
        return SiteStockMovementResponseDTO.builder()
                .id(movement.getId())
                .locationType(movement.getLocationType())
                .itemId(movement.getItem().getId())
                .fromLocationId(movement.getFromLocationId())
                .toLocationId(movement.getToLocationId())
                .quantityChange(movement.getQuantityChange())
                .reason(movement.getReason())
                .actorId(movement.getActorId())
                .at(movement.getAt())
                .metadata(movement.getMetadata())
                .siteAttribution(movement.getSite() == null ? "UNKNOWN" : null)
                .build();
    }

    @Test
    void after_siteMovements_page_isComparableToLegacyAuditLogPage() throws Exception {
        String dbSql = "SELECT * FROM stock_movements WHERE site_id = ? ORDER BY at DESC LIMIT 20 OFFSET 0";

        // Measures the actual v1 response shape (slim DTO, matching SiteInventoryController), not
        // the raw entity page -- the entity's lazy associations are unmapped outside the
        // transaction this call ran in, which is exactly why the controller never returns them
        // directly.
        Measurement m = measure(
                "GET /api/v1/sites/{siteId}/inventory/movements (page 0, size 20)",
                () -> inventoryQueries
                        .findAuditLogPageBySite(siteId, AuditLogFilterDTO.builder().build(), PageRequest.of(0, 20))
                        .map(InventoryEgressAfterIT::toSiteMovementDTO),
                (Page<SiteStockMovementResponseDTO> r) -> r.getContent().size(),
                List.of(new DbQuery(dbSql, siteId)));

        assertThat(m.apiRowCount()).isGreaterThan(0);
        assertThat(m.dbRowCount()).isGreaterThan(0L);
        assertThat(m.apiRowCount()).isLessThanOrEqualTo(20);
    }
}
