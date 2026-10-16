package com.mirai.inventoryservice.displays.api;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.displays.domain.MachineDisplay;
import com.mirai.inventoryservice.displays.infrastructure.MachineDisplayRepository;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import com.mirai.inventoryservice.models.enums.LocationType;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * .specs/phase-7-displays T-4, AC-3: proves a caller who IS a member of one site cannot reach a
 * machine/display that belongs to a different, real site through that site's own v1 path -- a
 * foreign-site id must be "not found," not "forbidden" or the other site's data. Mirrors
 * {@code shipments.api.SiteShipmentControllerCrossSiteAccessIT}'s shape and reasoning exactly.
 */
class SiteMachineDisplayControllerCrossSiteAccessIT extends BaseIntegrationTest {

    @Autowired private SiteRepository siteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSiteMembershipRepository userSiteMembershipRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private MachineDisplayRepository machineDisplayRepository;

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

    private record ForeignDisplay(UUID displayId, UUID machineId) {}

    private ForeignDisplay createDisplayAtSite(Site site, String suffix) {
        Category category = categoryRepository.save(Category.builder()
                .name("Cross Site Display Category " + suffix)
                .slug("cross-site-display-category-" + suffix)
                .build());
        Product product = productRepository.save(Product.builder()
                .sku("XSITE-DISPLAY-" + suffix)
                .name("Cross Site Display Product")
                .category(category)
                .reorderPoint(5)
                .targetStockLevel(20)
                .leadTimeDays(7)
                .isActive(true)
                .quantity(0)
                .build());
        Location machine = saveMachine(site, "XSITE-DISPLAY-MACHINE-" + suffix);
        MachineDisplay display = machineDisplayRepository.save(MachineDisplay.builder()
                .location(machine)
                .locationType(LocationType.SINGLE_CLAW_MACHINE)
                .machineId(machine.getId())
                .product(product)
                .startedAt(OffsetDateTime.now())
                .site(site)
                .build());
        return new ForeignDisplay(display.getId(), machine.getId());
    }

