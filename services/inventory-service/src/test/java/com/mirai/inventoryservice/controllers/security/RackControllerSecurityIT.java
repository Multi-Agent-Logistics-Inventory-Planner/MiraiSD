package com.mirai.inventoryservice.controllers.security;

import com.mirai.inventoryservice.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security integration tests for RackController.
 * Authorization matrix:
 * - GET: ALL authenticated users
 * - POST/PUT/DELETE: ADMIN only
 */
@DisplayName("RackController Security Tests")
class RackControllerSecurityIT extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/racks";
    private static final String RACK_JSON = """
            {
                "rackCode": "RACK-001"
            }
            """;

    @Nested
    @DisplayName("GET /api/racks")
    class GetAllRacksTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void getAllRacks_noAuth_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should allow USER role to list racks")
        void getAllRacks_userRole_notForbidden() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to list racks")
        void getAllRacks_employeeRole_notForbidden() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("POST /api/racks")
    class CreateRackTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void createRack_noAuth_returns401() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType("application/json")
                            .content(RACK_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to create")
        void createRack_userRole_returns403() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + userToken())
                            .contentType("application/json")
                            .content(RACK_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 403 when EMPLOYEE role attempts to create")
        void createRack_employeeRole_returns403() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType("application/json")
                            .content(RACK_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ADMIN role to create rack")
        void createRack_adminRole_notForbidden() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType("application/json")
                            .content(RACK_JSON))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("PUT /api/racks/{id}")
    class UpdateRackTests {

        private static final String RACK_URL = BASE_URL + "/550e8400-e29b-41d4-a716-446655440000";

        @Test
        @DisplayName("Should return 403 when USER role attempts to update")
        void updateRack_userRole_returns403() throws Exception {
            mockMvc.perform(put(RACK_URL)
                            .header("Authorization", "Bearer " + userToken())
                            .contentType("application/json")
                            .content(RACK_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 403 when EMPLOYEE role attempts to update")
        void updateRack_employeeRole_returns403() throws Exception {
            mockMvc.perform(put(RACK_URL)
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType("application/json")
                            .content(RACK_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ADMIN role to update rack")
        void updateRack_adminRole_notForbidden() throws Exception {
            mockMvc.perform(put(RACK_URL)
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType("application/json")
                            .content(RACK_JSON))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("DELETE /api/racks/{id}")
    class DeleteRackTests {

        private static final String RACK_URL = BASE_URL + "/550e8400-e29b-41d4-a716-446655440000";

        @Test
        @DisplayName("Should return 403 when USER role attempts to delete")
        void deleteRack_userRole_returns403() throws Exception {
            mockMvc.perform(delete(RACK_URL)
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 403 when EMPLOYEE role attempts to delete")
        void deleteRack_employeeRole_returns403() throws Exception {
            mockMvc.perform(delete(RACK_URL)
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ADMIN role to delete rack")
        void deleteRack_adminRole_notForbidden() throws Exception {
            mockMvc.perform(delete(RACK_URL)
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }
}
