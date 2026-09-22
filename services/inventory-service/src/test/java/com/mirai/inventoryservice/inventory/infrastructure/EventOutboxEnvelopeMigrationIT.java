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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes V63__add_envelope_context_to_event_outbox.sql against real PostgreSQL
 * (.specs/phase-6-inventory, T-6c-7), the same standalone-Testcontainers approach
 * {@link StockMovementSiteIndexMigrationIT}/{@code StockMovementSiteBackfillIT} use for the 6b
 * stock_movements migrations — no Spring context, a minimal stub {@code event_outbox} table
 * (event_outbox itself predates Flyway, same as stock_movements before V59), and the real
 * migration file executed verbatim. Asserts the resulting column shape: all five new columns
 * exist, are nullable, have the expected Postgres type, and that new inserts which omit them
 * entirely (the "old deployed writer" compatibility case V63's header describes) still succeed
 * and pick up event_version's DEFAULT 1 while the other four columns stay NULL.
 */
@Testcontainers
class EventOutboxEnvelopeMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("mirai_event_outbox_envelope_test")
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
            statement.execute(readMigrationFile("V63__add_envelope_context_to_event_outbox.sql"));
        }
    }

    @AfterAll
    static void closeConnection() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    private static String readMigrationFile(String fileName) throws IOException {
        try (InputStream in = EventOutboxEnvelopeMigrationIT.class.getClassLoader()
                .getResourceAsStream("db/migration/" + fileName)) {
            if (in == null) {
                throw new IllegalStateException(fileName + " not found on test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Map<String, String[]> columnShapes() throws SQLException {
        Map<String, String[]> shapes = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT column_name, data_type, is_nullable, column_default "
                             + "FROM information_schema.columns WHERE table_name = 'event_outbox'")) {
            while (rs.next()) {
                shapes.put(rs.getString("column_name"), new String[]{
                        rs.getString("data_type"), rs.getString("is_nullable"), rs.getString("column_default")
                });
            }
        }
        return shapes;
    }

    @Test
    void addsAllFiveEnvelopeColumnsNullableWithExpectedTypes() throws SQLException {
        Map<String, String[]> shapes = columnShapes();

        assertThat(shapes).containsKey("site_id");
        assertThat(shapes.get("site_id")[0]).isEqualTo("uuid");
        assertThat(shapes.get("site_id")[1]).isEqualTo("YES");

        assertThat(shapes).containsKey("event_version");
        assertThat(shapes.get("event_version")[0]).isEqualTo("integer");
        assertThat(shapes.get("event_version")[1]).isEqualTo("YES");
        assertThat(shapes.get("event_version")[2]).contains("1");

        for (String textColumn : new String[]{"correlation_id", "causation_id", "idempotency_key"}) {
            assertThat(shapes).containsKey(textColumn);
            assertThat(shapes.get(textColumn)[0]).isEqualTo("text");
            assertThat(shapes.get(textColumn)[1]).isEqualTo("YES");
        }
    }

    @Test
    void oldWriterInsertOmittingNewColumnsStillSucceedsAndDefaultsEventVersion() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO event_outbox (event_type, entity_type, payload, topic) "
                    + "VALUES ('CREATED', 'stock_movement', '{}'::jsonb, 'inventory-changes')");
        }

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT site_id, event_version, correlation_id, causation_id, idempotency_key "
                             + "FROM event_outbox WHERE entity_type = 'stock_movement'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getObject("site_id")).isNull();
            assertThat(rs.getInt("event_version")).isEqualTo(1);
            assertThat(rs.getString("correlation_id")).isNull();
            assertThat(rs.getString("causation_id")).isNull();
            assertThat(rs.getString("idempotency_key")).isNull();
        }
    }
}
