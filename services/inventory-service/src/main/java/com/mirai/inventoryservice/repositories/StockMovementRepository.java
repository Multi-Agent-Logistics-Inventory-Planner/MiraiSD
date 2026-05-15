package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.repositories.projections.StockMovementHistoryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.mirai.inventoryservice.models.enums.StockMovementReason;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long>, JpaSpecificationExecutor<StockMovement> {
    // Find all movements for a specific product, newest first
    List<StockMovement> findByItem_IdOrderByAtDesc(UUID productId);

    // Paginated version for large histories
    Page<StockMovement> findByItem_IdOrderByAtDesc(UUID productId, Pageable pageable);

    // Filter by location type
    List<StockMovement> findByItem_IdAndLocationTypeOrderByAtDesc(UUID productId, LocationType locationType);

    // Find recent movements (last 30 days, etc)
    List<StockMovement> findByItem_IdAndAtAfterOrderByAtDesc(UUID productId, OffsetDateTime since);

    // Find most recent movement by actor (user)
    Optional<StockMovement> findTopByActorIdOrderByAtDesc(UUID actorId);

    // Bulk query: find most recent movement timestamp for each actor
    @Query("SELECT sm.actorId, MAX(sm.at) FROM StockMovement sm WHERE sm.actorId IS NOT NULL GROUP BY sm.actorId")
    List<Object[]> findLatestMovementTimestampsByActor();

    // Audit log query with eager fetch of item to avoid N+1
    @EntityGraph(value = "StockMovement.withItem")
    Page<StockMovement> findAll(Specification<StockMovement> spec, Pageable pageable);

    // Analytics queries with JOIN FETCH on item to avoid N+1
    @Query("SELECT sm FROM StockMovement sm JOIN FETCH sm.item WHERE sm.reason = :reason AND sm.at >= :since")
    List<StockMovement> findByReasonAndAtAfterWithItem(@Param("reason") StockMovementReason reason, @Param("since") OffsetDateTime since);

    @Query("SELECT sm FROM StockMovement sm JOIN FETCH sm.item WHERE sm.reason = :reason AND sm.at >= :since AND sm.at < :until")
    List<StockMovement> findByReasonAndAtBetweenWithItem(@Param("reason") StockMovementReason reason, @Param("since") OffsetDateTime since, @Param("until") OffsetDateTime until);

    // Find all movements for a specific audit log
    @Query("SELECT sm FROM StockMovement sm JOIN FETCH sm.item WHERE sm.auditLog.id = :auditLogId ORDER BY sm.id")
    List<StockMovement> findByAuditLogIdWithItem(@Param("auditLogId") java.util.UUID auditLogId);

    // Find stock movements by metadata source (used for dev seed cleanup)
    @Query(value = "SELECT * FROM stock_movements WHERE metadata->>'source' = :source", nativeQuery = true)
    List<StockMovement> findByMetadataSource(@Param("source") String source);

    void deleteByItem_Id(UUID productId);

    // Batch delete all stock movements for multiple products (optimized for N+1 prevention)
    @Modifying
    @Query("DELETE FROM StockMovement sm WHERE sm.item.id IN :itemIds")
    void deleteAllByItemIdIn(@Param("itemIds") Collection<UUID> itemIds);

    /**
     * Product Assistant drill-down. Returns a projection so Hibernate never
     * hydrates the item / auditLog / location graphs. Reasons filter is
     * optional (null = all reasons). Backed by the V19 (item_id, at DESC) index.
     */
    @Query("SELECT sm.id AS id, sm.at AS at, sm.reason AS reason, " +
            "sm.quantityChange AS quantityChange, sm.previousQuantity AS previousQuantity, " +
            "sm.currentQuantity AS currentQuantity, sm.fromLocationId AS fromLocationId, " +
            "sm.toLocationId AS toLocationId " +
            "FROM StockMovement sm " +
            "WHERE sm.item.id = :productId " +
            "AND sm.at >= :from AND sm.at < :to " +
            "AND (:reasons IS NULL OR sm.reason IN :reasons) " +
            "ORDER BY sm.at DESC")
    List<StockMovementHistoryView> findHistoryByItemId(
            @Param("productId") UUID productId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("reasons") List<StockMovementReason> reasons,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Aggregate sales movements by item and date for rollup computation.
     * Does the aggregation in SQL to avoid loading all entities into memory.
     * Returns: item_id, rollup_date, units_sold, revenue, cost, profit, movement_count
     */
    @Query(value = """
        SELECT
            sm.item_id,
            DATE(sm.at AT TIME ZONE 'UTC') as rollup_date,
            SUM(ABS(sm.quantity_change)) as units_sold,
            SUM(ABS(sm.quantity_change) * COALESCE(p.msrp, 0)) as revenue,
            SUM(ABS(sm.quantity_change) * COALESCE(p.unit_cost, 0)) as cost,
            SUM(ABS(sm.quantity_change) * (COALESCE(p.msrp, 0) - COALESCE(p.unit_cost, 0))) as profit,
            COUNT(*) as movement_count
        FROM stock_movements sm
        JOIN products p ON sm.item_id = p.id
        WHERE sm.reason = 'SALE'
          AND sm.at >= :startDate
          AND sm.at < :endDate
        GROUP BY sm.item_id, DATE(sm.at AT TIME ZONE 'UTC')
        """, nativeQuery = true)
    List<Object[]> aggregateSalesByItemAndDate(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    /**
     * Aggregate KUJI draw payouts for a single box, bucketed per calendar day in the
     * requested timezone. Slip counts come from metadata.slip_quantity (KUJI movements
     * carry quantity_change = 0). Value is slip count multiplied by effective tier price
     * (tier.price OR linked-product msrp). Reversals subtract on the day the reversal
     * occurred. Returns rows only for days with activity; the service pads zeros for
     * the dense series.
     * Columns: bucket_date (date), slip_count (int), value_won (numeric).
     */
    @Query(value = """
        SELECT
            (sm.at AT TIME ZONE :tz)::date AS bucket_date,
            SUM(
                CASE
                    WHEN sm.reason = 'KUJI_PRIZE_WON'     THEN COALESCE((sm.metadata->>'slip_quantity')::int, 0)
                    WHEN sm.reason = 'KUJI_DRAW_REVERSED' THEN -COALESCE((sm.metadata->>'slip_quantity')::int, 0)
                    ELSE 0
                END
            ) AS slip_count,
            SUM(
                CASE
                    WHEN sm.reason = 'KUJI_PRIZE_WON'
                        THEN COALESCE((sm.metadata->>'slip_quantity')::int, 0)
                            * COALESCE(t.price, p.msrp, 0)
                    WHEN sm.reason = 'KUJI_DRAW_REVERSED'
                        THEN -COALESCE((sm.metadata->>'slip_quantity')::int, 0)
                            * COALESCE(t.price, p.msrp, 0)
                    ELSE 0
                END
            ) AS value_won
        FROM stock_movements sm
        LEFT JOIN kuji_box_tiers t ON t.id = (sm.metadata->>'kuji_box_tier_id')::uuid
        LEFT JOIN products p ON p.id = t.linked_product_id
        WHERE sm.reason IN ('KUJI_PRIZE_WON', 'KUJI_DRAW_REVERSED')
          AND (sm.metadata->>'kuji_box_id')::uuid = :boxId
          AND (sm.at AT TIME ZONE :tz)::date >= :fromDate
          AND (sm.at AT TIME ZONE :tz)::date <= :toDate
        GROUP BY bucket_date
        ORDER BY bucket_date
        """, nativeQuery = true)
    List<Object[]> aggregateKujiDailyPayouts(
            @Param("boxId") UUID boxId,
            @Param("fromDate") java.time.LocalDate fromDate,
            @Param("toDate") java.time.LocalDate toDate,
            @Param("tz") String tz);
}

