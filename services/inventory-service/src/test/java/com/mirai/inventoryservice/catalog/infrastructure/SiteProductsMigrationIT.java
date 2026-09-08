package com.mirai.inventoryservice.catalog.infrastructure;

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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Executes V56__create_site_products.sql itself against real PostgreSQL (.specs/
 * phase-5c-site-products AC-1). {@link SiteProductRepositoryIT} proves entity/repository
 * behavior against a Hibernate {@code ddl-auto=create-drop} schema with hand-added foreign
 * keys, which never runs the migration file and would not catch it being broken or absent. This
 * class runs no Spring context and shares no schema with any other test - it is a standalone
 * Testcontainers Postgres with only the minimal prerequisite tables (`sites`, `products`) the
 * migration's foreign keys reference, so it can prove the actual shipped SQL creates the exact
 * columns, defaults, constraints, and indexes AC-1 requires.
 */
@Testcontainers
class SiteProductsMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("mirai_migration_test")
                    .withUsername("test")
                    .withPassword("test");

    private static Connection connection;

    @BeforeAll
    static void migrate() throws SQLException, IOException {
        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sites (id UUID PRIMARY KEY DEFAULT gen_random_uuid())");
            statement.execute("CREATE TABLE products (id UUID PRIMARY KEY DEFAULT gen_random_uuid())");
            statement.execute(readMigrationFile());
        }
    }

    @AfterAll
    static void closeConnection() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    private static String readMigrationFile() throws IOException {
        try (InputStream in = SiteProductsMigrationIT.class.getClassLoader()
                .getResourceAsStream("db/migration/V56__create_site_products.sql")) {
            if (in == null) {
                throw new IllegalStateException("V56__create_site_products.sql not found on test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static UUID insertSite() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO sites (id) VALUES (gen_random_uuid()) RETURNING id")) {
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getObject("id", UUID.class);
        }
    }

    private static UUID insertProduct() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO products (id) VALUES (gen_random_uuid()) RETURNING id")) {
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getObject("id", UUID.class);
        }
    }

    private static void insertSiteProduct(UUID siteId, UUID productId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO site_products (site_id, product_id) VALUES (?, ?)")) {
            ps.setObject(1, siteId);
            ps.setObject(2, productId);
            ps.executeUpdate();
        }
    }

    @Test
    void migrationCreatesColumnsWithSpecifiedNullabilityAndDefaults() throws SQLException {
        Map<String, String[]> columns = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT column_name, is_nullable, column_default FROM information_schema.columns "
                        + "WHERE table_name = 'site_products'")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                columns.put(rs.getString("column_name"),
                        new String[]{rs.getString("is_nullable"), rs.getString("column_default")});
            }
        }

        assertThat(columns).containsKeys("id", "site_id", "product_id", "is_stocked",
                "forecasting_enabled", "unit_cost", "msrp", "reorder_point", "target_stock_level",
                "lead_time_days", "version", "created_at", "updated_at");

        assertThat(columns.get("is_stocked")[0]).isEqualTo("NO");
        assertThat(columns.get("is_stocked")[1]).contains("false");
        assertThat(columns.get("forecasting_enabled")[0]).isEqualTo("NO");
        assertThat(columns.get("forecasting_enabled")[1]).contains("true");
        assertThat(columns.get("version")[0]).isEqualTo("NO");
        assertThat(columns.get("version")[1]).contains("0");
        assertThat(columns.get("created_at")[0]).isEqualTo("NO");
        assertThat(columns.get("updated_at")[0]).isEqualTo("NO");
        assertThat(columns.get("site_id")[0]).isEqualTo("NO");
        assertThat(columns.get("product_id")[0]).isEqualTo("NO");

        // AC-1/AC-2: every nullable override column must actually be nullable, not just absent
        // a NOT NULL clause by coincidence - this is what makes NULL-preserves-inheritance possible.
        assertThat(columns.get("unit_cost")[0]).isEqualTo("YES");
        assertThat(columns.get("msrp")[0]).isEqualTo("YES");
        assertThat(columns.get("reorder_point")[0]).isEqualTo("YES");
        assertThat(columns.get("target_stock_level")[0]).isEqualTo("YES");
        assertThat(columns.get("lead_time_days")[0]).isEqualTo("YES");
    }

    @Test
    void migrationCreatesTheExpectedIndexes() throws SQLException {
        Map<String, String> indexes = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'site_products'")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                indexes.put(rs.getString("indexname"), rs.getString("indexdef"));
            }
        }

        assertThat(indexes).containsKey("idx_site_products_site");
        assertThat(indexes).containsKey("idx_site_products_site_stocked");
        assertThat(indexes).containsKey("idx_site_products_product");
        assertThat(indexes.get("idx_site_products_site")).contains("(site_id)");
        assertThat(indexes.get("idx_site_products_site_stocked")).contains("site_id").contains("is_stocked");
    }

    @Test
    void migrationRejectsADuplicateSiteProductPairViaTheRealUniqueConstraint() throws SQLException {
        UUID site = insertSite();
        UUID product = insertProduct();
        insertSiteProduct(site, product);

        assertThatThrownBy(() -> insertSiteProduct(site, product))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("duplicate key");
    }

    @Test
    void migrationCascadesDeleteFromProductsButRestrictsDeleteFromSites() throws SQLException {
        UUID site = insertSite();
        UUID product = insertProduct();
        insertSiteProduct(site, product);

        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute("DELETE FROM sites WHERE id = '" + site + "'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("violates foreign key constraint");

            statement.execute("DELETE FROM products WHERE id = '" + product + "'");
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM site_products WHERE product_id = ?")) {
            ps.setObject(1, product);
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }
}
