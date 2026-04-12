package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    /**
     * Find supplier by canonical name (normalized for case/whitespace).
     */
    Optional<Supplier> findByCanonicalName(String canonicalName);

    /**
     * Find all active suppliers ordered by display name.
     */
    List<Supplier> findByIsActiveTrueOrderByDisplayNameAsc();

    /**
     * Find all suppliers ordered by display name.
     */
    List<Supplier> findAllByOrderByDisplayNameAsc();

    /**
     * Search suppliers by partial display name match (case-insensitive).
     */
    @Query("SELECT s FROM Supplier s WHERE LOWER(s.displayName) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY s.displayName ASC")
    List<Supplier> searchByDisplayName(@Param("query") String query);

    /**
     * Search active suppliers by partial display name match (case-insensitive).
     */
    @Query("SELECT s FROM Supplier s WHERE s.isActive = true AND LOWER(s.displayName) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY s.displayName ASC")
    List<Supplier> searchActiveByDisplayName(@Param("query") String query);

    /**
     * Thread-safe upsert: Insert if not exists, return existing if conflict on canonical_name.
     * Uses PostgreSQL ON CONFLICT to handle race conditions.
     * Returns the supplier ID (either newly created or existing).
     */
    @Modifying
    @Query(value = """
        INSERT INTO suppliers (id, display_name, canonical_name, is_active, created_at, updated_at)
        VALUES (gen_random_uuid(), :displayName, canonicalize_supplier_name(:displayName), true, NOW(), NOW())
        ON CONFLICT (canonical_name) DO UPDATE SET updated_at = NOW()
        RETURNING id
        """, nativeQuery = true)
    UUID upsertByDisplayName(@Param("displayName") String displayName);

    /**
     * Count shipments associated with a supplier.
     */
    @Query(value = """
        SELECT COUNT(*) FROM shipments WHERE supplier_id = :supplierId
        """, nativeQuery = true)
    long countShipmentsBySupplierId(@Param("supplierId") UUID supplierId);

    /**
     * Get lead time statistics for a supplier from the materialized view.
     * Returns avg_lt and sigma_l for supplier-level stats (hierarchy_level = 2).
     */
    @Query(value = """
        SELECT avg_lt, sigma_l, n
        FROM mv_lead_time_stats
        WHERE supplier_id = :supplierId AND hierarchy_level = 2
        LIMIT 1
        """, nativeQuery = true)
    Object[] getSupplierLeadTimeStats(@Param("supplierId") UUID supplierId);

    /**
     * Get all suppliers with their lead time statistics.
     * Joins with MV for stats and shipments for count.
     */
    @Query(value = """
        SELECT
            s.id,
            s.display_name,
            s.contact_email,
            s.is_active,
            s.created_at,
            COALESCE(stats.n, 0) as shipment_count,
            stats.avg_lt,
            stats.sigma_l,
            (SELECT COUNT(*) FROM products WHERE preferred_supplier_id = s.id) as product_count
        FROM suppliers s
        LEFT JOIN mv_lead_time_stats stats ON stats.supplier_id = s.id AND stats.hierarchy_level = 2
        ORDER BY s.display_name ASC
        """, nativeQuery = true)
    List<Object[]> findAllWithStats();

    /**
     * Get suppliers with stats, filtered by active status.
     */
    @Query(value = """
        SELECT
            s.id,
            s.display_name,
            s.contact_email,
            s.is_active,
            s.created_at,
            COALESCE(stats.n, 0) as shipment_count,
            stats.avg_lt,
            stats.sigma_l,
            (SELECT COUNT(*) FROM products WHERE preferred_supplier_id = s.id) as product_count
        FROM suppliers s
        LEFT JOIN mv_lead_time_stats stats ON stats.supplier_id = s.id AND stats.hierarchy_level = 2
        WHERE s.is_active = :active
        ORDER BY s.display_name ASC
        """, nativeQuery = true)
    List<Object[]> findAllWithStatsByActive(@Param("active") boolean active);
}
