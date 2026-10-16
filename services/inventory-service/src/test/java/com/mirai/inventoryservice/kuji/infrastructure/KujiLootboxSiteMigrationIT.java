package com.mirai.inventoryservice.kuji.infrastructure;

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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Executes V75-V80 (.specs/phase-7-kuji-lootbox T-2/T-3/T-5) themselves against real PostgreSQL,
 * the same standalone-Testcontainers approach as {@code SiteIntegrityMigrationIT} - no Spring
 * context, a minimal stub schema shaped like the columns each migration actually touches, and
 * the real migration files executed verbatim.
 */
@Testcontainers
class KujiLootboxSiteMigrationIT {

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
            s.execute("CREATE TABLE machine_display (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), site_id UUID)");
            s.execute("CREATE TABLE users (id UUID PRIMARY KEY DEFAULT gen_random_uuid())");
            s.execute("CREATE TABLE products (id UUID PRIMARY KEY DEFAULT gen_random_uuid())");

            s.execute("CREATE TABLE kuji_boxes (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "product_id UUID NOT NULL REFERENCES products(id), location_id UUID NOT NULL REFERENCES locations(id), "
                    + "machine_display_id UUID REFERENCES machine_display(id), status TEXT NOT NULL DEFAULT 'OPEN')");
            s.execute("CREATE TABLE kuji_box_tiers (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "box_id UUID NOT NULL REFERENCES kuji_boxes(id) ON DELETE CASCADE)");

