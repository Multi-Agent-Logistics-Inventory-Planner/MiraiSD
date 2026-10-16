package com.mirai.inventoryservice.shipments.infrastructure;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes V69 then V71__shipments_site_indexes.sql against real PostgreSQL (.specs/
 * phase-7-shipments T-3), the same standalone-Testcontainers approach
 * {@code StockMovementSiteIndexMigrationIT} uses for V62 -- CONCURRENTLY statements must each be
 * their own simple-query message, so the file is split on statement boundaries after stripping
 * comment lines rather than executed as one batch.
 */
@Testcontainers
class ShipmentSiteIndexMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("mirai_shipments_index_test")
                    .withUsername("test")
                    .withPassword("test");

    private static Connection connection;

    @BeforeAll
    static void migrate() throws SQLException, IOException {
        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE shipments (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "status VARCHAR(30) NOT NULL DEFAULT 'PENDING')");
            statement.execute("CREATE TABLE shipment_items (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "shipment_id UUID NOT NULL REFERENCES shipments(id))");
            statement.execute("CREATE TABLE shipment_item_allocations (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "shipment_item_id UUID NOT NULL REFERENCES shipment_items(id))");
            statement.execute("CREATE TABLE webhook_events (id UUID PRIMARY KEY DEFAULT gen_random_uuid())");
            statement.execute(readMigrationFile("V69__add_site_id_to_shipments.sql"));
            for (String sql : sqlStatements("V71__shipments_site_indexes.sql")) {
                statement.execute(sql);
            }
        }
    }

    @AfterAll
    static void closeConnection() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    private static List<String> sqlStatements(String fileName) throws IOException {
        String withoutComments = readMigrationFile(fileName).lines()
                .filter(line -> !line.strip().startsWith("--"))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        return java.util.Arrays.stream(withoutComments.split(";"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String readMigrationFile(String fileName) throws IOException {
        try (InputStream in = ShipmentSiteIndexMigrationIT.class.getClassLoader()
                .getResourceAsStream("db/migration/" + fileName)) {
            if (in == null) {
                throw new IllegalStateException(fileName + " not found on test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void createsAllThreeSiteIndexes() throws SQLException {
        Set<String> indexNames = new HashSet<>();
        for (String table : List.of("shipments", "shipment_items", "shipment_item_allocations")) {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "SELECT indexname FROM pg_indexes WHERE tablename = '" + table + "'")) {
                while (rs.next()) {
                    indexNames.add(rs.getString("indexname"));
                }
            }
        }

        assertThat(indexNames).contains(
                "idx_shipments_site_status",
                "idx_shipment_items_site",
                "idx_shipment_item_allocations_site");
    }
}
