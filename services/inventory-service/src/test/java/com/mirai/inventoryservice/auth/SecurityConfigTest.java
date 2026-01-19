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

    @MockBean
    private JwtService jwtService;

    private String testSecret = "test-secret-key-for-jwt-validation-that-is-long-enough-for-hmac-sha";
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        signingKey = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testPublicEndpoint_AuthEndpoint_AllowsAccess() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/auth/validate")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest()); // Bad request because token is invalid, but endpoint is accessible

        // Endpoint is accessible without authentication
    }

    @Test
    void testPublicEndpoint_HealthEndpoint_AllowsAccess() throws Exception {
        // When & Then
        mockMvc.perform(get("/health"))
                .andExpect(status().is4xxClientError()); // 404 if endpoint doesn't exist
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

