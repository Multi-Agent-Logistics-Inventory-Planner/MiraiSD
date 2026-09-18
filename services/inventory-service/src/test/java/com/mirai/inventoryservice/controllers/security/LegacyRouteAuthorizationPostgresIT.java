package com.mirai.inventoryservice.controllers.security;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserRole;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.integration.BasePostgresMockMvcIntegrationTest;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.sites.infrastructure.LocationRepository;
import com.mirai.inventoryservice.sites.infrastructure.StorageLocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PostgreSQL HTTP proof for the legacy routes kept on MAIN during the v1 migration.
 * These scenarios deliberately port the authorization behavior fixed by G3-G5 rather
 * than changing it.
 */
class LegacyRouteAuthorizationPostgresIT extends BasePostgresMockMvcIntegrationTest {

    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LocationInventoryRepository locationInventoryRepository;
    @Autowired private StockMovementRepository stockMovementRepository;

    @ParameterizedTest(name = "revoked membership is denied on {0}")
    @MethodSource("legacyRoutes")
    void revokedMainMembershipIsDeniedOnEveryLegacyRoute(String route) throws Exception {
        User user = seedUser(uniqueEmail("revoked"), UserRole.EMPLOYEE, false);
        grant(user, mainSite());
        UserSiteMembership membership = membershipRepository.findByUserIdAndSiteId(user.getId(), mainSite().getId()).orElseThrow();
        membership.setIsActive(false);
        membershipRepository.save(membership);

        mockMvc.perform(get(route).header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "absent membership is denied on {0}")
    @MethodSource("legacyRoutes")
    void absentMainMembershipIsDeniedOnEveryLegacyRoute(String route) throws Exception {
        User user = seedUser(uniqueEmail("absent"), UserRole.EMPLOYEE, false);

        mockMvc.perform(get(route).header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void foreignSiteLocationIsNotVisibleThroughLegacyMainRoute() throws Exception {
        User user = member(uniqueEmail("foreign"));
        Site second = siteRepository.save(Site.builder().name("Second " + UUID.randomUUID()).code("SECOND-" + UUID.randomUUID().toString().substring(0, 8)).build());
        Location foreignLocation = location(second, "FOREIGN");

        mockMvc.perform(get("/api/locations/{id}", foreignLocation.getId())
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isNotFound());
    }

    @Test
    void inventoryIdUnderTheWrongLocationParentIsNotFound() throws Exception {
        User user = member(uniqueEmail("parent"));
        Location requestedParent = location(mainSite(), "REQUESTED");
        Location actualParent = location(mainSite(), "ACTUAL");
        LocationInventory inventory = inventory(actualParent, 3);

        mockMvc.perform(get("/api/locations/{locationId}/inventory/{inventoryId}", requestedParent.getId(), inventory.getId())
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isNotFound());
    }

    @Test
    void batchAdjustIgnoresForgedActorIdAndUsesAuthenticatedPrincipal() throws Exception {
        User user = member(uniqueEmail("actor"));
        Location location = location(mainSite(), "ACTOR");
        LocationInventory inventory = inventory(location, 3);
        UUID forgedActorId = UUID.randomUUID();
        String body = """
                {"locationType":"RACK","locationId":"%s","actorId":"%s","reason":"RESTOCK",
                 "adjustments":[{"inventoryId":"%s","quantityChange":1}]}
                """.formatted(location.getId(), forgedActorId, inventory.getId());

        mockMvc.perform(post("/api/stock-movements/batch-adjust")
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());

        assertThat(stockMovementRepository.findTopByActorIdOrderByAtDesc(user.getId())).isPresent();
        assertThat(stockMovementRepository.findTopByActorIdOrderByAtDesc(forgedActorId)).isEmpty();
    }

    @Test
    void systemAdminBypassesMainMembershipWithoutCreatingMembership() throws Exception {
        User systemAdmin = seedUser(uniqueEmail("system-admin"), UserRole.EMPLOYEE, true);
        long membershipsBefore = membershipRepository.count();

        mockMvc.perform(get("/api/locations").header("Authorization", "Bearer " + tokenFor(systemAdmin)))
                .andExpect(status().isOk());

        assertThat(membershipRepository.count()).isEqualTo(membershipsBefore);
        assertThat(membershipRepository.findByUserId(systemAdmin.getId())).isEmpty();
    }

    @Test
    void stockMovementReadsExcludeForeignSiteRowsButIncludeNullSiteRows() throws Exception {
        User user = member(uniqueEmail("read-scope"));
        LocationInventory inventory = inventory(location(mainSite(), "READ"), 3);
        Site foreign = siteRepository.save(Site.builder().name("Foreign " + UUID.randomUUID())
                .code("FOREIGN-" + UUID.randomUUID().toString().substring(0, 8)).build());
        var mainRow = stockMovementRepository.save(movement(inventory, mainSite()));
        var unknownRow = stockMovementRepository.save(movement(inventory, null));
        var foreignRow = stockMovementRepository.save(movement(inventory, foreign));
        String token = tokenFor(user);

        for (String route : Stream.of(
                "/api/stock-movements/history/" + inventory.getProduct().getId(),
                "/api/stock-movements/history/" + inventory.getProduct().getId() + "/all",
                "/api/stock-movements/audit-log").toList()) {
            mockMvc.perform(get(route).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                            .string(org.hamcrest.Matchers.containsString("\"id\":" + mainRow.getId())))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                            .string(org.hamcrest.Matchers.containsString("\"id\":" + unknownRow.getId())))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                            .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"id\":" + foreignRow.getId()))));
        }
    }

    private User member(String email) {
        User user = seedUser(email, UserRole.EMPLOYEE, false);
        grant(user, mainSite());
        return user;
    }

    private Location location(Site site, String suffix) {
        StorageLocation storage = storageLocationRepository.save(StorageLocation.builder()
                // The legacy batch-adjust DTO maps LocationType.RACK to this canonical code.
                .site(site).name("Racks " + suffix).code("ACTOR".equals(suffix) ? "RACKS" : "RACKS-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 6))
                .codePrefix("R").build());
        return locationRepository.save(Location.builder().storageLocation(storage).locationCode("R-" + suffix).build());
    }

    private LocationInventory inventory(Location location, int quantity) {
        Category category = categoryRepository.save(Category.builder().name("Category " + UUID.randomUUID())
                .slug("category-" + UUID.randomUUID()).build());
        Product product = productRepository.save(Product.builder().name("Product " + UUID.randomUUID())
                .sku("SKU-" + UUID.randomUUID()).category(category).build());
        return locationInventoryRepository.save(LocationInventory.builder().location(location)
                .site(location.getStorageLocation().getSite()).product(product).quantity(quantity).build());
    }

    private static String uniqueEmail(String scenario) {
        return scenario + "." + UUID.randomUUID() + "@postgres-authz.test";
    }

    private static com.mirai.inventoryservice.inventory.domain.StockMovement movement(LocationInventory inventory, Site site) {
        return com.mirai.inventoryservice.inventory.domain.StockMovement.builder().item(inventory.getProduct())
                .site(site).locationType(com.mirai.inventoryservice.models.enums.LocationType.RACK)
                .previousQuantity(0).currentQuantity(1).quantityChange(1)
                .reason(com.mirai.inventoryservice.models.enums.StockMovementReason.RESTOCK).at(OffsetDateTime.now()).build();
    }

    private static Stream<String> legacyRoutes() {
        return Stream.of(
                "/api/locations",
                "/api/locations/" + UUID.randomUUID() + "/inventory",
                "/api/inventory/totals",
                "/api/stock-movements/history/" + UUID.randomUUID(),
                "/api/stock-movements/history/" + UUID.randomUUID() + "/all",
                "/api/stock-movements/audit-log");
    }
}
