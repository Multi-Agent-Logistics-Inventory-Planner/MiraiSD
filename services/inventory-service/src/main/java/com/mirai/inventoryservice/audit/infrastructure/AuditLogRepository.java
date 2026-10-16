package com.mirai.inventoryservice.audit.infrastructure;

import com.mirai.inventoryservice.audit.domain.AuditLog;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    java.util.Optional<AuditLog> findByIdAndSiteId(UUID id, UUID siteId);

    Page<AuditLog> findBySiteIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID siteId, OffsetDateTime fromDate, OffsetDateTime toDate, Pageable pageable);

    Page<AuditLog> findByReasonOrderByCreatedAtDesc(StockMovementReason reason, Pageable pageable);

    // Filtered, site-scoped listing goes through AuditLogSpecifications.
}
