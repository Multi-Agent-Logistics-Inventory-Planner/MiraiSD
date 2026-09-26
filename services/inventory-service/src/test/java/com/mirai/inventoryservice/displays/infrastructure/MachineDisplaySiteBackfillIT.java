package com.mirai.inventoryservice.displays.infrastructure;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Executes V72__add_site_id_to_machine_display.sql then V73__backfill_machine_display_site_main.sql
 * against real PostgreSQL (.specs/phase-7-displays AC-1/AC-2, T-2), the same standalone-Testcontainers
 * approach {@code ShipmentSiteBackfillIT} uses for V69/V70 - no Spring context, minimal stub
 * prerequisite tables, and the real migration files executed verbatim. Schema is dropped and
 * rebuilt per test so each test can set up exactly the fixture its assertion needs.
 */
@Testcontainers
class MachineDisplaySiteBackfillIT {

    @Container
    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("mirai_machine_display_backfill_test")
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
            statement.execute("DROP TABLE IF EXISTS machine_display, locations, storage_locations, sites CASCADE");
            statement.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
            statement.execute("CREATE TABLE sites (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "code VARCHAR(20) UNIQUE)");
            statement.execute("CREATE TABLE storage_locations (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "site_id UUID NOT NULL REFERENCES sites(id))");
            statement.execute("CREATE TABLE locations (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "storage_location_id UUID NOT NULL REFERENCES storage_locations(id))");
            statement.execute("CREATE TABLE machine_display (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "location_id UUID REFERENCES locations(id))");
            statement.execute(readMigrationFile("V72__add_site_id_to_machine_display.sql"));
        }
    }

    @AfterEach
    void dropSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS machine_display, locations, storage_locations, sites CASCADE");
        }
    }

    private static String readMigrationFile(String fileName) throws IOException {
        try (InputStream in = MachineDisplaySiteBackfillIT.class.getClassLoader()
                .getResourceAsStream("db/migration/" + fileName)) {
            if (in == null) {
                throw new IllegalStateException(fileName + " not found on test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void runBackfill() throws SQLException, IOException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(readMigrationFile("V73__backfill_machine_display_site_main.sql"));
        }
    }

    private UUID insertSite(String code) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO sites (code) VALUES (?) RETURNING id")) {
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getObject("id", UUID.class);
        }
    }

    private UUID insertStorageLocation(UUID siteId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO storage_locations (site_id) VALUES (?) RETURNING id")) {
            ps.setObject(1, siteId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getObject("id", UUID.class);
        }
    }

    private UUID insertLocation(UUID storageLocationId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO locations (storage_location_id) VALUES (?) RETURNING id")) {
            ps.setObject(1, storageLocationId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getObject("id", UUID.class);
        }
    }

    private UUID insertMachineDisplay(UUID locationId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO machine_display (location_id) VALUES (?) RETURNING id")) {
            ps.setObject(1, locationId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getObject("id", UUID.class);
        }
    }

    private UUID siteIdFor(UUID displayId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT site_id FROM machine_display WHERE id = ?")) {
            ps.setObject(1, displayId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getObject("site_id", UUID.class);
        }
    }

    private int scalarInt(String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void failsLoudlyWhenNoMainSiteExists() throws SQLException {
        UUID second = insertSite("SECOND");
        UUID secondStorage = insertStorageLocation(second);
        UUID secondLocation = insertLocation(secondStorage);
        insertMachineDisplay(secondLocation);

        assertThatThrownBy(this::runBackfill)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("requires a MAIN site row to already exist");

        assertThat(scalarInt("SELECT COUNT(*) FROM machine_display WHERE site_id IS NOT NULL")).isZero();
    }

    @Test
    void rowResolvesThroughLocationToItsRealNonMainSite() throws SQLException, IOException {
        insertSite("MAIN");
        UUID second = insertSite("SECOND");
        UUID secondStorage = insertStorageLocation(second);
        UUID secondLocation = insertLocation(secondStorage);
        UUID display = insertMachineDisplay(secondLocation);

        runBackfill();

        assertThat(siteIdFor(display)).isEqualTo(second);
    }

    @Test
    void orphanRowWithNoResolvableLocationFallsBackToMain() throws SQLException, IOException {
        UUID main = insertSite("MAIN");
        UUID display = insertMachineDisplay(null);

        runBackfill();

        assertThat(siteIdFor(display)).isEqualTo(main);
    }

    @Test
    void isIdempotentOnRerunAndLeavesNoNullSiteIds() throws SQLException, IOException {
        insertSite("MAIN");
        UUID second = insertSite("SECOND");
        UUID secondStorage = insertStorageLocation(second);
        UUID secondLocation = insertLocation(secondStorage);
        insertMachineDisplay(secondLocation);
        insertMachineDisplay(null);

        runBackfill();
        runBackfill();

        assertThat(scalarInt("SELECT COUNT(*) FROM machine_display WHERE site_id IS NULL")).isZero();
    }
}
