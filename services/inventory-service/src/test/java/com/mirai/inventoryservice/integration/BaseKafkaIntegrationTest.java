package com.mirai.inventoryservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirai.inventoryservice.auth.RateLimitingFilter;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
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
@Testcontainers
public abstract class BaseKafkaIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("mirai_test")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

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

    @Value("${supabase.jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void clearRateLimits() {
        rateLimitingFilter.clearBuckets();
    }

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

    protected String adminToken() {
        return generateTestToken("admin-id", "ADMIN");
    }

    protected String employeeToken() {
        return generateTestToken("employee-id", "EMPLOYEE");
    }
}
