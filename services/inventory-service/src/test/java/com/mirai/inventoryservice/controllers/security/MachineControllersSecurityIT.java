package com.mirai.inventoryservice.controllers.security;

import com.mirai.inventoryservice.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security integration tests for all machine location controllers.
 * This test covers:
 * - KeychainMachineController
 * - SingleClawMachineController
 * - DoubleClawMachineController
 * - FourCornerMachineController
 * - PusherMachineController
 *
 * Authorization matrix:
 * - GET: ALL authenticated users
 * - POST/PUT/DELETE: ADMIN only
 */
@DisplayName("Machine Controllers Security Tests")
class MachineControllersSecurityIT extends BaseIntegrationTest {

    private static final String[] MACHINE_ENDPOINTS = {
            "/api/keychain-machines",
            "/api/single-claw-machines",
            "/api/double-claw-machines",
            "/api/four-corner-machines",
            "/api/pusher-machines"
    };

    @Nested
    @DisplayName("GET endpoints - All machine types")
    class GetTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "/api/keychain-machines",
                "/api/single-claw-machines",
                "/api/double-claw-machines",
                "/api/four-corner-machines",
                "/api/pusher-machines"
        })
        @DisplayName("Should return 401 when no token provided")
        void getMachines_noAuth_returns401(String endpoint) throws Exception {
            mockMvc.perform(get(endpoint))
                    .andExpect(status().isUnauthorized());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/api/keychain-machines",
                "/api/single-claw-machines",
                "/api/double-claw-machines",
                "/api/four-corner-machines",
                "/api/pusher-machines"
        })
        @DisplayName("Should allow USER role to list machines")
        void getMachines_userRole_notForbidden(String endpoint) throws Exception {
            mockMvc.perform(get(endpoint)
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/api/keychain-machines",
                "/api/single-claw-machines",
                "/api/double-claw-machines",
                "/api/four-corner-machines",
                "/api/pusher-machines"
        })
        @DisplayName("Should allow EMPLOYEE role to list machines")
        void getMachines_employeeRole_notForbidden(String endpoint) throws Exception {
            mockMvc.perform(get(endpoint)
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("POST endpoints - All machine types")
    class PostTests {

        @Test
        @DisplayName("KeychainMachine: Should return 403 when EMPLOYEE attempts to create")
        void createKeychainMachine_employeeRole_returns403() throws Exception {
            String json = """
                    {"keychainMachineCode": "KEY-001"}
                    """;
            mockMvc.perform(post("/api/keychain-machines")
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType("application/json")
                            .content(json))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("SingleClawMachine: Should return 403 when EMPLOYEE attempts to create")
        void createSingleClawMachine_employeeRole_returns403() throws Exception {
            String json = """
                    {"singleClawMachineCode": "SCM-001"}
                    """;
            mockMvc.perform(post("/api/single-claw-machines")
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType("application/json")
                            .content(json))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("DoubleClawMachine: Should return 403 when EMPLOYEE attempts to create")
        void createDoubleClawMachine_employeeRole_returns403() throws Exception {
            String json = """
                    {"doubleClawMachineCode": "DCM-001"}
                    """;
            mockMvc.perform(post("/api/double-claw-machines")
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType("application/json")
                            .content(json))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("FourCornerMachine: Should return 403 when EMPLOYEE attempts to create")
        void createFourCornerMachine_employeeRole_returns403() throws Exception {
            String json = """
                    {"fourCornerMachineCode": "FCM-001"}
                    """;
            mockMvc.perform(post("/api/four-corner-machines")
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType("application/json")
                            .content(json))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PusherMachine: Should return 403 when EMPLOYEE attempts to create")
        void createPusherMachine_employeeRole_returns403() throws Exception {
            String json = """
                    {"pusherMachineCode": "PM-001"}
                    """;
            mockMvc.perform(post("/api/pusher-machines")
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType("application/json")
                            .content(json))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("KeychainMachine: Should allow ADMIN role to create")
        void createKeychainMachine_adminRole_notForbidden() throws Exception {
            String json = """
                    {"keychainMachineCode": "KEY-001"}
                    """;
            mockMvc.perform(post("/api/keychain-machines")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType("application/json")
                            .content(json))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("PUT endpoints - All machine types")
    class PutTests {

        private static final String UUID = "/550e8400-e29b-41d4-a716-446655440000";

        @Test
        @DisplayName("KeychainMachine: Should return 403 when EMPLOYEE attempts to update")
        void updateKeychainMachine_employeeRole_returns403() throws Exception {
            String json = """
                    {"keychainMachineCode": "KEY-001-UPDATED"}
                    """;
            mockMvc.perform(put("/api/keychain-machines" + UUID)
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType("application/json")
                            .content(json))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("SingleClawMachine: Should return 403 when EMPLOYEE attempts to update")
        void updateSingleClawMachine_employeeRole_returns403() throws Exception {
            String json = """
                    {"singleClawMachineCode": "SCM-001-UPDATED"}
                    """;
            mockMvc.perform(put("/api/single-claw-machines" + UUID)
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType("application/json")
                            .content(json))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("DoubleClawMachine: Should allow ADMIN role to update")
        void updateDoubleClawMachine_adminRole_notForbidden() throws Exception {
            String json = """
                    {"doubleClawMachineCode": "DCM-001-UPDATED"}
                    """;
            mockMvc.perform(put("/api/double-claw-machines" + UUID)
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType("application/json")
                            .content(json))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("DELETE endpoints - All machine types")
    class DeleteTests {

        private static final String UUID = "/550e8400-e29b-41d4-a716-446655440000";

        @Test
        @DisplayName("KeychainMachine: Should return 403 when EMPLOYEE attempts to delete")
        void deleteKeychainMachine_employeeRole_returns403() throws Exception {
            mockMvc.perform(delete("/api/keychain-machines" + UUID)
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("SingleClawMachine: Should return 403 when EMPLOYEE attempts to delete")
        void deleteSingleClawMachine_employeeRole_returns403() throws Exception {
            mockMvc.perform(delete("/api/single-claw-machines" + UUID)
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("DoubleClawMachine: Should return 403 when EMPLOYEE attempts to delete")
        void deleteDoubleClawMachine_employeeRole_returns403() throws Exception {
            mockMvc.perform(delete("/api/double-claw-machines" + UUID)
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("FourCornerMachine: Should return 403 when EMPLOYEE attempts to delete")
        void deleteFourCornerMachine_employeeRole_returns403() throws Exception {
            mockMvc.perform(delete("/api/four-corner-machines" + UUID)
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PusherMachine: Should return 403 when EMPLOYEE attempts to delete")
        void deletePusherMachine_employeeRole_returns403() throws Exception {
            mockMvc.perform(delete("/api/pusher-machines" + UUID)
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("FourCornerMachine: Should allow ADMIN role to delete")
        void deleteFourCornerMachine_adminRole_notForbidden() throws Exception {
            mockMvc.perform(delete("/api/four-corner-machines" + UUID)
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }
}
