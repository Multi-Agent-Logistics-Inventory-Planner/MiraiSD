package com.mirai.inventoryservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirai.inventoryservice.auth.RateLimitingFilter;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserRole;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import com.mirai.inventoryservice.sites.application.LocationService;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/** Shared MockMvc base for HTTP authorization tests that need PostgreSQL but not Kafka. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("integration")
public abstract class BasePostgresMockMvcIntegrationTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("mirai_legacy_authz_test")
            .withUsername("test")
            .withPassword("test");

    static {
        // The Spring context is cached across subclasses. Starting once for the JVM prevents a
        // cached context from retaining the dead container left by JUnit's @Container lifecycle.
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected UserSiteMembershipRepository membershipRepository;
    @Autowired protected SiteRepository siteRepository;
    @Autowired private RateLimitingFilter rateLimitingFilter;

    @Value("${supabase.jwt.secret}") private String jwtSecret;
    @Value("${supabase.url}") private String supabaseUrl;
    @Value("${supabase.jwt.audience:authenticated}") private String jwtAudience;

    @BeforeEach
    void prepareBaseState() {
        rateLimitingFilter.clearBuckets();
        siteRepository.findByCode(LocationService.DEFAULT_SITE_CODE)
                .orElseGet(() -> siteRepository.save(Site.builder().code("MAIN").name("Main").build()));
    }

    protected User seedUser(String email, UserRole role, boolean systemAdmin) {
        User user = userRepository.findByEmail(email).orElseGet(() -> User.builder()
                .email(email).fullName("Postgres Authorization Test User").build());
        user.setRole(role);
        user.setIsSystemAdmin(systemAdmin);
        return userRepository.save(user);
    }

    protected String tokenFor(User user) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", user.getFullName());
        metadata.put("role", user.getRole().name());
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("postgres-" + user.getId())
                .issuer(supabaseUrl.replaceAll("/+$", "") + "/auth/v1")
                .audience().add(jwtAudience).and()
                .claim("user_metadata", metadata)
                .claim("email", user.getEmail())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key)
                .compact();
    }

    protected Site mainSite() {
        return siteRepository.findByCode(LocationService.DEFAULT_SITE_CODE).orElseThrow();
    }

    protected void grant(User user, Site site) {
        membershipRepository.findByUserIdAndSiteId(user.getId(), site.getId()).orElseGet(() ->
                membershipRepository.save(UserSiteMembership.builder()
                        .userId(user.getId()).siteId(site.getId()).isActive(true).build()));
    }
}
