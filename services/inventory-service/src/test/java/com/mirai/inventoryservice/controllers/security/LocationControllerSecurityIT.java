package com.mirai.inventoryservice.controllers.security;

import com.mirai.inventoryservice.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security integration tests for LocationController.
 * Authorization matrix:
 * - GET: ALL authenticated users
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
        @DisplayName("Should allow USER role to list locations")
        void getLocations_userRole_notForbidden() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to list locations")
        void getLocations_employeeRole_notForbidden() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }

        @Test
        @DisplayName("Should allow ADMIN role to list locations")
        void getLocations_adminRole_notForbidden() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
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
        void createLocation_employeeRole_notForbidden() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType("application/json")
                            .content(LOCATION_JSON))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }

        @Test
        @DisplayName("Should allow ADMIN role to create location")
        void createLocation_adminRole_notForbidden() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType("application/json")
                            .content(LOCATION_JSON))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
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
        void updateLocation_employeeRole_notForbidden() throws Exception {
            mockMvc.perform(put(LOCATION_URL)
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType("application/json")
                            .content(UPDATE_JSON))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }

        @Test
        @DisplayName("Should allow ADMIN role to update location")
        void updateLocation_adminRole_notForbidden() throws Exception {
            mockMvc.perform(put(LOCATION_URL)
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType("application/json")
                            .content(UPDATE_JSON))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
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
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ADMIN role to delete location")
        void deleteLocation_adminRole_notForbidden() throws Exception {
            mockMvc.perform(delete(LOCATION_URL)
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }
}
