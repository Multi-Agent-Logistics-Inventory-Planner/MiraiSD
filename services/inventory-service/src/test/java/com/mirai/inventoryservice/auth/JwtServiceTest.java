package com.mirai.inventoryservice.auth;

import com.mirai.inventoryservice.auth.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @InjectMocks
    private JwtService jwtService;

    private String testSecret = "test-secret-key-for-jwt-validation-that-is-long-enough-for-hmac-sha";
    private Key signingKey;

    @BeforeEach
    void setUp() {
        // Set the JWT secret using reflection since it's injected via @Value
        ReflectionTestUtils.setField(jwtService, "jwtSecret", testSecret);
        signingKey = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testExtractPersonId_ValidToken_ReturnsSubject() {
        // Given
        String personId = "user-123";
        String token = createValidToken(personId, "John Doe", "admin");

        // When
        String extractedPersonId = jwtService.extractPersonId(token);

        // Then
        assertEquals(personId, extractedPersonId);
    }

    @Test
    void testExtractName_ValidToken_ReturnsName() {
        // Given
        String name = "John Doe";
        String token = createValidToken("user-123", name, "admin");

        // When
        String extractedName = jwtService.extractName(token);

        // Then
        assertEquals(name, extractedName);
    }

    @Test
    void testExtractRole_ValidToken_ReturnsRole() {
        // Given
        String role = "admin";
        String token = createValidToken("user-123", "John Doe", role);

        // When
        String extractedRole = jwtService.extractRole(token);

        // Then
        assertEquals(role, extractedRole);
    }

    @Test
    void testExtractRole_UserRole_ReturnsUser() {
        // Given
        String role = "user";
        String token = createValidToken("user-123", "Jane Smith", role);

        // When
        String extractedRole = jwtService.extractRole(token);

        // Then
        assertEquals(role, extractedRole);
    }

    @Test
    void testValidateToken_ValidToken_ReturnsTrue() {
        // Given
        String token = createValidToken("user-123", "John Doe", "admin");

        // When
        Boolean isValid = jwtService.validateToken(token);

        // Then
        assertTrue(isValid);
    }

    @Test
    void testValidateToken_ExpiredToken_ReturnsFalse() {
        // Given
        String token = createExpiredToken("user-123", "John Doe", "admin");

        // When
        Boolean isValid = jwtService.validateToken(token);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testValidateToken_InvalidToken_ReturnsFalse() {
        // Given
        String invalidToken = "invalid.token.string";

        // When
        Boolean isValid = jwtService.validateToken(invalidToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testValidateToken_NullToken_ReturnsFalse() {
        // When
        Boolean isValid = jwtService.validateToken(null);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testExtractName_MissingUserMetadata_ReturnsNull() {
        // Given
        String token = createTokenWithoutUserMetadata("user-123");

        // When
        String extractedName = jwtService.extractName(token);

        // Then
        assertNull(extractedName);
    }

    @Test
    void testExtractRole_MissingUserMetadata_ReturnsNull() {
        // Given
        String token = createTokenWithoutUserMetadata("user-123");

        // When
        String extractedRole = jwtService.extractRole(token);

        // Then
        assertNull(extractedRole);
    }

    // Helper methods to create test tokens
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

    private String createTokenWithoutUserMetadata(String personId) {
        return Jwts.builder()
                .setSubject(personId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60)) // 1 hour from now
                .signWith(signingKey)
                .compact();
    }
}

