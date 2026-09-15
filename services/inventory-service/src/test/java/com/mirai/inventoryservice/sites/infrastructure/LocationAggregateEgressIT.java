package com.mirai.inventoryservice.sites.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import com.mirai.inventoryservice.sites.api.LocationWithCountsDTO;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * .specs/phase-6-inventory 6e, T-6e-be-1/T-6e-be-11: the AC-8 before/after measurement for
 * {@code locations/with-counts}. "Before" (legacy, site-blind {@code findAllLocationsWithCounts}/
 * {@code findLocationsByTypeWithCounts(String)}) and "after" (site-scoped overloads) are measured
 * in one class rather than the separate Baseline/AfterIT files 6c used, because the legacy
 * un-scoped methods were kept, unmodified, alongside the new scoped ones (T-6e-be-2's design) --
 * the "before" behavior is still directly measurable today, not only recoverable from history.
 *
 * <p>Same fixed-shape discipline as {@code InventoryEgressAfterIT}: two sites seeded with an
 * identical number of locations/products each, so equal-catalog-size before/after comparison
 * holds and the cross-site leak is visible in the numbers (the legacy query's row/byte counts
 * include the second site's data; the scoped query's do not).
 */
class LocationAggregateEgressIT extends BaseKafkaIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(LocationAggregateEgressIT.class);
    private static final int LOCATIONS_PER_SITE = 3;

    @Autowired private LocationAggregateRepository locationAggregateRepository;
    @Autowired private com.mirai.inventoryservice.sites.infrastructure.SiteRepository siteRepository;
    @Autowired private com.mirai.inventoryservice.sites.infrastructure.StorageLocationRepository storageLocationRepository;
    @Autowired private com.mirai.inventoryservice.sites.infrastructure.LocationRepository locationRepository;
    @Autowired private LocationInventoryRepository locationInventoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Statistics statistics;
    private Site siteA;
    private Site siteB;
    private Location locationInSiteA;
    private Category categoryInSiteA;

    @DynamicPropertySource
    static void enableHibernateStatistics(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        String suffix = UUID.randomUUID().toString().substring(0, 6);
        siteA = siteRepository.save(Site.builder().code("EGA-" + suffix).name("Egress A").build());
        siteB = siteRepository.save(Site.builder().code("EGB-" + suffix).name("Egress B").build());
        categoryInSiteA = seedSite(siteA, suffix + "-A");
        seedSite(siteB, suffix + "-B");
    }

    private Category seedSite(Site site, String suffix) {
        Category category = categoryRepository.save(Category.builder()
                .name("Egress Category " + suffix).slug("egress-category-" + suffix).build());
        StorageLocation storage = storageLocationRepository.save(StorageLocation.builder()
                .site(site).code("EGRESS-" + suffix).name("Egress Storage " + suffix)
                .hasDisplay(false).isDisplayOnly(false).displayOrder(1).build());
        for (int i = 0; i < LOCATIONS_PER_SITE; i++) {
            Location location = locationRepository.save(Location.builder()
                    .storageLocation(storage).locationCode("L" + suffix + "-" + i).build());
            if (i == 0 && site == siteA) {
                locationInSiteA = location;
            }
            Product product = productRepository.save(Product.builder()
                    .sku("EGRESS-" + suffix + "-P" + i).name("Egress Product " + suffix + "-" + i)
                    .category(category).isActive(true).quantity(0).build());
            locationInventoryRepository.save(LocationInventory.builder()
                    .location(location).site(site).product(product).quantity(10 + i).build());
        }
        return category;
    }

    private record Measurement(String label, long statementCount, int apiRowCount, int apiBytes) {
        void logIt() {
            log.info("AC-8 [{}]: statements={} apiRows={} apiBytes={}", label, statementCount, apiRowCount, apiBytes);
        }
    }

    private Measurement measure(String label, List<LocationWithCountsDTO> result) throws Exception {
        long statements = statistics.getPrepareStatementCount();
        int bytes = objectMapper.writeValueAsBytes(result).length;
        Measurement m = new Measurement(label, statements, result.size(), bytes);
        m.logIt();
        return m;
    }

    private java.util.Set<UUID> locationIdsForSite(Site site) {
        return locationRepository.findBySite_Id(site.getId()).stream()
                .map(Location::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void before_legacyFindAllLocationsWithCounts_returnsBothSitesRowsTogether() throws Exception {
        java.util.Set<UUID> siteALocationIds = locationIdsForSite(siteA);
        java.util.Set<UUID> siteBLocationIds = locationIdsForSite(siteB);

        statistics.clear();
        @SuppressWarnings("deprecation")
        List<LocationWithCountsDTO> legacy = locationAggregateRepository.findAllLocationsWithCounts();
        measure("before/legacy findAllLocationsWithCounts", legacy);

        long siteACount = legacy.stream().filter(l -> siteALocationIds.contains(l.getId())).count();
        long siteBCount = legacy.stream().filter(l -> siteBLocationIds.contains(l.getId())).count();

        // The defect this checkpoint fixes: the legacy query has no site predicate at all, so
        // one call returns rows from *every* site mixed together.
        assertThat(siteACount).isEqualTo(LOCATIONS_PER_SITE);
        assertThat(siteBCount).isEqualTo(LOCATIONS_PER_SITE);
    }

    @Test
    void after_siteScopedFindAllLocationsWithCounts_returnsOnlyTheCallingSite() throws Exception {
        java.util.Set<UUID> siteBLocationIds = locationIdsForSite(siteB);

        statistics.clear();
        List<LocationWithCountsDTO> scoped = locationAggregateRepository.findAllLocationsWithCounts(siteA.getId());
        Measurement after = measure("after/site-scoped findAllLocationsWithCounts(siteA)", scoped);

        boolean anySiteBRow = scoped.stream().anyMatch(l -> siteBLocationIds.contains(l.getId()));
        assertThat(anySiteBRow).isFalse();
        assertThat(after.apiRowCount()).isEqualTo(LOCATIONS_PER_SITE);
    }

    @Test
    void after_siteScopedInventorySubquery_excludesAMismatchedSiteRow() throws Exception {
        // A location_inventory row physically at one of siteA's locations, but tagged with siteB.
        Product mismatchProduct = productRepository.save(Product.builder()
                .sku("EGRESS-MISMATCH-" + UUID.randomUUID()).name("Mismatch Product")
                .category(categoryInSiteA).isActive(true).quantity(0).build());
        locationInventoryRepository.save(LocationInventory.builder()
                .location(locationInSiteA).site(siteB).product(mismatchProduct).quantity(999).build());

        List<LocationWithCountsDTO> scoped = locationAggregateRepository.findAllLocationsWithCounts(siteA.getId());
        boolean any999 = scoped.stream().anyMatch(l -> l.getTotalQuantity() == 999);
        assertThat(any999).isFalse();
    }
}
