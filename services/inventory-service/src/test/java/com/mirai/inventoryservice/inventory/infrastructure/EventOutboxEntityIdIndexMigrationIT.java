package com.mirai.inventoryservice.inventory.infrastructure;

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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Code-review finding (.specs/phase-6-inventory T-6c-8, review pass 2026-09-13):
 * {@code EventOutboxRepository.existsByEntityId} (T-6c-8's dedupe guard, run on every stock
 * movement) had no supporting index -- V16 indexes a JSONB payload expression
 * ({@code payload->>'stock_movement_id'}), not the plain {@code entity_id} column. Executes
 * V66__add_event_outbox_entity_id_index.sql itself against real PostgreSQL, the same standalone-
 * Testcontainers approach {@link StockMovementSiteIndexMigrationIT} uses for V62, including its
 * planner-assertion technique ({@code enable_seqscan=off}, since the seeded row count here is not
 * itself enough for a real Postgres planner to prefer an index scan at this small a scale).
 */
@Testcontainers
class EventOutboxEntityIdIndexMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("mirai_event_outbox_index_test")
                    .withUsername("test")
                    .withPassword("test");

    private static Connection connection;

    @BeforeAll
    static void migrate() throws SQLException, IOException {
        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE event_outbox ("
                    + "id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "event_type VARCHAR(255) NOT NULL, "
                    + "entity_type VARCHAR(255) NOT NULL, "
                    + "entity_id UUID, "
                    + "payload JSONB NOT NULL, "
                    + "topic VARCHAR(255) NOT NULL, "
                    + "publish_attempts INTEGER NOT NULL DEFAULT 0, "
                    + "last_error TEXT, "
                    + "created_at TIMESTAMPTZ NOT NULL DEFAULT now(), "
                    + "published_at TIMESTAMPTZ)");
            // CONCURRENTLY is rejected inside a pipelined/batched multi-statement execute, so this
            // file's single statement is run on its own -- same reasoning as
            // StockMovementSiteIndexMigrationIT's per-statement execution of V62.
            statement.execute(readMigrationFile("V66__add_event_outbox_entity_id_index.sql"));
        }
        seedRowsForPlannerAssertion();
    }

    @AfterAll
    static void closeConnection() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    private static void seedRowsForPlannerAssertion() throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO event_outbox (event_type, entity_type, entity_id, payload, topic) "
                        + "VALUES ('CREATED', 'stock_movement', ?, '{}'::jsonb, 'inventory-changes')")) {
            for (int i = 0; i < 500; i++) {
                insert.setObject(1, UUID.randomUUID());
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ANALYZE event_outbox");
        }
    }

    private static String readMigrationFile(String fileName) throws IOException {
        try (InputStream in = EventOutboxEntityIdIndexMigrationIT.class.getClassLoader()
                .getResourceAsStream("db/migration/" + fileName)) {
            if (in == null) {
                throw new IllegalStateException(fileName + " not found on test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void createsTheEntityIdIndex() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT indexdef FROM pg_indexes WHERE tablename = 'event_outbox' "
                             + "AND indexname = 'idx_event_outbox_entity_id'")) {
            assertThat(rs.next()).as("idx_event_outbox_entity_id must exist").isTrue();
            assertThat(rs.getString("indexdef")).containsIgnoringCase("(entity_id)");
        }
    }

    @Test
    void existsByEntityIdLookupPlansThroughTheIndex() throws SQLException {
        UUID knownEntityId = UUID.randomUUID();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO event_outbox (event_type, entity_type, entity_id, payload, topic) "
                        + "VALUES ('CREATED', 'stock_movement', ?, '{}'::jsonb, 'inventory-changes')")) {
            insert.setObject(1, knownEntityId);
            insert.executeUpdate();
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("SET enable_seqscan = off");
        }
        String plan;
        try (PreparedStatement explain = connection.prepareStatement(
                "EXPLAIN SELECT 1 FROM event_outbox WHERE entity_id = ?")) {
            explain.setObject(1, knownEntityId);
            StringBuilder sb = new StringBuilder();
            try (ResultSet rs = explain.executeQuery()) {
                while (rs.next()) {
                    sb.append(rs.getString(1)).append('\n');
                }
            }
            plan = sb.toString();
        } finally {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET enable_seqscan = on");
            }
        }

        assertThat(plan).contains("idx_event_outbox_entity_id");
    }
}
