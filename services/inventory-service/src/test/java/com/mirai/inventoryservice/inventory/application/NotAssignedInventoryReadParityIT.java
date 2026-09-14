package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.KujiType;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
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

/**
 * T-6d-be-6 (.specs/phase-6-inventory 6d): proves the real read-parity delta the backend design
 * flagged (R-9's NOT_ASSIGNED resolution) between the legacy, unfiltered
 * {@code LocationInventoryRepository.findByStorageLocation_Id} (today's NOT_ASSIGNED read path)
 * and the v1 per-location route's {@code InventoryQueries.findByLocationIdAndSite} (backed by
 * {@code findByLocation_IdAndSite_Id}), which excludes kuji-child rows (non-null {@code
 * product.parent}) and CUSTOM-kuji-parent rows (own {@code kujiType == CUSTOM}).
 * <p>
 * Seeds one root product, one kuji-child product (parented to a CUSTOM kuji parent), and the
 * CUSTOM kuji parent product itself -- each with a real {@code location_inventory} row at one
 * NOT_ASSIGNED location -- and asserts: the legacy read returns all three (today's actual,
 * unfiltered behavior), while the v1 read returns only the root product. Moving the web's
 * NOT_ASSIGNED flow onto the v1 route (T-6d-9) is therefore a genuine behavior change, not a
 * no-op -- exactly what the design note requires this test to prove before the web slice ships it
 * as fact.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class NotAssignedInventoryReadParityIT {

    @Autowired private LocationInventoryRepository locationInventoryRepository;
    @Autowired private InventoryQueries inventoryQueries;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SiteRepository siteRepository;

    @Test
    void v1Read_excludesKujiChildAndCustomKujiParentRows_legacyReadDoesNot() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Site site = siteRepository.save(Site.builder().code("NA-IT-" + suffix).name("NA IT Site").build());

        StorageLocation naStorage = storageLocationRepository.save(StorageLocation.builder()
                .site(site).code("NOT_ASSIGNED").name("Not Assigned")
                .isDisplayOnly(false).hasDisplay(false).displayOrder(99).build());
        Location naLocation = locationRepository.save(Location.builder()
                .storageLocation(naStorage).locationCode("NA-" + suffix).build());

        Category category = categoryRepository.save(Category.builder()
                .name("NA IT Category " + suffix).slug("na-it-category-" + suffix).build());

        Product customParent = productRepository.save(Product.builder()
                .sku("NA-IT-CUSTOM-" + suffix).name("NA IT Custom Kuji Parent")
                .category(category).isActive(true).quantity(0).kujiType(KujiType.CUSTOM).build());
        Product kujiChild = productRepository.save(Product.builder()
                .sku("NA-IT-CHILD-" + suffix).name("NA IT Kuji Child")
                .category(category).isActive(true).quantity(0).parent(customParent).build());
        Product rootProduct = productRepository.save(Product.builder()
                .sku("NA-IT-ROOT-" + suffix).name("NA IT Root Product")
                .category(category).isActive(true).quantity(0).build());

        locationInventoryRepository.save(LocationInventory.builder()
                .location(naLocation).site(site).product(customParent).quantity(2).build());
        locationInventoryRepository.save(LocationInventory.builder()
                .location(naLocation).site(site).product(kujiChild).quantity(3).build());
        locationInventoryRepository.save(LocationInventory.builder()
                .location(naLocation).site(site).product(rootProduct).quantity(5).build());

        List<LocationInventory> legacyRows = locationInventoryRepository.findByStorageLocation_Id(naStorage.getId());
        List<LocationInventory> v1Rows = inventoryQueries.findByLocationIdAndSite(site.getId(), naLocation.getId());

        assertThat(legacyRows).extracting(li -> li.getProduct().getId())
                .containsExactlyInAnyOrder(customParent.getId(), kujiChild.getId(), rootProduct.getId());
        assertThat(v1Rows).extracting(li -> li.getProduct().getId())
                .containsExactly(rootProduct.getId());
    }

    /**
     * Proves only {@code storage_locations(site_id, code)} uniqueness -- a second
     * {@code NOT_ASSIGNED}-coded <b>storage location</b> row for the same site fails on the
     * schema's own constraint. This does NOT prove the web's actual assumption, which is one
     * {@code locations} row underneath a site's NOT_ASSIGNED storage location.
     * {@code locations} only carries {@code UNIQUE(storage_location_id, location_code)}
     * (`infra/init-db/20-unified-locations.sql`), so nothing at the schema level stops
     * {@code LocationService.createLocation} from adding a second {@code locations} row under one
     * site's single NOT_ASSIGNED storage location -- {@code LocationService.getNotAssignedLocation}
     * would then silently resolve one of several via an unordered {@code .stream().findFirst()}.
     * <p>
     * Corrected by the 6d review-driven fix (.specs/phase-6-inventory 6d, review-driven fix,
     * finding 3): this test's original Javadoc incorrectly claimed to prove "the web's
     * single-NA-location assumption is enforced." A read-only query against the project's real
     * database (`.specs/phase-6-inventory/validation.md`, "Finding 3 evidence") found no site with
     * more than one {@code locations} row under its NOT_ASSIGNED storage location today, but that
     * is empirical, not schema-enforced -- see validation.md and the log's Current handoff for the
     * open-risk disposition.
     */
    @Test
    void secondNotAssignedStorageLocationForSameSite_violatesUniqueConstraint() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Site site = siteRepository.save(Site.builder().code("NA-UNIQ-" + suffix).name("NA Uniq Site").build());
        storageLocationRepository.save(StorageLocation.builder()
                .site(site).code("NOT_ASSIGNED").name("Not Assigned")
                .isDisplayOnly(false).hasDisplay(false).displayOrder(99).build());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        storageLocationRepository.saveAndFlush(StorageLocation.builder()
                                .site(site).code("NOT_ASSIGNED").name("Not Assigned Duplicate")
                                .isDisplayOnly(false).hasDisplay(false).displayOrder(100).build()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
