package com.mirai.inventoryservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for integration tests providing common test infrastructure.
 * Provides JWT token generation helpers for testing authorization.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Value("${supabase.jwt.secret}")
    private String jwtSecret;

    /**
     * Generate a test JWT token with specified person ID and role.
     *
     * @param personId The person/user ID
     * @param role The user's role (ADMIN, EMPLOYEE, etc.)
     * @return A valid JWT token string
     */
    protected String generateTestToken(String personId, String role) {
        Map<String, Object> userMetadata = new HashMap<>();
        userMetadata.put("name", "Test User");
        userMetadata.put("role", role);

        SecretKey signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(personId)
                .claim("user_metadata", userMetadata)
                .claim("email", "test@test.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generate an admin JWT token.
     *
     * @return A valid admin JWT token
     */
    protected String adminToken() {
        return generateTestToken("admin-id", "ADMIN");
    }

    /**
     * Generate an employee JWT token.
     *
     * @return A valid employee JWT token
     */
    protected String employeeToken() {
        return generateTestToken("employee-id", "EMPLOYEE");
    }

    /**
     * Generate a regular user JWT token (no special permissions).
     *
     * @return A valid user JWT token
     */
    protected String userToken() {
        return generateTestToken("user-id", "USER");
    }
}
