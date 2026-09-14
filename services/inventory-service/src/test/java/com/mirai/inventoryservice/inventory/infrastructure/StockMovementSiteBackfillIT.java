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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Executes V59__add_site_id_to_stock_movements.sql then
 * V60__backfill_stock_movements_site_main.sql against real PostgreSQL (.specs/phase-6-inventory
 * 6b, AC-2), the same standalone-Testcontainers approach {@code SiteProductBackfillIT} uses for
 * V57 - no Spring context, minimal stub `sites`/`locations`/`storage_locations`/`stock_movements`
 * prerequisite tables, and the real migration files executed verbatim. Schema is dropped and
 * rebuilt per test so each test can set up exactly the fixture (MAIN present/absent, SECOND
 * present, location-resolvable vs. both-null vs. dangling movements) its assertion needs.
 */
@Testcontainers
class StockMovementSiteBackfillIT {

    @Container
    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("mirai_stock_movements_backfill_test")
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
            statement.execute("DROP TABLE IF EXISTS stock_movements, locations, storage_locations, sites CASCADE");
            statement.execute("CREATE TABLE sites (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "code VARCHAR(20) UNIQUE)");
            statement.execute("CREATE TABLE storage_locations (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "site_id UUID NOT NULL REFERENCES sites(id))");
            statement.execute("CREATE TABLE locations (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "storage_location_id UUID NOT NULL REFERENCES storage_locations(id))");
            statement.execute("CREATE TABLE stock_movements (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
                    + "from_location_id UUID, to_location_id UUID, quantity_change INTEGER NOT NULL)");
            statement.execute(readMigrationFile("V59__add_site_id_to_stock_movements.sql"));
        }
    }

    @AfterEach
    void dropSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS stock_movements, locations, storage_locations, sites CASCADE");
        }
    }

    private static String readMigrationFile(String fileName) throws IOException {
        try (InputStream in = StockMovementSiteBackfillIT.class.getClassLoader()
                .getResourceAsStream("db/migration/" + fileName)) {
            if (in == null) {
                throw new IllegalStateException(fileName + " not found on test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void runBackfill() throws SQLException, IOException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(readMigrationFile("V60__backfill_stock_movements_site_main.sql"));
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

    private long insertMovement(UUID fromLocationId, UUID toLocationId) throws SQLException {
        return insertMovement(fromLocationId, toLocationId, 0);
    }

    private long insertMovement(UUID fromLocationId, UUID toLocationId, int quantityChange) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO stock_movements (from_location_id, to_location_id, quantity_change) "
                        + "VALUES (?, ?, ?) RETURNING id")) {
            ps.setObject(1, fromLocationId);
            ps.setObject(2, toLocationId);
            ps.setInt(3, quantityChange);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }

    private UUID siteIdFor(long movementId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT site_id FROM stock_movements WHERE id = ?")) {
            ps.setLong(1, movementId);
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
        insertSite("SECOND");
        insertMovement(null, null);

        assertThatThrownBy(this::runBackfill)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("requires a MAIN site row to already exist");

        assertThat(scalarInt("SELECT COUNT(*) FROM stock_movements WHERE site_id IS NOT NULL")).isZero();
    }

    @Test
    void locationDerivedMovementGetsItsActualLocationSiteNotMain() throws SQLException, IOException {
        UUID main = insertSite("MAIN");
        UUID second = insertSite("SECOND");
        UUID mainStorage = insertStorageLocation(main);
        UUID secondStorage = insertStorageLocation(second);
        UUID mainLocation = insertLocation(mainStorage);
        UUID secondLocation = insertLocation(secondStorage);

        long mainMovement = insertMovement(null, mainLocation);
        long secondMovement = insertMovement(null, secondLocation);

        runBackfill();

        assertThat(siteIdFor(mainMovement)).isEqualTo(main);
        assertThat(siteIdFor(secondMovement)).isEqualTo(second);
    }

    @Test
    void transferPairResolvesEachLegToItsOwnSiteBySign() throws SQLException, IOException {
        // Reproduces the real write shape: an inter-site transfer writes two StockMovement rows
        // with identical from/to location columns but opposite signs and independently-derived
        // sites (StockMovementService/KujiBoxService set .site(sourceLocation...) on the
        // withdrawal leg and .site(destinationLocation...) on the deposit leg). A COALESCE(to,
        // from) that ignores quantity_change would assign both legs the destination's site.
        UUID main = insertSite("MAIN");
        UUID second = insertSite("SECOND");
        UUID mainStorage = insertStorageLocation(main);
        UUID secondStorage = insertStorageLocation(second);
        UUID mainLocation = insertLocation(mainStorage);
        UUID secondLocation = insertLocation(secondStorage);

        long withdrawal = insertMovement(mainLocation, secondLocation, -5);
        long deposit = insertMovement(mainLocation, secondLocation, 5);

        runBackfill();

        assertThat(siteIdFor(withdrawal)).as("withdrawal leg resolves to the source site").isEqualTo(main);
        assertThat(siteIdFor(deposit)).as("deposit leg resolves to the destination site").isEqualTo(second);
    }

    @Test
    void bothLocationsNullFallsBackToMain() throws SQLException, IOException {
        UUID main = insertSite("MAIN");
        long kujiLedgerRow = insertMovement(null, null);

        runBackfill();

        assertThat(siteIdFor(kujiLedgerRow)).isEqualTo(main);
    }

    @Test
    void danglingLocationReferenceFallsBackToMain() throws SQLException, IOException {
        UUID main = insertSite("MAIN");
        long movement = insertMovement(null, UUID.randomUUID());

        runBackfill();

        assertThat(siteIdFor(movement)).isEqualTo(main);
    }

    @Test
    void isIdempotentOnRerun() throws SQLException, IOException {
        UUID main = insertSite("MAIN");
        UUID second = insertSite("SECOND");
        UUID secondStorage = insertStorageLocation(second);
        UUID secondLocation = insertLocation(secondStorage);
        long locationDerived = insertMovement(null, secondLocation);
        long fallback = insertMovement(null, null);

        runBackfill();
        runBackfill();

        assertThat(siteIdFor(locationDerived)).isEqualTo(second);
        assertThat(siteIdFor(fallback)).isEqualTo(main);
        assertThat(scalarInt("SELECT COUNT(*) FROM stock_movements WHERE site_id IS NULL")).isZero();
    }
}
