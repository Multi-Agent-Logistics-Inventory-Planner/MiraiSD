package com.mirai.inventoryservice.controllers.security;

import com.mirai.inventoryservice.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security integration tests for ForecastController.
 * Authorization matrix:
 * - All GET endpoints: EMPLOYEE+ (EMPLOYEE or ADMIN)
 */
@DisplayName("ForecastController Security Tests")
class ForecastControllerSecurityIT extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/forecasts";

    @Nested
    @DisplayName("GET /api/forecasts")
    class GetAllForecastsTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void getAllForecasts_noAuth_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to access")
        void getAllForecasts_userRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to access")
        void getAllForecasts_employeeRole_returns200() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should allow ADMIN role to access")
        void getAllForecasts_adminRole_returns200() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/forecasts/{itemId}")
    class GetForecastByItemTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void getForecastByItem_noAuth_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to access")
        void getForecastByItem_userRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to access")
        void getForecastByItem_employeeRole_notForbidden() throws Exception {
            mockMvc.perform(get(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("GET /api/forecasts/at-risk")
    class GetAtRiskItemsTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void getAtRiskItems_noAuth_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL + "/at-risk"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to access")
        void getAtRiskItems_userRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL + "/at-risk")
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to access")
        void getAtRiskItems_employeeRole_returns200() throws Exception {
            mockMvc.perform(get(BASE_URL + "/at-risk")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should allow ADMIN role to access")
        void getAtRiskItems_adminRole_returns200() throws Exception {
            mockMvc.perform(get(BASE_URL + "/at-risk")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isOk());
        }
    }
}
