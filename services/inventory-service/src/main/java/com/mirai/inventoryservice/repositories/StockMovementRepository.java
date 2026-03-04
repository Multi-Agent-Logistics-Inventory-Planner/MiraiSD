package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.enums.LocationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.mirai.inventoryservice.models.enums.StockMovementReason;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
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
}

