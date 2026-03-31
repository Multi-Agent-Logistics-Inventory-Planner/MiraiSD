package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.dtos.responses.LocationWithCountsDTO;
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
            SELECT location_id,
                   COUNT(*) as inventory_records,
                   COALESCE(SUM(quantity), 0) as total_quantity
            FROM location_inventory
            GROUP BY location_id
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
            SELECT location_id,
                   COUNT(*) as inventory_records,
                   COALESCE(SUM(quantity), 0) as total_quantity
            FROM location_inventory
            GROUP BY location_id
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
     * Fetch all locations across all types with their inventory counts.
     */
    @SuppressWarnings("unchecked")
    public List<LocationWithCountsDTO> findAllLocationsWithCounts() {
        List<Object[]> results = entityManager
                .createNativeQuery(ALL_LOCATIONS_WITH_COUNTS_SQL)
                .getResultList();

        return mapResultsToDTO(results);
    }

    /**
     * Fetch locations of a specific type with their inventory counts.
     */
    @SuppressWarnings("unchecked")
    public List<LocationWithCountsDTO> findLocationsByTypeWithCounts(String locationType) {
        List<Object[]> results = entityManager
                .createNativeQuery(LOCATIONS_BY_TYPE_WITH_COUNTS_SQL)
                .setParameter("locationType", locationType)
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
