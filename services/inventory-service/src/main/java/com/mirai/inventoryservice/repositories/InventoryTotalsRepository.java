package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.dtos.responses.InventoryTotalDTO;
import com.mirai.inventoryservice.utils.TimestampUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for fetching aggregated inventory totals across all location types.
 * Uses native SQL to efficiently aggregate inventory quantities by item.
 */
@Repository
public class InventoryTotalsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private static final String INVENTORY_TOTALS_SQL = """
        WITH all_inventory AS (
            SELECT item_id, quantity, updated_at FROM box_bin_inventory
            UNION ALL
            SELECT item_id, quantity, updated_at FROM rack_inventory
            UNION ALL
            SELECT item_id, quantity, updated_at FROM cabinet_inventory
            UNION ALL
            SELECT item_id, quantity, updated_at FROM single_claw_machine_inventory
            UNION ALL
            SELECT item_id, quantity, updated_at FROM double_claw_machine_inventory
            UNION ALL
            SELECT item_id, quantity, updated_at FROM keychain_machine_inventory
            UNION ALL
            SELECT item_id, quantity, updated_at FROM four_corner_machine_inventory
            UNION ALL
            SELECT item_id, quantity, updated_at FROM pusher_machine_inventory
            UNION ALL
            SELECT item_id, quantity, updated_at FROM not_assigned_inventory
        )
        SELECT
            p.id,
            p.sku,
            p.name,
            p.image_url,
            p.category,
            p.subcategory,
            p.unit_cost,
            p.is_active,
            COALESCE(SUM(i.quantity), 0) as total_quantity,
            MAX(i.updated_at) as last_updated_at
        FROM products p
        LEFT JOIN all_inventory i ON p.id = i.item_id
        GROUP BY p.id, p.sku, p.name, p.image_url, p.category, p.subcategory, p.unit_cost, p.is_active
        ORDER BY p.name
        """;

    @SuppressWarnings("unchecked")
    public List<InventoryTotalDTO> findAllInventoryTotals() {
        List<Object[]> results = entityManager
                .createNativeQuery(INVENTORY_TOTALS_SQL)
                .getResultList();

        return results.stream()
                .map(row -> InventoryTotalDTO.builder()
                        .itemId((UUID) row[0])
                        .sku((String) row[1])
                        .name((String) row[2])
                        .imageUrl((String) row[3])
                        .category((String) row[4])
                        .subcategory((String) row[5])
                        .unitCost(row[6] != null ? ((Number) row[6]).doubleValue() : null)
                        .isActive((Boolean) row[7])
                        .totalQuantity(((Number) row[8]).intValue())
                        .lastUpdatedAt(TimestampUtils.toOffsetDateTime(row[9]))
                        .build())
                .toList();
    }
}
