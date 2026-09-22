package com.mirai.inventoryservice.shared.idempotency;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Executes V65__create_command_idempotency.sql against real PostgreSQL
 * (.specs/phase-6-inventory T-6c-10), the same standalone-Testcontainers, no-Spring-context
 * approach {@link com.mirai.inventoryservice.inventory.infrastructure.EventOutboxEnvelopeMigrationIT}
 * uses for V63. Proves the real migration file, not just the entity-level
 * {@code @UniqueConstraint} {@link CommandIdempotencyServiceIT} exercises against
 * Hibernate's create-drop test schema.
 */
@Testcontainers
class CommandIdempotencyMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("mirai_command_idempotency_test")
                    .withUsername("test")
                    .withPassword("test");

    private static Connection connection;

    @BeforeAll
    static void migrate() throws SQLException, IOException {
        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
            statement.execute(readMigrationFile("V65__create_command_idempotency.sql"));
        }
    }

    @AfterAll
    static void closeConnection() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    private static String readMigrationFile(String fileName) throws IOException {
        try (InputStream in = CommandIdempotencyMigrationIT.class.getClassLoader()
                .getResourceAsStream("db/migration/" + fileName)) {
            if (in == null) {
                throw new IllegalStateException(fileName + " not found on test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void tableHasExpectedColumnsAndTypes() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT column_name, data_type, is_nullable FROM information_schema.columns "
                             + "WHERE table_name = 'command_idempotency'")) {
            var shapes = new java.util.HashMap<String, String[]>();
            while (rs.next()) {
                shapes.put(rs.getString("column_name"),
                        new String[]{rs.getString("data_type"), rs.getString("is_nullable")});
            }
            assertThat(shapes.get("site_id")).containsExactly("uuid", "NO");
            assertThat(shapes.get("user_id")).containsExactly("uuid", "NO");
            assertThat(shapes.get("idempotency_key")).containsExactly("text", "NO");
            assertThat(shapes.get("command_type")).containsExactly("text", "NO");
            assertThat(shapes.get("request_fingerprint")).containsExactly("text", "NO");
            assertThat(shapes.get("result_status")).containsExactly("integer", "NO");
            assertThat(shapes.get("result_body")).containsExactly("text", "YES");
            assertThat(shapes.get("created_at")).containsExactly("timestamp with time zone", "NO");
        }
    }

    @Test
    void uniqueIndexRejectsADuplicateSiteUserKeyTriple() throws SQLException {
        UUID siteId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        try (Statement statement = connection.createStatement()) {
            statement.execute(String.format(
                    "INSERT INTO command_idempotency (site_id, user_id, idempotency_key, command_type, "
                            + "request_fingerprint, result_status) VALUES ('%s', '%s', 'dup-key', 'test', 'fp', 200)",
                    siteId, userId));
        }

        assertThatThrownBy(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(String.format(
                        "INSERT INTO command_idempotency (site_id, user_id, idempotency_key, command_type, "
                                + "request_fingerprint, result_status) VALUES ('%s', '%s', 'dup-key', 'test', 'fp2', 200)",
                        siteId, userId));
            }
        }).isInstanceOf(SQLException.class).hasMessageContaining("idx_command_idempotency_site_user_key");
    }

    @Test
    void sameKeyAtADifferentSiteIsNotRejected() throws SQLException {
        UUID userId = UUID.randomUUID();
        try (Statement statement = connection.createStatement()) {
            statement.execute(String.format(
                    "INSERT INTO command_idempotency (site_id, user_id, idempotency_key, command_type, "
                            + "request_fingerprint, result_status) VALUES ('%s', '%s', 'shared-key', 'test', 'fp', 200)",
                    UUID.randomUUID(), userId));
            statement.execute(String.format(
                    "INSERT INTO command_idempotency (site_id, user_id, idempotency_key, command_type, "
                            + "request_fingerprint, result_status) VALUES ('%s', '%s', 'shared-key', 'test', 'fp', 200)",
                    UUID.randomUUID(), userId));
        }

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT count(*) FROM command_idempotency WHERE idempotency_key = 'shared-key'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }
    }
}
