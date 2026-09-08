package com.mirai.inventoryservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirai.inventoryservice.auth.RateLimitingFilter;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserRole;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for integration tests that require real PostgreSQL and Kafka.
 *
 * Uses Testcontainers to spin up PostgreSQL (for JSONB support that H2 lacks)
 * and Kafka (for testing the outbox-to-Kafka publish flow).
 * Containers are shared across all tests in subclasses (static fields).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("integration")
public abstract class BaseKafkaIntegrationTest {

    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("mirai_test")
                    .withUsername("test")
                    .withPassword("test");

    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    static {
        // Spring caches the application context across concrete subclasses of this
        // base class. JUnit's @Container lifecycle stops inherited containers after
        // the first subclass, leaving that cached context pointing at a dead
        // PostgreSQL/Kafka pair. Start them once for the test JVM instead.
        postgres.start();
        kafka.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Value("${supabase.jwt.secret}")
    private String jwtSecret;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.jwt.audience:authenticated}")
    private String jwtAudience;

    @BeforeEach
    void clearRateLimits() {
        rateLimitingFilter.clearBuckets();
    }

    /**
     * Every product write now touches MAIN's site_products row (.specs/phase-5c-site-products
     * AC-5). This class does not wrap tests in a rolled-back transaction, so seeding is
     * idempotent (find-or-create) rather than per-test.
     */
    @BeforeEach
    void ensureMainSiteExists() {
        siteRepository.findByCode("MAIN")
                .orElseGet(() -> siteRepository.save(Site.builder().code("MAIN").name("Main").build()));
    }

    protected String generateTestToken(String personId, String role) {
        Map<String, Object> userMetadata = new HashMap<>();
        userMetadata.put("name", "Test User");
        userMetadata.put("role", role);

        SecretKey signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        String email = personId + "@test.internal";
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

    protected String adminToken() {
        seedUser("admin-id@test.internal", UserRole.ADMIN);
        return generateTestToken("admin-id", "ADMIN");
    }

    protected String employeeToken() {
        seedUser("employee-id@test.internal", UserRole.EMPLOYEE);
        return generateTestToken("employee-id", "EMPLOYEE");
    }

    private void seedUser(String email, UserRole role) {
        User user = userRepository.findByEmail(email).orElseGet(() -> User.builder()
                .email(email)
                .fullName("Integration Test User")
                .build());
        user.setRole(role);
        userRepository.save(user);
    }
}
