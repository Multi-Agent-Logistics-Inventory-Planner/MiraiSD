package com.mirai.inventoryservice.sites.api;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
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

import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers {@code GET /api/v1/sites/{siteId}/locations/with-counts} (.specs/phase-6-inventory
 * 6e, T-6e-be-3): the site-scoped counterpart to the legacy, cross-site-leaking
 * {@code /api/locations/with-counts} (LocationAggregateControllerIT, untouched). Also pins
 * that {@code /with-counts} routes to this controller and not into
 * {@link SiteLocationController#getSiteLocationById}'s {@code /{id}} path-variable pattern.
 */
class SiteLocationAggregateControllerIT extends BaseIntegrationTest {

    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private StorageLocationRepository storageLocationRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private LocationInventoryRepository locationInventoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserSiteMembershipRepository userSiteMembershipRepository;

    private Site createSiteWithMembership(String email, String siteCode) {
        Site site = siteRepository.save(Site.builder().name(siteCode + " Site").code(siteCode).build());
        User user = userRepository.findByEmail(email).orElseThrow();
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(user.getId()).siteId(site.getId()).isActive(true).build());
        return site;
    }

    private StorageLocation boxBins(Site site) {
        return storageLocationRepository.save(StorageLocation.builder()
                .site(site).name("Box Bins").code("BOX_BINS").codePrefix("B")
                .hasDisplay(false).isDisplayOnly(false).displayOrder(1).build());
    }

    private Product product(String sku) {
        Category category = categoryRepository.save(Category.builder()
                .name("Test Category").slug("test-category-" + UUID.randomUUID()).build());
        return productRepository.save(Product.builder().sku(sku).name("Test Product " + sku).category(category).build());
    }

    @Test
    void withCountsRoutesHereNotToGetById() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership("admin.persona@test.internal", "SLAC-ROUTE");

        // If /with-counts were captured by the /{id} pattern, this would 400 on UUID parsing
        // instead of returning 200 with an array.
        mockMvc.perform(get("/api/v1/sites/{siteId}/locations/with-counts", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void returnsOnlyTheCallingSitesLocationsAndQuantities() throws Exception {
        String token = adminToken();
        Site siteA = createSiteWithMembership("admin.persona@test.internal", "SLAC-A");
        Site siteB = siteRepository.save(Site.builder().name("Site B").code("SLAC-B").build());

        StorageLocation storageA = boxBins(siteA);
        StorageLocation storageB = boxBins(siteB);
        Location locationA = locationRepository.save(Location.builder().storageLocation(storageA).locationCode("B1").build());
        Location locationB = locationRepository.save(Location.builder().storageLocation(storageB).locationCode("B1").build());

        Product productA = product("SLAC-A-1");
        Product productB = product("SLAC-B-1");
        locationInventoryRepository.save(LocationInventory.builder()
                .location(locationA).site(siteA).product(productA).quantity(5).build());
        locationInventoryRepository.save(LocationInventory.builder()
                .location(locationB).site(siteB).product(productB).quantity(99).build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/locations/with-counts", siteA.getId())
                        .param("type", "BOX_BIN")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].locationType", everyItem(equalTo("BOX_BINS"))))
                .andExpect(jsonPath("$[?(@.locationCode=='B1')].totalQuantity", hasItem(5)));

        // siteB's row/quantity must never appear under siteA's call.
        mockMvc.perform(get("/api/v1/sites/{siteId}/locations/with-counts", siteA.getId())
                        .param("type", "BOX_BIN")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.totalQuantity==99)]").isEmpty());
    }

    @Test
    void excludesAMismatchedSiteInventoryRowFromTheCount() throws Exception {
        String token = adminToken();
        Site siteA = createSiteWithMembership("admin.persona@test.internal", "SLAC-MISMATCH-A");
        Site siteB = siteRepository.save(Site.builder().name("Site B").code("SLAC-MISMATCH-B").build());

        StorageLocation storageA = boxBins(siteA);
        Location locationA = locationRepository.save(Location.builder().storageLocation(storageA).locationCode("B1").build());
        Product productX = product("SLAC-MISMATCH-1");

        // A location_inventory row physically under siteA's location, but tagged with siteB.
        locationInventoryRepository.save(LocationInventory.builder()
                .location(locationA).site(siteB).product(productX).quantity(42).build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/locations/with-counts", siteA.getId())
                        .param("type", "BOX_BIN")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.locationCode=='B1')].totalQuantity", hasItem(0)));
    }

    @Test
    void foreignSiteMembershipIsRejected() throws Exception {
        String token = adminToken();
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign Site").code("SLAC-FOREIGN").build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/locations/with-counts", foreignSite.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresAuthentication() throws Exception {
        Site site = siteRepository.save(Site.builder().name("Auth Site").code("SLAC-AUTH").build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/locations/with-counts", site.getId()))
                .andExpect(status().isUnauthorized());
    }
}
