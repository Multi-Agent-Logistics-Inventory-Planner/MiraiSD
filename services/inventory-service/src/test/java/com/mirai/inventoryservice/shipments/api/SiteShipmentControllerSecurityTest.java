package com.mirai.inventoryservice.shipments.api;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security/site-scoping tests for {@link SiteShipmentController} (.specs/phase-7-shipments T-5,
 * AC-5). Mirrors {@code ShipmentControllerSecurityTest}'s role-gate assertions for the v1 route
 * shape, plus foreign-site rejection ({@code SiteAccessAuthorizationFilter} runs before
 * {@code @PreAuthorize}, so a caller with no membership at the path's {@code siteId} is rejected
 * with 403 before any role check).
 */
@DisplayName("SiteShipmentController (v1 site-scoped shipments)")
class SiteShipmentControllerSecurityTest extends BaseIntegrationTest {

    @Autowired private SiteRepository siteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSiteMembershipRepository userSiteMembershipRepository;

    private Site siteWithMembership(String email, String code) {
        Site site = siteRepository.save(Site.builder().name(code + " Site").code(code).build());
        User user = userRepository.findByEmail(email).orElseThrow();
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(user.getId()).siteId(site.getId()).isActive(true).build());
        return site;
    }

    private String validShipmentRequestBody() {
        return """
                {
                    "shipmentNumber": "SHIP-001",
                    "supplierName": "Test Supplier",
                    "status": "PENDING",
                    "orderDate": "%s",
                    "createdBy": "%s",
                    "items": [{"itemId": "%s", "orderedQuantity": 10}]
                }
                """.formatted(LocalDate.now(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    @DisplayName("create: 401 with no token")
    void createSiteShipment_noAuth_returnsUnauthorized() throws Exception {
        UUID siteId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/sites/{siteId}/shipments", siteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validShipmentRequestBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("create: 403 for a caller with no membership at the path's site")
    void createSiteShipment_foreignSite_returnsForbidden() throws Exception {
        String token = adminToken();
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign Site").code("SS-CREATE-FOREIGN").build());

        mockMvc.perform(post("/api/v1/sites/{siteId}/shipments", foreignSite.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validShipmentRequestBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("create: 403 for EMPLOYEE role even with membership at this site (ADMIN/ASSISTANT_MANAGER only)")
    void createSiteShipment_employeeRole_returnsForbidden() throws Exception {
        String token = employeeToken();
        Site site = siteWithMembership("employee.persona@test.internal", "SS-CREATE-EMP");

        mockMvc.perform(post("/api/v1/sites/{siteId}/shipments", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validShipmentRequestBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("create: ADMIN with membership at this site is allowed through auth (not 401/403)")
    void createSiteShipment_adminWithMembership_isNotRejectedByAuth() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "SS-CREATE-ADMIN");

        int statusCode = mockMvc.perform(post("/api/v1/sites/{siteId}/shipments", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validShipmentRequestBody()))
                .andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(statusCode).isNotIn(401, 403);
    }

    @Test
    @DisplayName("list: 403 for a caller with no membership at the path's site")
    void listSiteShipments_foreignSite_returnsForbidden() throws Exception {
        String token = employeeToken();
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign Site").code("SS-LIST-FOREIGN").build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/shipments", foreignSite.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("list: EMPLOYEE with membership at this site is allowed (read-only routes permit EMPLOYEE)")
    void listSiteShipments_employeeWithMembership_isAllowed() throws Exception {
        String token = employeeToken();
        Site site = siteWithMembership("employee.persona@test.internal", "SS-LIST-EMP");

        mockMvc.perform(get("/api/v1/sites/{siteId}/shipments", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("delete: 403 for EMPLOYEE role even with membership at this site")
    void deleteSiteShipment_employeeRole_returnsForbidden() throws Exception {
        String token = employeeToken();
        Site site = siteWithMembership("employee.persona@test.internal", "SS-DELETE-EMP");

        mockMvc.perform(delete("/api/v1/sites/{siteId}/shipments/{id}", site.getId(), UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("receive: EMPLOYEE role with membership is allowed through the role gate (EMPLOYEE+ required)")
    void receiveSiteShipment_employeeWithMembership_isNotRejectedByAuth() throws Exception {
        String token = employeeToken();
        Site site = siteWithMembership("employee.persona@test.internal", "SS-RECEIVE-EMP");
        String body = """
                {
                    "actualDeliveryDate": "2024-01-26",
                    "receivedBy": "550e8400-e29b-41d4-a716-446655440000",
                    "itemReceipts": []
                }
                """;

        int statusCode = mockMvc.perform(post("/api/v1/sites/{siteId}/shipments/{id}/receive",
                        site.getId(), UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(statusCode).isNotIn(401, 403);
    }
}
