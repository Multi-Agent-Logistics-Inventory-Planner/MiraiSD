package com.mirai.inventoryservice.auth;

import com.mirai.inventoryservice.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/**
 * Integration tests for rate limiting functionality.
 *
 * These tests verify that:
 * - Rate limiting allows up to the configured number of requests per minute
 * - Rate limiting blocks requests that exceed the limit with HTTP 429
 * - Health endpoint is excluded from rate limiting
 * - X-Forwarded-For header is properly handled for proxied requests
 * - Rate limits are applied per IP address
 */
class RateLimitingIT extends BaseIntegrationTest {

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @BeforeEach
    void setUp() {
        // Clear rate limit buckets before each test to ensure isolation
        rateLimitingFilter.clearBuckets();
    }

    @Test
    @DisplayName("Rate limiting allows requests up to the configured limit")
    void rateLimiting_allowsRequestsUpToLimit() throws Exception {
        String token = employeeToken();

        // Make requests up to the limit (using test limit of 10 for faster tests)
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/products")
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("Rate limiting blocks requests exceeding the limit with HTTP 429")
    void rateLimiting_blocksExcessiveRequests() throws Exception {
        String token = employeeToken();

        // Exhaust the rate limit
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/products")
                    .header("Authorization", "Bearer " + token));
        }

        // The next request should be blocked
        MvcResult result = mockMvc.perform(get("/api/products")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType("application/json"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("Too many requests");
    }

    @Test
    @DisplayName("Health endpoint is excluded from rate limiting")
    void rateLimiting_excludesHealthEndpoint() throws Exception {
        // Health endpoint should never be rate limited
        // Even after exhausting other endpoints, health should work
        for (int i = 0; i < 50; i++) {
            mockMvc.perform(get("/health"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("Rate limiting handles X-Forwarded-For header for proxied requests")
    void rateLimiting_handlesXForwardedForHeader() throws Exception {
        String token = employeeToken();

        // Requests from one IP should not affect another IP's limit
        String ip1 = "192.168.1.100";
        String ip2 = "192.168.1.200";

        // Exhaust limit for IP1
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/products")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Forwarded-For", ip1));
        }

        // IP1 should be blocked
        mockMvc.perform(get("/api/products")
                .header("Authorization", "Bearer " + token)
                .header("X-Forwarded-For", ip1))
                .andExpect(status().isTooManyRequests());

        // IP2 should still be allowed
        mockMvc.perform(get("/api/products")
                .header("Authorization", "Bearer " + token)
                .header("X-Forwarded-For", ip2))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Rate limiting extracts first IP from comma-separated X-Forwarded-For")
    void rateLimiting_extractsFirstIpFromXForwardedFor() throws Exception {
        String token = employeeToken();

        // X-Forwarded-For can contain multiple IPs: client, proxy1, proxy2
        String forwardedFor = "10.0.0.1, 10.0.0.2, 10.0.0.3";

        // Exhaust limit using the forwarded header
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/products")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Forwarded-For", forwardedFor));
        }

        // Same first IP should be blocked
        mockMvc.perform(get("/api/products")
                .header("Authorization", "Bearer " + token)
                .header("X-Forwarded-For", forwardedFor))
                .andExpect(status().isTooManyRequests());

        // Different first IP should not be affected
        mockMvc.perform(get("/api/products")
                .header("Authorization", "Bearer " + token)
                .header("X-Forwarded-For", "10.0.0.99, 10.0.0.2, 10.0.0.3"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Rate limiting applies to auth endpoints")
    void rateLimiting_appliesToAuthEndpoints() throws Exception {
        // Auth endpoints should also be rate limited (to prevent brute force)
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/auth/check")
                    .header("X-Forwarded-For", "10.10.10.10"));
        }

        mockMvc.perform(get("/api/auth/check")
                .header("X-Forwarded-For", "10.10.10.10"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Rate limiting returns proper JSON error response")
    void rateLimiting_returnsProperJsonErrorResponse() throws Exception {
        String token = employeeToken();

        // Exhaust the limit
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/products")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Forwarded-For", "rate-limit-test-ip"));
        }

        // Verify the error response format
        mockMvc.perform(get("/api/products")
                .header("Authorization", "Bearer " + token)
                .header("X-Forwarded-For", "rate-limit-test-ip"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.error").value("Too many requests. Please try again later."));
    }
}
