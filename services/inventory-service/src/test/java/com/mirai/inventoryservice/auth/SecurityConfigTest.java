package com.mirai.inventoryservice.auth;

import com.mirai.inventoryservice.auth.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @MockBean
    private JwtService jwtService;

    private String testSecret = "test-secret-key-for-jwt-validation-that-is-long-enough-for-hmac-sha";
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        signingKey = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
        rateLimitingFilter.clearBuckets();
    }

    @Test
    void testPublicEndpoint_AuthEndpoint_AllowsAccess() throws Exception {
        // When & Then - endpoint is permitAll; with mocked JwtService returning defaults,
        // the filter does not set authentication. The validate method handles it accordingly.
        mockMvc.perform(post("/api/auth/validate")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // Should not be 403 (forbidden) - the endpoint is public
                    org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(403);
                });
    }

    @Test
    void testPublicEndpoint_HealthEndpoint_AllowsAccess() throws Exception {
        // When & Then - health endpoint should be accessible without auth
        mockMvc.perform(get("/health"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // Should not require authentication (401/403)
                    org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                });
    }

    @Test
    void testProtectedEndpoint_WithoutAuth_ReturnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testProtectedEndpoint_WithValidToken_AllowsAccess() throws Exception {
        // Given
        String token = createValidToken("user-123", "John Doe", "user");
        String bearerToken = "Bearer " + token;

        when(jwtService.extractPersonId(token)).thenReturn("user-123");
        when(jwtService.extractName(token)).thenReturn("John Doe");
        when(jwtService.extractRole(token)).thenReturn("user");
        when(jwtService.validateToken(token)).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/api/v1/test")
                        .header("Authorization", bearerToken))
                .andExpect(status().isNotFound()); // Endpoint doesn't exist
    }

    @Test
    void testAdminEndpoint_WithUserRole_ReturnsForbidden() throws Exception {
        // Given
        String token = createValidToken("user-123", "John Doe", "user");
        String bearerToken = "Bearer " + token;

        when(jwtService.extractPersonId(token)).thenReturn("user-123");
        when(jwtService.extractName(token)).thenReturn("John Doe");
        when(jwtService.extractRole(token)).thenReturn("user");
        when(jwtService.validateToken(token)).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/api/admin/test")
                        .header("Authorization", bearerToken))
                .andExpect(status().isForbidden()); // 403 Forbidden - user doesn't have admin role
    }

    @Test
    void testAdminEndpoint_WithAdminRole_AllowsAccess() throws Exception {
        // Given
        String token = createValidToken("admin-123", "Admin User", "admin");
        String bearerToken = "Bearer " + token;

        when(jwtService.extractPersonId(token)).thenReturn("admin-123");
        when(jwtService.extractName(token)).thenReturn("Admin User");
        when(jwtService.extractRole(token)).thenReturn("admin");
        when(jwtService.validateToken(token)).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/api/admin/test")
                        .header("Authorization", bearerToken))
                .andExpect(status().isNotFound()); // Endpoint doesn't exist, but security allows access
    }

    @Test
    void testAdminEndpoint_WithoutAuth_ReturnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/admin/test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testProtectedEndpoint_WithExpiredToken_ReturnsUnauthorized() throws Exception {
        // Given
        String token = createExpiredToken("user-123", "John Doe", "user");
        String bearerToken = "Bearer " + token;

        when(jwtService.extractPersonId(token)).thenReturn("user-123");
        when(jwtService.extractName(token)).thenReturn("John Doe");
        when(jwtService.extractRole(token)).thenReturn("user");
        when(jwtService.validateToken(token)).thenReturn(false); // Token is expired

        // When & Then
        mockMvc.perform(get("/api/v1/test")
                        .header("Authorization", bearerToken))
                .andExpect(status().isUnauthorized());
    }

    // Dev endpoint security tests (defense in depth)
    @Test
    void testDevEndpoint_IsDeniedWithoutAuth() throws Exception {
        // Dev endpoints should be blocked even without authentication
        mockMvc.perform(post("/api/dev/seed/all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testDevEndpoint_IsDeniedWithUserAuth() throws Exception {
        // Given - authenticated user
        String token = createValidToken("user-123", "John Doe", "user");
        String bearerToken = "Bearer " + token;

        when(jwtService.extractPersonId(token)).thenReturn("user-123");
        when(jwtService.extractName(token)).thenReturn("John Doe");
        when(jwtService.extractRole(token)).thenReturn("user");
        when(jwtService.validateToken(token)).thenReturn(true);

        // When & Then - dev endpoints should be denied even with valid user auth
        mockMvc.perform(post("/api/dev/seed/all")
                        .header("Authorization", bearerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDevEndpoint_IsDeniedWithAdminAuth() throws Exception {
        // Given - authenticated admin
        String token = createValidToken("admin-123", "Admin User", "admin");
        String bearerToken = "Bearer " + token;

        when(jwtService.extractPersonId(token)).thenReturn("admin-123");
        when(jwtService.extractName(token)).thenReturn("Admin User");
        when(jwtService.extractRole(token)).thenReturn("admin");
        when(jwtService.validateToken(token)).thenReturn(true);

        // When & Then - dev endpoints should be denied even with admin auth (defense in depth)
        mockMvc.perform(post("/api/dev/seed/all")
                        .header("Authorization", bearerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDevEndpoint_AllPathsDenied() throws Exception {
        // Given - authenticated admin
        String token = createValidToken("admin-123", "Admin User", "admin");
        String bearerToken = "Bearer " + token;

        when(jwtService.extractPersonId(token)).thenReturn("admin-123");
        when(jwtService.extractName(token)).thenReturn("Admin User");
        when(jwtService.extractRole(token)).thenReturn("admin");
        when(jwtService.validateToken(token)).thenReturn(true);

        // When & Then - all dev sub-paths should be denied
        mockMvc.perform(post("/api/dev/seed/sales")
                        .header("Authorization", bearerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/dev/seed/products")
                        .header("Authorization", bearerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/dev/anything")
                        .header("Authorization", bearerToken))
                .andExpect(status().isForbidden());
    }

    // Actuator endpoint security tests
    @Test
    void testActuatorHealth_IsPublic() throws Exception {
        // When & Then - health endpoint should be accessible without auth
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void testActuatorHealthSubpath_IsPublic() throws Exception {
        // When & Then - health subpaths should not require authentication
        // They may return 404 if probes aren't enabled in the test profile
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                });
    }

    @Test
    void testActuatorOtherEndpoints_RequireAuth() throws Exception {
        // When & Then - other actuator endpoints should require authentication
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testActuatorOtherEndpoints_RequireAdminRole() throws Exception {
        // Given - authenticated user (not admin)
        String token = createValidToken("user-123", "John Doe", "user");
        String bearerToken = "Bearer " + token;

        when(jwtService.extractPersonId(token)).thenReturn("user-123");
        when(jwtService.extractName(token)).thenReturn("John Doe");
        when(jwtService.extractRole(token)).thenReturn("user");
        when(jwtService.validateToken(token)).thenReturn(true);

        // When & Then - actuator endpoints should be forbidden for non-admin users
        mockMvc.perform(get("/actuator/info")
                        .header("Authorization", bearerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", bearerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testActuatorOtherEndpoints_AllowedForAdmin() throws Exception {
        // Given - authenticated admin
        String token = createValidToken("admin-123", "Admin User", "admin");
        String bearerToken = "Bearer " + token;

        when(jwtService.extractPersonId(token)).thenReturn("admin-123");
        when(jwtService.extractName(token)).thenReturn("Admin User");
        when(jwtService.extractRole(token)).thenReturn("admin");
        when(jwtService.validateToken(token)).thenReturn(true);

        // When & Then - actuator endpoints should be accessible for admin (not 401/403)
        mockMvc.perform(get("/actuator/info")
                        .header("Authorization", bearerToken))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                });
    }

    // Helper methods
    private String createValidToken(String personId, String name, String role) {
        Map<String, Object> userMetadata = new HashMap<>();
        userMetadata.put("name", name);
        userMetadata.put("role", role);

        return Jwts.builder()
                .setSubject(personId)
                .claim("user_metadata", userMetadata)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60)) // 1 hour from now
                .signWith(signingKey)
                .compact();
    }

    private String createExpiredToken(String personId, String name, String role) {
        Map<String, Object> userMetadata = new HashMap<>();
        userMetadata.put("name", name);
        userMetadata.put("role", role);

        return Jwts.builder()
                .setSubject(personId)
                .claim("user_metadata", userMetadata)
                .setIssuedAt(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 2)) // 2 hours ago
                .setExpiration(new Date(System.currentTimeMillis() - 1000 * 60 * 60)) // 1 hour ago (expired)
                .signWith(signingKey)
                .compact();
    }
}

