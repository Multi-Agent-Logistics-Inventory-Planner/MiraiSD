package com.mirai.inventoryservice.audit.infrastructure;

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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Executes V81-V83 (.specs/phase-7-notifications-reviews-audit T-3) against real PostgreSQL with a
 * minimal stub schema and the real migration files run verbatim.
 */
@Testcontainers
class NotificationsReviewsAuditSiteMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    private static Connection connection;

    @BeforeAll
    static void connect() throws SQLException {
        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @AfterAll
    static void close() throws SQLException {
        if (connection != null) connection.close();
    }

    @BeforeEach
    void schema() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
            s.execute("CREATE TABLE sites (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), code TEXT UNIQUE NOT NULL)");
            s.execute("CREATE TABLE storage_locations (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), site_id UUID NOT NULL REFERENCES sites(id))");
            s.execute("CREATE TABLE locations (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), storage_location_id UUID NOT NULL REFERENCES storage_locations(id))");
            s.execute("CREATE TABLE location_inventory (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), location_id UUID NOT NULL REFERENCES locations(id))");
            s.execute("CREATE TABLE users (id UUID PRIMARY KEY DEFAULT gen_random_uuid())");
            s.execute("CREATE TABLE shipments (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), site_id UUID NOT NULL REFERENCES sites(id))");
            s.execute("CREATE TABLE audit_logs (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "primary_from_location_id UUID, primary_to_location_id UUID, shipment_id UUID, "
                    + "created_at TIMESTAMPTZ NOT NULL DEFAULT now())");
            s.execute("CREATE TABLE stock_movements (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "audit_log_id UUID REFERENCES audit_logs(id), site_id UUID)");
            s.execute("CREATE TABLE notifications (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "recipient_id UUID, inventory_id UUID, metadata JSONB, created_at TIMESTAMPTZ NOT NULL DEFAULT now())");
            s.execute("CREATE TABLE reviews (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "user_id UUID REFERENCES users(id), review_date DATE NOT NULL DEFAULT current_date)");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("DROP TABLE IF EXISTS reviews, notifications, stock_movements, audit_logs, shipments, "
                    + "users, location_inventory, locations, storage_locations, sites CASCADE");
        }
    }

    @Test
    void auditLogsInheritTheSiteOfTheirMovementsThenShipmentThenLocation() throws Exception {
        UUID main = site("MAIN");
        UUID second = site("SECOND");
        UUID secondStorage = uuid("INSERT INTO storage_locations (site_id) VALUES ('" + second + "') RETURNING id");
        UUID secondLocation = uuid("INSERT INTO locations (storage_location_id) VALUES ('" + secondStorage + "') RETURNING id");
        UUID secondShipment = uuid("INSERT INTO shipments (site_id) VALUES ('" + second + "') RETURNING id");

        UUID byMovement = uuid("INSERT INTO audit_logs DEFAULT VALUES RETURNING id");
        exec("INSERT INTO stock_movements (audit_log_id, site_id) VALUES ('" + byMovement + "', '" + second + "')");
        UUID byShipment = uuid("INSERT INTO audit_logs (shipment_id) VALUES ('" + secondShipment + "') RETURNING id");
        UUID byLocation = uuid("INSERT INTO audit_logs (primary_to_location_id) VALUES ('" + secondLocation + "') RETURNING id");
        UUID orphan = uuid("INSERT INTO audit_logs DEFAULT VALUES RETURNING id");

        migrate("V81__add_site_id_to_notifications_reviews_audit_logs.sql");
        migrate("V82__backfill_notifications_reviews_audit_logs_site.sql");

        assertThat(count("SELECT COUNT(*) FROM audit_logs")).isEqualTo(4);
        assertThat(siteOf("audit_logs", byMovement)).isEqualTo(second);
        assertThat(siteOf("audit_logs", byShipment)).isEqualTo(second);
        assertThat(siteOf("audit_logs", byLocation)).isEqualTo(second);
        assertThat(siteOf("audit_logs", orphan)).isEqualTo(main);
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE site_id IS NULL")).isZero();
    }

    @Test
    void backfillRefusesAnAuditLogWhoseMovementsSpanSites() throws Exception {
        UUID main = site("MAIN");
        UUID second = site("SECOND");
        UUID mixed = uuid("INSERT INTO audit_logs DEFAULT VALUES RETURNING id");
        exec("INSERT INTO stock_movements (audit_log_id, site_id) VALUES ('" + mixed + "', '" + second + "')");
        exec("INSERT INTO stock_movements (audit_log_id, site_id) VALUES ('" + mixed + "', '" + main + "')");

        migrate("V81__add_site_id_to_notifications_reviews_audit_logs.sql");

        assertThatThrownBy(() -> migrate("V82__backfill_notifications_reviews_audit_logs_site.sql"))
                .hasMessageContaining("span more than one site");
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE site_id IS NOT NULL")).isZero();
    }

    @Test
    void notificationsFollowTheirInventoryRowAndReviewsFallBackToMain() throws Exception {
        UUID main = site("MAIN");
        UUID second = site("SECOND");
        UUID secondStorage = uuid("INSERT INTO storage_locations (site_id) VALUES ('" + second + "') RETURNING id");
        UUID secondLocation = uuid("INSERT INTO locations (storage_location_id) VALUES ('" + secondStorage + "') RETURNING id");
        UUID inventory = uuid("INSERT INTO location_inventory (location_id) VALUES ('" + secondLocation + "') RETURNING id");
        UUID user = uuid("INSERT INTO users DEFAULT VALUES RETURNING id");

        UUID located = uuid("INSERT INTO notifications (inventory_id) VALUES ('" + inventory + "') RETURNING id");
        UUID dangling = uuid("INSERT INTO notifications (inventory_id) VALUES ('" + UUID.randomUUID() + "') RETURNING id");
        UUID broadcast = uuid("INSERT INTO notifications DEFAULT VALUES RETURNING id");
        UUID review = uuid("INSERT INTO reviews (user_id) VALUES ('" + user + "') RETURNING id");

        migrate("V81__add_site_id_to_notifications_reviews_audit_logs.sql");
        migrate("V82__backfill_notifications_reviews_audit_logs_site.sql");

        assertThat(siteOf("notifications", located)).isEqualTo(second);
        assertThat(siteOf("notifications", dangling)).isEqualTo(main);
        assertThat(siteOf("notifications", broadcast)).isEqualTo(main);
        assertThat(siteOf("reviews", review)).isEqualTo(main);
        assertThat(count("SELECT COUNT(*) FROM notifications")).isEqualTo(3);
    }

    @Test
    void machineNotificationsFollowTheMachinesSiteWhenThereIsNoInventoryRow() throws Exception {
        UUID main = site("MAIN");
        UUID second = site("SECOND");
        UUID secondStorage = uuid("INSERT INTO storage_locations (site_id) VALUES ('" + second + "') RETURNING id");
        UUID machine = uuid("INSERT INTO locations (storage_location_id) VALUES ('" + secondStorage + "') RETURNING id");

        UUID stale = uuid("INSERT INTO notifications (metadata) VALUES ('{\"machine_id\": \"" + machine
                + "\"}'::jsonb) RETURNING id");
        UUID secondShipment = uuid("INSERT INTO shipments (site_id) VALUES ('" + second + "') RETURNING id");
        UUID tracking = uuid("INSERT INTO notifications (metadata) VALUES ('{\"shipment_id\": \"" + secondShipment
                + "\"}'::jsonb) RETURNING id");
        UUID notAUuid = uuid("INSERT INTO notifications (metadata) VALUES ('{\"machine_id\": \"not-a-uuid\"}'::jsonb) RETURNING id");

        migrate("V81__add_site_id_to_notifications_reviews_audit_logs.sql");
        migrate("V82__backfill_notifications_reviews_audit_logs_site.sql");

        assertThat(siteOf("notifications", stale)).isEqualTo(second);
        assertThat(siteOf("notifications", tracking)).isEqualTo(second);
        assertThat(siteOf("notifications", notAUuid)).isEqualTo(main);
    }

    @Test
    void backfillIsIdempotentAndFailsLoudlyWithoutAMainSite() throws Exception {
        migrate("V81__add_site_id_to_notifications_reviews_audit_logs.sql");
        assertThatThrownBy(() -> migrate("V82__backfill_notifications_reviews_audit_logs_site.sql"))
                .isInstanceOf(SQLException.class);

        site("MAIN");
        exec("INSERT INTO notifications DEFAULT VALUES");
        migrate("V82__backfill_notifications_reviews_audit_logs_site.sql");
        migrate("V82__backfill_notifications_reviews_audit_logs_site.sql");
        assertThat(count("SELECT COUNT(*) FROM notifications WHERE site_id IS NOT NULL")).isEqualTo(1);
    }

    @Test
    void indexMigrationRunsStatementByStatementAndIsRepeatable() throws Exception {
        migrate("V81__add_site_id_to_notifications_reviews_audit_logs.sql");
        migrateStatements("V83__notifications_reviews_audit_logs_site_indexes.sql");
        migrateStatements("V83__notifications_reviews_audit_logs_site_indexes.sql");

        assertThat(count("SELECT COUNT(*) FROM pg_indexes WHERE indexname IN ("
                + "'idx_notifications_site_created','idx_notifications_site_recipient_created',"
                + "'idx_reviews_site_user_date','idx_audit_logs_site_created')")).isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid "
                + "WHERE c.relname LIKE 'idx_%_site_%' AND NOT i.indisvalid")).isZero();
    }

    private static UUID siteOf(String table, UUID id) throws SQLException {
        return uuid("SELECT site_id FROM " + table + " WHERE id = '" + id + "'");
    }

    private static UUID site(String code) throws SQLException {
        return uuid("INSERT INTO sites (code) VALUES ('" + code + "') RETURNING id");
    }

    private static void exec(String sql) throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute(sql);
        }
    }

    private static UUID uuid(String sql) throws SQLException {
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getObject(1, UUID.class);
        }
    }

    private static int count(String sql) throws SQLException {
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void migrate(String fileName) throws IOException, SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute(resource(fileName));
        }
    }

    /** CONCURRENTLY statements must each be their own simple-query message (see StockMovementSiteIndexMigrationIT). */
    private static void migrateStatements(String fileName) throws IOException, SQLException {
        try (Statement s = connection.createStatement()) {
            for (String sql : sqlStatements(fileName)) {
                s.execute(sql);
            }
        }
    }

    private static List<String> sqlStatements(String fileName) throws IOException {
        String withoutComments = resource(fileName).lines()
                .filter(line -> !line.strip().startsWith("--"))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        return Arrays.stream(withoutComments.split(";"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = NotificationsReviewsAuditSiteMigrationIT.class.getClassLoader()
                .getResourceAsStream("db/migration/" + name)) {
            if (in == null) throw new IllegalStateException(name + " not found on test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
