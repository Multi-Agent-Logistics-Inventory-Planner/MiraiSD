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
 *
 * Uses the unified location_inventory table which consolidates all inventory
 * from storage locations (box bins, racks, machines, etc.).
 */
@Repository
public class InventoryTotalsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private static final String INVENTORY_TOTALS_SQL = """
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
            COALESCE(SUM(li.quantity), 0) as total_quantity,
            MAX(li.updated_at) as last_updated_at
        FROM products p
        LEFT JOIN categories c ON p.category_id = c.id
        LEFT JOIN categories parent ON c.parent_id = parent.id
        LEFT JOIN location_inventory li ON p.id = li.product_id
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
        SELECT product_id, COALESCE(SUM(quantity), 0) as total_quantity
        FROM location_inventory
        GROUP BY product_id
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
