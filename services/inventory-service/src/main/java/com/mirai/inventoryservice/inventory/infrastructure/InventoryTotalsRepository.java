package com.mirai.inventoryservice.inventory.infrastructure;

import com.mirai.inventoryservice.inventory.api.InventoryTotalDTO;
import com.mirai.inventoryservice.inventory.api.SiteInventoryTotalDTO;
import com.mirai.inventoryservice.inventory.domain.InvalidInventoryOperationException;
import com.mirai.inventoryservice.utils.TimestampUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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

    // --- Slim, site-scoped, batched totals projection (.specs/phase-6-inventory 6c, T-6c-5,
    // AC-5/AC-7). INVENTORY_TOTALS_SQL above stays untouched for the legacy endpoint -- it is
    // AC-8's measured baseline (InventoryEgressBaselineIT) and must not regress. These are new,
    // additive queries returning only (product_id, total_quantity, last_updated_at) for one site,
    // with no embedded catalog metadata (F-6c-9).

    /**
     * Maximum number of product ids accepted by {@link #findInventoryTotalsBySiteAndProductIds}.
     * AC-7 requires a real ceiling ("neither full-catalog refreshes nor one request per product");
     * this is that documented ceiling. Chosen generously above any realistic coalesced-refresh
     * batch (6e's targeted-refresh work) while still rejecting a full-catalog-sized id list sent
     * through the batched path instead of {@link #findAllInventoryTotalsBySite}.
     */
    public static final int MAX_PRODUCT_IDS_BATCH_SIZE = 500;

    /**
     * Full-catalog mode: one row per product in the entire catalog, scoped to one site. The site
     * predicate lives in the {@code LEFT JOIN}'s {@code ON} clause, not a {@code WHERE} filter
     * (F-6c-3's zero-stock hazard) -- a product with no {@code location_inventory} row at this
     * site still appears, with {@code totalQuantity = 0} and {@code lastUpdatedAt = null}. Mirrors
     * the legacy {@link #findAllInventoryTotals()}'s row-per-product guarantee, scoped by site.
     */
    private static final String SITE_INVENTORY_TOTALS_JPQL = """
        SELECT p.id, COALESCE(SUM(li.quantity), 0), MAX(li.updatedAt)
        FROM Product p LEFT JOIN LocationInventory li ON li.product = p AND li.site.id = :siteId
        GROUP BY p.id
        ORDER BY p.id
        """;

    /**
     * Batched mode: restricted to a caller-supplied, size-bounded set of product ids. Contract is
     * deliberately different from the full-catalog mode above: a requested id with no
     * {@code location_inventory} row at this site is simply absent from the returned list --
     * callers must treat a missing id as quantity 0, not as "not found" -- consistent with
     * {@link com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository
     * #sumQuantitiesByProductIdsAndSiteId}'s already-recorded same choice for the same shape of
     * query (caller already knows the id is a real product; this is a targeted refresh of known
     * ids, not catalog discovery, so the full-catalog mode's row-per-product guarantee is not
     * needed here).
     */
    private static final String SITE_INVENTORY_TOTALS_BY_PRODUCT_IDS_JPQL = """
        SELECT li.product.id, SUM(li.quantity), MAX(li.updatedAt)
        FROM LocationInventory li
        WHERE li.product.id IN :productIds AND li.site.id = :siteId
        GROUP BY li.product.id
        ORDER BY li.product.id
        """;

    @SuppressWarnings("unchecked")
    public List<SiteInventoryTotalDTO> findAllInventoryTotalsBySite(UUID siteId) {
        List<Object[]> results = entityManager
                .createQuery(SITE_INVENTORY_TOTALS_JPQL)
                .setParameter("siteId", siteId)
                .getResultList();
        return mapRows(results);
    }

    @SuppressWarnings("unchecked")
    public List<SiteInventoryTotalDTO> findInventoryTotalsBySiteAndProductIds(UUID siteId, Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        if (productIds.size() > MAX_PRODUCT_IDS_BATCH_SIZE) {
            throw new InvalidInventoryOperationException(
                    "productIds batch size " + productIds.size()
                            + " exceeds the maximum of " + MAX_PRODUCT_IDS_BATCH_SIZE);
        }
        List<Object[]> results = entityManager
                .createQuery(SITE_INVENTORY_TOTALS_BY_PRODUCT_IDS_JPQL)
                .setParameter("productIds", productIds)
                .setParameter("siteId", siteId)
                .getResultList();
        return mapRows(results);
    }

    private List<SiteInventoryTotalDTO> mapRows(List<Object[]> results) {
        return results.stream()
                .map(row -> SiteInventoryTotalDTO.builder()
                        .productId((UUID) row[0])
                        .totalQuantity(row[1] == null ? 0 : ((Number) row[1]).intValue())
                        .lastUpdatedAt(TimestampUtils.toOffsetDateTime(row[2]))
                        .build())
                .toList();
    }
}
