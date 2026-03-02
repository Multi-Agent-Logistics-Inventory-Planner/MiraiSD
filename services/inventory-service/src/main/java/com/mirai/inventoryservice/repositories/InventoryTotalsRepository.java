package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.dtos.responses.InventoryTotalDTO;
import com.mirai.inventoryservice.utils.TimestampUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            c.id as category_id,
            c.name as category_name,
            parent.id as parent_category_id,
            parent.name as parent_category_name,
            p.unit_cost,
            p.is_active,
            COALESCE(SUM(i.quantity), 0) as total_quantity,
            MAX(i.updated_at) as last_updated_at
        FROM products p
        LEFT JOIN categories c ON p.category_id = c.id
        LEFT JOIN categories parent ON c.parent_id = parent.id
        LEFT JOIN all_inventory i ON p.id = i.item_id
        GROUP BY p.id, p.sku, p.name, p.image_url, c.id, c.name, parent.id, parent.name, p.unit_cost, p.is_active
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
                        .categoryId((UUID) row[4])
                        .categoryName((String) row[5])
                        .parentCategoryId((UUID) row[6])
                        .parentCategoryName((String) row[7])
                        .unitCost(row[8] != null ? ((Number) row[8]).doubleValue() : null)
                        .isActive((Boolean) row[9])
                        .totalQuantity(((Number) row[10]).intValue())
                        .lastUpdatedAt(TimestampUtils.toOffsetDateTime(row[11]))
                        .build())
                .toList();
    }

    private static final String STOCK_TOTALS_SQL = """
        SELECT item_id, COALESCE(SUM(quantity), 0) as total_quantity FROM (
            SELECT item_id, quantity FROM box_bin_inventory
            UNION ALL SELECT item_id, quantity FROM rack_inventory
            UNION ALL SELECT item_id, quantity FROM cabinet_inventory
            UNION ALL SELECT item_id, quantity FROM single_claw_machine_inventory
            UNION ALL SELECT item_id, quantity FROM double_claw_machine_inventory
            UNION ALL SELECT item_id, quantity FROM keychain_machine_inventory
            UNION ALL SELECT item_id, quantity FROM four_corner_machine_inventory
            UNION ALL SELECT item_id, quantity FROM pusher_machine_inventory
            UNION ALL SELECT item_id, quantity FROM not_assigned_inventory
        ) combined GROUP BY item_id
        """;

    /**
     * Get stock totals for all products as a Map<UUID, Integer>.
     * Single query across all inventory tables.
     */
    @SuppressWarnings("unchecked")
    public Map<UUID, Integer> findAllStockTotalsMap() {
        List<Object[]> results = entityManager
                .createNativeQuery(STOCK_TOTALS_SQL)
                .getResultList();

        Map<UUID, Integer> stockMap = new HashMap<>();
        for (Object[] row : results) {
            UUID itemId = (UUID) row[0];
            Integer quantity = ((Number) row[1]).intValue();
            stockMap.put(itemId, quantity);
        }
        return stockMap;
    }
}
