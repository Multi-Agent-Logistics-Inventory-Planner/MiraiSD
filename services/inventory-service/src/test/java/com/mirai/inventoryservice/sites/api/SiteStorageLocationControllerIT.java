package com.mirai.inventoryservice.sites.api;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import com.mirai.inventoryservice.sites.infrastructure.StorageLocationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers the new site-scoped, read-only storage location routes. */
class SiteStorageLocationControllerIT extends BaseIntegrationTest {

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private StorageLocationRepository storageLocationRepository;

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

    @Test
    void listReturnsOnlyStorageLocationsForTheGivenSite() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership("admin.persona@test.internal", "SITE-SL-1");
        Site otherSite = siteRepository.save(Site.builder().name("Other").code("SITE-SL-2").build());
        storageLocationRepository.save(StorageLocation.builder()
                .site(site).code("BOX_BINS").name("Box Bins").displayOrder(1)
                .isDisplayOnly(false).hasDisplay(false).build());
        storageLocationRepository.save(StorageLocation.builder()
                .site(otherSite).code("RACKS").name("Racks").displayOrder(1)
                .isDisplayOnly(false).hasDisplay(false).build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/storage-locations", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("BOX_BINS"));
    }

    @Test
    void byCodeReturns404WhenTheCodeBelongsToAnotherSite() throws Exception {
        String token = adminToken();
        Site site = createSiteWithMembership("admin.persona@test.internal", "SITE-SL-3");
        Site otherSite = siteRepository.save(Site.builder().name("Other").code("SITE-SL-4").build());
        storageLocationRepository.save(StorageLocation.builder()
                .site(otherSite).code("BOX_BINS").name("Box Bins").displayOrder(1)
                .isDisplayOnly(false).hasDisplay(false).build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/storage-locations/by-code/{code}", site.getId(), "BOX_BINS")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturns403ForANonMember() throws Exception {
        String token = employeeToken();
        Site site = siteRepository.save(Site.builder().name("Site E").code("SITE-SL-5").build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/storage-locations", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
