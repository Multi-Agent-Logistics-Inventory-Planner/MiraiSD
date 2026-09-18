package com.mirai.inventoryservice.controllers.security;

import com.mirai.inventoryservice.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security integration tests for LocationController.
 * Authorization matrix:
 * - GET: any role (ADMIN/ASSISTANT_MANAGER/EMPLOYEE), gated by active MAIN site membership
 *   (see {@code LegacyMainSiteContextResolver.requireMain()}) rather than by role alone
 * - POST/PUT: ADMIN, EMPLOYEE
 * - DELETE: ADMIN only
 */
@DisplayName("LocationController Security Tests")
class LocationControllerSecurityIT extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/locations";
    private static final String LOCATION_JSON = """
            {
                "locationCode": "T99",
                "storageLocationId": "550e8400-e29b-41d4-a716-446655440000"
            }
            """;
    private static final String UPDATE_JSON = """
            {
                "locationCode": "T99-UPDATED"
            }
            """;

    @Nested
    @DisplayName("GET /api/locations")
    class GetTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void getLocations_noAuth_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 for USER role with no MAIN site membership")
        void getLocations_userRoleNoMembership_returns403() throws Exception {
            // "USER" role is what JwtAuthenticationFilter assigns precisely when no backend
            // User record matches the caller (see its dbRole/backendUserId derivation) - such a
            // caller has no User.id, so it can never hold a UserSiteMembership row either. Role
            // alone no longer decides GET access here: LegacyMainSiteContextResolver.requireMain()
            // also requires an active MAIN membership, and an unregistered/no-membership caller
            // fails that regardless of role. The below employee/admin "notForbidden" tests are
            // this endpoint's "authenticated + membership -> 200" coverage, using the
            // *TokenWithMainMembership() helpers to grant that membership explicitly.
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to list locations")
        void getLocations_employeeRole_returns200() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + employeeTokenWithMainMembership()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should allow ADMIN role to list locations")
        void getLocations_adminRole_returns200() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + adminTokenWithMainMembership()))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /api/locations")
    class PostTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void createLocation_noAuth_returns401() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType("application/json")
                            .content(LOCATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to create")
        void createLocation_userRole_returns403() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + userToken())
                            .contentType("application/json")
                            .content(LOCATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to create location")
        void createLocation_employeeRole_returns404() throws Exception {
            // LOCATION_JSON's storageLocationId is a placeholder UUID with no seeded storage
            // location behind it, so a permitted caller reliably gets 404 here, not 201 - this
            // asserts the auth gate specifically, same as the sibling PUT/DELETE tests below.
            // Asserting the exact code (not just "not 401/403") also excludes a 500.
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + employeeTokenWithMainMembership())
                            .contentType("application/json")
                            .content(LOCATION_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should allow ADMIN role to create location")
        void createLocation_adminRole_returns404() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + adminTokenWithMainMembership())
                            .contentType("application/json")
                            .content(LOCATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/locations/{id}")
    class PutTests {

        private static final String LOCATION_URL = BASE_URL + "/550e8400-e29b-41d4-a716-446655440000";

        @Test
        @DisplayName("Should return 401 when no token provided")
        void updateLocation_noAuth_returns401() throws Exception {
            mockMvc.perform(put(LOCATION_URL)
                            .contentType("application/json")
                            .content(UPDATE_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to update")
        void updateLocation_userRole_returns403() throws Exception {
            mockMvc.perform(put(LOCATION_URL)
                            .header("Authorization", "Bearer " + userToken())
                            .contentType("application/json")
                            .content(UPDATE_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to update location")
        void updateLocation_employeeRole_returns404() throws Exception {
            // LOCATION_URL is a placeholder UUID with no seeded Location behind it, so a
            // permitted caller reliably gets 404 here, not 200 - this asserts the auth gate
            // only. Asserting the exact code (not just "not 401/403") also excludes a 500.
            mockMvc.perform(put(LOCATION_URL)
                            .header("Authorization", "Bearer " + employeeTokenWithMainMembership())
                            .contentType("application/json")
                            .content(UPDATE_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should allow ADMIN role to update location")
        void updateLocation_adminRole_returns404() throws Exception {
            mockMvc.perform(put(LOCATION_URL)
                            .header("Authorization", "Bearer " + adminTokenWithMainMembership())
                            .contentType("application/json")
                            .content(UPDATE_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/locations/{id}")
    class DeleteTests {

        private static final String LOCATION_URL = BASE_URL + "/550e8400-e29b-41d4-a716-446655440000";

        @Test
        @DisplayName("Should return 401 when no token provided")
        void deleteLocation_noAuth_returns401() throws Exception {
            mockMvc.perform(delete(LOCATION_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to delete")
        void deleteLocation_userRole_returns403() throws Exception {
            mockMvc.perform(delete(LOCATION_URL)
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 403 when EMPLOYEE role attempts to delete")
        void deleteLocation_employeeRole_returns403() throws Exception {
            mockMvc.perform(delete(LOCATION_URL)
                            .header("Authorization", "Bearer " + employeeTokenWithMainMembership()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ADMIN role to delete location")
        void deleteLocation_adminRole_returns404() throws Exception {
            // LOCATION_URL is a placeholder UUID with no seeded Location behind it, so a
            // permitted caller reliably gets 404 here, not 204 - this asserts the auth gate
            // only. Asserting the exact code (not just "not 401/403") also excludes a 500.
            mockMvc.perform(delete(LOCATION_URL)
                            .header("Authorization", "Bearer " + adminTokenWithMainMembership()))
                    .andExpect(status().isNotFound());
        }
    }
}
