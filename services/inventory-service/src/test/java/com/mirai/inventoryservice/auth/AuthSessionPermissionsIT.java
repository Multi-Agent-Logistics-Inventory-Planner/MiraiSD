package com.mirai.inventoryservice.auth;

import com.mirai.inventoryservice.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms /api/auth/session carries the backend-resolved permission set (Workstream A:
 * the permission matrix moves from frontend-only to backend-owned, per
 * lib/rbac/role-permissions.ts parity in RolePermissionsTest).
 */
class AuthSessionPermissionsIT extends BaseIntegrationTest {

    @Test
    void adminSessionIncludesCostsView() throws Exception {
        mockMvc.perform(get("/api/auth/session")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.permissions").isArray())
                .andExpect(jsonPath("$.permissions", org.hamcrest.Matchers.hasItem("costs:view")))
                .andExpect(jsonPath("$.permissions", org.hamcrest.Matchers.hasItem("users:manage")));
    }

    @Test
    void employeeSessionExcludesCostsAndMsrp() throws Exception {
        mockMvc.perform(get("/api/auth/session")
                        .header("Authorization", "Bearer " + employeeToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("costs:view"))))
                .andExpect(jsonPath("$.permissions", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("msrp:view"))))
                .andExpect(jsonPath("$.permissions", org.hamcrest.Matchers.hasItem("products:view")));
    }

    @Test
    void assistantManagerSessionIncludesMsrpButNotCosts() throws Exception {
        mockMvc.perform(get("/api/auth/session")
                        .header("Authorization", "Bearer " + assistantManagerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", org.hamcrest.Matchers.hasItem("msrp:view")))
                .andExpect(jsonPath("$.permissions", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("costs:view"))));
    }
}
