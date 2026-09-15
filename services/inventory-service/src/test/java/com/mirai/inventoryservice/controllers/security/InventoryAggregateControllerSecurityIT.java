package com.mirai.inventoryservice.controllers.security;

import com.mirai.inventoryservice.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security coverage for {@code GET /api/inventory/by-product/{productId}}
 * ({@code InventoryAggregateController}) -- moved out of
 * {@code LocationInventoryControllerSecurityIT} (.specs/phase-6-inventory 6e, T-6e-be-9) before
 * that file was deleted: this route belongs to a different, still-live controller and had no
 * other security coverage anywhere in the suite, so it could not simply be dropped along with
 * the rest of that file's LocationInventoryController-specific tests (all of which do have v1
 * equivalents in SiteInventoryMutationControllerSecurityIT/SiteInventoryControllerSecurityIT).
 */
@DisplayName("InventoryAggregateController Security Tests")
class InventoryAggregateControllerSecurityIT extends BaseIntegrationTest {

    private static final String PRODUCT_URL = "/api/inventory/by-product/550e8400-e29b-41d4-a716-446655440004";

    @Test
    @DisplayName("Should return 401 when no token provided")
    void listByProduct_noAuth_returns401() throws Exception {
        mockMvc.perform(get(PRODUCT_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should deny USER role from listing by product (EMPLOYEE+ required)")
    void listByProduct_userRole_returns403() throws Exception {
        mockMvc.perform(get(PRODUCT_URL)
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden());
    }
}
