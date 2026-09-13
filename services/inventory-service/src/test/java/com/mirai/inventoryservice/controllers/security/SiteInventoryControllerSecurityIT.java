package com.mirai.inventoryservice.controllers.security;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the new site-scoped inventory read routes (.specs/phase-6-inventory 6c, T-6c-11).
 * Legacy {@code /api/inventory}/{@code /api/stock-movements} coverage
 * ({@code LocationInventoryControllerSecurityIT}/{@code StockMovementControllerSecurityIT}) is
 * untouched; this class only exercises the new {@code /api/v1/sites/{siteId}/inventory} surface.
 */
@DisplayName("SiteInventoryController Security Tests")
class SiteInventoryControllerSecurityIT extends BaseIntegrationTest {

    @Autowired private SiteRepository siteRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private LocationInventoryRepository locationInventoryRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSiteMembershipRepository userSiteMembershipRepository;

    private Site createSiteWithMembership(String email, String siteCode) {
        Site site = siteRepository.save(Site.builder().name(siteCode + " Site").code(siteCode).build());
        User user = userRepository.findByEmail(email).orElseThrow();
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(user.getId()).siteId(site.getId()).isActive(true).build());
        return site;
    }

    private Location newLocation(Site site, String suffix) {
        StorageLocation storage = storageLocationRepository.save(StorageLocation.builder()
                .site(site).code("BOX_BINS").name("Box Bins").isDisplayOnly(false).hasDisplay(false).build());
        return locationRepository.save(Location.builder()
                .storageLocation(storage).locationCode("B-" + suffix).build());
    }

    private Product newProduct(String suffix) {
        Category category = categoryRepository.save(Category.builder()
                .name("Site Inv Category " + suffix).slug("site-inv-category-" + suffix).build());
        return productRepository.save(Product.builder()
                .sku("SITEINV-" + suffix).name("Site Inv Product " + suffix)
                .category(category).isActive(true).quantity(0).build());
    }

    @Test
    @DisplayName("totals: 401 for an unauthenticated request")
    void totals_noAuth_returns401() throws Exception {
        Site site = siteRepository.save(Site.builder().name("No Auth Site").code("SIC-1").build());
        mockMvc.perform(get("/api/v1/sites/{siteId}/inventory/totals", site.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("totals: 403 for a non-member")
    void totals_nonMember_returns403() throws Exception {
        String token = adminToken();
        Site site = siteRepository.save(Site.builder().name("Foreign Site").code("SIC-2").build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/inventory/totals", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("totals: 404 for an unknown siteId")
    void totals_unknownSite_returns404() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/sites/{siteId}/inventory/totals", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("totals: 200 for an active member, returns an array")
    void totals_activeMember_returns200() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership("admin.persona@test.internal", "SIC-3");

        mockMvc.perform(get("/api/v1/sites/{siteId}/inventory/totals", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("totals: 403 for USER role (below EMPLOYEE)")
    void totals_userRole_returns403() throws Exception {
        Site site = siteRepository.save(Site.builder().name("User Role Site").code("SIC-4").build());
        mockMvc.perform(get("/api/v1/sites/{siteId}/inventory/totals", site.getId())
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("products/{id}: 200 with empty entries for a product with no inventory at this site")
    void byProduct_noInventoryAtSite_returnsEmptyEntries() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership("admin.persona@test.internal", "SIC-5");
        Product product = newProduct("by-product-empty");

        mockMvc.perform(get("/api/v1/sites/{siteId}/inventory/products/{productId}", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isArray())
                .andExpect(jsonPath("$.entries.length()").value(0));
    }

    @Test
    @DisplayName("products/{id}: 200 with the site's own row, not another site's")
    void byProduct_onlyReturnsThisSitesInventory() throws Exception {
        String token = adminToken();
        Site siteA = createSiteWithMembership("admin.persona@test.internal", "SIC-6A");
        Site siteB = siteRepository.save(Site.builder().name("Site B").code("SIC-6B").build());
        Product product = newProduct("by-product-scoped");

        Location locationA = newLocation(siteA, "6a");
        Location locationB = newLocation(siteB, "6b");
        locationInventoryRepository.save(LocationInventory.builder()
                .location(locationA).site(siteA).product(product).quantity(3).build());
        locationInventoryRepository.save(LocationInventory.builder()
                .location(locationB).site(siteB).product(product).quantity(9).build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/inventory/products/{productId}", siteA.getId(), product.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuantity").value(3))
                .andExpect(jsonPath("$.entries.length()").value(1));
    }

    @Test
    @DisplayName("locations/{id}: 404 for a location belonging to another site")
    void byLocation_foreignSiteLocation_returns404() throws Exception {
        String token = adminToken();
        Site siteA = createSiteWithMembership("admin.persona@test.internal", "SIC-7A");
        Site siteB = siteRepository.save(Site.builder().name("Site B").code("SIC-7B").build());
        Location locationInSiteB = newLocation(siteB, "7b");

        mockMvc.perform(get("/api/v1/sites/{siteId}/inventory/locations/{locationId}", siteA.getId(), locationInSiteB.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("locations/{id}: 200 with the location's rows for the given site")
    void byLocation_ownSite_returns200() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership("admin.persona@test.internal", "SIC-8");
        Location location = newLocation(site, "8");
        Product product = newProduct("by-location");
        locationInventoryRepository.save(LocationInventory.builder()
                .location(location).site(site).product(product).quantity(7).build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/inventory/locations/{locationId}", site.getId(), location.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].quantity").value(7))
                .andExpect(jsonPath("$[0].productId").value(product.getId().toString()));
    }

    @Test
    @DisplayName("movements: 200, returns an array, for an active member")
    void movements_activeMember_returns200() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership("admin.persona@test.internal", "SIC-9");

        mockMvc.perform(get("/api/v1/sites/{siteId}/inventory/movements", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("movements: 403 for a non-member")
    void movements_nonMember_returns403() throws Exception {
        String token = adminToken();
        Site site = siteRepository.save(Site.builder().name("Foreign Movements Site").code("SIC-10").build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/inventory/movements", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
