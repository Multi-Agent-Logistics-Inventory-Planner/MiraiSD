package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.sites.infrastructure.LocationRepository;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import com.mirai.inventoryservice.sites.infrastructure.StorageLocationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * .specs/phase-6-inventory 6c, T-6c-15 (F-6c-4's Row 4 resolution): pins that
 * {@code products.quantity}/{@code products.is_active} stay computed <b>globally</b>, across
 * every site, not scoped to one -- a product stocked at two sites must yield the sum of both in
 * {@code products.quantity}, and {@code is_active} must derive from that global total. This is
 * deliberate, spec'd behavior (Q-6c-2: kept as-is, multi-site-data-and-api.md's global-activity
 * meaning), not an oversight; this test makes the failure mode loud if a future change silently
 * scopes either derivation to one site. Real Postgres (Testcontainers, via
 * {@link BaseKafkaIntegrationTest}), not H2 -- exercises the actual GROUP BY aggregation across
 * real {@code location_inventory} rows at two distinct sites.
 */
class StockStateGlobalAggregationIT extends BaseKafkaIntegrationTest {

    @Autowired private StockMovementService stockMovementService;
    @Autowired private LocationInventoryRepository locationInventoryRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SiteRepository siteRepository;

    private Location newLocation(String siteCode, String suffix) {
        Site site = siteRepository.findByCode(siteCode)
                .orElseGet(() -> siteRepository.save(Site.builder().code(siteCode).name(siteCode).build()));
        StorageLocation storage = storageLocationRepository.findByCodeAndSite_Code("BOX_BINS", siteCode)
                .orElseGet(() -> storageLocationRepository.save(StorageLocation.builder()
                        .site(site).code("BOX_BINS").name("Box Bins")
                        .hasDisplay(false).isDisplayOnly(false).displayOrder(1).build()));
        return locationRepository.save(Location.builder()
                .storageLocation(storage).locationCode("GLOBAGG-" + suffix).build());
    }

    @Test
    void syncProductTotals_sumsQuantityAcrossEverySite_notJustOne() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = categoryRepository.save(Category.builder()
                .name("Global Agg Category " + suffix).slug("global-agg-category-" + suffix).build());
        Product product = productRepository.save(Product.builder()
                .sku("GLOBAGG-" + suffix).name("Global Agg Product")
                .category(category).isActive(false).quantity(0).build());

        Location mainLocation = newLocation("MAIN", "main-" + suffix);
        Location secondLocation = newLocation("SECOND", "second-" + suffix);

        locationInventoryRepository.save(LocationInventory.builder()
                .location(mainLocation).site(mainLocation.getStorageLocation().getSite())
                .product(product).quantity(5).build());
        locationInventoryRepository.save(LocationInventory.builder()
                .location(secondLocation).site(secondLocation.getStorageLocation().getSite())
                .product(product).quantity(7).build());

        stockMovementService.syncProductTotals(List.of(product.getId()));

        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualTo(12);
        assertThat(reloaded.getIsActive()).isTrue();
    }

    @Test
    @Transactional
    void calculateTotalInventory_sumsAcrossEverySite() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = categoryRepository.save(Category.builder()
                .name("Global Agg Calc Category " + suffix).slug("global-agg-calc-category-" + suffix).build());
        Product product = productRepository.save(Product.builder()
                .sku("GLOBAGGCALC-" + suffix).name("Global Agg Calc Product")
                .category(category).isActive(true).quantity(0).build());

        Location mainLocation = newLocation("MAIN", "calc-main-" + suffix);
        Location secondLocation = newLocation("SECOND", "calc-second-" + suffix);

        locationInventoryRepository.save(LocationInventory.builder()
                .location(mainLocation).site(mainLocation.getStorageLocation().getSite())
                .product(product).quantity(3).build());
        locationInventoryRepository.save(LocationInventory.builder()
                .location(secondLocation).site(secondLocation.getStorageLocation().getSite())
                .product(product).quantity(4).build());

        int total = stockMovementService.calculateTotalInventory(product.getId());

        assertThat(total).isEqualTo(7);
    }
}
