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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Executes V67 and the separately gated V68 against PostgreSQL. */
@Testcontainers
class SiteIntegrityMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
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
            s.execute("CREATE TABLE storage_locations (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), site_id UUID NOT NULL REFERENCES sites(id), name TEXT NOT NULL, code TEXT NOT NULL, code_prefix TEXT, icon TEXT, has_display BOOLEAN NOT NULL DEFAULT false, is_display_only BOOLEAN NOT NULL DEFAULT false, display_order INTEGER NOT NULL DEFAULT 0, UNIQUE(site_id, code))");
            s.execute("CREATE TABLE locations (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), storage_location_id UUID NOT NULL REFERENCES storage_locations(id), location_code TEXT NOT NULL, UNIQUE(storage_location_id, location_code))");
            s.execute("CREATE TABLE stock_movements (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, site_id UUID)");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("DROP TABLE IF EXISTS stock_movements, locations, storage_locations, sites CASCADE");
            s.execute("DROP FUNCTION IF EXISTS enforce_one_not_assigned_location()");
        }
    }

    @Test
    void v67SeedsAndGuardsIndependentlyThenV68ConstrainsMovementSite() throws Exception {
        UUID main = site("MAIN");
        UUID second = site("SECOND");
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO stock_movements (site_id) VALUES (?)")) {
            ps.setObject(1, main);
            ps.executeUpdate();
        }

        migrateV67();

        assertThat(count("SELECT COUNT(*) FROM storage_locations WHERE site_id = '" + second + "' AND code = 'NOT_ASSIGNED'")).isOne();
        assertThat(count("SELECT COUNT(*) FROM locations l JOIN storage_locations sl ON sl.id = l.storage_location_id WHERE sl.site_id = '" + second + "' AND sl.code = 'NOT_ASSIGNED' AND l.location_code = 'NA'")).isOne();
        assertThat(count("SELECT COUNT(*) FROM storage_locations WHERE site_id = '" + main + "'")).isZero();
        // V67 is safe before new writers: nullable site_id remains accepted.
        try (Statement s = connection.createStatement()) { s.execute("INSERT INTO stock_movements (site_id) VALUES (NULL)"); }

        // V68 is intentionally gated on the production NULL re-check after site-aware writers deploy.
        try (Statement s = connection.createStatement()) { s.execute("UPDATE stock_movements SET site_id = '" + main + "' WHERE site_id IS NULL"); }
        migrateV68();
        assertThatThrownBy(() -> connection.createStatement().execute("INSERT INTO stock_movements (site_id) VALUES (NULL)"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> connection.createStatement().execute("INSERT INTO stock_movements (site_id) VALUES ('" + UUID.randomUUID() + "')"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void concurrentSecondCanonicalLocationIsRejected() throws Exception {
        UUID second = site("SECOND");
        try (Statement s = connection.createStatement()) { s.execute("INSERT INTO stock_movements (site_id) VALUES ('" + second + "')"); }
        migrateV67();
        UUID storage = uuid("SELECT id FROM storage_locations WHERE site_id = '" + second + "' AND code = 'NOT_ASSIGNED'");
        // Remove the seed row so two independent transactions race to establish it.
        try (Statement s = connection.createStatement()) { s.execute("DELETE FROM locations WHERE storage_location_id = '" + storage + "'"); }
        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> insertAndHold(storage));
        Thread.sleep(200);
        assertThatThrownBy(() -> insert(storage)).isInstanceOf(SQLException.class)
                .hasMessageContaining("only one canonical NOT_ASSIGNED location");
        first.get(5, TimeUnit.SECONDS);
        assertThat(count("SELECT COUNT(*) FROM locations WHERE storage_location_id = '" + storage + "'")).isOne();
    }

    @Test
    void repointingAnExistingLocationIntoNotAssignedIsRejected() throws Exception {
        UUID second = site("SECOND");
        migrateV67();
        UUID ordinaryStorage = uuid("INSERT INTO storage_locations (site_id, name, code) VALUES ('" + second + "', 'Rack', 'RACKS') RETURNING id");
        UUID ordinaryLocation = uuid("INSERT INTO locations (storage_location_id, location_code) VALUES ('" + ordinaryStorage + "', 'R1') RETURNING id");
        UUID notAssigned = uuid("SELECT id FROM storage_locations WHERE site_id = '" + second + "' AND code = 'NOT_ASSIGNED'");

        assertThatThrownBy(() -> connection.createStatement().execute("UPDATE locations SET storage_location_id = '" + notAssigned + "' WHERE id = '" + ordinaryLocation + "'"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("only one canonical NOT_ASSIGNED location");
    }

    private void insertAndHold(UUID storage) {
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            c.setAutoCommit(false);
            insert(c, storage, "NA-1");
            Thread.sleep(500);
            c.commit();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void insert(UUID storage) throws SQLException { insert(connection, storage, "NA-2"); }
    private void insert(Connection c, UUID storage, String code) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO locations (storage_location_id, location_code) VALUES (?, ?)")) {
            ps.setObject(1, storage); ps.setString(2, code); ps.executeUpdate();
        }
    }
    private UUID site(String code) throws SQLException { return uuid("INSERT INTO sites (code) VALUES ('" + code + "') RETURNING id"); }
    private UUID uuid(String sql) throws SQLException { try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery(sql)) { rs.next(); return rs.getObject(1, UUID.class); } }
    private int count(String sql) throws SQLException { try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery(sql)) { rs.next(); return rs.getInt(1); } }
    private void migrateV67() throws IOException, SQLException { try (Statement s = connection.createStatement()) { s.execute(resource("V67__seed_second_not_assigned_and_guard_canonical_location.sql")); } }
    private void migrateV68() throws IOException, SQLException { try (Statement s = connection.createStatement()) { s.execute(resource("V68__constrain_stock_movement_site.sql")); } }
    private static String resource(String name) throws IOException { try (InputStream in = SiteIntegrityMigrationIT.class.getClassLoader().getResourceAsStream("db/migration/" + name)) { if (in == null) throw new IllegalStateException(name); return new String(in.readAllBytes(), StandardCharsets.UTF_8); } }
}
