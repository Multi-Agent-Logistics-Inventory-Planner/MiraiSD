package com.mirai.inventoryservice.shipments.api;

import com.mirai.inventoryservice.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * .specs/phase-7-shipments T-5/T-6, external review finding (Blocker): the v1 webhook alias
 * ({@code /api/v1/webhooks/easypost}) was reachable in the OpenAPI contract but
 * {@code auth.SecurityConfig}/{@code config.OpenApiConfig} only allowlisted the legacy
 * {@code /api/webhooks/**} prefix, so every unauthenticated EasyPost delivery to the v1 path fell
 * through to {@code anyRequest().authenticated()} and got Spring Security's generic 401 before
 * HMAC validation ever ran. Fixed by adding {@code /api/v1/webhooks/**} to both allowlists. This
 * test proves parity: neither path returns the Spring Security 401 JSON shape, with no
 * {@code Authorization} header at all -- matching how EasyPost actually calls this endpoint.
 */
class EasyPostWebhookControllerSecurityIT extends BaseIntegrationTest {

    @Test
    @DisplayName("legacy and v1 webhook paths behave identically with no Authorization header (neither is gated by JWT auth)")
    void bothWebhookPathsAreReachableWithNoAuth() throws Exception {
        int legacyStatus = mockMvc.perform(post("/api/webhooks/easypost")
                        .contentType("application/json")
                        .content("{}"))
                .andReturn().getResponse().getStatus();

        int v1Status = mockMvc.perform(post("/api/v1/webhooks/easypost")
                        .contentType("application/json")
                        .content("{}"))
                .andReturn().getResponse().getStatus();

        assertThat(legacyStatus).isNotEqualTo(401);
        assertThat(v1Status).isNotEqualTo(401);
        assertThat(v1Status).isEqualTo(legacyStatus);
    }
}
