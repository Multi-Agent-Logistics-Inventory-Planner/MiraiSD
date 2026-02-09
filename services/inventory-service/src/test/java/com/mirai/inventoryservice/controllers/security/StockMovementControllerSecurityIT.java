package com.mirai.inventoryservice.controllers.security;

import com.mirai.inventoryservice.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security integration tests for StockMovementController.
 * Authorization matrix:
 * - GET (history, audit-log): EMPLOYEE+ (employees need to view movements)
 * - POST (adjust, transfer): EMPLOYEE+ (employees perform stock operations)
 */
@DisplayName("StockMovementController Security Tests")
class StockMovementControllerSecurityIT extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/stock-movements";

    @Nested
    @DisplayName("GET /api/stock-movements/history/{itemId}")
    class GetMovementHistoryTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void getMovementHistory_noAuth_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL + "/history/550e8400-e29b-41d4-a716-446655440000"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to get history")
        void getMovementHistory_userRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL + "/history/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to get movement history")
        void getMovementHistory_employeeRole_notForbidden() throws Exception {
            mockMvc.perform(get(BASE_URL + "/history/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }

        @Test
        @DisplayName("Should allow ADMIN role to get movement history")
        void getMovementHistory_adminRole_notForbidden() throws Exception {
            mockMvc.perform(get(BASE_URL + "/history/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("GET /api/stock-movements/audit-log")
    class GetAuditLogTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void getAuditLog_noAuth_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL + "/audit-log"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to get audit log")
        void getAuditLog_userRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL + "/audit-log")
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to get audit log")
        void getAuditLog_employeeRole_notForbidden() throws Exception {
            mockMvc.perform(get(BASE_URL + "/audit-log")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("POST /api/stock-movements/{locationType}/{inventoryId}/adjust")
    class AdjustInventoryTests {

        private static final String ADJUST_URL = BASE_URL + "/RACK/550e8400-e29b-41d4-a716-446655440000/adjust";
        private static final String ADJUST_JSON = """
                {
                    "quantityChange": 5,
                    "reason": "RESTOCK",
                    "notes": "Test adjustment"
                }
                """;

        @Test
        @DisplayName("Should return 401 when no token provided")
        void adjustInventory_noAuth_returns401() throws Exception {
            mockMvc.perform(post(ADJUST_URL)
                            .contentType("application/json")
                            .content(ADJUST_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to adjust")
        void adjustInventory_userRole_returns403() throws Exception {
            mockMvc.perform(post(ADJUST_URL)
                            .header("Authorization", "Bearer " + userToken())
                            .contentType("application/json")
                            .content(ADJUST_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to adjust inventory")
        void adjustInventory_employeeRole_notForbidden() throws Exception {
            mockMvc.perform(post(ADJUST_URL)
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType("application/json")
                            .content(ADJUST_JSON))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("POST /api/stock-movements/transfer")
    class TransferInventoryTests {

        private static final String TRANSFER_URL = BASE_URL + "/transfer";
        private static final String TRANSFER_JSON = """
                {
                    "sourceLocationType": "RACK",
                    "sourceLocationId": "550e8400-e29b-41d4-a716-446655440000",
                    "targetLocationType": "CABINET",
                    "targetLocationId": "550e8400-e29b-41d4-a716-446655440001",
                    "itemId": "550e8400-e29b-41d4-a716-446655440002",
                    "quantity": 5
                }
                """;

        @Test
        @DisplayName("Should return 401 when no token provided")
        void transferInventory_noAuth_returns401() throws Exception {
            mockMvc.perform(post(TRANSFER_URL)
                            .contentType("application/json")
                            .content(TRANSFER_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to transfer")
        void transferInventory_userRole_returns403() throws Exception {
            mockMvc.perform(post(TRANSFER_URL)
                            .header("Authorization", "Bearer " + userToken())
                            .contentType("application/json")
                            .content(TRANSFER_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to transfer inventory")
        void transferInventory_employeeRole_notForbidden() throws Exception {
            mockMvc.perform(post(TRANSFER_URL)
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType("application/json")
                            .content(TRANSFER_JSON))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }
}
