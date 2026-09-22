package com.mirai.inventoryservice.inventory.infrastructure;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.inventory.api.SiteInventoryTotalDTO;
import com.mirai.inventoryservice.inventory.domain.InvalidInventoryOperationException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T-6c-5 (.specs/phase-6-inventory/log.md, AC-5/AC-7): proves the new slim, site-scoped, batched
 * totals projection on {@link InventoryTotalsRepository}. Covers, per the task's four required
 * cases:
 * <ul>
 *   <li>(a) a zero-stock product at a site still appears with quantity 0 in the full-catalog
 *   mode ({@link InventoryTotalsRepository#findAllInventoryTotalsBySite} -- F-6c-3's hazard,
 *   proven via the LEFT JOIN's site predicate living in the ON clause, not a WHERE filter);</li>
 *   <li>(b) the same product with different quantities at MAIN and SECOND resolves
 *   independently;</li>
 *   <li>(c) the batched form ({@link InventoryTotalsRepository#findInventoryTotalsBySiteAndProductIds})
 *   returns exactly the requested ids (and, per its documented different contract, omits an id
 *   with no inventory row at the site rather than returning a zero row for it);</li>
 *   <li>(d) a batch over {@link InventoryTotalsRepository#MAX_PRODUCT_IDS_BATCH_SIZE} is
 *   rejected with {@link InvalidInventoryOperationException}.</li>
 * </ul>
 * H2 {@code test} profile: unlike {@code InventoryEgressBaselineIT}'s native-SQL UUID cast (which
 * H2 cannot handle), these queries are JPQL, which Hibernate translates for H2 like any other
 * dialect -- no Testcontainers needed, matching the reasoning already recorded for
 * {@code LocationInventorySiteScopedQueriesIT}/{@code InventoryOperationsSiteIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class InventoryTotalsRepositorySiteScopedIT {

    @Autowired private InventoryTotalsRepository inventoryTotalsRepository;
    @Autowired private LocationInventoryRepository locationInventoryRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SiteRepository siteRepository;

    private Site siteOf(String code) {
        return siteRepository.findByCode(code)
                .orElseGet(() -> siteRepository.save(Site.builder().code(code).name(code).build()));
    }

    private Location newLocation(Site site, String label) {
        String suffix = label + "-" + UUID.randomUUID().toString().substring(0, 8);
        StorageLocation storage = storageLocationRepository.save(StorageLocation.builder()
                .site(site)
                .code("TOTALSIT-" + suffix)
                .name("Totals IT Storage " + suffix)
                .hasDisplay(false)
                .isDisplayOnly(false)
                .displayOrder(1)
                .build());
        return locationRepository.save(Location.builder()
                .storageLocation(storage)
                .locationCode("TOTALSIT-" + suffix)
                .build());
    }

    private Product newProduct(String label) {
        String suffix = label + "-" + UUID.randomUUID().toString().substring(0, 8);
        Category category = categoryRepository.save(Category.builder()
                .name("Totals IT Category " + suffix)
                .slug("totals-it-category-" + suffix.toLowerCase())
                .build());
        return productRepository.save(Product.builder()
                .sku("TOTALSIT-" + suffix)
                .name("Totals IT Product " + suffix)
                .category(category)
                .isActive(true)
                .quantity(0)
                .build());
    }

    @Test
    void findAllInventoryTotalsBySite_zeroStockProduct_stillAppearsWithZeroQuantity() {
        Site main = siteOf("MAIN");
        Product zeroStockProduct = newProduct("zero-stock");
        // Deliberately no LocationInventory row for this product at MAIN (or anywhere) --
        // F-6c-3's zero-stock hazard: it must still appear in the full-catalog result.

        List<SiteInventoryTotalDTO> totals = inventoryTotalsRepository.findAllInventoryTotalsBySite(main.getId());

        SiteInventoryTotalDTO row = totals.stream()
                .filter(t -> t.getProductId().equals(zeroStockProduct.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "zero-stock product missing from full-catalog result -- LEFT JOIN row-per-product guarantee broken"));
        assertThat(row.getTotalQuantity()).isZero();
        assertThat(row.getLastUpdatedAt()).isNull();
    }

    @Test
    void findAllInventoryTotalsBySite_sameProductDifferentSites_resolveIndependently() {
        Site main = siteOf("MAIN");
        Site second = siteOf("SECOND");
        Location mainLocation = newLocation(main, "indep-main");
        Location secondLocation = newLocation(second, "indep-second");
        Product product = newProduct("indep-shared");

        locationInventoryRepository.save(LocationInventory.builder()
                .location(mainLocation).site(main).product(product).quantity(7).build());
        locationInventoryRepository.save(LocationInventory.builder()
                .location(secondLocation).site(second).product(product).quantity(20).build());

        List<SiteInventoryTotalDTO> mainTotals = inventoryTotalsRepository.findAllInventoryTotalsBySite(main.getId());
        List<SiteInventoryTotalDTO> secondTotals = inventoryTotalsRepository.findAllInventoryTotalsBySite(second.getId());

        int mainQuantity = mainTotals.stream()
                .filter(t -> t.getProductId().equals(product.getId())).findFirst().orElseThrow()
                .getTotalQuantity();
        int secondQuantity = secondTotals.stream()
                .filter(t -> t.getProductId().equals(product.getId())).findFirst().orElseThrow()
                .getTotalQuantity();

        assertThat(mainQuantity).isEqualTo(7);
        assertThat(secondQuantity).isEqualTo(20);
    }

    @Test
    void findInventoryTotalsBySiteAndProductIds_returnsExactlyRequestedIds_absentIdOmitted() {
        Site main = siteOf("MAIN");
        Location mainLocation = newLocation(main, "batch-requested");
        Product stockedProduct = newProduct("batch-stocked");
        Product zeroStockRequestedProduct = newProduct("batch-zero-requested");
        Product notRequestedProduct = newProduct("batch-not-requested");

        locationInventoryRepository.save(LocationInventory.builder()
                .location(mainLocation).site(main).product(stockedProduct).quantity(12).build());
        locationInventoryRepository.save(LocationInventory.builder()
                .location(mainLocation).site(main).product(notRequestedProduct).quantity(99).build());
        // zeroStockRequestedProduct has no LocationInventory row anywhere.

        List<SiteInventoryTotalDTO> batch = inventoryTotalsRepository.findInventoryTotalsBySiteAndProductIds(
                main.getId(), List.of(stockedProduct.getId(), zeroStockRequestedProduct.getId()));

        List<UUID> returnedIds = batch.stream().map(SiteInventoryTotalDTO::getProductId).toList();
        assertThat(returnedIds).containsExactly(stockedProduct.getId());
        assertThat(returnedIds).doesNotContain(notRequestedProduct.getId());

        int stockedQuantity = batch.stream()
                .filter(t -> t.getProductId().equals(stockedProduct.getId())).findFirst().orElseThrow()
                .getTotalQuantity();
        assertThat(stockedQuantity).isEqualTo(12);
        // Documented batched-mode contract: an id with no inventory row at the site is simply
        // absent from the result (not returned as a zero row), unlike the full-catalog mode.
    }

    @Test
    void findInventoryTotalsBySiteAndProductIds_emptyOrNullIds_returnsEmptyList() {
        Site main = siteOf("MAIN");

        assertThat(inventoryTotalsRepository.findInventoryTotalsBySiteAndProductIds(main.getId(), List.of())).isEmpty();
        assertThat(inventoryTotalsRepository.findInventoryTotalsBySiteAndProductIds(main.getId(), null)).isEmpty();
    }

    @Test
    void findInventoryTotalsBySiteAndProductIds_overLimitBatch_rejectedWith400TypeException() {
        Site main = siteOf("MAIN");
        List<UUID> tooMany = java.util.stream.Stream
                .generate(UUID::randomUUID)
                .limit(InventoryTotalsRepository.MAX_PRODUCT_IDS_BATCH_SIZE + 1)
                .toList();

        assertThatThrownBy(() -> inventoryTotalsRepository.findInventoryTotalsBySiteAndProductIds(main.getId(), tooMany))
                .isInstanceOf(InvalidInventoryOperationException.class)
                .hasMessageContaining(String.valueOf(InventoryTotalsRepository.MAX_PRODUCT_IDS_BATCH_SIZE));
    }

    @Test
    void findInventoryTotalsBySiteAndProductIds_atLimitBatch_isAccepted() {
        Site main = siteOf("MAIN");
        List<UUID> exactlyAtLimit = java.util.stream.Stream
                .generate(UUID::randomUUID)
                .limit(InventoryTotalsRepository.MAX_PRODUCT_IDS_BATCH_SIZE)
                .toList();

        List<SiteInventoryTotalDTO> result =
                inventoryTotalsRepository.findInventoryTotalsBySiteAndProductIds(main.getId(), exactlyAtLimit);

        // None of these random ids are real products, so the result is empty, but no exception --
        // proves the boundary is inclusive of the documented maximum.
        assertThat(result).isEmpty();
    }
}
