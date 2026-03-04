package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    // Basic queries
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // Filter by actor
    Page<AuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorId, Pageable pageable);

    // Filter by reason
    Page<AuditLog> findByReasonOrderByCreatedAtDesc(StockMovementReason reason, Pageable pageable);

    // Filter by date range
    Page<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            OffsetDateTime fromDate, OffsetDateTime toDate, Pageable pageable);

    // Find audit logs containing a specific product (via stock_movements join)
    @Query("SELECT DISTINCT al FROM AuditLog al JOIN al.movements sm WHERE sm.item.id = :productId ORDER BY al.createdAt DESC")
    Page<AuditLog> findByProductId(@Param("productId") UUID productId, Pageable pageable);

    // Find audit logs involving a specific location
    @Query("SELECT DISTINCT al FROM AuditLog al JOIN al.movements sm " +
           "WHERE sm.fromLocationId = :locationId OR sm.toLocationId = :locationId " +
           "ORDER BY al.createdAt DESC")
    Page<AuditLog> findByLocationId(@Param("locationId") UUID locationId, Pageable pageable);

    // Combined filter query using specifications is preferred - see AuditLogSpecifications
}
