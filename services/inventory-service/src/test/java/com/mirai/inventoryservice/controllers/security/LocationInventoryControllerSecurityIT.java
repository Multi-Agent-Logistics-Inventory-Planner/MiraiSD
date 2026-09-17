package com.mirai.inventoryservice.controllers.security;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security integration tests for LocationInventoryController.
 * Consolidates tests for all location types into unified endpoint tests.
 *
 * Authorization matrix:
 * - GET: ALL authenticated users
 * - POST/PUT: EMPLOYEE+
 * - DELETE: ADMIN only
 */
@DisplayName("LocationInventoryController Security Tests")
class LocationInventoryControllerSecurityIT extends BaseIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired private UserRepository userRepository;
    @org.springframework.beans.factory.annotation.Autowired private UserSiteMembershipRepository membershipRepository;
    @org.springframework.beans.factory.annotation.Autowired private SiteRepository siteRepository;

    private static final String BASE_URL = "/api/locations/550e8400-e29b-41d4-a716-446655440000/inventory";
    private static final String INVENTORY_JSON = """
            {
                "itemId": "550e8400-e29b-41d4-a716-446655440001",
                "quantity": 10
            }
            """;

    @BeforeEach
    void grantPrivilegedPersonasMainMembership() {
        employeeToken();
        adminToken();
        var main = siteRepository.findByCode("MAIN").orElseThrow();
        grant("employee.persona@test.internal", main.getId());
        grant("admin.persona@test.internal", main.getId());
    }

    private void grant(String email, java.util.UUID siteId) {
        User user = userRepository.findByEmail(email).orElseThrow();
        if (membershipRepository.findByUserIdAndSiteId(user.getId(), siteId).isEmpty()) {
            membershipRepository.save(UserSiteMembership.builder().userId(user.getId()).siteId(siteId).isActive(true).build());
        }
    }

    @Nested
    @DisplayName("GET endpoints")
    class GetTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void listInventory_noAuth_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should deny an authenticated user without MAIN membership")
        void listInventory_userRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to list inventory")
        void listInventory_employeeRole_notForbidden() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }

        @Test
        @DisplayName("Should allow ADMIN role to list inventory")
        void listInventory_adminRole_notForbidden() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("POST endpoints")
    class PostTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void addInventory_noAuth_returns401() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType("application/json")
                            .content(INVENTORY_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to add")
        void addInventory_userRole_returns403() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + userToken())
                            .contentType("application/json")
                            .content(INVENTORY_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to add inventory")
        void addInventory_employeeRole_notForbidden() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType("application/json")
                            .content(INVENTORY_JSON))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }

        @Test
        @DisplayName("Should allow ADMIN role to add inventory")
        void addInventory_adminRole_notForbidden() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType("application/json")
                            .content(INVENTORY_JSON))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    // PUT (updateInventory) stays removed even through the 6e R-3 revert -- see the controller's
    // own comment for why.

    @Nested
    @DisplayName("DELETE endpoints")
    class DeleteTests {

        private static final String INVENTORY_URL = BASE_URL + "/550e8400-e29b-41d4-a716-446655440002";

        @Test
        @DisplayName("Should return 401 when no token provided")
        void deleteInventory_noAuth_returns401() throws Exception {
            mockMvc.perform(delete(INVENTORY_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to delete")
        void deleteInventory_userRole_returns403() throws Exception {
            mockMvc.perform(delete(INVENTORY_URL)
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 403 when EMPLOYEE role attempts to delete")
        void deleteInventory_employeeRole_returns403() throws Exception {
            mockMvc.perform(delete(INVENTORY_URL)
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ADMIN role to delete inventory")
        void deleteInventory_adminRole_notForbidden() throws Exception {
            mockMvc.perform(delete(INVENTORY_URL)
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("Storage location level endpoints")
    class StorageLocationTests {

        private static final String STORAGE_URL = "/api/storage-locations/550e8400-e29b-41d4-a716-446655440003/inventory";

        @Test
        @DisplayName("Should return 401 when no token provided")
        void listByStorageLocation_noAuth_returns401() throws Exception {
            mockMvc.perform(get(STORAGE_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should deny an authenticated user without MAIN membership")
        void listByStorageLocation_userRole_returns403() throws Exception {
            mockMvc.perform(get(STORAGE_URL)
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }
    }

    // Product-level (/api/inventory/by-product/{id}) coverage lives in
    // InventoryAggregateControllerSecurityIT -- that route belongs to a different controller
    // (InventoryAggregateController) and was moved out during the 6e R-3 revert cycle to avoid
    // duplicate test methods across two files covering the same route.
}
