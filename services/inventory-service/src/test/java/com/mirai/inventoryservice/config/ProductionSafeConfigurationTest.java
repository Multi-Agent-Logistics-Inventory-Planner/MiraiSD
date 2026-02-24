package com.mirai.inventoryservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests to verify production-safe configuration defaults.
 * These tests ensure that sensitive settings have secure defaults
 * that won't leak data or degrade performance in production.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProductionSafeConfigurationTest {

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("JPA show-sql should default to false when JPA_SHOW_SQL env var is not set")
    void jpaShowSqlDefaultsToFalse() {
        // Given: JPA_SHOW_SQL environment variable is not set
        // When: We read the show-sql property
        String showSql = environment.getProperty("spring.jpa.show-sql");

        // Then: It should default to false for security and performance
        assertEquals("false", showSql,
            "spring.jpa.show-sql should default to 'false' to prevent SQL logging in production. " +
            "SQL logging can leak sensitive data and impact performance.");
    }

    @Test
    @DisplayName("Test profile should not accidentally enable dev profile")
    void testProfileDoesNotEnableDev() {
        // Given: We're running with the test profile
        // When: We check active profiles
        String[] activeProfiles = environment.getActiveProfiles();

        // Then: The dev profile should NOT be active
        for (String profile : activeProfiles) {
            assertFalse("dev".equalsIgnoreCase(profile),
                "The 'dev' profile must NOT be active during tests. " +
                "Dev profile enables insecure endpoints (DevSecurityConfig, DevSeedController).");
        }
    }

    @Test
    @DisplayName("Default profile should not be dev")
    void defaultProfileIsNotDev() {
        // Given: We check the default profiles
        String[] defaultProfiles = environment.getDefaultProfiles();

        // Then: dev should not be a default profile
        for (String profile : defaultProfiles) {
            assertFalse("dev".equalsIgnoreCase(profile),
                "The 'dev' profile must NOT be a default profile. " +
                "Production deployment could accidentally inherit insecure settings.");
        }
    }

    @Test
    @DisplayName("Logging level root should be info or higher (not debug/trace)")
    void loggingLevelIsNotVerbose() {
        // Given: We read the root logging level
        String loggingLevel = environment.getProperty("logging.level.root", "info");

        // Then: It should not be debug or trace (which could leak sensitive data)
        assertFalse("debug".equalsIgnoreCase(loggingLevel) || "trace".equalsIgnoreCase(loggingLevel),
            "Root logging level should not be DEBUG or TRACE in production-like configuration. " +
            "Verbose logging can leak sensitive data.");
    }
}
