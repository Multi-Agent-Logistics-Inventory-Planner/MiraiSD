package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Review finding (.specs/phase-7-shipments T-4 module-move review): {@code
 * ActivityFeedController} had no site resolution at all -- any authenticated user, with zero
 * site memberships, could read the activity feed (including cross-site shipment data). Proves
 * the fix: the route now requires an active MAIN membership like every other legacy shipment
 * route.
 */
class ActivityFeedControllerSecurityTest extends BaseIntegrationTest {

    @Test
    @DisplayName("getActivityFeed denies a user with no site membership")
    void getActivityFeed_NoMainMembership_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/activity-feed")
                        .header("Authorization", "Bearer " + employeeToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getActivityFeed allows a user with an active MAIN membership")
    void getActivityFeed_MainMembership_ReturnsOk() throws Exception {
        mockMvc.perform(get("/api/activity-feed")
                        .header("Authorization", "Bearer " + employeeTokenWithMainMembership()))
                .andExpect(status().isOk());
    }
}
