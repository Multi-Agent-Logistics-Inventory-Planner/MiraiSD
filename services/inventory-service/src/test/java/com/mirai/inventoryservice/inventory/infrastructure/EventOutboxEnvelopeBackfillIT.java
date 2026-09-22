package com.mirai.inventoryservice.inventory.infrastructure;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
 * Executes V63__add_envelope_context_to_event_outbox.sql then
 * V64__backfill_event_outbox_envelope_context.sql against real PostgreSQL
 * (.specs/phase-6-inventory, T-6c-7), the same standalone-Testcontainers approach
 * {@code StockMovementSiteBackfillIT} uses for V59/V60 — minimal stub {@code sites}/
 * {@code stock_movements}/{@code event_outbox} tables, schema dropped and rebuilt per test.
 */
@Testcontainers
class EventOutboxEnvelopeBackfillIT {

    @Container
    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("mirai_event_outbox_backfill_test")
                    .withUsername("test")
                    .withPassword("test");

    private static Connection connection;

    @BeforeAll
    static void connect() throws SQLException {
        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @AfterAll
    static void closeConnection() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    @BeforeEach
    void freshSchema() throws SQLException, IOException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS event_outbox, stock_movements, sites CASCADE");
            statement.execute("CREATE TABLE sites (id UUID PRIMARY KEY DEFAULT gen_random_uuid())");
            statement.execute("CREATE TABLE stock_movements (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
                    + "site_id UUID REFERENCES sites(id))");
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

    @AfterEach
    void dropSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS event_outbox, stock_movements, sites CASCADE");
        }
    }

    private static String readMigrationFile(String fileName) throws IOException {
        try (InputStream in = EventOutboxEnvelopeBackfillIT.class.getClassLoader()
                .getResourceAsStream("db/migration/" + fileName)) {
            if (in == null) {
                throw new IllegalStateException(fileName + " not found on test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void runBackfill() throws SQLException, IOException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(readMigrationFile("V64__backfill_event_outbox_envelope_context.sql"));
        }
    }

    private UUID insertSite() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO sites DEFAULT VALUES RETURNING id")) {
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getObject("id", UUID.class);
        }
    }

    private long insertMovement(UUID siteId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO stock_movements (site_id) VALUES (?) RETURNING id")) {
            ps.setObject(1, siteId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }

    // event_version is inserted explicitly as NULL here, not omitted: once the EventOutbox
    // entity carries the eventVersion field (this phase), Hibernate's static INSERT sends every
    // mapped column including unset ones as NULL, overriding event_outbox's DEFAULT 1 -- so a
    // row written by the current (post-V63, pre-T-6c-8) production code path really does have
    // event_version = NULL, which is exactly the "not yet backfilled" state V64 must detect.
    // Omitting the column entirely (letting Postgres apply DEFAULT 1 at insert time) would only
    // be realistic for a raw INSERT from code that predates this column, which
    // oldWriterInsertOmittingNewColumnsStillSucceedsAndDefaultsEventVersion already covers.
    private UUID insertUnpublishedStockMovementEvent(long movementId, String correlationId) throws SQLException {
        String payload = correlationId == null
                ? String.format("{\"stock_movement_id\": \"%d\"}", movementId)
                : String.format("{\"stock_movement_id\": \"%d\", \"correlation_id\": \"%s\"}", movementId, correlationId);
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO event_outbox (event_type, entity_type, payload, topic, event_version) "
                        + "VALUES ('CREATED', 'stock_movement', ?::jsonb, 'inventory-changes', NULL) RETURNING id")) {
            ps.setString(1, payload);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getObject("id", UUID.class);
        }
    }

    // Simulates a row written by a path that omits event_version from its INSERT entirely (a raw
    // SQL writer, or any code that predates this phase's entity field) -- Postgres applies V63's
    // DEFAULT 1 immediately, so this row reads back event_version = 1 without the backfill ever
    // having run. site_id and correlation_id are still genuinely unresolved.
    private UUID insertUnpublishedStockMovementEventOmittingEventVersion(long movementId, String correlationId) throws SQLException {
        String payload = String.format("{\"stock_movement_id\": \"%d\", \"correlation_id\": \"%s\"}", movementId, correlationId);
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO event_outbox (event_type, entity_type, payload, topic) "
                        + "VALUES ('CREATED', 'stock_movement', ?::jsonb, 'inventory-changes') RETURNING id")) {
            ps.setString(1, payload);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getObject("id", UUID.class);
        }
    }

    private UUID insertPublishedStockMovementEvent(long movementId) throws SQLException {
        String payload = String.format("{\"stock_movement_id\": \"%d\"}", movementId);
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO event_outbox (event_type, entity_type, payload, topic, published_at, event_version) "
                        + "VALUES ('CREATED', 'stock_movement', ?::jsonb, 'inventory-changes', now(), NULL) RETURNING id")) {
            ps.setString(1, payload);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getObject("id", UUID.class);
        }
    }

    private ResultSet envelopeRow(UUID eventId) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
                "SELECT site_id, event_version, correlation_id FROM event_outbox WHERE id = ?");
        ps.setObject(1, eventId);
        ResultSet rs = ps.executeQuery();
        rs.next();
        return rs;
    }

    private int scalarInt(String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void resolvableMovementBackfillsSiteVersionAndCorrelation() throws SQLException, IOException {
        UUID site = insertSite();
        long movement = insertMovement(site);
        UUID event = insertUnpublishedStockMovementEvent(movement, "req-123");

        runBackfill();

        ResultSet row = envelopeRow(event);
        assertThat(row.getObject("site_id", UUID.class)).isEqualTo(site);
        assertThat(row.getInt("event_version")).isEqualTo(1);
        assertThat(row.getString("correlation_id")).isEqualTo("req-123");
    }

    @Test
    void missingCorrelationInPayloadBackfillsNullCorrelation() throws SQLException, IOException {
        UUID site = insertSite();
        long movement = insertMovement(site);
        UUID event = insertUnpublishedStockMovementEvent(movement, null);

        runBackfill();

        ResultSet row = envelopeRow(event);
        assertThat(row.getObject("site_id", UUID.class)).isEqualTo(site);
        assertThat(row.getInt("event_version")).isEqualTo(1);
        assertThat(row.getString("correlation_id")).isNull();
    }

    @Test
    void danglingMovementReferenceGetsUnknownSiteButStillBackfillsVersionAndCorrelation() throws SQLException, IOException {
        // Guard case (T-6c-7's explicit test requirement): the referenced movement no longer
        // exists. site_id cannot be resolved and stays NULL (the same unknown-site posture Q-6c-5
        // accepts for stock_movements during the compatibility window), but event_version/
        // correlation_id do not depend on the movement and still get backfilled.
        long nonExistentMovementId = 999_999L;
        UUID event = insertUnpublishedStockMovementEvent(nonExistentMovementId, "req-orphan");

        runBackfill();

        ResultSet row = envelopeRow(event);
        assertThat(row.getObject("site_id")).isNull();
        assertThat(row.getInt("event_version")).isEqualTo(1);
        assertThat(row.getString("correlation_id")).isEqualTo("req-orphan");
    }

    @Test
    void oldWriterDefaultedEventVersionStillGetsSiteAndCorrelationBackfilled() throws SQLException, IOException {
        // Proves the guard is not event_version-only: this row's event_version already reads 1
        // (V63's column DEFAULT, applied at insert time because the INSERT omitted the column)
        // before the backfill ever runs, but site_id/correlation_id are still genuinely
        // unresolved and must not be skipped because of that pre-existing default value.
        UUID site = insertSite();
        long movement = insertMovement(site);
        UUID event = insertUnpublishedStockMovementEventOmittingEventVersion(movement, "req-old-writer");

        ResultSet before = envelopeRow(event);
        assertThat(before.getInt("event_version")).isEqualTo(1);
        assertThat(before.getObject("site_id")).isNull();

        runBackfill();

        ResultSet after = envelopeRow(event);
        assertThat(after.getObject("site_id", UUID.class)).isEqualTo(site);
        assertThat(after.getInt("event_version")).isEqualTo(1);
        assertThat(after.getString("correlation_id")).isEqualTo("req-old-writer");
    }

    @Test
    void publishedRowsAreNotBackfilled() throws SQLException, IOException {
        UUID site = insertSite();
        long movement = insertMovement(site);
        UUID event = insertPublishedStockMovementEvent(movement);

        runBackfill();

        ResultSet row = envelopeRow(event);
        assertThat(row.getObject("site_id")).isNull();
        assertThat(row.getObject("event_version")).isNull();
    }

    @Test
    void isIdempotentOnRerun() throws SQLException, IOException {
        UUID site = insertSite();
        long movement = insertMovement(site);
        UUID resolvable = insertUnpublishedStockMovementEvent(movement, "req-1");
        UUID orphan = insertUnpublishedStockMovementEvent(999_998L, "req-2");

        runBackfill();
        runBackfill();

        ResultSet resolvableRow = envelopeRow(resolvable);
        assertThat(resolvableRow.getObject("site_id", UUID.class)).isEqualTo(site);
        assertThat(resolvableRow.getInt("event_version")).isEqualTo(1);

        ResultSet orphanRow = envelopeRow(orphan);
        assertThat(orphanRow.getObject("site_id")).isNull();
        assertThat(orphanRow.getInt("event_version")).isEqualTo(1);

        assertThat(scalarInt(
                "SELECT COUNT(*) FROM event_outbox WHERE published_at IS NULL AND event_version IS NULL"))
                .isZero();
    }
}
