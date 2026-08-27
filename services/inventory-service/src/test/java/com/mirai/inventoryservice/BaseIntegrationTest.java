package com.mirai.inventoryservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirai.inventoryservice.auth.RateLimitingFilter;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.UserRole;
import com.mirai.inventoryservice.repositories.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
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

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private UserRepository userRepository;

    @Value("${supabase.jwt.secret}")
    private String jwtSecret;

    // JwtService requires iss = "{supabase.url}/auth/v1" and aud = supabase.jwt.audience
    // (default "authenticated") on every token, matching how real Supabase-issued tokens are
    // validated in production. See JwtService.extractAllClaims.
    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.jwt.audience:authenticated}")
    private String jwtAudience;

    @BeforeEach
    void clearRateLimits() {
        rateLimitingFilter.clearBuckets();
    }

    /**
     * Generate a test JWT token with specified person ID, email, and role.
     * <p>
     * The role claim is set in user_metadata purely for realism; per
     * JwtAuthenticationFilter, the actually-enforced role is looked up from the backend
     * User record matching the {@code email} claim, so callers that need a specific
     * enforced role must ensure a matching User row exists (see the persona helpers below,
     * which seed one).
     *
     * @param personId The person/user ID (JWT subject)
     * @param email The email claim, used by JwtAuthenticationFilter to look up the User record
     * @param role The role claim carried in user_metadata (non-authoritative)
     * @return A valid JWT token string
     */
    protected String generateTestToken(String personId, String email, String role) {
        Map<String, Object> userMetadata = new HashMap<>();
        userMetadata.put("name", "Test User");
        userMetadata.put("role", role);

        SecretKey signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        String issuer = supabaseUrl.replaceAll("/+$", "") + "/auth/v1";

        return Jwts.builder()
                .subject(personId)
                .issuer(issuer)
                .audience().add(jwtAudience).and()
                .claim("user_metadata", userMetadata)
                .claim("email", email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Overload retained for any direct callers that don't need a distinct email; uses a
     * generic email that (by design) has no seeded User record, so the enforced role
     * resolves to "USER" regardless of the role argument.
     */
    protected String generateTestToken(String personId, String role) {
        return generateTestToken(personId, "unseeded-" + personId + "@test.internal", role);
    }

    /**
     * Ensures a User record exists with the given email and role, so the DB-backed role
     * lookup in JwtAuthenticationFilter resolves to that role for tokens carrying this
     * email. Creates the record if missing, otherwise updates the role on the existing one.
     */
    private void seedUser(String email, String fullName, UserRole role) {
        User user = userRepository.findByEmail(email).orElseGet(() ->
                User.builder()
                        .email(email)
                        .fullName(fullName)
                        .build());
        user.setFullName(fullName);
        user.setRole(role);
        userRepository.save(user);
    }

    /**
     * Generate an admin JWT token, seeding a matching ADMIN User record.
     *
     * @return A valid admin JWT token
     */
    protected String adminToken() {
        String email = "admin.persona@test.internal";
        seedUser(email, "Admin Persona", UserRole.ADMIN);
        return generateTestToken("admin-id", email, "ADMIN");
    }

    /**
     * Generate an employee JWT token, seeding a matching EMPLOYEE User record.
     *
     * @return A valid employee JWT token
     */
    protected String employeeToken() {
        String email = "employee.persona@test.internal";
        seedUser(email, "Employee Persona", UserRole.EMPLOYEE);
        return generateTestToken("employee-id", email, "EMPLOYEE");
    }

    /**
     * Generate an assistant manager JWT token, seeding a matching ASSISTANT_MANAGER User
     * record.
     *
     * @return A valid assistant manager JWT token
     */
    protected String assistantManagerToken() {
        String email = "assistant-manager.persona@test.internal";
        seedUser(email, "Assistant Manager Persona", UserRole.ASSISTANT_MANAGER);
        return generateTestToken("assistant-manager-id", email, "ASSISTANT_MANAGER");
    }

    /**
     * Generate a regular user JWT token (no special permissions). Deliberately does NOT
     * seed a matching User record, so JwtAuthenticationFilter's DB lookup finds nothing and
     * the enforced role falls back to "USER" -- exercising the "no backend user record"
     * path the same way an authenticated-but-unregistered Supabase user would.
     *
     * @return A valid user JWT token
     */
    protected String userToken() {
        return generateTestToken("user-id", "user.persona@test.internal", "USER");
    }
}
