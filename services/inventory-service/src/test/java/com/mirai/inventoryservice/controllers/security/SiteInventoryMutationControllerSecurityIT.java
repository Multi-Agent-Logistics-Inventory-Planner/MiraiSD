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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    // ========= locations/{locationId}/items (T-6d-be-2/3, R-9) =========

    private Location seedLocation(Site site, String suffix) {
        StorageLocation storage = storageLocationRepository.findByCodeAndSite_Code("BOX_BINS", site.getCode())
                .orElseGet(() -> storageLocationRepository.save(StorageLocation.builder()
                        .site(site).code("BOX_BINS").name("Box Bins").isDisplayOnly(false).hasDisplay(false).build()));
        return locationRepository.save(Location.builder()
                .storageLocation(storage).locationCode("MUTSEC-ITEMS-" + suffix).build());
    }

    private Product seedProduct(String suffix) {
        Category category = categoryRepository.save(Category.builder()
                .name("Mut Sec Items Category " + suffix).slug("mut-sec-items-category-" + suffix).build());
        return productRepository.save(Product.builder()
                .sku("MUTSEC-ITEMS-" + suffix).name("Mut Sec Items Product " + suffix)
                .category(category).isActive(true).quantity(0).build());
    }

    private String createItemBody(UUID productId, int quantity) {
        return """
                {
                  "productId": "%s",
                  "quantity": %d
                }
                """.formatted(productId, quantity);
    }

    @Test
    @DisplayName("create item: 400 when Idempotency-Key header is missing")
    void createItem_missingIdempotencyKey_returns400() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership("admin.persona@test.internal", "SIMC-12");
        Location location = seedLocation(site, "12");
        Product product = seedProduct("12");

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/locations/{locationId}/items", site.getId(), location.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(createItemBody(product.getId(), 5)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("create item: 201 for an active EMPLOYEE member, persists the row")
    void createItem_employeeMember_returns201AndPersists() throws Exception {
        String token = employeeToken();
        Site site = createSiteWithMembership("employee.persona@test.internal", "SIMC-13");
        Location location = seedLocation(site, "13");
        Product product = seedProduct("13");

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/locations/{locationId}/items", site.getId(), location.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(createItemBody(product.getId(), 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.productId").value(product.getId().toString()));

        assertThat(locationInventoryRepository.findByLocation_IdAndProduct_Id(location.getId(), product.getId()))
                .isPresent();
    }

    @Test
    @DisplayName("create item: a location belonging to another site is rejected with 404, no write")
    void createItem_foreignSiteLocation_returns404() throws Exception {
        String token = adminToken();
        Site siteA = createSiteWithMembership("admin.persona@test.internal", "SIMC-14A");
        Site siteB = siteRepository.save(Site.builder().name("Site B").code("SIMC-14B").build());
        Location locationInSiteB = seedLocation(siteB, "14b");
        Product product = seedProduct("14");

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/locations/{locationId}/items", siteA.getId(), locationInSiteB.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(createItemBody(product.getId(), 5)))
                .andExpect(status().isNotFound());

        assertThat(locationInventoryRepository.findByLocation_IdAndProduct_Id(locationInSiteB.getId(), product.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("create item: same Idempotency-Key + identical body but a different locationId is "
            + "rejected with 409, not silently reused (review-driven fix, finding 2)")
    void createItem_sameKeySameBodyDifferentLocation_returns409AndDoesNotReuseFirstLocationResponse() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership("admin.persona@test.internal", "SIMC-12B");
        Location locationA = seedLocation(site, "12b-a");
        Location locationB = seedLocation(site, "12b-b");
        Product product = seedProduct("12b");
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/locations/{locationId}/items", site.getId(), locationA.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(createItemBody(product.getId(), 5)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/locations/{locationId}/items", site.getId(), locationB.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(createItemBody(product.getId(), 5)))
                .andExpect(status().isConflict());

        assertThat(locationInventoryRepository.findByLocation_IdAndProduct_Id(locationA.getId(), product.getId()))
                .isPresent();
        assertThat(locationInventoryRepository.findByLocation_IdAndProduct_Id(locationB.getId(), product.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("delete item: 403 for EMPLOYEE (below ADMIN/ASSISTANT_MANAGER)")
    void deleteItem_employeeRole_returns403() throws Exception {
        String token = employeeToken();
        Site site = createSiteWithMembership("employee.persona@test.internal", "SIMC-15");
        LocationInventory inventory = seedInventory(site, "15", 5);

        mockMvc.perform(delete("/api/v1/sites/{siteId}/inventory/locations/{locationId}/items/{inventoryId}",
                        site.getId(), inventory.getLocation().getId(), inventory.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        assertThat(locationInventoryRepository.findById(inventory.getId())).isPresent();
    }

    @Test
    @DisplayName("delete item: 204 for ADMIN, row is removed")
    void deleteItem_adminRole_returns204AndRemovesRow() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership("admin.persona@test.internal", "SIMC-16");
        LocationInventory inventory = seedInventory(site, "16", 5);

        mockMvc.perform(delete("/api/v1/sites/{siteId}/inventory/locations/{locationId}/items/{inventoryId}",
                        site.getId(), inventory.getLocation().getId(), inventory.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isNoContent());

        assertThat(locationInventoryRepository.findById(inventory.getId())).isEmpty();
    }

    @Test
    @DisplayName("delete item: a foreign-site inventory row is rejected with 404, no write")
    void deleteItem_foreignSiteInventory_returns404() throws Exception {
        String token = adminToken();
        Site siteA = createSiteWithMembership("admin.persona@test.internal", "SIMC-17A");
        Site siteB = siteRepository.save(Site.builder().name("Site B").code("SIMC-17B").build());
        LocationInventory inventoryInSiteB = seedInventory(siteB, "17b", 5);

        mockMvc.perform(delete("/api/v1/sites/{siteId}/inventory/locations/{locationId}/items/{inventoryId}",
                        siteA.getId(), inventoryInSiteB.getLocation().getId(), inventoryInSiteB.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());

        assertThat(locationInventoryRepository.findById(inventoryInSiteB.getId())).isPresent();
    }

    @Test
    @DisplayName("delete item: a location/inventory-row mismatch is rejected with 400, no write")
    void deleteItem_locationRowMismatch_returns400() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership("admin.persona@test.internal", "SIMC-18");
        LocationInventory inventory = seedInventory(site, "18", 5);
        Location otherLocation = seedLocation(site, "18b");

        mockMvc.perform(delete("/api/v1/sites/{siteId}/inventory/locations/{locationId}/items/{inventoryId}",
                        site.getId(), otherLocation.getId(), inventory.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());

        assertThat(locationInventoryRepository.findById(inventory.getId())).isPresent();
    }

    // ========= transfers/batch (T-6d-be-5) =========

    private String batchTransferBody(List<LocationInventory> sources, List<LocationInventory> destinations) {
        StringBuilder transfers = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            if (i > 0) transfers.append(",");
            transfers.append("""
                    {
                      "sourceLocationType": "BOX_BIN",
                      "sourceInventoryId": "%s",
                      "destinationLocationType": "BOX_BIN",
                      "destinationInventoryId": "%s",
                      "quantity": 1
                    }
                    """.formatted(sources.get(i).getId(), destinations.get(i).getId()));
        }
        return "{ \"transfers\": [" + transfers + "] }";
    }

    @Test
    @DisplayName("batch transfer: 400 when Idempotency-Key header is missing")
    void batchTransfer_missingIdempotencyKey_returns400() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership("admin.persona@test.internal", "SIMC-19");
        LocationInventory source = seedInventory(site, "19-src", 10);
        LocationInventory destination = seedInventory(site, "19-dst", 0);

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/transfers/batch", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(batchTransferBody(List.of(source), List.of(destination))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("batch transfer: 403 for USER role (below EMPLOYEE)")
    void batchTransfer_userRole_returns403() throws Exception {
        Site site = siteRepository.save(Site.builder().name("Batch User Role Site").code("SIMC-20").build());
        LocationInventory source = seedInventory(site, "20-src", 10);
        LocationInventory destination = seedInventory(site, "20-dst", 0);

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/transfers/batch", site.getId())
                        .header("Authorization", "Bearer " + userToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(batchTransferBody(List.of(source), List.of(destination))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("batch transfer: 404 for a source belonging to another site, no write")
    void batchTransfer_foreignSiteSource_returns404() throws Exception {
        String token = adminToken();
        Site siteA = createSiteWithMembership("admin.persona@test.internal", "SIMC-21A");
        Site siteB = siteRepository.save(Site.builder().name("Site B").code("SIMC-21B").build());
        LocationInventory sourceInSiteB = seedInventory(siteB, "21b-src", 10);
        LocationInventory destinationInSiteB = seedInventory(siteB, "21b-dst", 0);

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/transfers/batch", siteA.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(batchTransferBody(List.of(sourceInSiteB), List.of(destinationInSiteB))))
                .andExpect(status().isNotFound());

        assertThat(locationInventoryRepository.findById(sourceInSiteB.getId()).orElseThrow().getQuantity())
                .isEqualTo(10);
    }

    @Test
    @DisplayName("batch transfer: 201 for a same-site batch by an active member, replay is a no-op")
    void batchTransfer_sameSite_returns201AndReplayIsNoOp() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership("admin.persona@test.internal", "SIMC-22");
        LocationInventory source = seedInventory(site, "22-src", 10);
        LocationInventory destination = seedInventory(site, "22-dst", 0);
        String key = "http-batch-" + UUID.randomUUID();
        String body = batchTransferBody(List.of(source), List.of(destination));

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/transfers/batch", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/transfers/batch", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated());

        assertThat(locationInventoryRepository.findById(destination.getId()).orElseThrow().getQuantity())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("batch transfer: a 51-element batch is rejected with 400")
    void batchTransfer_51Elements_returns400() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership("admin.persona@test.internal", "SIMC-23");
        List<LocationInventory> sources = new java.util.ArrayList<>();
        List<LocationInventory> destinations = new java.util.ArrayList<>();
        for (int i = 0; i < 51; i++) {
            sources.add(seedInventory(site, "23-src-" + i, 10));
            destinations.add(seedInventory(site, "23-dst-" + i, 0));
        }

        mockMvc.perform(post("/api/v1/sites/{siteId}/inventory/transfers/batch", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(batchTransferBody(sources, destinations)))
                .andExpect(status().isBadRequest());
    }
}
