package com.mirai.inventoryservice.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


/*
Integration test for JWT authentication with real Supabase tokens.
*/
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {
    // Previously excluded DataSourceAutoConfiguration/HibernateJpaAutoConfiguration here
    // as a test-speed shortcut. That broke once JwtAuthenticationFilter started
    // requiring UserService -> UserRepository for every request (DB-role lookup,
    // see JwtAuthenticationFilter): AuthController's context failed to load with
    // no UserRepository bean available. The "test" profile already points at a
    // fast in-memory H2 (application-test.properties), so there's no real cost to
    // just letting the datasource autoconfigure normally.

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @BeforeEach
    void setUp() {
        rateLimitingFilter.clearBuckets();
    }

    @Test
    void testValidateToken_WithRealToken() throws Exception {
        // Get real token from environment variable
        String realToken = System.getenv("TEST_JWT_TOKEN");
        
        if (realToken == null || realToken.isEmpty()) {
            System.out.println("⚠️  TEST_JWT_TOKEN not set. Skipping integration test.");
            System.out.println("   To run this test, set TEST_JWT_TOKEN environment variable:");
            System.out.println("   export TEST_JWT_TOKEN=$(node scripts/get-token.mjs)");
            return; // Skip test if no token provided
        }

        mockMvc.perform(post("/api/auth/validate")
                .header("Authorization", "Bearer " + realToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.role").exists())
                .andExpect(jsonPath("$.personId").exists())
                .andExpect(jsonPath("$.personName").exists());
    }

    /**
     * Test with an invalid token to ensure validation still works.
     */
    @Test
    void testValidateToken_WithInvalidToken() throws Exception {
        String invalidToken = "invalid.token.string";

        mockMvc.perform(post("/api/auth/validate")
                .header("Authorization", "Bearer " + invalidToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid").value(false));
    }

    /**
     * Test with expired token
     */
    @Test
    void testValidateToken_WithExpiredToken() throws Exception {
        String expiredToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJleHAiOjE1MTYyMzkwMjJ9.invalid";

        mockMvc.perform(post("/api/auth/validate")
                .header("Authorization", "Bearer " + expiredToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid").value(false));
    }
}
