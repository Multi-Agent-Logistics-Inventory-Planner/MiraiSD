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
    private static final String TEST_SUPABASE_URL = "https://test.supabase.co";
    private static final String TEST_ISSUER = TEST_SUPABASE_URL + "/auth/v1";
    private static final String TEST_AUDIENCE = "authenticated";
    private Key signingKey;

    @BeforeEach
    void setUp() {
        // Set fields injected via @Value using reflection, since MockitoExtension doesn't
        // process @Value annotations.
        ReflectionTestUtils.setField(jwtService, "jwtSecret", testSecret);
        ReflectionTestUtils.setField(jwtService, "supabaseUrl", TEST_SUPABASE_URL);
        ReflectionTestUtils.setField(jwtService, "expectedAudience", TEST_AUDIENCE);
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

    @Test
    void testValidateToken_WrongIssuer_ReturnsFalse() {
        // Given: a token signed correctly but issued by a different Supabase project/issuer.
        String token = createTokenWithIssuerAndAudience(
                "user-123", "John Doe", "admin",
                "https://a-different-project.supabase.co/auth/v1", TEST_AUDIENCE);

        // When
        Boolean isValid = jwtService.validateToken(token);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testValidateToken_MissingIssuer_ReturnsFalse() {
        // Given: a validly signed token with no iss claim at all.
        String token = createTokenMissingIssuer("user-123");

        // When
        Boolean isValid = jwtService.validateToken(token);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testValidateToken_WrongAudience_ReturnsFalse() {
        // Given: right issuer, wrong audience (e.g. a service-role or anon-key token, not a
        // user session token).
        String token = createTokenWithIssuerAndAudience(
                "user-123", "John Doe", "admin", TEST_ISSUER, "service_role");

        // When
        Boolean isValid = jwtService.validateToken(token);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testValidateToken_BlankSubject_ReturnsFalse() {
        // Given: correctly signed, correct issuer/audience, but no subject at all.
        String token = Jwts.builder()
                .claim("user_metadata", Map.of("name", "John Doe", "role", "admin"))
                .setIssuer(TEST_ISSUER)
                .claim("aud", TEST_AUDIENCE)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60))
                .signWith(signingKey)
                .compact();

        // When
        Boolean isValid = jwtService.validateToken(token);

        // Then
        assertFalse(isValid);
    }

    // Helper methods to create test tokens
    private String createValidToken(String personId, String name, String role) {
        return createTokenWithIssuerAndAudience(personId, name, role, TEST_ISSUER, TEST_AUDIENCE);
    }

    private String createTokenWithIssuerAndAudience(String personId, String name, String role,
                                                      String issuer, String audience) {
        Map<String, Object> userMetadata = new HashMap<>();
        userMetadata.put("name", name);
        userMetadata.put("role", role);

        return Jwts.builder()
                .setSubject(personId)
                .claim("user_metadata", userMetadata)
                .setIssuer(issuer)
                .claim("aud", audience)
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
                .setIssuer(TEST_ISSUER)
                .claim("aud", TEST_AUDIENCE)
                .setIssuedAt(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 2)) // 2 hours ago
                .setExpiration(new Date(System.currentTimeMillis() - 1000 * 60 * 60)) // 1 hour ago (expired)
                .signWith(signingKey)
                .compact();
    }

    private String createTokenWithoutUserMetadata(String personId) {
        // Has a valid issuer/audience — this token is deliberately missing only
        // user_metadata, to isolate that specific case from issuer/audience validation.
        return Jwts.builder()
                .setSubject(personId)
                .setIssuer(TEST_ISSUER)
                .claim("aud", TEST_AUDIENCE)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60)) // 1 hour from now
                .signWith(signingKey)
                .compact();
    }

    private String createTokenMissingIssuer(String personId) {
        return Jwts.builder()
                .setSubject(personId)
                .claim("aud", TEST_AUDIENCE)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60))
                .signWith(signingKey)
                .compact();
    }
}
