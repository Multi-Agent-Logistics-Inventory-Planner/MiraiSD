package com.mirai.inventoryservice.controllers.security;

import com.mirai.inventoryservice.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security integration tests for DoubleClawMachineInventoryController.
 * Authorization matrix:
 * - GET: ALL authenticated users
 * - POST/PUT: EMPLOYEE+
 * - DELETE: ADMIN only
 */
@DisplayName("DoubleClawMachineInventoryController Security Tests")
class DoubleClawMachineInventoryControllerSecurityIT extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/double-claw-machines/550e8400-e29b-41d4-a716-446655440000/inventory";
    private static final String INVENTORY_JSON = """
            {
                "itemId": "550e8400-e29b-41d4-a716-446655440001",
                "quantity": 10
            }
            """;

    @Nested
    @DisplayName("GET endpoints")
    class GetTests {

        @Test
        @DisplayName("Should allow USER role to list inventory")
        void listInventory_userRole_notForbidden() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("POST endpoints")
    class PostTests {

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
    }

    @Nested
    @DisplayName("PUT endpoints")
    class PutTests {

        private static final String INVENTORY_URL = BASE_URL + "/550e8400-e29b-41d4-a716-446655440002";

        @Test
        @DisplayName("Should return 403 when USER role attempts to update")
        void updateInventory_userRole_returns403() throws Exception {
            mockMvc.perform(put(INVENTORY_URL)
                            .header("Authorization", "Bearer " + userToken())
                            .contentType("application/json")
                            .content(INVENTORY_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to update inventory")
        void updateInventory_employeeRole_notForbidden() throws Exception {
            mockMvc.perform(put(INVENTORY_URL)
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType("application/json")
                            .content(INVENTORY_JSON))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("DELETE endpoints")
    class DeleteTests {

        private static final String INVENTORY_URL = BASE_URL + "/550e8400-e29b-41d4-a716-446655440002";

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
}
