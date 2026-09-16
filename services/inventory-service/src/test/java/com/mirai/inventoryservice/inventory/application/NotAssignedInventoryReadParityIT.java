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
 * T-6d-be-6 (.specs/phase-6-inventory 6d): originally proved a read-parity *delta* between the
 * legacy, unfiltered {@code LocationInventoryRepository.findByStorageLocation_Id} (the
 * NOT_ASSIGNED read path before T-6d-9) and the v1 per-location route's {@code
 * InventoryQueries.findByLocationIdAndSite}.
 * <p>
 * 6e (T-6e-be-9) deleted the legacy method as part of removing {@code LocationInventoryController}
 * entirely; the 6e independent review's R-3 finding required reverting that deletion (the
 * documented compatibility-removal gate, docs/baseline/api-v1-map.md, needs access-log evidence
 * plus a stabilization window on a *released* version -- unsatisfiable when the deprecation
 * headers for these exact routes only just landed on this same unmerged branch). On revert, the
 * legacy method's missing filter (the "trap" 6d only recorded, never closed) was fixed rather
 * than re-shipped: {@code findByStorageLocation_Id} now applies the same kuji-child/
 * CUSTOM-kuji-parent exclusion {@code findByLocation_Id}/{@code findByLocationIdAndSite} always
 * have. So there is no longer a delta to prove -- this test now proves read *parity*: both the
 * legacy and v1 reads exclude the same rows and agree on the root-only result.
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
    void legacyAndV1Reads_bothExcludeKujiChildAndCustomKujiParentRows_agreeOnRootOnly() {
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

        List<LocationInventory> v1Rows = inventoryQueries.findByLocationIdAndSite(site.getId(), naLocation.getId());
        List<LocationInventory> legacyRows = locationInventoryRepository.findByStorageLocation_Id(naStorage.getId());

        assertThat(v1Rows).extracting(li -> li.getProduct().getId())
                .containsExactly(rootProduct.getId());
        assertThat(legacyRows).extracting(li -> li.getProduct().getId())
                .containsExactly(rootProduct.getId());

        // The three seeded rows (root, kuji-child, CUSTOM-kuji-parent) are all real
        // location_inventory rows at this NOT_ASSIGNED location - confirms both reads' exclusion
        // is a real filter, not an artifact of an empty fixture. findBySite_Id is genuinely
        // unfiltered (unlike findByLocation_Id/findByStorageLocation_Id, which apply the same
        // kuji-child/CUSTOM-parent exclusion findByLocationIdAndSite does).
        List<LocationInventory> allRowsAtLocation = locationInventoryRepository.findBySite_Id(site.getId()).stream()
                .filter(li -> li.getLocation().getId().equals(naLocation.getId()))
                .toList();
        assertThat(allRowsAtLocation).extracting(li -> li.getProduct().getId())
                .containsExactlyInAnyOrder(customParent.getId(), kujiChild.getId(), rootProduct.getId());
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
