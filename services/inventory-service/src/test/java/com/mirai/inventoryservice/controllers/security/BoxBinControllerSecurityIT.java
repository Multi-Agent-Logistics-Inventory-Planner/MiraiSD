package com.mirai.inventoryservice.controllers.security;

import com.mirai.inventoryservice.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security integration tests for BoxBinController.
 * Authorization matrix:
 * - GET: ALL authenticated users
 * - POST/PUT/DELETE: ADMIN only
 */
@DisplayName("BoxBinController Security Tests")
class BoxBinControllerSecurityIT extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/box-bins";
    private static final String BOX_BIN_JSON = """
            {
                "boxBinCode": "BOX-001"
            }
            """;

    @Nested
    @DisplayName("GET endpoints")
    class GetTests {

        @Test
        @DisplayName("Should allow USER role to list box bins")
        void getAllBoxBins_userRole_notForbidden() throws Exception {
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
        @DisplayName("Should return 403 when EMPLOYEE role attempts to create")
        void createBoxBin_employeeRole_returns403() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType("application/json")
                            .content(BOX_BIN_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ADMIN role to create box bin")
        void createBoxBin_adminRole_notForbidden() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType("application/json")
                            .content(BOX_BIN_JSON))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("PUT endpoints")
    class PutTests {

        private static final String BOX_BIN_URL = BASE_URL + "/550e8400-e29b-41d4-a716-446655440000";

        @Test
        @DisplayName("Should return 403 when EMPLOYEE role attempts to update")
        void updateBoxBin_employeeRole_returns403() throws Exception {
            mockMvc.perform(put(BOX_BIN_URL)
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType("application/json")
                            .content(BOX_BIN_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ADMIN role to update box bin")
        void updateBoxBin_adminRole_notForbidden() throws Exception {
            mockMvc.perform(put(BOX_BIN_URL)
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType("application/json")
                            .content(BOX_BIN_JSON))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("DELETE endpoints")
    class DeleteTests {

        private static final String BOX_BIN_URL = BASE_URL + "/550e8400-e29b-41d4-a716-446655440000";

        @Test
        @DisplayName("Should return 403 when EMPLOYEE role attempts to delete")
        void deleteBoxBin_employeeRole_returns403() throws Exception {
            mockMvc.perform(delete(BOX_BIN_URL)
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ADMIN role to delete box bin")
        void deleteBoxBin_adminRole_notForbidden() throws Exception {
            mockMvc.perform(delete(BOX_BIN_URL)
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }
}
