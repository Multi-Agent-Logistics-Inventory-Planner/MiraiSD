package com.mirai.inventoryservice.controllers.security;

import com.mirai.inventoryservice.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security integration tests for AnalyticsController.
 * Authorization matrix:
 * - All GET endpoints: EMPLOYEE+ (EMPLOYEE or ADMIN)
 */
@DisplayName("AnalyticsController Security Tests")
class AnalyticsControllerSecurityIT extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/analytics";

    @Nested
    @DisplayName("GET /api/analytics/inventory-by-category")
    class GetInventoryByCategoryTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void getInventoryByCategory_noAuth_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL + "/inventory-by-category"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to access")
        void getInventoryByCategory_userRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL + "/inventory-by-category")
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to access")
        void getInventoryByCategory_employeeRole_returns200() throws Exception {
            mockMvc.perform(get(BASE_URL + "/inventory-by-category")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should allow ADMIN role to access")
        void getInventoryByCategory_adminRole_returns200() throws Exception {
            mockMvc.perform(get(BASE_URL + "/inventory-by-category")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/analytics/performance-metrics")
    class GetPerformanceMetricsTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void getPerformanceMetrics_noAuth_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL + "/performance-metrics"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to access")
        void getPerformanceMetrics_userRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL + "/performance-metrics")
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to access")
        void getPerformanceMetrics_employeeRole_returns200() throws Exception {
            mockMvc.perform(get(BASE_URL + "/performance-metrics")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should allow ADMIN role to access")
        void getPerformanceMetrics_adminRole_returns200() throws Exception {
            mockMvc.perform(get(BASE_URL + "/performance-metrics")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/analytics/sales-summary")
    class GetSalesSummaryTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void getSalesSummary_noAuth_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL + "/sales-summary"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to access")
        void getSalesSummary_userRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL + "/sales-summary")
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to access")
        void getSalesSummary_employeeRole_returns200() throws Exception {
            mockMvc.perform(get(BASE_URL + "/sales-summary")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should allow ADMIN role to access")
        void getSalesSummary_adminRole_returns200() throws Exception {
            mockMvc.perform(get(BASE_URL + "/sales-summary")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isOk());
        }
    }
}
