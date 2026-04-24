package com.mirai.inventoryservice.controllers.security;

import com.mirai.inventoryservice.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

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

    @Nested
    @DisplayName("GET /api/analytics/demand-leaders")
    class GetDemandLeadersTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void getDemandLeaders_noAuth_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL + "/demand-leaders"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to access")
        void getDemandLeaders_userRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL + "/demand-leaders")
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 403 when EMPLOYEE role attempts to access")
        void getDemandLeaders_employeeRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL + "/demand-leaders")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ASSISTANT_MANAGER role to access")
        void getDemandLeaders_assistantManagerRole_returns200() throws Exception {
            mockMvc.perform(get(BASE_URL + "/demand-leaders")
                            .header("Authorization", "Bearer " + assistantManagerToken()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should allow ADMIN role to access")
        void getDemandLeaders_adminRole_returns200() throws Exception {
            mockMvc.perform(get(BASE_URL + "/demand-leaders")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/analytics/products/{id}/report-bundle/* (Product Assistant)")
    class ProductAssistantEndpointsTests {

        private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

        @Test
        @DisplayName("Header: 401 without token")
        void header_noAuth_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL + "/products/" + PRODUCT_ID + "/report-bundle/header"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Header: 403 for USER role")
        void header_userRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL + "/products/" + PRODUCT_ID + "/report-bundle/header")
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Header: 403 for EMPLOYEE role (admin-only)")
        void header_employeeRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL + "/products/" + PRODUCT_ID + "/report-bundle/header")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Detail: 403 for EMPLOYEE role")
        void detail_employeeRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL + "/products/" + PRODUCT_ID + "/report-bundle/detail")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Movements: 403 for EMPLOYEE role")
        void movements_employeeRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL + "/products/" + PRODUCT_ID + "/movements")
                            .param("from", "2026-01-01T00:00:00Z")
                            .param("to", "2026-04-01T00:00:00Z")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Movements summary: 403 for EMPLOYEE role")
        void movementSummary_employeeRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL + "/products/" + PRODUCT_ID + "/movements/summary")
                            .param("from", "2026-01-01T00:00:00Z")
                            .param("to", "2026-04-01T00:00:00Z")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Comparison: 403 for EMPLOYEE role")
        void comparison_employeeRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL + "/products/" + PRODUCT_ID + "/comparison")
                            .param("metric", "sales_velocity")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }
    }
}
