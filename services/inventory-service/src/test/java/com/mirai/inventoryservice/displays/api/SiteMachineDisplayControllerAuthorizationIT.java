package com.mirai.inventoryservice.displays.api;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.displays.infrastructure.MachineDisplayRepository;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.repositories.AuditLogRepository;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.sites.infrastructure.LocationRepository;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import com.mirai.inventoryservice.sites.infrastructure.StorageLocationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * .specs/phase-7-displays T-4 review findings 1 and 2: (1) a v1 mutation's actor is always the
 * authenticated principal, never a client-supplied value; (2) a machine already showing more than
 * one active product does not 500 the v1 "set" or "current display" routes.
 */
class SiteMachineDisplayControllerAuthorizationIT extends BaseIntegrationTest {

    @Autowired private SiteRepository siteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSiteMembershipRepository userSiteMembershipRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private MachineDisplayRepository machineDisplayRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private Site siteWithMembership(String email, String code) {
        Site site = siteRepository.save(Site.builder().name(code + " Site").code(code).build());
        User user = userRepository.findByEmail(email).orElseThrow();
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(user.getId()).siteId(site.getId()).isActive(true).build());
        return site;
    }

    private Location saveMachine(Site site, String code) {
        StorageLocation storage = storageLocationRepository.save(StorageLocation.builder()
                .site(site)
                .code(code)
                .name("Machine " + code)
                .hasDisplay(true)
                .isDisplayOnly(true)
                .displayOrder(0)
                .build());
        return locationRepository.save(Location.builder()
                .storageLocation(storage)
                .locationCode(code)
                .build());
    }

    private Product saveProduct(String suffix) {
        Category category = categoryRepository.save(Category.builder()
                .name("Auth IT Category " + suffix)
                .slug("auth-it-category-" + suffix)
                .build());
        return productRepository.save(Product.builder()
                .sku("AUTH-IT-" + suffix)
                .name("Auth IT Product " + suffix)
                .category(category)
                .reorderPoint(5)
                .targetStockLevel(20)
                .leadTimeDays(7)
                .isActive(true)
                .quantity(0)
                .build());
    }

    @Test
    @DisplayName("POST set ignores a client-supplied actorId and stores the authenticated principal instead")
    void setDisplay_ignoresClientSuppliedActorId_storesAuthenticatedPrincipal() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "AUTHIT-SPOOF");
        Location machine = saveMachine(site, "AUTHIT-SPOOF-MACHINE");
        Product product = saveProduct("SPOOF");
        UUID spoofedActorId = UUID.randomUUID();

        String body = objectMapper.writeValueAsString(Map.of(
                "locationType", LocationType.SINGLE_CLAW_MACHINE.name(),
                "machineId", machine.getId(),
                "productId", product.getId(),
                "actorId", spoofedActorId
        ));

        mockMvc.perform(post("/api/v1/sites/{siteId}/machine-displays", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        User admin = userRepository.findByEmail("admin.persona@test.internal").orElseThrow();
        List<com.mirai.inventoryservice.displays.domain.MachineDisplay> stored =
                machineDisplayRepository.findActiveByLocationTypeAndMachineId(
                        LocationType.SINGLE_CLAW_MACHINE, machine.getId());

        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getActorId()).isEqualTo(admin.getId());
        assertThat(stored.get(0).getActorId()).isNotEqualTo(spoofedActorId);
    }

    @Test
    @DisplayName("DELETE clear ignores a spoofed actorId query parameter and records the authenticated principal as the operation actor")
    void clearDisplay_doesNotAcceptClientActorIdQueryParam() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "AUTHIT-CLEAR-SPOOF");
        Location machine = saveMachine(site, "AUTHIT-CLEAR-SPOOF-MACHINE");
        Product product = saveProduct("CLEAR-SPOOF");

        String body = objectMapper.writeValueAsString(Map.of(
                "locationType", LocationType.SINGLE_CLAW_MACHINE.name(),
                "machineId", machine.getId(),
                "productId", product.getId()
        ));
        mockMvc.perform(post("/api/v1/sites/{siteId}/machine-displays", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        UUID spoofedActorId = UUID.randomUUID();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/sites/{siteId}/machine-displays/{locationType}/{machineId}",
                                site.getId(), LocationType.SINGLE_CLAW_MACHINE, machine.getId())
                        .param("actorId", spoofedActorId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Clearing only sets endedAt on the existing row -- it does not rewrite that row's
        // actorId (set at creation time). The clear operation's own actor is recorded on the
        // DISPLAY_REMOVED audit log/stock movement instead, so that's what must be asserted here;
        // asserting the display row's actorId would pass even if this route started trusting the
        // spoofed query param again. AuditLog.actorId is a read-only shadow column mirroring the
        // `user` association (insertable/updatable = false, and AuditLogService only ever sets
        // `.user(...)`, never `.actorId(...)` on the builder) -- asserting getUser().getId()
        // avoids depending on that column ever getting populated on a same-persistence-context
        // read.
        User admin = userRepository.findByEmail("admin.persona@test.internal").orElseThrow();
        Page<AuditLog> removalLogs = auditLogRepository.findByReasonOrderByCreatedAtDesc(
                StockMovementReason.DISPLAY_REMOVED, PageRequest.of(0, 1));
        assertThat(removalLogs.getContent()).hasSize(1);
        assertThat(removalLogs.getContent().get(0).getUser()).isNotNull();
        assertThat(removalLogs.getContent().get(0).getUser().getId()).isEqualTo(admin.getId());
        assertThat(removalLogs.getContent().get(0).getUser().getId()).isNotEqualTo(spoofedActorId);
    }

    @Test
    @DisplayName("POST set on a machine with an existing active display does not 500, both products end up active, and the response reflects the product just added")
    void setDisplay_machineAlreadyHasAnActiveDisplay_doesNotFailAndBothAreActive() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "AUTHIT-MULTI");
        Location machine = saveMachine(site, "AUTHIT-MULTI-MACHINE");
        Product first = saveProduct("MULTI-1");
        Product second = saveProduct("MULTI-2");

        mockMvc.perform(post("/api/v1/sites/{siteId}/machine-displays", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "locationType", LocationType.SINGLE_CLAW_MACHINE.name(),
                                "machineId", machine.getId(),
                                "productId", first.getId()
                        ))))
                .andExpect(status().isCreated());

        // The response must reflect the display just created (product B), not "the" active
        // display on the machine reselected by a query that orders oldest-first -- that would
        // silently return product A's display again with a 201, which a caller reading the
        // response body would mistake for confirmation that B is now displayed.
        mockMvc.perform(post("/api/v1/sites/{siteId}/machine-displays", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "locationType", LocationType.SINGLE_CLAW_MACHINE.name(),
                                "machineId", machine.getId(),
                                "productId", second.getId()
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(second.getId().toString()));

        List<com.mirai.inventoryservice.displays.domain.MachineDisplay> active =
                machineDisplayRepository.findActiveByLocationTypeAndMachineId(
                        LocationType.SINGLE_CLAW_MACHINE, machine.getId());
        assertThat(active).hasSize(2);
    }

    @Test
    @DisplayName("GET current display on a machine with two active products does not 500")
    void getCurrentDisplay_machineWithTwoActiveProducts_doesNotFail() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "AUTHIT-MULTI-GET");
        Location machine = saveMachine(site, "AUTHIT-MULTI-GET-MACHINE");
        Product first = saveProduct("MULTI-GET-1");
        Product second = saveProduct("MULTI-GET-2");

        mockMvc.perform(post("/api/v1/sites/{siteId}/machine-displays/batch", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "locationType", LocationType.SINGLE_CLAW_MACHINE.name(),
                                "machineId", machine.getId(),
                                "productIds", List.of(first.getId(), second.getId())
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/sites/{siteId}/machine-displays/{locationType}/{machineId}",
                        site.getId(), LocationType.SINGLE_CLAW_MACHINE, machine.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());
    }
}