            s.execute("CREATE TABLE lootboxes (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "active BOOLEAN NOT NULL DEFAULT true, starts_at TIMESTAMPTZ, ends_at TIMESTAMPTZ, site_id UUID)");
            s.execute("CREATE TABLE lootbox_tiers (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "lootbox_id UUID NOT NULL REFERENCES lootboxes(id))");
            s.execute("CREATE TABLE lootbox_prizes (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "tier_id UUID NOT NULL REFERENCES lootbox_tiers(id) ON DELETE CASCADE)");
            s.execute("CREATE TABLE lootbox_plays (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "user_id UUID NOT NULL REFERENCES users(id), lootbox_id UUID REFERENCES lootboxes(id), "
                    + "status TEXT NOT NULL DEFAULT 'WON', idempotency_key TEXT, "
                    + "played_at TIMESTAMPTZ NOT NULL DEFAULT now())");
            s.execute("CREATE TABLE coin_adjustments (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "user_id UUID NOT NULL REFERENCES users(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), "
                    + "expires_at TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '90 days'))");
            s.execute("CREATE TABLE review_daily_counts (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "user_id UUID NOT NULL REFERENCES users(id), date DATE NOT NULL, "
                    + "expires_at DATE NOT NULL DEFAULT (current_date + 90), "
                    + "CONSTRAINT uq_review_daily_counts_user_date UNIQUE (user_id, date))");
            s.execute("CREATE UNIQUE INDEX ux_lootbox_plays_user_idem ON lootbox_plays(user_id, idempotency_key) "
                    + "WHERE idempotency_key IS NOT NULL");
            s.execute("CREATE UNIQUE INDEX uniq_kuji_boxes_open_per_product ON kuji_boxes (product_id) "
                    + "WHERE status = 'OPEN'");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("DROP TABLE IF EXISTS review_daily_counts, coin_adjustments, lootbox_plays, lootbox_prizes, "
                    + "lootbox_tiers, lootboxes, kuji_box_tiers, kuji_boxes, products, users, machine_display, "
                    + "locations, storage_locations, sites CASCADE");
            s.execute("DROP FUNCTION IF EXISTS check_kuji_box_site_consistency() CASCADE");
        }
    }

    @Test
    void backfillsEveryTableToMainAndPreservesRowCounts() throws Exception {
        UUID main = site("MAIN");
        UUID second = site("SECOND");
        UUID secondStorage = uuid("INSERT INTO storage_locations (site_id) VALUES ('" + second + "') RETURNING id");
        UUID secondLocation = uuid("INSERT INTO locations (storage_location_id) VALUES ('" + secondStorage + "') RETURNING id");
        UUID product = uuid("INSERT INTO products DEFAULT VALUES RETURNING id");
        UUID user = uuid("INSERT INTO users DEFAULT VALUES RETURNING id");

        UUID boxAtSecond = uuid("INSERT INTO kuji_boxes (product_id, location_id) VALUES ('" + product + "', '" + secondLocation + "') RETURNING id");
        exec("INSERT INTO kuji_box_tiers (box_id) VALUES ('" + boxAtSecond + "')");

        UUID danglingCrate = uuid("INSERT INTO lootboxes (site_id) VALUES ('" + UUID.randomUUID() + "') RETURNING id");
        UUID nullSiteCrate = uuid("INSERT INTO lootboxes (site_id) VALUES (NULL) RETURNING id");
        UUID tier = uuid("INSERT INTO lootbox_tiers (lootbox_id) VALUES ('" + nullSiteCrate + "') RETURNING id");
        exec("INSERT INTO lootbox_prizes (tier_id) VALUES ('" + tier + "')");
        exec("INSERT INTO lootbox_plays (user_id, lootbox_id) VALUES ('" + user + "', '" + nullSiteCrate + "')");
        exec("INSERT INTO coin_adjustments (user_id) VALUES ('" + user + "')");
        exec("INSERT INTO review_daily_counts (user_id, date) VALUES ('" + user + "', CURRENT_DATE)");

        migrate("V75__add_site_id_to_kuji_lootbox_and_coin_ledger.sql");
        migrate("V76__backfill_kuji_lootbox_site_main.sql");

        assertThat(count("SELECT COUNT(*) FROM kuji_boxes")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM kuji_boxes WHERE site_id = '" + second + "'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM kuji_box_tiers WHERE site_id = '" + second + "'")).isEqualTo(1);

        assertThat(count("SELECT COUNT(*) FROM lootboxes WHERE site_id = '" + main + "'")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM lootbox_tiers WHERE site_id = '" + main + "'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM lootbox_prizes WHERE site_id = '" + main + "'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM lootbox_plays WHERE site_id = '" + main + "'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM coin_adjustments WHERE site_id = '" + main + "'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM review_daily_counts WHERE site_id = '" + main + "'")).isEqualTo(1);
    }

    @Test
    void v76FailsLoudlyWithoutAMainSite() throws Exception {
        site("SECOND");
        migrate("V75__add_site_id_to_kuji_lootbox_and_coin_ledger.sql");
        assertThatThrownBy(() -> migrate("V76__backfill_kuji_lootbox_site_main.sql"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("requires a MAIN site row");
    }

    @Test
    void constrainRejectsCrossSiteChildrenAndFixesLootboxPlaysNullability() throws Exception {
        UUID main = site("MAIN");
        UUID second = site("SECOND");
        UUID mainStorage = uuid("INSERT INTO storage_locations (site_id) VALUES ('" + main + "') RETURNING id");
        UUID mainLocation = uuid("INSERT INTO locations (storage_location_id) VALUES ('" + mainStorage + "') RETURNING id");
        UUID product = uuid("INSERT INTO products DEFAULT VALUES RETURNING id");
        UUID box = uuid("INSERT INTO kuji_boxes (product_id, location_id) VALUES ('" + product + "', '" + mainLocation + "') RETURNING id");
        UUID crate = uuid("INSERT INTO lootboxes (site_id) VALUES ('" + main + "') RETURNING id");
        UUID tier = uuid("INSERT INTO lootbox_tiers (lootbox_id) VALUES ('" + crate + "') RETURNING id");
        UUID secondCrate = uuid("INSERT INTO lootboxes (site_id) VALUES ('" + second + "') RETURNING id");

        migrate("V75__add_site_id_to_kuji_lootbox_and_coin_ledger.sql");
        migrate("V76__backfill_kuji_lootbox_site_main.sql");
        migrateStatements("V77__kuji_lootbox_site_indexes.sql");
        migrate("V78__constrain_kuji_lootbox_catalog_site.sql");
        migrate("V79__constrain_coin_ledger_site.sql");

        // NOT NULL now enforced.
        assertThatThrownBy(() -> exec("INSERT INTO kuji_boxes (product_id, location_id, site_id) VALUES "
                + "('" + product + "', '" + mainLocation + "', NULL)"))
                .isInstanceOf(SQLException.class);

        // A tier can't be created at a different site than its parent box.
        assertThatThrownBy(() -> exec("INSERT INTO kuji_box_tiers (box_id, site_id) VALUES ('" + box + "', '" + second + "')"))
                .isInstanceOf(SQLException.class);

        // A prize can't be repointed to a tier belonging to a different site (T-2's
        // updatePrize/updateTier gap, closed at the database layer).
        UUID otherSiteTier = uuid("INSERT INTO lootbox_tiers (lootbox_id, site_id) VALUES "
                + "('" + secondCrate + "', '" + second + "') RETURNING id");
        UUID prize = uuid("INSERT INTO lootbox_prizes (tier_id, site_id) VALUES ('" + tier + "', '" + main + "') RETURNING id");
        assertThatThrownBy(() -> exec("UPDATE lootbox_prizes SET tier_id = '" + otherSiteTier + "' WHERE id = '" + prize + "'"))
                .isInstanceOf(SQLException.class);

        // The kuji_boxes site-consistency trigger rejects a box whose site_id doesn't match its
        // location's actual site.
        assertThatThrownBy(() -> exec("UPDATE kuji_boxes SET site_id = '" + second + "' WHERE id = '" + box + "'"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("does not match location");

        // lootbox_plays.lootbox_id is NOT NULL after V79 (pre-flight confirmed zero NULLs in prod).
        UUID user = uuid("INSERT INTO users DEFAULT VALUES RETURNING id");
        assertThatThrownBy(() -> exec("INSERT INTO lootbox_plays (user_id, lootbox_id, site_id) VALUES "
                + "('" + user + "', NULL, '" + main + "')"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void v80ReplacesGlobalOpenBoxUniquenessWithPerSite() throws Exception {
        UUID main = site("MAIN");
        UUID second = site("SECOND");
        UUID mainStorage = uuid("INSERT INTO storage_locations (site_id) VALUES ('" + main + "') RETURNING id");
        UUID mainLocation = uuid("INSERT INTO locations (storage_location_id) VALUES ('" + mainStorage + "') RETURNING id");
        UUID secondStorage = uuid("INSERT INTO storage_locations (site_id) VALUES ('" + second + "') RETURNING id");
        UUID secondLocation = uuid("INSERT INTO locations (storage_location_id) VALUES ('" + secondStorage + "') RETURNING id");
        UUID product = uuid("INSERT INTO products DEFAULT VALUES RETURNING id");

        migrate("V75__add_site_id_to_kuji_lootbox_and_coin_ledger.sql");
        migrate("V76__backfill_kuji_lootbox_site_main.sql");
        migrateStatements("V77__kuji_lootbox_site_indexes.sql");
        migrate("V78__constrain_kuji_lootbox_catalog_site.sql");
        migrate("V79__constrain_coin_ledger_site.sql");
        migrateStatements("V80__drop_global_kuji_open_box_uniqueness.sql");

        // Two sites can each have an OPEN box for the same product now that the constraint is
        // per-site instead of global.
        exec("INSERT INTO kuji_boxes (product_id, location_id, site_id) VALUES "
                + "('" + product + "', '" + mainLocation + "', '" + main + "')");
        exec("INSERT INTO kuji_boxes (product_id, location_id, site_id) VALUES "
                + "('" + product + "', '" + secondLocation + "', '" + second + "')");
        assertThat(count("SELECT COUNT(*) FROM kuji_boxes WHERE product_id = '" + product
                + "' AND status = 'OPEN'")).isEqualTo(2);

        // A second OPEN box for the same product at the same site is still rejected.
        assertThatThrownBy(() -> exec("INSERT INTO kuji_boxes (product_id, location_id, site_id) VALUES "
                + "('" + product + "', '" + mainLocation + "', '" + main + "')"))
                .isInstanceOf(SQLException.class);
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
        try (InputStream in = KujiLootboxSiteMigrationIT.class.getClassLoader()
                .getResourceAsStream("db/migration/" + name)) {
            if (in == null) throw new IllegalStateException(name + " not found on test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
