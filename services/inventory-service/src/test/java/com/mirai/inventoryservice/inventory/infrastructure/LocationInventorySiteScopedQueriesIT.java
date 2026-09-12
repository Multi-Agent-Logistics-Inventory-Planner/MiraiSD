package com.mirai.inventoryservice.inventory.infrastructure;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.domain.StockMovement;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.sites.infrastructure.LocationRepository;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import com.mirai.inventoryservice.sites.infrastructure.StorageLocationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-6c-1 (.specs/phase-6-inventory/log.md, AC-3): proves every new site-qualified repository
 * method rejects a foreign-site identifier at the query, not after loading in Java. Seeds the
 * same {@code (location, product)} shape at MAIN and SECOND and asserts each method returns only
 * its own site's rows, {@code Optional.empty()}/an empty list for a foreign-site id, and that
 * {@link StockMovementSpecifications#withSiteFilter} includes null-site rows per Q-6c-5's
 * resolved "label, don't hide" decision.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class LocationInventorySiteScopedQueriesIT {

    @Autowired private LocationInventoryRepository locationInventoryRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
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
                .code("SCOPEDIT-" + suffix)
                .name("Scoped IT Storage " + suffix)
                .hasDisplay(false)
                .isDisplayOnly(false)
                .displayOrder(1)
                .build());
        return locationRepository.save(Location.builder()
                .storageLocation(storage)
                .locationCode("SCOPEDIT-" + suffix)
                .build());
    }

    private Product newProduct(String label) {
        String suffix = label + "-" + UUID.randomUUID().toString().substring(0, 8);
        Category category = categoryRepository.save(Category.builder()
                .name("Scoped IT Category " + suffix)
                .slug("scoped-it-category-" + suffix.toLowerCase())
                .build());
        return productRepository.save(Product.builder()
                .sku("SCOPEDIT-" + suffix)
                .name("Scoped IT Product " + suffix)
                .category(category)
                .isActive(true)
                .quantity(0)
                .build());
    }

    @Test
    void findByIdAndSite_Id_returnsOwnSiteOnly_emptyForForeignSite() {
        Site main = siteOf("MAIN");
        Site second = siteOf("SECOND");
        Location mainLocation = newLocation(main, "find-by-id");
        Product product = newProduct("find-by-id");

        LocationInventory inventory = locationInventoryRepository.save(LocationInventory.builder()
                .location(mainLocation).site(main).product(product).quantity(5).build());

        Optional<LocationInventory> ownSite = locationInventoryRepository.findByIdAndSite_Id(inventory.getId(), main.getId());
        Optional<LocationInventory> foreignSite = locationInventoryRepository.findByIdAndSite_Id(inventory.getId(), second.getId());

        assertThat(ownSite).isPresent();
        assertThat(ownSite.get().getId()).isEqualTo(inventory.getId());
        assertThat(foreignSite).isEmpty();
    }

    @Test
    void findByLocation_IdAndProduct_IdAndSite_Id_isolatesBySite() {
        Site main = siteOf("MAIN");
        Site second = siteOf("SECOND");
        Location mainLocation = newLocation(main, "loc-product-site");
        Product product = newProduct("loc-product-site");
        locationInventoryRepository.save(LocationInventory.builder()
                .location(mainLocation).site(main).product(product).quantity(7).build());

        Optional<LocationInventory> ownSite = locationInventoryRepository
                .findByLocation_IdAndProduct_IdAndSite_Id(mainLocation.getId(), product.getId(), main.getId());
        Optional<LocationInventory> foreignSite = locationInventoryRepository
                .findByLocation_IdAndProduct_IdAndSite_Id(mainLocation.getId(), product.getId(), second.getId());

        assertThat(ownSite).isPresent();
        assertThat(foreignSite).isEmpty();
    }

    @Test
    void findAllByIdInAndSite_IdWithGraph_excludesForeignSiteRowsFromBatch() {
        Site main = siteOf("MAIN");
        Site second = siteOf("SECOND");
        Location mainLocation = newLocation(main, "batch-main");
        Location secondLocation = newLocation(second, "batch-second");
        Product mainProduct = newProduct("batch-main");
        Product secondProduct = newProduct("batch-second");

        LocationInventory mainInv = locationInventoryRepository.save(LocationInventory.builder()
                .location(mainLocation).site(main).product(mainProduct).quantity(3).build());
        LocationInventory secondInv = locationInventoryRepository.save(LocationInventory.builder()
                .location(secondLocation).site(second).product(secondProduct).quantity(4).build());

        List<LocationInventory> mainOnly = locationInventoryRepository.findAllByIdInAndSite_IdWithGraph(
                List.of(mainInv.getId(), secondInv.getId()), main.getId());

        assertThat(mainOnly).extracting(LocationInventory::getId).containsExactly(mainInv.getId());
    }

    @Test
    void findByLocation_IdAndSite_Id_rejectsForeignSiteLocationId() {
        Site main = siteOf("MAIN");
        Site second = siteOf("SECOND");
        Location mainLocation = newLocation(main, "loc-site");
        Product product = newProduct("loc-site");
        locationInventoryRepository.save(LocationInventory.builder()
                .location(mainLocation).site(main).product(product).quantity(2).build());

        List<LocationInventory> ownSite =
                locationInventoryRepository.findByLocation_IdAndSite_Id(mainLocation.getId(), main.getId());
        List<LocationInventory> foreignSite =
                locationInventoryRepository.findByLocation_IdAndSite_Id(mainLocation.getId(), second.getId());

        assertThat(ownSite).hasSize(1);
        assertThat(foreignSite).isEmpty();
    }

    @Test
    void sumQuantitiesByProductIdsAndSiteId_sumsOnlyTheGivenSite() {
        Site main = siteOf("MAIN");
        Site second = siteOf("SECOND");
        Location mainLocation = newLocation(main, "sum-main");
        Location secondLocation = newLocation(second, "sum-second");
        Product product = newProduct("sum-shared");

        locationInventoryRepository.save(LocationInventory.builder()
                .location(mainLocation).site(main).product(product).quantity(10).build());
        locationInventoryRepository.save(LocationInventory.builder()
                .location(secondLocation).site(second).product(product).quantity(99).build());

        List<Object[]> mainSums = locationInventoryRepository
                .sumQuantitiesByProductIdsAndSiteId(List.of(product.getId()), main.getId());
        List<Object[]> secondSums = locationInventoryRepository
                .sumQuantitiesByProductIdsAndSiteId(List.of(product.getId()), second.getId());

        assertThat(mainSums).hasSize(1);
        assertThat(((Number) mainSums.get(0)[1]).intValue()).isEqualTo(10);
        assertThat(secondSums).hasSize(1);
        assertThat(((Number) secondSums.get(0)[1]).intValue()).isEqualTo(99);
    }

    @Test
    void stockMovementHistory_findByItem_IdAndSite_IdOrderByAtDesc_isolatesBySite() {
        Site main = siteOf("MAIN");
        Site second = siteOf("SECOND");
        Product product = newProduct("history-shared");

        stockMovementRepository.save(StockMovement.builder()
                .item(product).locationType(LocationType.BOX_BIN)
                .previousQuantity(0).currentQuantity(5).quantityChange(5)
                .reason(StockMovementReason.SHIPMENT_RECEIPT).site(main).at(OffsetDateTime.now())
                .build());
        stockMovementRepository.save(StockMovement.builder()
                .item(product).locationType(LocationType.BOX_BIN)
                .previousQuantity(0).currentQuantity(9).quantityChange(9)
                .reason(StockMovementReason.SHIPMENT_RECEIPT).site(second).at(OffsetDateTime.now())
                .build());

        Page<StockMovement> mainHistory = stockMovementRepository
                .findByItem_IdAndSite_IdOrderByAtDesc(product.getId(), main.getId(), PageRequest.of(0, 10));

        assertThat(mainHistory.getContent()).hasSize(1);
        assertThat(mainHistory.getContent().get(0).getSite().getId()).isEqualTo(main.getId());
    }

    @Test
    void withSiteFilter_matchesOwnSiteAndIncludesNullSiteRows_excludesForeignSite() {
        Site main = siteOf("MAIN");
        Site second = siteOf("SECOND");
        Product product = newProduct("audit-site-filter");

        StockMovement mainMovement = stockMovementRepository.save(StockMovement.builder()
                .item(product).locationType(LocationType.BOX_BIN)
                .previousQuantity(0).currentQuantity(1).quantityChange(1)
                .reason(StockMovementReason.SHIPMENT_RECEIPT).site(main).at(OffsetDateTime.now())
                .metadata(Map.of())
                .build());
        StockMovement secondMovement = stockMovementRepository.save(StockMovement.builder()
                .item(product).locationType(LocationType.BOX_BIN)
                .previousQuantity(0).currentQuantity(2).quantityChange(2)
                .reason(StockMovementReason.SHIPMENT_RECEIPT).site(second).at(OffsetDateTime.now())
                .metadata(Map.of())
                .build());
        StockMovement nullSiteMovement = stockMovementRepository.save(StockMovement.builder()
                .item(product).locationType(LocationType.BOX_BIN)
                .previousQuantity(0).currentQuantity(3).quantityChange(3)
                .reason(StockMovementReason.SHIPMENT_RECEIPT).site(null).at(OffsetDateTime.now())
                .metadata(Map.of())
                .build());

        AuditLogFilterDTO filters = AuditLogFilterDTO.builder().productId(product.getId()).build();
        Page<StockMovement> mainScoped = stockMovementRepository.findAll(
                StockMovementSpecifications.withSiteFilter(filters, main.getId()), PageRequest.of(0, 10));

        List<Long> ids = mainScoped.getContent().stream().map(StockMovement::getId).toList();
        assertThat(ids).contains(mainMovement.getId(), nullSiteMovement.getId());
        assertThat(ids).doesNotContain(secondMovement.getId());
    }
}
