package com.mirai.inventoryservice.catalog.infrastructure;

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
import java.math.BigDecimal;
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
 * Executes V57__backfill_site_products_main.sql itself against real PostgreSQL (.specs/
 * phase-5c-site-products AC-2/AC-3/AC-3b), the same standalone-Testcontainers approach
 * {@link SiteProductsMigrationIT} uses for V56 - no Spring context, minimal stub `sites`/
 * `products` prerequisite tables shaped like production's relevant columns, and the real
 * migration files executed verbatim. Schema is dropped and rebuilt per test so each test can set
 * up exactly the fixture (MAIN present/absent, SECOND present, product data) its assertion needs.
 */
@Testcontainers
class SiteProductBackfillIT {

    @Container
    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("mirai_backfill_test")
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
            statement.execute("DROP TABLE IF EXISTS site_products, products, sites CASCADE");
            statement.execute("CREATE TABLE sites (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "code VARCHAR(20) UNIQUE)");
            statement.execute("CREATE TABLE products (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "is_active BOOLEAN NOT NULL DEFAULT TRUE, "
                    + "forecasting_enabled BOOLEAN NOT NULL DEFAULT TRUE, "
                    + "unit_cost NUMERIC(10, 2), msrp NUMERIC(10, 2), reorder_point INTEGER, "
                    + "target_stock_level INTEGER, lead_time_days INTEGER)");
            statement.execute(readMigrationFile("V56__create_site_products.sql"));
        }
    }

    @AfterEach
    void dropSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS site_products, products, sites CASCADE");
        }
    }

    private static String readMigrationFile(String fileName) throws IOException {
        try (InputStream in = SiteProductBackfillIT.class.getClassLoader()
                .getResourceAsStream("db/migration/" + fileName)) {
            if (in == null) {
                throw new IllegalStateException(fileName + " not found on test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void runBackfill() throws SQLException, IOException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(readMigrationFile("V57__backfill_site_products_main.sql"));
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

    private UUID insertProduct(boolean isActive, boolean forecastingEnabled) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO products (is_active, forecasting_enabled, reorder_point, "
                        + "target_stock_level, lead_time_days, unit_cost, msrp) "
                        + "VALUES (?, ?, 10, 50, 14, 1.23, 4.56) RETURNING id")) {
            ps.setBoolean(1, isActive);
            ps.setBoolean(2, forecastingEnabled);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getObject("id", UUID.class);
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
        insertProduct(true, true);

        assertThatThrownBy(this::runBackfill)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("requires a MAIN site row to already exist");

        assertThat(scalarInt("SELECT COUNT(*) FROM site_products")).isZero();
    }

    @Test
    void backfillsExactlyOneRowPerProductForMainAndZeroRowsForSecond() throws SQLException, IOException {
        UUID main = insertSite("MAIN");
        insertSite("SECOND");
        insertProduct(true, true);
        insertProduct(false, true);
        insertProduct(true, false);

        runBackfill();

        assertThat(scalarInt("SELECT COUNT(*) FROM site_products")).isEqualTo(3);
        assertThat(scalarInt("SELECT COUNT(*) FROM site_products WHERE site_id = '" + main + "'")).isEqualTo(3);
        assertThat(scalarInt("SELECT COUNT(*) FROM site_products sp JOIN sites s ON s.id = sp.site_id "
                + "WHERE s.code = 'SECOND'")).isZero();

        // AC-3: zero orphaned product_ids, zero duplicate (site_id, product_id) pairs.
        assertThat(scalarInt("SELECT COUNT(*) FROM site_products sp "
                + "LEFT JOIN products p ON p.id = sp.product_id WHERE p.id IS NULL")).isZero();
        assertThat(scalarInt("SELECT COUNT(*) FROM ("
                + "SELECT site_id, product_id FROM site_products GROUP BY site_id, product_id "
                + "HAVING COUNT(*) > 1) dup")).isZero();

        // AC-2: every nullable override column is left NULL, never copied from products.
        assertThat(scalarInt("SELECT COUNT(*) FROM site_products WHERE "
                + "unit_cost IS NOT NULL OR msrp IS NOT NULL OR reorder_point IS NOT NULL "
                + "OR target_stock_level IS NOT NULL OR lead_time_days IS NOT NULL")).isZero();
    }

    @Test
    void seedsIsStockedAndForecastingEnabledFromProductsBooleans() throws SQLException, IOException {
        insertSite("MAIN");
        UUID activeForecasting = insertProduct(true, true);
        UUID inactiveForecasting = insertProduct(false, true);
        UUID activeNoForecasting = insertProduct(true, false);
        UUID inactiveNoForecasting = insertProduct(false, false);

        runBackfill();

        assertThat(isStockedFor(activeForecasting)).isTrue();
        assertThat(forecastingEnabledFor(activeForecasting)).isTrue();
        assertThat(isStockedFor(inactiveForecasting)).isFalse();
        assertThat(forecastingEnabledFor(inactiveForecasting)).isTrue();
        assertThat(isStockedFor(activeNoForecasting)).isTrue();
        assertThat(forecastingEnabledFor(activeNoForecasting)).isFalse();
        assertThat(isStockedFor(inactiveNoForecasting)).isFalse();
        assertThat(forecastingEnabledFor(inactiveNoForecasting)).isFalse();
    }

    private boolean isStockedFor(UUID productId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT is_stocked FROM site_products WHERE product_id = ?")) {
            ps.setObject(1, productId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getBoolean(1);
        }
    }

    private boolean forecastingEnabledFor(UUID productId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT forecasting_enabled FROM site_products WHERE product_id = ?")) {
            ps.setObject(1, productId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getBoolean(1);
        }
    }

    /** AC-3: effective-value equality - COALESCE(site_products.X, products.X) must equal the pre-migration products.X. */
    @Test
    void effectiveValueAfterBackfillEqualsThePreMigrationGlobalValueForEveryOverrideColumn() throws SQLException, IOException {
        insertSite("MAIN");
        UUID product = insertProduct(true, true);

        runBackfill();

        String sql = "SELECT COALESCE(sp.reorder_point, p.reorder_point) AS eff_reorder, "
                + "COALESCE(sp.target_stock_level, p.target_stock_level) AS eff_target, "
                + "COALESCE(sp.lead_time_days, p.lead_time_days) AS eff_lead, "
                + "COALESCE(sp.unit_cost, p.unit_cost) AS eff_cost, "
                + "COALESCE(sp.msrp, p.msrp) AS eff_msrp "
                + "FROM products p JOIN site_products sp ON sp.product_id = p.id WHERE p.id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, product);
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt("eff_reorder")).isEqualTo(10);
            assertThat(rs.getInt("eff_target")).isEqualTo(50);
            assertThat(rs.getInt("eff_lead")).isEqualTo(14);
            assertThat(rs.getBigDecimal("eff_cost")).isEqualByComparingTo(new BigDecimal("1.23"));
            assertThat(rs.getBigDecimal("eff_msrp")).isEqualByComparingTo(new BigDecimal("4.56"));
        }
    }

    private Integer effectiveReorderPointFor(UUID productId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(sp.reorder_point, p.reorder_point) FROM products p "
                        + "JOIN site_products sp ON sp.product_id = p.id WHERE p.id = ?")) {
            ps.setObject(1, productId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return (Integer) rs.getObject(1);
        }
    }

    /** AC-3b(a): with the override left NULL by backfill, a global (forecasting-nightly) write keeps flowing through. */
    @Test
    void inheritedEffectiveValueStaysLiveAfterBackfillWhenNoOverrideIsSet() throws SQLException, IOException {
        insertSite("MAIN");
        UUID product = insertProduct(true, true);
        runBackfill();

        assertThat(effectiveReorderPointFor(product)).isEqualTo(10);

        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE products SET reorder_point = 25 WHERE id = ?")) {
            ps.setObject(1, product);
            ps.executeUpdate();
        }

        assertThat(effectiveReorderPointFor(product)).isEqualTo(25);
    }

    /** AC-3b(b): once an explicit override exists, a later global write no longer changes the effective value. */
    @Test
    void explicitOverrideStopsFollowingLaterGlobalWrites() throws SQLException, IOException {
        insertSite("MAIN");
        UUID product = insertProduct(true, true);
        runBackfill();

        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE site_products SET reorder_point = 99 WHERE product_id = ?")) {
            ps.setObject(1, product);
            ps.executeUpdate();
        }
        assertThat(effectiveReorderPointFor(product)).isEqualTo(99);

        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE products SET reorder_point = 25 WHERE id = ?")) {
            ps.setObject(1, product);
            ps.executeUpdate();
        }

        assertThat(effectiveReorderPointFor(product)).isEqualTo(99);
    }
}
