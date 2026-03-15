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
 * Uses native SQL with UNION ALL to aggregate data across all location types.
 */
@Repository
public class LocationAggregateRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private static final String ALL_LOCATIONS_WITH_COUNTS_SQL = """
        SELECT
            l.id,
            l.location_type,
            l.location_code,
            CASE
                WHEN l.location_type IN ('GACHAPON', 'KEYCHAIN_MACHINE')
                THEN COALESCE(d.active_display_count, 0)
                ELSE COALESCE(i.inventory_records, 0)
            END as inventory_records,
            CASE
                WHEN l.location_type IN ('GACHAPON', 'KEYCHAIN_MACHINE')
                THEN COALESCE(d.active_display_count, 0)
                ELSE COALESCE(i.total_quantity, 0)
            END as total_quantity,
            l.created_at,
            l.updated_at,
            COALESCE(d.active_display_count, 0) as active_display_count,
            COALESCE(d.active_display_count, 0) > 0 as has_active_display
        FROM (
            SELECT id, 'BOX_BIN' as location_type, box_bin_code as location_code, created_at, updated_at
            FROM box_bins
            UNION ALL
            SELECT id, 'CABINET' as location_type, cabinet_code as location_code, created_at, updated_at
            FROM cabinets
            UNION ALL
            SELECT id, 'DOUBLE_CLAW_MACHINE' as location_type, double_claw_machine_code as location_code, created_at, updated_at
            FROM double_claw_machines
            UNION ALL
            SELECT id, 'FOUR_CORNER_MACHINE' as location_type, four_corner_machine_code as location_code, created_at, updated_at
            FROM four_corner_machines
            UNION ALL
            SELECT id, 'GACHAPON' as location_type, gachapon_code as location_code, created_at, updated_at
            FROM gachapons
            UNION ALL
            SELECT id, 'KEYCHAIN_MACHINE' as location_type, keychain_machine_code as location_code, created_at, updated_at
            FROM keychain_machines
            UNION ALL
            SELECT id, 'PUSHER_MACHINE' as location_type, pusher_machine_code as location_code, created_at, updated_at
            FROM pusher_machines
            UNION ALL
            SELECT id, 'RACK' as location_type, rack_code as location_code, created_at, updated_at
            FROM racks
            UNION ALL
            SELECT id, 'SINGLE_CLAW_MACHINE' as location_type, single_claw_machine_code as location_code, created_at, updated_at
            FROM single_claw_machines
            UNION ALL
            SELECT id, 'WINDOW' as location_type, window_code as location_code, created_at, updated_at
            FROM windows
        ) l
        LEFT JOIN (
            SELECT box_bin_id as location_id, 'BOX_BIN' as location_type,
                   COUNT(*) as inventory_records, COALESCE(SUM(quantity), 0) as total_quantity
            FROM box_bin_inventory GROUP BY box_bin_id
            UNION ALL
            SELECT rack_id as location_id, 'RACK' as location_type,
                   COUNT(*) as inventory_records, COALESCE(SUM(quantity), 0) as total_quantity
            FROM rack_inventory GROUP BY rack_id
            UNION ALL
            SELECT cabinet_id as location_id, 'CABINET' as location_type,
                   COUNT(*) as inventory_records, COALESCE(SUM(quantity), 0) as total_quantity
            FROM cabinet_inventory GROUP BY cabinet_id
            UNION ALL
            SELECT single_claw_machine_id as location_id, 'SINGLE_CLAW_MACHINE' as location_type,
                   COUNT(*) as inventory_records, COALESCE(SUM(quantity), 0) as total_quantity
            FROM single_claw_machine_inventory GROUP BY single_claw_machine_id
            UNION ALL
            SELECT double_claw_machine_id as location_id, 'DOUBLE_CLAW_MACHINE' as location_type,
                   COUNT(*) as inventory_records, COALESCE(SUM(quantity), 0) as total_quantity
            FROM double_claw_machine_inventory GROUP BY double_claw_machine_id
            UNION ALL
            SELECT four_corner_machine_id as location_id, 'FOUR_CORNER_MACHINE' as location_type,
                   COUNT(*) as inventory_records, COALESCE(SUM(quantity), 0) as total_quantity
            FROM four_corner_machine_inventory GROUP BY four_corner_machine_id
            UNION ALL
            SELECT pusher_machine_id as location_id, 'PUSHER_MACHINE' as location_type,
                   COUNT(*) as inventory_records, COALESCE(SUM(quantity), 0) as total_quantity
            FROM pusher_machine_inventory GROUP BY pusher_machine_id
            UNION ALL
            SELECT window_id as location_id, 'WINDOW' as location_type,
                   COUNT(*) as inventory_records, COALESCE(SUM(quantity), 0) as total_quantity
            FROM window_inventory GROUP BY window_id
        ) i ON l.id = i.location_id AND l.location_type = i.location_type
        LEFT JOIN (
            SELECT machine_id as location_id, location_type,
                   COUNT(*) as active_display_count
            FROM machine_display
            WHERE ended_at IS NULL
            GROUP BY machine_id, location_type
        ) d ON l.id = d.location_id AND l.location_type = d.location_type
        ORDER BY l.location_type, l.location_code
        """;

    private static final String LOCATIONS_BY_TYPE_WITH_COUNTS_SQL = """
        SELECT
            l.id,
            l.location_type,
            l.location_code,
            CASE
                WHEN l.location_type IN ('GACHAPON', 'KEYCHAIN_MACHINE')
                THEN COALESCE(d.active_display_count, 0)
                ELSE COALESCE(i.inventory_records, 0)
            END as inventory_records,
            CASE
                WHEN l.location_type IN ('GACHAPON', 'KEYCHAIN_MACHINE')
                THEN COALESCE(d.active_display_count, 0)
                ELSE COALESCE(i.total_quantity, 0)
            END as total_quantity,
            l.created_at,
            l.updated_at,
            COALESCE(d.active_display_count, 0) as active_display_count,
            COALESCE(d.active_display_count, 0) > 0 as has_active_display
        FROM (
            SELECT id, 'BOX_BIN' as location_type, box_bin_code as location_code, created_at, updated_at
            FROM box_bins WHERE 'BOX_BIN' = :locationType
            UNION ALL
            SELECT id, 'CABINET' as location_type, cabinet_code as location_code, created_at, updated_at
            FROM cabinets WHERE 'CABINET' = :locationType
            UNION ALL
            SELECT id, 'DOUBLE_CLAW_MACHINE' as location_type, double_claw_machine_code as location_code, created_at, updated_at
            FROM double_claw_machines WHERE 'DOUBLE_CLAW_MACHINE' = :locationType
            UNION ALL
            SELECT id, 'FOUR_CORNER_MACHINE' as location_type, four_corner_machine_code as location_code, created_at, updated_at
            FROM four_corner_machines WHERE 'FOUR_CORNER_MACHINE' = :locationType
            UNION ALL
            SELECT id, 'GACHAPON' as location_type, gachapon_code as location_code, created_at, updated_at
            FROM gachapons WHERE 'GACHAPON' = :locationType
            UNION ALL
            SELECT id, 'KEYCHAIN_MACHINE' as location_type, keychain_machine_code as location_code, created_at, updated_at
            FROM keychain_machines WHERE 'KEYCHAIN_MACHINE' = :locationType
            UNION ALL
            SELECT id, 'PUSHER_MACHINE' as location_type, pusher_machine_code as location_code, created_at, updated_at
            FROM pusher_machines WHERE 'PUSHER_MACHINE' = :locationType
            UNION ALL
            SELECT id, 'RACK' as location_type, rack_code as location_code, created_at, updated_at
            FROM racks WHERE 'RACK' = :locationType
            UNION ALL
            SELECT id, 'SINGLE_CLAW_MACHINE' as location_type, single_claw_machine_code as location_code, created_at, updated_at
            FROM single_claw_machines WHERE 'SINGLE_CLAW_MACHINE' = :locationType
            UNION ALL
            SELECT id, 'WINDOW' as location_type, window_code as location_code, created_at, updated_at
            FROM windows WHERE 'WINDOW' = :locationType
        ) l
        LEFT JOIN (
            SELECT box_bin_id as location_id, 'BOX_BIN' as location_type,
                   COUNT(*) as inventory_records, COALESCE(SUM(quantity), 0) as total_quantity
            FROM box_bin_inventory WHERE 'BOX_BIN' = :locationType GROUP BY box_bin_id
            UNION ALL
            SELECT rack_id as location_id, 'RACK' as location_type,
                   COUNT(*) as inventory_records, COALESCE(SUM(quantity), 0) as total_quantity
            FROM rack_inventory WHERE 'RACK' = :locationType GROUP BY rack_id
            UNION ALL
            SELECT cabinet_id as location_id, 'CABINET' as location_type,
                   COUNT(*) as inventory_records, COALESCE(SUM(quantity), 0) as total_quantity
            FROM cabinet_inventory WHERE 'CABINET' = :locationType GROUP BY cabinet_id
            UNION ALL
            SELECT single_claw_machine_id as location_id, 'SINGLE_CLAW_MACHINE' as location_type,
                   COUNT(*) as inventory_records, COALESCE(SUM(quantity), 0) as total_quantity
            FROM single_claw_machine_inventory WHERE 'SINGLE_CLAW_MACHINE' = :locationType GROUP BY single_claw_machine_id
            UNION ALL
            SELECT double_claw_machine_id as location_id, 'DOUBLE_CLAW_MACHINE' as location_type,
                   COUNT(*) as inventory_records, COALESCE(SUM(quantity), 0) as total_quantity
            FROM double_claw_machine_inventory WHERE 'DOUBLE_CLAW_MACHINE' = :locationType GROUP BY double_claw_machine_id
            UNION ALL
            SELECT four_corner_machine_id as location_id, 'FOUR_CORNER_MACHINE' as location_type,
                   COUNT(*) as inventory_records, COALESCE(SUM(quantity), 0) as total_quantity
            FROM four_corner_machine_inventory WHERE 'FOUR_CORNER_MACHINE' = :locationType GROUP BY four_corner_machine_id
            UNION ALL
            SELECT pusher_machine_id as location_id, 'PUSHER_MACHINE' as location_type,
                   COUNT(*) as inventory_records, COALESCE(SUM(quantity), 0) as total_quantity
            FROM pusher_machine_inventory WHERE 'PUSHER_MACHINE' = :locationType GROUP BY pusher_machine_id
            UNION ALL
            SELECT window_id as location_id, 'WINDOW' as location_type,
                   COUNT(*) as inventory_records, COALESCE(SUM(quantity), 0) as total_quantity
            FROM window_inventory WHERE 'WINDOW' = :locationType GROUP BY window_id
        ) i ON l.id = i.location_id AND l.location_type = i.location_type
        LEFT JOIN (
            SELECT machine_id as location_id, location_type,
                   COUNT(*) as active_display_count
            FROM machine_display
            WHERE ended_at IS NULL
            GROUP BY machine_id, location_type
        ) d ON l.id = d.location_id AND l.location_type = d.location_type
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

    private List<LocationWithCountsDTO> mapResultsToDTO(List<Object[]> results) {
        return results.stream()
                .map(row -> LocationWithCountsDTO.builder()
                        .id((UUID) row[0])
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
