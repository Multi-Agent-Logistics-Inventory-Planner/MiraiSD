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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-6c-3 (.specs/phase-6-inventory/log.md, P-2): executes V62__stock_movements_site_indexes.sql
 * itself against real PostgreSQL, same standalone-Testcontainers approach as
 * {@link StockMovementSiteMigrationIT} (Flyway never runs these files at runtime -- F-1) —
 * asserts both indexes exist with the right column order/direction, and that the scoped history
 * query ({@code site_id, item_id, at DESC} -- the shape
 * {@code StockMovementRepository.findByItem_IdAndSite_IdOrderByAtDesc} needs) actually gets
 * planned through {@code idx_stock_movements_site_item_at} rather than being cosmetically present
 * but unusable because of a column-order mismatch. {@code enable_seqscan=off} is used for the
 * plan assertion because the seeded row count here (a few hundred) is not by itself enough for a
 * real Postgres planner to prefer an index scan over a sequential scan -- matches the task list's
 * own "cheapest available guard" framing, not a claim about production planner behavior at scale.
 */
@Testcontainers
class StockMovementSiteIndexMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("mirai_stock_movements_index_test")
                    .withUsername("test")
                    .withPassword("test");

    private static Connection connection;

    @BeforeAll
    static void migrate() throws SQLException, IOException {
        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sites (id UUID PRIMARY KEY DEFAULT gen_random_uuid())");
            statement.execute("CREATE TABLE stock_movements (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
                    + "item_id UUID NOT NULL, from_location_id UUID, to_location_id UUID, "
                    + "at TIMESTAMPTZ NOT NULL DEFAULT now())");
            statement.execute(readMigrationFile("V59__add_site_id_to_stock_movements.sql"));
            // V62 has two CREATE INDEX CONCURRENTLY statements -- each must be its own simple-query
            // message (Postgres rejects CONCURRENTLY "within a pipeline" when both are sent as one
            // multi-statement batch, which a single Statement.execute() of the raw file text does).
            // Comment lines are stripped first so a semicolon inside prose can't be mistaken for a
            // statement boundary.
            for (String sql : sqlStatements("V62__stock_movements_site_indexes.sql")) {
                statement.execute(sql);
            }
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
        UUID siteId = UUID.randomUUID();
        try (PreparedStatement insertSite = connection.prepareStatement("INSERT INTO sites (id) VALUES (?)")) {
            insertSite.setObject(1, siteId);
            insertSite.executeUpdate();
        }
        UUID itemId = UUID.randomUUID();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO stock_movements (item_id, site_id, at) VALUES (?, ?, now() - (? || ' minutes')::interval)")) {
            for (int i = 0; i < 500; i++) {
                insert.setObject(1, itemId);
                insert.setObject(2, siteId);
                insert.setInt(3, i);
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ANALYZE stock_movements");
        }
    }

    private static java.util.List<String> sqlStatements(String fileName) throws IOException {
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
        try (InputStream in = StockMovementSiteIndexMigrationIT.class.getClassLoader()
                .getResourceAsStream("db/migration/" + fileName)) {
            if (in == null) {
                throw new IllegalStateException(fileName + " not found on test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void createsBothSiteIndexesWithExpectedColumnOrder() throws SQLException {
        Set<String> indexDefs = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'stock_movements'")) {
            while (rs.next()) {
                indexDefs.add(rs.getString("indexname") + " :: " + rs.getString("indexdef"));
            }
        }

        assertThat(indexDefs).anySatisfy(def -> assertThat(def)
                .startsWith("idx_stock_movements_site_at ::")
                .containsIgnoringCase("(site_id, at DESC)"));
        assertThat(indexDefs).anySatisfy(def -> assertThat(def)
                .startsWith("idx_stock_movements_site_item_at ::")
                .containsIgnoringCase("(site_id, item_id, at DESC)"));
    }

    @Test
    void scopedHistoryQueryPlansThroughTheSiteItemAtIndex() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET enable_seqscan = off");
        }
        String plan;
        try (PreparedStatement explain = connection.prepareStatement(
                "EXPLAIN SELECT * FROM stock_movements WHERE site_id = (SELECT id FROM sites LIMIT 1) "
                        + "AND item_id = (SELECT item_id FROM stock_movements LIMIT 1) ORDER BY at DESC")) {
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

        assertThat(plan).contains("idx_stock_movements_site_item_at");
    }
}
