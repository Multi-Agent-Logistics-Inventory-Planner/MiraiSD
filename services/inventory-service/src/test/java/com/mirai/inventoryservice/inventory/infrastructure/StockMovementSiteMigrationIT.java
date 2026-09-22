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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes V59__add_site_id_to_stock_movements.sql itself against real PostgreSQL (.specs/
 * phase-6-inventory 6b, AC-2), the same standalone-Testcontainers approach
 * {@code SiteProductsMigrationIT} uses for V56 - no Spring context, minimal stub prerequisite
 * tables shaped like production's relevant columns, and the real migration file executed
 * verbatim. Only V59 (expand) is exercised here: V61 (constrain - NOT NULL/FK/indexes) is
 * deliberately not part of this build pass (.specs/phase-6-inventory/log.md 6b worksheet - it
 * ships as its own PR/record once the writer release below has been deployed and verified), so
 * there is nothing to constrain yet in this test.
 */
@Testcontainers
class StockMovementSiteMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("mirai_stock_movements_migration_test")
                    .withUsername("test")
                    .withPassword("test");

    private static Connection connection;

    @BeforeAll
    static void migrate() throws SQLException, IOException {
        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sites (id UUID PRIMARY KEY DEFAULT gen_random_uuid())");
            statement.execute("CREATE TABLE stock_movements (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
                    + "from_location_id UUID, to_location_id UUID)");
            statement.execute(readMigrationFile("V59__add_site_id_to_stock_movements.sql"));
        }
    }

    @AfterAll
    static void closeConnection() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    private static String readMigrationFile(String fileName) throws IOException {
        try (InputStream in = StockMovementSiteMigrationIT.class.getClassLoader()
                .getResourceAsStream("db/migration/" + fileName)) {
            if (in == null) {
                throw new IllegalStateException(fileName + " not found on test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void addsSiteIdAsNullableUuidWithNoDefault() throws SQLException {
        Map<String, String[]> columns = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT column_name, is_nullable, column_default, data_type "
                             + "FROM information_schema.columns WHERE table_name = 'stock_movements'")) {
            while (rs.next()) {
                columns.put(rs.getString("column_name"),
                        new String[]{rs.getString("is_nullable"), rs.getString("column_default"), rs.getString("data_type")});
            }
        }

        assertThat(columns).containsKey("site_id");
        assertThat(columns.get("site_id")[0]).isEqualTo("YES");
        assertThat(columns.get("site_id")[1]).isNull();
        assertThat(columns.get("site_id")[2]).isEqualTo("uuid");
    }

    @Test
    void siteIdHasNoForeignKeyYet() throws SQLException {
        int fkCount;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.table_constraints "
                             + "WHERE table_name = 'stock_movements' AND constraint_type = 'FOREIGN KEY'")) {
            rs.next();
            fkCount = rs.getInt(1);
        }
        assertThat(fkCount).isZero();
    }
}
