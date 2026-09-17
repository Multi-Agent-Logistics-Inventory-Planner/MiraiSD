package com.mirai.inventoryservice.inventory.infrastructure;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.inventory.api.InventoryTotalDTO;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.sites.infrastructure.LocationRepository;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import com.mirai.inventoryservice.sites.infrastructure.StorageLocationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the {@code (UUID)} row-cast bug in
 * {@link InventoryTotalsRepository#findAllInventoryTotals()} and
 * {@link InventoryTotalsRepository#findAllStockTotalsMap()}: both run unmapped native SQL, and
 * H2's JDBC driver hands back a raw 16-byte {@code byte[]} for a UUID column there (never
 * {@code java.util.UUID}), unlike Postgres. A status-only test (e.g. the controller-level security
 * ITs) only proves the query stops throwing {@code ClassCastException} - it does not prove
 * {@link InventoryTotalsRepository#toUuid} reconstructed the *correct* id, since a wrong byte
 * order or an off-by-one on the two longs would silently produce a different, still-valid-looking
 * UUID. This asserts the reconstructed ids against JPA-assigned ones directly, over populated
 * data, for every id column both queries touch (product, category, parent category, and the
 * stock-map's key).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class InventoryTotalsRepositoryUuidMappingIT {

    @Autowired private InventoryTotalsRepository inventoryTotalsRepository;
    @Autowired private LocationInventoryRepository locationInventoryRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SiteRepository siteRepository;

    private Site mainSite() {
        return siteRepository.findByCode("MAIN")
                .orElseGet(() -> siteRepository.save(Site.builder().code("MAIN").name("MAIN").build()));
    }

    private Location newLocation(Site site, String suffix) {
        StorageLocation storage = storageLocationRepository.save(StorageLocation.builder()
                .site(site)
                .code("UUIDIT-" + suffix)
                .name("UUID Mapping IT Storage " + suffix)
                .hasDisplay(false)
                .isDisplayOnly(false)
                .displayOrder(1)
                .build());
        return locationRepository.save(Location.builder()
                .storageLocation(storage)
                .locationCode("UUIDIT-" + suffix)
                .build());
    }

    @Test
    void findAllInventoryTotals_reconstructsExactProductAndCategoryIds() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category parentCategory = categoryRepository.save(Category.builder()
                .name("UUID Mapping IT Parent " + suffix)
                .slug("uuidit-parent-" + suffix)
                .build());
        Category category = categoryRepository.save(Category.builder()
                .name("UUID Mapping IT Category " + suffix)
                .slug("uuidit-category-" + suffix)
                .parent(parentCategory)
                .build());
        Product product = productRepository.save(Product.builder()
                .sku("UUIDIT-" + suffix)
                .name("UUID Mapping IT Product " + suffix)
                .category(category)
                .isActive(true)
                .quantity(0)
                .build());
        Site site = mainSite();
        Location location = newLocation(site, suffix);
        locationInventoryRepository.save(LocationInventory.builder()
                .location(location).site(site).product(product).quantity(9).build());

        List<InventoryTotalDTO> totals = inventoryTotalsRepository.findAllInventoryTotals();

        InventoryTotalDTO row = totals.stream()
                .filter(t -> product.getId().equals(t.getItemId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("seeded product missing from findAllInventoryTotals()"));

        assertThat(row.getItemId()).isEqualTo(product.getId());
        assertThat(row.getCategoryId()).isEqualTo(category.getId());
        assertThat(row.getParentCategoryId()).isEqualTo(parentCategory.getId());
        assertThat(row.getTotalQuantity()).isEqualTo(9);
    }

    @Test
    void findAllInventoryTotals_productWithNoCategoryParent_leavesParentCategoryIdNull() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = categoryRepository.save(Category.builder()
                .name("UUID Mapping IT Root Category " + suffix)
                .slug("uuidit-root-category-" + suffix)
                .build());
        Product product = productRepository.save(Product.builder()
                .sku("UUIDIT-ROOT-" + suffix)
                .name("UUID Mapping IT Root Product " + suffix)
                .category(category)
                .isActive(true)
                .quantity(0)
                .build());

        List<InventoryTotalDTO> totals = inventoryTotalsRepository.findAllInventoryTotals();

        InventoryTotalDTO row = totals.stream()
                .filter(t -> product.getId().equals(t.getItemId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("seeded product missing from findAllInventoryTotals()"));

        assertThat(row.getCategoryId()).isEqualTo(category.getId());
        assertThat(row.getParentCategoryId()).isNull();
    }

    @Test
    void findAllStockTotalsMap_keyedByExactProductId() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = categoryRepository.save(Category.builder()
                .name("UUID Mapping IT Stock Category " + suffix)
                .slug("uuidit-stock-category-" + suffix)
                .build());
        Product product = productRepository.save(Product.builder()
                .sku("UUIDIT-STOCK-" + suffix)
                .name("UUID Mapping IT Stock Product " + suffix)
                .category(category)
                .isActive(true)
                .quantity(0)
                .build());
        Site site = mainSite();
        Location location = newLocation(site, "stock-" + suffix);
        locationInventoryRepository.save(LocationInventory.builder()
                .location(location).site(site).product(product).quantity(17).build());

        Map<UUID, Integer> stockMap = inventoryTotalsRepository.findAllStockTotalsMap();

        assertThat(stockMap).containsEntry(product.getId(), 17);
    }
}
