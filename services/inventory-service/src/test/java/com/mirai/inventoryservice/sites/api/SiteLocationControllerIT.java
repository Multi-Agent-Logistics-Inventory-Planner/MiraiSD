package com.mirai.inventoryservice.sites.api;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.sites.infrastructure.LocationRepository;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import com.mirai.inventoryservice.sites.infrastructure.StorageLocationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the new site-scoped location routes - the reference vertical slice for
 * docs/plans/enterprise-modernization.md Phase 4. Legacy {@code /api/locations} coverage
 * (LocationControllerSecurityIT) is untouched and asserts the old routes still behave
 * identically; this class only exercises the new {@code /api/v1/sites/{siteId}/locations}
 * surface, including the foreign-site-UUID case the legacy routes never checked.
 */
class SiteLocationControllerIT extends BaseIntegrationTest {

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private StorageLocationRepository storageLocationRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSiteMembershipRepository userSiteMembershipRepository;

    private Site createSiteWithMembership(String token, String email, String siteCode) {
        Site site = siteRepository.save(Site.builder().name(siteCode + " Site").code(siteCode).build());
        User user = userRepository.findByEmail(email).orElseThrow();
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(user.getId()).siteId(site.getId()).isActive(true).build());
        return site;
    }

    @Test
    void listReturns200ForAnActiveMember() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership(token, "admin.persona@test.internal", "SITE-LOC-1");

        mockMvc.perform(get("/api/v1/sites/{siteId}/locations", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listReturns403ForANonMember() throws Exception {
        String token = adminToken();
        Site site = siteRepository.save(Site.builder().name("Foreign Site").code("SITE-LOC-2").build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/locations", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void getByIdReturns404ForALocationBelongingToAnotherSite() throws Exception {
        String token = adminToken();
        Site siteA = createSiteWithMembership(token, "admin.persona@test.internal", "SITE-LOC-A");
        Site siteB = siteRepository.save(Site.builder().name("Site B").code("SITE-LOC-B").build());

        StorageLocation storageLocationB = storageLocationRepository.save(StorageLocation.builder()
                .site(siteB).code("BOX_BINS").name("Box Bins").isDisplayOnly(false).hasDisplay(false).build());
        Location locationInSiteB = locationRepository.save(Location.builder()
                .storageLocation(storageLocationB).locationCode("B1").build());

        // Foreign-site UUID: locationInSiteB exists, but not under siteA's path.
        mockMvc.perform(get("/api/v1/sites/{siteId}/locations/{id}", siteA.getId(), locationInSiteB.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByIdReturns200ForALocationBelongingToTheGivenSite() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership(token, "admin.persona@test.internal", "SITE-LOC-3");
        StorageLocation storageLocation = storageLocationRepository.save(StorageLocation.builder()
                .site(site).code("BOX_BINS").name("Box Bins").isDisplayOnly(false).hasDisplay(false).build());
        Location location = locationRepository.save(Location.builder()
                .storageLocation(storageLocation).locationCode("B1").build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/locations/{id}", site.getId(), location.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationCode").value("B1"));
    }

    @Test
    void createReturns403ForAnEmployeeWithoutMembership() throws Exception {
        String token = employeeToken();
        Site site = siteRepository.save(Site.builder().name("Site C").code("SITE-LOC-4").build());

        mockMvc.perform(post("/api/v1/sites/{siteId}/locations", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"locationCode\":\"B1\",\"storageLocationId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteReturns403ForAnEmployeeMember() throws Exception {
        String token = employeeToken();
        Site site = createSiteWithMembership(token, "employee.persona@test.internal", "SITE-LOC-5");
        StorageLocation storageLocation = storageLocationRepository.save(StorageLocation.builder()
                .site(site).code("BOX_BINS").name("Box Bins").isDisplayOnly(false).hasDisplay(false).build());
        Location location = locationRepository.save(Location.builder()
                .storageLocation(storageLocation).locationCode("B1").build());

        // Membership grants site access, but EMPLOYEE is still excluded from delete by @PreAuthorize.
        mockMvc.perform(delete("/api/v1/sites/{siteId}/locations/{id}", site.getId(), location.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void listReturns401ForAnUnauthenticatedRequest() throws Exception {
        Site site = siteRepository.save(Site.builder().name("Site D").code("SITE-LOC-6").build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/locations", site.getId()))
                .andExpect(status().isUnauthorized());
    }
}
