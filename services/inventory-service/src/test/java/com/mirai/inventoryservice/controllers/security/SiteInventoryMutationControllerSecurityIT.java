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
import com.mirai.inventoryservice.repositories.EventOutboxRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the new site-scoped inventory mutation routes (.specs/phase-6-inventory 6c, T-6c-12):
 * the required {@code Idempotency-Key} header, the authorization matrix, and end-to-end
 * idempotent replay through real HTTP (complementing
 * {@code SiteInventoryMutationControllerAtomicityIT}'s direct-bean-call commit/rollback proof).
 * Legacy {@code /api/stock-movements/*} coverage ({@code StockMovementControllerSecurityIT}) is
 * untouched.
 */
@DisplayName("SiteInventoryMutationController Security Tests")
class SiteInventoryMutationControllerSecurityIT extends BaseIntegrationTest {

    @Autowired private SiteRepository siteRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private LocationInventoryRepository locationInventoryRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSiteMembershipRepository userSiteMembershipRepository;
    @Autowired private EventOutboxRepository eventOutboxRepository;

    private Site createSiteWithMembership(String email, String siteCode) {
        Site site = siteRepository.save(Site.builder().name(siteCode + " Site").code(siteCode).build());
        User user = userRepository.findByEmail(email).orElseThrow();
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(user.getId()).siteId(site.getId()).isActive(true).build());
        return site;
    }

    private LocationInventory seedInventory(Site site, String suffix, int quantity) {
        StorageLocation storage = storageLocationRepository.findByCodeAndSite_Code("BOX_BINS", site.getCode())
                .orElseGet(() -> storageLocationRepository.save(StorageLocation.builder()
                        .site(site).code("BOX_BINS").name("Box Bins").isDisplayOnly(false).hasDisplay(false).build()));
        Location location = locationRepository.save(Location.builder()
                .storageLocation(storage).locationCode("MUTSEC-" + suffix).build());
        Category category = categoryRepository.save(Category.builder()
                .name("Mut Sec Category " + suffix).slug("mut-sec-category-" + suffix).build());
        Product product = productRepository.save(Product.builder()
                .sku("MUTSEC-" + suffix).name("Mut Sec Product " + suffix)
                .category(category).isActive(true).quantity(quantity).build());
        return locationInventoryRepository.save(LocationInventory.builder()
                .location(location).site(site).product(product).quantity(quantity).build());
    }

    private String adjustBody(LocationInventory inventory, int change) {
        return """
                {
                  "locationType": "BOX_BIN",
                  "locationId": "%s",
                  "reason": "SALE",
                  "adjustments": [{"inventoryId": "%s", "quantityChange": %d}]
                }
                """.formatted(inventory.getLocation().getId(), inventory.getId(), change);
    }

    @Test
    @DisplayName("adjustments: 401 for an unauthenticated request")
    void adjustments_noAuth_returns401() throws Exception {
        Site site = siteRepository.save(Site.builder().name("No Auth Site").code("SIMC-1").build());
        LocationInventory inventory = seedInventory(site, "1", 10);

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/adjustments", site.getId())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(adjustBody(inventory, -1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("adjustments: 403 for a non-member")
    void adjustments_nonMember_returns403() throws Exception {
        String token = adminToken();
        Site site = siteRepository.save(Site.builder().name("Foreign Site").code("SIMC-2").build());
        LocationInventory inventory = seedInventory(site, "2", 10);

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/adjustments", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(adjustBody(inventory, -1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("adjustments: 403 for USER role (below EMPLOYEE)")
    void adjustments_userRole_returns403() throws Exception {
        Site site = siteRepository.save(Site.builder().name("User Role Site").code("SIMC-3").build());
        LocationInventory inventory = seedInventory(site, "3", 10);

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/adjustments", site.getId())
                        .header("Authorization", "Bearer " + userToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(adjustBody(inventory, -1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("adjustments: 400 when Idempotency-Key header is missing")
    void adjustments_missingIdempotencyKey_returns400() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership("admin.persona@test.internal", "SIMC-4");
        LocationInventory inventory = seedInventory(site, "4", 10);

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/adjustments", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(adjustBody(inventory, -1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("adjustments: 201 for an active EMPLOYEE member, and the outbox event carries the idempotency key")
    void adjustments_employeeMember_returns201AndPropagatesIdempotencyKeyToOutbox() throws Exception {
        String token = employeeToken();
        Site site = createSiteWithMembership("employee.persona@test.internal", "SIMC-5");
        LocationInventory inventory = seedInventory(site, "5", 10);
        String key = "http-key-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/adjustments", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(adjustBody(inventory, -1)))
                .andExpect(status().isCreated());

        assertThat(eventOutboxRepository.findAll())
                .anyMatch(event -> key.equals(event.getIdempotencyKey()));
    }

    @Test
    @DisplayName("adjustments: replaying the same key+body over HTTP returns the same status without a second effect")
    void adjustments_httpReplay_returnsSameStatusWithoutDuplicateEffect() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership("admin.persona@test.internal", "SIMC-6");
        LocationInventory inventory = seedInventory(site, "6", 10);
        String key = "http-replay-" + UUID.randomUUID();
        String body = adjustBody(inventory, -1);

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/adjustments", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/adjustments", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated());

        LocationInventory reloaded = locationInventoryRepository.findById(inventory.getId()).orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualTo(9);
    }

    @Test
    @DisplayName("adjustments: the same key with a different body returns 409")
    void adjustments_sameKeyDifferentBody_returns409() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership("admin.persona@test.internal", "SIMC-7");
        LocationInventory inventory = seedInventory(site, "7", 10);
        String key = "http-conflict-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/adjustments", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(adjustBody(inventory, -1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/adjustments", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(adjustBody(inventory, -2)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("adjustments: a location belonging to another site is rejected with 404, no write")
    void adjustments_foreignSiteLocation_returns404() throws Exception {
        String token = adminToken();
        Site siteA = createSiteWithMembership("admin.persona@test.internal", "SIMC-8A");
        Site siteB = siteRepository.save(Site.builder().name("Site B").code("SIMC-8B").build());
        LocationInventory inventoryInSiteB = seedInventory(siteB, "8b", 10);

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/adjustments", siteA.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(adjustBody(inventoryInSiteB, -1)))
                .andExpect(status().isNotFound());

        LocationInventory reloaded = locationInventoryRepository.findById(inventoryInSiteB.getId()).orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("transfers: 400 when Idempotency-Key header is missing")
    void transfers_missingIdempotencyKey_returns400() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership("admin.persona@test.internal", "SIMC-9");
        LocationInventory source = seedInventory(site, "9-src", 10);
        LocationInventory destination = seedInventory(site, "9-dst", 0);

        String body = """
                {
                  "sourceLocationType": "BOX_BIN",
                  "sourceInventoryId": "%s",
                  "destinationLocationType": "BOX_BIN",
                  "destinationInventoryId": "%s",
                  "quantity": 1
                }
                """.formatted(source.getId(), destination.getId());

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/transfers", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("transfers: 404 for a source inventory belonging to another site, no write")
    void transfers_foreignSiteSource_returns404() throws Exception {
        String token = adminToken();
        Site siteA = createSiteWithMembership("admin.persona@test.internal", "SIMC-10A");
        Site siteB = siteRepository.save(Site.builder().name("Site B").code("SIMC-10B").build());
        LocationInventory sourceInSiteB = seedInventory(siteB, "10b-src", 10);
        LocationInventory destinationInSiteB = seedInventory(siteB, "10b-dst", 0);

        String body = """
                {
                  "sourceLocationType": "BOX_BIN",
                  "sourceInventoryId": "%s",
                  "destinationLocationType": "BOX_BIN",
                  "destinationInventoryId": "%s",
                  "quantity": 1
                }
                """.formatted(sourceInSiteB.getId(), destinationInSiteB.getId());

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/transfers", siteA.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isNotFound());

        assertThat(locationInventoryRepository.findById(sourceInSiteB.getId()).orElseThrow().getQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("transfers: 201 for a same-site transfer by an active member")
    void transfers_sameSite_returns201() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership("admin.persona@test.internal", "SIMC-11");
        LocationInventory source = seedInventory(site, "11-src", 10);
        LocationInventory destination = seedInventory(site, "11-dst", 0);

        String body = """
                {
                  "sourceLocationType": "BOX_BIN",
                  "sourceInventoryId": "%s",
                  "destinationLocationType": "BOX_BIN",
                  "destinationInventoryId": "%s",
                  "quantity": 3
                }
                """.formatted(source.getId(), destination.getId());

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/transfers", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated());
    }
}
