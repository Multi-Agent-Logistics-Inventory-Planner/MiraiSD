package com.mirai.inventoryservice.shipments.infrastructure;

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
 * Executes V69__add_site_id_to_shipments.sql then V70__backfill_shipments_site_main.sql against
 * real PostgreSQL (.specs/phase-7-shipments AC-1, T-3), the same standalone-Testcontainers
 * approach {@code StockMovementSiteBackfillIT} uses for V59/V60 - no Spring context, minimal stub
 * prerequisite tables, and the real migration files executed verbatim. Schema is dropped and
 * rebuilt per test so each test can set up exactly the fixture its assertion needs.
 */
@Testcontainers
class ShipmentSiteBackfillIT {

    @Container
    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("mirai_shipments_backfill_test")
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
            statement.execute("DROP TABLE IF EXISTS shipment_item_allocations, shipment_items, "
                    + "webhook_events, shipments, locations, storage_locations, sites CASCADE");
            statement.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
            statement.execute("CREATE TABLE sites (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "code VARCHAR(20) UNIQUE)");
            statement.execute("CREATE TABLE storage_locations (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "site_id UUID NOT NULL REFERENCES sites(id))");
            statement.execute("CREATE TABLE locations (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "storage_location_id UUID NOT NULL REFERENCES storage_locations(id))");
            statement.execute("CREATE TABLE shipments (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "created_at TIMESTAMPTZ NOT NULL DEFAULT now())");
            statement.execute("CREATE TABLE shipment_items (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "shipment_id UUID NOT NULL REFERENCES shipments(id), "
                    + "destination_location_id UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT now())");
            statement.execute("CREATE TABLE shipment_item_allocations (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "shipment_item_id UUID NOT NULL REFERENCES shipment_items(id), location_id UUID)");
            statement.execute("CREATE TABLE webhook_events (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "event_id VARCHAR(255))");
            statement.execute(readMigrationFile("V69__add_site_id_to_shipments.sql"));
        }
    }

    @AfterEach
    void dropSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS shipment_item_allocations, shipment_items, "
                    + "webhook_events, shipments, locations, storage_locations, sites CASCADE");
        }
    }

    private static String readMigrationFile(String fileName) throws IOException {
        try (InputStream in = ShipmentSiteBackfillIT.class.getClassLoader()
                .getResourceAsStream("db/migration/" + fileName)) {
            if (in == null) {
                throw new IllegalStateException(fileName + " not found on test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void runBackfill() throws SQLException, IOException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(readMigrationFile("V70__backfill_shipments_site_main.sql"));
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

    private UUID insertShipment() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO shipments DEFAULT VALUES RETURNING id")) {
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getObject("id", UUID.class);
        }
    }

    private UUID insertShipmentItem(UUID shipmentId, UUID destinationLocationId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO shipment_items (shipment_id, destination_location_id) VALUES (?, ?) RETURNING id")) {
            ps.setObject(1, shipmentId);
            ps.setObject(2, destinationLocationId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getObject("id", UUID.class);
        }
    }

    private UUID insertAllocation(UUID shipmentItemId, UUID locationId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO shipment_item_allocations (shipment_item_id, location_id) VALUES (?, ?) RETURNING id")) {
            ps.setObject(1, shipmentItemId);
            ps.setObject(2, locationId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getObject("id", UUID.class);
        }
    }

    private void insertWebhookEvent() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO webhook_events (event_id) VALUES ('evt-1')")) {
            ps.executeUpdate();
        }
    }

    private UUID siteIdFor(String table, UUID id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT site_id FROM " + table + " WHERE id = ?")) {
            ps.setObject(1, id);
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
        insertShipment();

        assertThatThrownBy(this::runBackfill)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("requires a MAIN site row to already exist");

        assertThat(scalarInt("SELECT COUNT(*) FROM shipments WHERE site_id IS NOT NULL")).isZero();
    }

    @Test
    void shipmentResolvesFromItsItemsDestinationLocationNotMain() throws SQLException, IOException {
        UUID main = insertSite("MAIN");
        UUID second = insertSite("SECOND");
        UUID secondStorage = insertStorageLocation(second);
        UUID secondLocation = insertLocation(secondStorage);

        UUID shipment = insertShipment();
        insertShipmentItem(shipment, secondLocation);

        runBackfill();

        assertThat(siteIdFor("shipments", shipment)).isEqualTo(second);
    }

    @Test
    void shipmentWithNoResolvableItemLocationFallsBackToMain() throws SQLException, IOException {
        UUID main = insertSite("MAIN");
        UUID shipment = insertShipment();
        insertShipmentItem(shipment, null);

        runBackfill();

        assertThat(siteIdFor("shipments", shipment)).isEqualTo(main);
    }

    @Test
    void everyItemInheritsTheParentShipmentsSiteEvenWhenItsOwnLocationDiffers() throws SQLException, IOException {
        // One shipment owns exactly one site (matches the application-layer invariant every new
        // write enforces). The shipment resolves from its earliest-created resolvable item
        // (mainItem, at a MAIN location); a later item whose own destination location resolves to
        // SECOND must still inherit the shipment's MAIN site, not its own location's.
        UUID main = insertSite("MAIN");
        UUID second = insertSite("SECOND");
        UUID mainStorage = insertStorageLocation(main);
        UUID mainLocation = insertLocation(mainStorage);
        UUID secondStorage = insertStorageLocation(second);
        UUID secondLocation = insertLocation(secondStorage);

        UUID shipment = insertShipment();
        insertShipmentItem(shipment, mainLocation);
        UUID itemWithDifferentLocationSite = insertShipmentItem(shipment, secondLocation);

        runBackfill();

        assertThat(siteIdFor("shipments", shipment)).isEqualTo(main);
        assertThat(siteIdFor("shipment_items", itemWithDifferentLocationSite)).isEqualTo(main);
    }

    @Test
    void everyAllocationInheritsItsParentItemsSiteEvenWhenItsOwnLocationDiffers() throws SQLException, IOException {
        UUID main = insertSite("MAIN");
        UUID second = insertSite("SECOND");
        UUID secondStorage = insertStorageLocation(second);
        UUID secondLocation = insertLocation(secondStorage);

        UUID shipment = insertShipment();
        UUID item = insertShipmentItem(shipment, null);
        UUID allocationWithDifferentLocationSite = insertAllocation(item, secondLocation);
        UUID allocationWithNoLocation = insertAllocation(item, null);

        runBackfill();

        assertThat(siteIdFor("shipment_item_allocations", allocationWithDifferentLocationSite)).isEqualTo(main);
        assertThat(siteIdFor("shipment_item_allocations", allocationWithNoLocation)).isEqualTo(main);
    }

    @Test
    void webhookEventsBackfillToMain() throws SQLException, IOException {
        UUID main = insertSite("MAIN");
        insertWebhookEvent();

        runBackfill();

        assertThat(scalarInt("SELECT COUNT(*) FROM webhook_events WHERE site_id = '" + main + "'")).isEqualTo(1);
    }

    @Test
    void isIdempotentOnRerunAndLeavesNoNullSiteIds() throws SQLException, IOException {
        UUID main = insertSite("MAIN");
        UUID second = insertSite("SECOND");
        UUID secondStorage = insertStorageLocation(second);
        UUID secondLocation = insertLocation(secondStorage);

        UUID shipment = insertShipment();
        UUID item = insertShipmentItem(shipment, secondLocation);
        insertAllocation(item, secondLocation);
        insertWebhookEvent();

        runBackfill();
        runBackfill();

        assertThat(scalarInt("SELECT COUNT(*) FROM shipments WHERE site_id IS NULL")).isZero();
        assertThat(scalarInt("SELECT COUNT(*) FROM shipment_items WHERE site_id IS NULL")).isZero();
        assertThat(scalarInt("SELECT COUNT(*) FROM shipment_item_allocations WHERE site_id IS NULL")).isZero();
        assertThat(scalarInt("SELECT COUNT(*) FROM webhook_events WHERE site_id IS NULL")).isZero();
    }
}