    @Test
    @DisplayName("GET current display for a foreign-site machine id through this site's path is 404")
    void getCurrentDisplay_foreignSiteMachine_returnsNotFound() throws Exception {
        String token = adminToken();
        Site callerSite = siteWithMembership("admin.persona@test.internal", "XSITE-DISP-GET-A");
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign B").code("XSITE-DISP-GET-B").build());
        ForeignDisplay foreign = createDisplayAtSite(foreignSite, "GET");

        mockMvc.perform(get("/api/v1/sites/{siteId}/machine-displays/{locationType}/{machineId}",
                        callerSite.getId(), LocationType.SINGLE_CLAW_MACHINE, foreign.machineId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET current display for the caller's own site returns 200 with the real display (same-site control)")
    void getCurrentDisplay_ownSiteMachine_returnsOk() throws Exception {
        String token = adminToken();
        Site callerSite = siteWithMembership("admin.persona@test.internal", "XSITE-DISP-CTRL-A");
        ForeignDisplay own = createDisplayAtSite(callerSite, "CTRL");

        mockMvc.perform(get("/api/v1/sites/{siteId}/machine-displays/{locationType}/{machineId}",
                        callerSite.getId(), LocationType.SINGLE_CLAW_MACHINE, own.machineId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(own.displayId().toString()));
    }

    @Test
    @DisplayName("batch-swap: a foreign targetMachineId with displayIdsFromTarget is 404 and leaves the foreign display active")
    void batchSwap_foreignTargetMachineWithDisplayIdsFromTarget_returnsNotFoundAndLeavesItIntact() throws Exception {
        String token = adminToken();
        Site callerSite = siteWithMembership("admin.persona@test.internal", "XSITE-DISP-BSFT-A");
        ForeignDisplay own = createDisplayAtSite(callerSite, "BSFT-OWN");
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign B").code("XSITE-DISP-BSFT-B").build());
        ForeignDisplay foreign = createDisplayAtSite(foreignSite, "BSFT-FOREIGN");

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "locationType", LocationType.SINGLE_CLAW_MACHINE.name(),
                "machineId", own.machineId(),
                "targetLocationType", LocationType.SINGLE_CLAW_MACHINE.name(),
                "targetMachineId", foreign.machineId(),
                "displayIdsFromTarget", List.of(foreign.displayId())
        ));

        mockMvc.perform(post("/api/v1/sites/{siteId}/machine-displays/batch-swap", callerSite.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());

        assertThat(machineDisplayRepository.findById(foreign.displayId()))
                .hasValueSatisfying(d -> assertThat(d.getEndedAt()).isNull());
    }

    @Test
    @DisplayName("batch-swap: a foreign display id in displayIdsToRemove on the caller's own machine is 400 and leaves it intact")
    void batchSwap_foreignDisplayIdInDisplayIdsToRemove_returnsBadRequestAndLeavesItIntact() throws Exception {
        String token = adminToken();
        Site callerSite = siteWithMembership("admin.persona@test.internal", "XSITE-DISP-BSR-A");
        ForeignDisplay own = createDisplayAtSite(callerSite, "BSR-OWN");
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign B").code("XSITE-DISP-BSR-B").build());
        ForeignDisplay foreign = createDisplayAtSite(foreignSite, "BSR-FOREIGN");

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "locationType", LocationType.SINGLE_CLAW_MACHINE.name(),
                "machineId", own.machineId(),
                "displayIdsToRemove", List.of(foreign.displayId())
        ));

        mockMvc.perform(post("/api/v1/sites/{siteId}/machine-displays/batch-swap", callerSite.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertThat(machineDisplayRepository.findById(foreign.displayId()))
                .hasValueSatisfying(d -> assertThat(d.getEndedAt()).isNull());
    }

    @Test
    @DisplayName("swap: a foreign outgoingDisplayId on the caller's own machine is 400 and leaves it intact")
    void swap_foreignOutgoingDisplayId_returnsBadRequestAndLeavesItIntact() throws Exception {
        String token = adminToken();
        Site callerSite = siteWithMembership("admin.persona@test.internal", "XSITE-DISP-SWAP-A");
        ForeignDisplay own = createDisplayAtSite(callerSite, "SWAP-OWN");
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign B").code("XSITE-DISP-SWAP-B").build());
        ForeignDisplay foreign = createDisplayAtSite(foreignSite, "SWAP-FOREIGN");

        Category category = categoryRepository.save(Category.builder()
                .name("Cross Site Swap Incoming Category").slug("cross-site-swap-incoming-category").build());
        Product incoming = productRepository.save(Product.builder()
                .sku("XSITE-SWAP-INCOMING").name("Incoming Product").category(category)
                .reorderPoint(5).targetStockLevel(20).leadTimeDays(7).isActive(true).quantity(0).build());

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "locationType", LocationType.SINGLE_CLAW_MACHINE.name(),
                "machineId", own.machineId(),
                "outgoingDisplayId", foreign.displayId(),
                "incomingProductId", incoming.getId()
        ));

        mockMvc.perform(post("/api/v1/sites/{siteId}/machine-displays/swap", callerSite.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertThat(machineDisplayRepository.findById(foreign.displayId()))
                .hasValueSatisfying(d -> assertThat(d.getEndedAt()).isNull());
    }

    @Test
    @DisplayName("renew: a foreign display id on the caller's own machine is 400 and leaves it intact")
    void renew_foreignDisplayId_returnsBadRequestAndLeavesItIntact() throws Exception {
        String token = adminToken();
        Site callerSite = siteWithMembership("admin.persona@test.internal", "XSITE-DISP-RENEW-A");
        ForeignDisplay own = createDisplayAtSite(callerSite, "RENEW-OWN");
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign B").code("XSITE-DISP-RENEW-B").build());
        ForeignDisplay foreign = createDisplayAtSite(foreignSite, "RENEW-FOREIGN");

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "locationType", LocationType.SINGLE_CLAW_MACHINE.name(),
                "machineId", own.machineId(),
                "displayIds", List.of(foreign.displayId())
        ));

        mockMvc.perform(post("/api/v1/sites/{siteId}/machine-displays/renew", callerSite.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertThat(machineDisplayRepository.findById(foreign.displayId()))
                .hasValueSatisfying(d -> assertThat(d.getEndedAt()).isNull());
    }

    @Test
    @DisplayName("batch-clear: a foreign display id is 400 and leaves it intact")
    void batchClear_foreignDisplayId_returnsBadRequestAndLeavesItIntact() throws Exception {
        String token = adminToken();
        Site callerSite = siteWithMembership("admin.persona@test.internal", "XSITE-DISP-BC-A");
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign B").code("XSITE-DISP-BC-B").build());
        ForeignDisplay foreign = createDisplayAtSite(foreignSite, "BC-FOREIGN");

        String body = objectMapper.writeValueAsString(
                java.util.Map.of("displayIds", List.of(foreign.displayId())));

        mockMvc.perform(post("/api/v1/sites/{siteId}/machine-displays/batch-clear", callerSite.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertThat(machineDisplayRepository.findById(foreign.displayId()))
                .hasValueSatisfying(d -> assertThat(d.getEndedAt()).isNull());
    }

    @Test
    @DisplayName("DELETE by-id: a foreign-site display id through this site's path is 400 and leaves it intact")
    void clearDisplayById_foreignSiteDisplay_returnsBadRequestAndLeavesItIntact() throws Exception {
        String token = adminToken();
        Site callerSite = siteWithMembership("admin.persona@test.internal", "XSITE-DISP-BYID-A");
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign B").code("XSITE-DISP-BYID-B").build());
        ForeignDisplay foreign = createDisplayAtSite(foreignSite, "BYID-FOREIGN");

        mockMvc.perform(delete("/api/v1/sites/{siteId}/machine-displays/by-id/{displayId}",
                        callerSite.getId(), foreign.displayId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        assertThat(machineDisplayRepository.findById(foreign.displayId()))
                .hasValueSatisfying(d -> assertThat(d.getEndedAt()).isNull());
    }

    @Test
    @DisplayName("DELETE display history for a foreign-site display id through this site's path is 400 and leaves it intact")
    void deleteDisplayHistory_foreignSiteDisplay_returnsBadRequestAndLeavesItIntact() throws Exception {
        String token = adminToken();
        Site callerSite = siteWithMembership("admin.persona@test.internal", "XSITE-DISP-DEL-A");
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign B").code("XSITE-DISP-DEL-B").build());
        ForeignDisplay foreign = createDisplayAtSite(foreignSite, "DEL");

        mockMvc.perform(delete("/api/v1/sites/{siteId}/machine-displays/history/{displayId}",
                        callerSite.getId(), foreign.displayId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        assertThat(machineDisplayRepository.findById(foreign.displayId())).isPresent();
    }

    @Test
    @DisplayName("DELETE (clear) a foreign-site machine through this site's path is 404 and does not clear it")
    void clearDisplay_foreignSiteMachine_returnsNotFoundAndLeavesItIntact() throws Exception {
        String token = adminToken();
        Site callerSite = siteWithMembership("admin.persona@test.internal", "XSITE-DISP-CLEAR-A");
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign B").code("XSITE-DISP-CLEAR-B").build());
        ForeignDisplay foreign = createDisplayAtSite(foreignSite, "CLEAR");

        mockMvc.perform(delete("/api/v1/sites/{siteId}/machine-displays/{locationType}/{machineId}",
                        callerSite.getId(), LocationType.SINGLE_CLAW_MACHINE, foreign.machineId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        assertThat(machineDisplayRepository.findById(foreign.displayId()))
                .hasValueSatisfying(d -> assertThat(d.getEndedAt()).isNull());
    }
}
