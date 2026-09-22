package com.mirai.inventoryservice.sites.infrastructure;

import com.mirai.inventoryservice.sites.api.LocationWithCountsDTO;
import com.mirai.inventoryservice.utils.TimestampUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for fetching all locations with their inventory counts in a single query.
 * Uses the unified locations and location_inventory tables.
 */
@Repository
public class LocationAggregateRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Aggregate location_inventory rows per location, excluding child products and CUSTOM
     * kuji parents. Matches LocationInventoryRepository.findByLocation_Id so rack-card
     * counts agree with the rack-dialog contents.
     */
    private static final String INVENTORY_SUBQUERY = """
        SELECT li.location_id,
               COUNT(*) FILTER (WHERE li.quantity > 0) as inventory_records,
               COALESCE(SUM(li.quantity), 0) as total_quantity
        FROM location_inventory li
        JOIN products p ON p.id = li.product_id
        WHERE p.parent_id IS NULL
          AND p.kuji_type IS DISTINCT FROM 'CUSTOM'
        GROUP BY li.location_id
        """;

    /**
     * Site-scoped counterpart to {@link #INVENTORY_SUBQUERY} (.specs/phase-6-inventory 6e,
     * T-6e-be-2): filters {@code location_inventory} rows to the caller's site before
     * aggregation, both enforcing the tenant boundary (a row whose site disagrees with its
     * location's site is excluded rather than silently counted) and letting Postgres use the
     * {@code idx_location_inventory_site_product} (site_id, product_id) index instead of
     * aggregating every site's rows.
     */
    private static final String SITE_SCOPED_INVENTORY_SUBQUERY = """
        SELECT li.location_id,
               COUNT(*) FILTER (WHERE li.quantity > 0) as inventory_records,
               COALESCE(SUM(li.quantity), 0) as total_quantity
        FROM location_inventory li
        JOIN products p ON p.id = li.product_id
        WHERE p.parent_id IS NULL
          AND p.kuji_type IS DISTINCT FROM 'CUSTOM'
          AND li.site_id = :siteId
        GROUP BY li.location_id
        """;

    /**
     * Query using unified locations and storage_locations tables.
     * Excludes NOT_ASSIGNED locations from the listing.
     */
    private static final String ALL_LOCATIONS_WITH_COUNTS_SQL = """
        SELECT
            l.id,
            sl.code as location_type,
            l.location_code,
            CASE
                WHEN sl.is_display_only
                THEN COALESCE(d.active_display_count, 0)
                ELSE COALESCE(i.inventory_records, 0)
            END as inventory_records,
            CASE
                WHEN sl.is_display_only
                THEN COALESCE(d.active_display_count, 0)
                ELSE COALESCE(i.total_quantity, 0)
            END as total_quantity,
            l.created_at,
            l.updated_at,
            COALESCE(d.active_display_count, 0) as active_display_count,
            COALESCE(d.active_display_count, 0) > 0 as has_active_display
        FROM locations l
        JOIN storage_locations sl ON l.storage_location_id = sl.id
        LEFT JOIN (
            """ + INVENTORY_SUBQUERY + """
        ) i ON l.id = i.location_id
        LEFT JOIN (
            SELECT machine_id as location_id,
                   COUNT(*) as active_display_count
            FROM machine_display
            WHERE ended_at IS NULL
            GROUP BY machine_id
        ) d ON l.id = d.location_id
        WHERE sl.code != 'NOT_ASSIGNED'
        ORDER BY sl.display_order, l.location_code
        """;

    /**
     * Site-scoped counterpart to {@link #ALL_LOCATIONS_WITH_COUNTS_SQL} (.specs/phase-6-inventory
     * 6e, T-6e-be-2). Without this, the untyped listing had no site predicate at all -- a
     * cross-site data leak, not merely a missing scope (docs/specs/multi-site-data-and-api.md's
     * "cross-site joins are prohibited").
     */
    private static final String SITE_SCOPED_ALL_LOCATIONS_WITH_COUNTS_SQL = """
        SELECT
            l.id,
            sl.code as location_type,
            l.location_code,
            CASE
                WHEN sl.is_display_only
                THEN COALESCE(d.active_display_count, 0)
                ELSE COALESCE(i.inventory_records, 0)
            END as inventory_records,
            CASE
                WHEN sl.is_display_only
                THEN COALESCE(d.active_display_count, 0)
                ELSE COALESCE(i.total_quantity, 0)
            END as total_quantity,
            l.created_at,
            l.updated_at,
            COALESCE(d.active_display_count, 0) as active_display_count,
            COALESCE(d.active_display_count, 0) > 0 as has_active_display
        FROM locations l
        JOIN storage_locations sl ON l.storage_location_id = sl.id
        LEFT JOIN (
            """ + SITE_SCOPED_INVENTORY_SUBQUERY + """
        ) i ON l.id = i.location_id
        LEFT JOIN (
            SELECT machine_id as location_id,
                   COUNT(*) as active_display_count
            FROM machine_display
            WHERE ended_at IS NULL
            GROUP BY machine_id
        ) d ON l.id = d.location_id
        WHERE sl.code != 'NOT_ASSIGNED'
          AND sl.site_id = :siteId
        ORDER BY sl.display_order, l.location_code
        """;

    /**
     * Query for filtering locations by storage location type.
     * Uses the unified locations and storage_locations tables.
     */
    private static final String LOCATIONS_BY_TYPE_WITH_COUNTS_SQL = """
        SELECT
            l.id,
            sl.code as location_type,
            l.location_code,
            CASE
                WHEN sl.is_display_only
                THEN COALESCE(d.active_display_count, 0)
                ELSE COALESCE(i.inventory_records, 0)
            END as inventory_records,
            CASE
                WHEN sl.is_display_only
                THEN COALESCE(d.active_display_count, 0)
                ELSE COALESCE(i.total_quantity, 0)
            END as total_quantity,
            l.created_at,
            l.updated_at,
            COALESCE(d.active_display_count, 0) as active_display_count,
            COALESCE(d.active_display_count, 0) > 0 as has_active_display
        FROM locations l
        JOIN storage_locations sl ON l.storage_location_id = sl.id
        LEFT JOIN (
            """ + INVENTORY_SUBQUERY + """
        ) i ON l.id = i.location_id
        LEFT JOIN (
            SELECT machine_id as location_id,
                   COUNT(*) as active_display_count
            FROM machine_display
            WHERE ended_at IS NULL
            GROUP BY machine_id
        ) d ON l.id = d.location_id
        WHERE sl.code = :locationType
        ORDER BY l.location_code
        """;

    /**
     * Site-scoped counterpart to {@link #LOCATIONS_BY_TYPE_WITH_COUNTS_SQL}
     * (.specs/phase-6-inventory 6e, T-6e-be-2).
     */
    private static final String SITE_SCOPED_LOCATIONS_BY_TYPE_WITH_COUNTS_SQL = """
        SELECT
            l.id,
            sl.code as location_type,
            l.location_code,
            CASE
                WHEN sl.is_display_only
                THEN COALESCE(d.active_display_count, 0)
                ELSE COALESCE(i.inventory_records, 0)
            END as inventory_records,
            CASE
                WHEN sl.is_display_only
                THEN COALESCE(d.active_display_count, 0)
                ELSE COALESCE(i.total_quantity, 0)
            END as total_quantity,
            l.created_at,
            l.updated_at,
            COALESCE(d.active_display_count, 0) as active_display_count,
            COALESCE(d.active_display_count, 0) > 0 as has_active_display
        FROM locations l
        JOIN storage_locations sl ON l.storage_location_id = sl.id
        LEFT JOIN (
            """ + SITE_SCOPED_INVENTORY_SUBQUERY + """
        ) i ON l.id = i.location_id
        LEFT JOIN (
            SELECT machine_id as location_id,
                   COUNT(*) as active_display_count
            FROM machine_display
            WHERE ended_at IS NULL
            GROUP BY machine_id
        ) d ON l.id = d.location_id
        WHERE sl.code = :locationType
          AND sl.site_id = :siteId
        ORDER BY l.location_code
        """;

    /**
     * Fetch all locations across all types with their inventory counts.
     *
     * @deprecated site-blind; use {@link #findAllLocationsWithCounts(UUID)}.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public List<LocationWithCountsDTO> findAllLocationsWithCounts() {
        List<Object[]> results = entityManager
                .createNativeQuery(ALL_LOCATIONS_WITH_COUNTS_SQL)
                .getResultList();

        return mapResultsToDTO(results);
    }

    /**
     * Fetch locations of a specific type with their inventory counts.
     *
     * @deprecated site-blind; use {@link #findLocationsByTypeWithCounts(String, UUID)}.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public List<LocationWithCountsDTO> findLocationsByTypeWithCounts(String locationType) {
        List<Object[]> results = entityManager
                .createNativeQuery(LOCATIONS_BY_TYPE_WITH_COUNTS_SQL)
                .setParameter("locationType", locationType)
                .getResultList();

        return mapResultsToDTO(results);
    }

    /**
     * Site-scoped fetch of every location and its inventory counts, for {@code siteId} only
     * (.specs/phase-6-inventory 6e, T-6e-be-2). A {@code location_inventory} row whose site
     * disagrees with its location's site is excluded from the count rather than counted into
     * the wrong site's badge.
     */
    @SuppressWarnings("unchecked")
    public List<LocationWithCountsDTO> findAllLocationsWithCounts(UUID siteId) {
        List<Object[]> results = entityManager
                .createNativeQuery(SITE_SCOPED_ALL_LOCATIONS_WITH_COUNTS_SQL)
                .setParameter("siteId", siteId)
                .getResultList();

        return mapResultsToDTO(results);
    }

    /**
     * Site-scoped fetch of locations of a specific type and their inventory counts, for
     * {@code siteId} only (.specs/phase-6-inventory 6e, T-6e-be-2).
     */
    @SuppressWarnings("unchecked")
    public List<LocationWithCountsDTO> findLocationsByTypeWithCounts(String locationType, UUID siteId) {
        List<Object[]> results = entityManager
                .createNativeQuery(SITE_SCOPED_LOCATIONS_BY_TYPE_WITH_COUNTS_SQL)
                .setParameter("locationType", locationType)
                .setParameter("siteId", siteId)
                .getResultList();

        return mapResultsToDTO(results);
    }

    private UUID toUUID(Object value) {
        if (value instanceof UUID) return (UUID) value;
        if (value instanceof byte[] bytes) {
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(bytes);
            return new UUID(bb.getLong(), bb.getLong());
        }
        if (value instanceof String) return UUID.fromString((String) value);
        throw new IllegalArgumentException("Cannot convert " + value.getClass() + " to UUID");
    }

    private List<LocationWithCountsDTO> mapResultsToDTO(List<Object[]> results) {
        return results.stream()
                .map(row -> LocationWithCountsDTO.builder()
                        .id(toUUID(row[0]))
                        .locationType((String) row[1])
                        .locationCode((String) row[2])
                        .inventoryRecords(((Number) row[3]).intValue())
                        .totalQuantity(((Number) row[4]).intValue())
                        .createdAt(TimestampUtils.toOffsetDateTime(row[5]))
                        .updatedAt(TimestampUtils.toOffsetDateTime(row[6]))
                        .activeDisplayCount(((Number) row[7]).intValue())
                        .hasActiveDisplay((Boolean) row[8])
                        .build())
                .toList();
    }
}
