package com.mirai.inventoryservice.audit.application;

import com.mirai.inventoryservice.audit.api.AuditLogDTOMapper;
import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.audit.api.AuditLogDTO;
import com.mirai.inventoryservice.audit.api.AuditLogDetailDTO;
import com.mirai.inventoryservice.audit.domain.AuditLog;
import com.mirai.inventoryservice.inventory.domain.StockMovement;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.audit.infrastructure.AuditLogRepository;
import com.mirai.inventoryservice.services.SupabaseBroadcastService;
import com.mirai.inventoryservice.audit.infrastructure.AuditLogSpecifications;
import com.mirai.inventoryservice.inventory.application.InventoryQueries;
import com.mirai.inventoryservice.identity.application.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final InventoryQueries inventoryQueries;
    private final AuditLogDTOMapper auditLogMapper;
    private final UserService userService;
    private final SupabaseBroadcastService broadcastService;

    /**
     * Create a new audit log entry (looks up actor by ID)
     */
    @Transactional
    public AuditLog createAuditLog(
            UUID siteId,
            UUID actorId,
            StockMovementReason reason,
            UUID primaryFromLocationId,
            String primaryFromLocationCode,
            UUID primaryToLocationId,
            String primaryToLocationCode,
            int itemCount,
            int totalQuantityMoved,
            String productSummary,
            String notes
    ) {
        User user = null;
        String actorName = null;
        if (actorId != null) {
            user = userService.findById(actorId).orElse(null);
            actorName = user != null ? user.getFullName() : null;
        }

        return createAuditLogInternal(siteId, user, actorName, reason, primaryFromLocationId,
                primaryFromLocationCode, primaryToLocationId, primaryToLocationCode,
                itemCount, totalQuantityMoved, productSummary, notes);
    }

    /**
     * Create a new audit log entry with pre-resolved actor info (avoids redundant user lookup)
     */
    @Transactional
    public AuditLog createAuditLog(
            UUID siteId,
            UUID actorId,
            String actorName,
            StockMovementReason reason,
            UUID primaryFromLocationId,
            String primaryFromLocationCode,
            UUID primaryToLocationId,
            String primaryToLocationCode,
            int itemCount,
            int totalQuantityMoved,
            String productSummary,
            String notes
    ) {
        // When actorName is provided, skip the user lookup to avoid redundant query
        User user = null;
        if (actorId != null && actorName == null) {
            user = userService.findById(actorId).orElse(null);
            actorName = user != null ? user.getFullName() : null;
        }

        return createAuditLogInternal(siteId, user, actorName, reason, primaryFromLocationId,
                primaryFromLocationCode, primaryToLocationId, primaryToLocationCode,
                itemCount, totalQuantityMoved, productSummary, notes);
    }

    private AuditLog createAuditLogInternal(
            UUID siteId,
            User user,
            String actorName,
            StockMovementReason reason,
            UUID primaryFromLocationId,
            String primaryFromLocationCode,
            UUID primaryToLocationId,
            String primaryToLocationCode,
            int itemCount,
            int totalQuantityMoved,
            String productSummary,
            String notes
    ) {
        AuditLog auditLog = AuditLog.builder()
                .siteId(siteId)
                .user(user)
                .actorName(actorName)
                .reason(reason)
                .primaryFromLocationId(primaryFromLocationId)
                .primaryFromLocationCode(primaryFromLocationCode)
                .primaryToLocationId(primaryToLocationId)
                .primaryToLocationCode(primaryToLocationCode)
                .itemCount(itemCount)
                .totalQuantityMoved(totalQuantityMoved)
                .productSummary(productSummary)
                .notes(notes)
                .build();

        AuditLog saved = auditLogRepository.save(auditLog);
        log.info("Created audit log: {} for reason: {}", saved.getId(), reason);

        // Broadcast so connected clients refresh audit log views / activity feed
        broadcastService.broadcastAuditLogCreated(siteId, null);

        return saved;
    }

    /**
     * Create an audit log for a shipment lifecycle event (edit, delete, status override).
     * Has no associated stock movements; the event detail lives in the structured columns.
     */
    @Transactional
    public AuditLog createShipmentEvent(
            UUID siteId,
            UUID actorId,
            String actorName,
            StockMovementReason reason,
            UUID shipmentId,
            String shipmentNumber,
            int itemCount,
            List<Map<String, Object>> fieldChanges,
            String previousStatus,
            String newStatus,
            String overrideReason
    ) {
        User user = null;
        if (actorId != null && actorName == null) {
            user = userService.findById(actorId).orElse(null);
            actorName = user != null ? user.getFullName() : null;
        }

        AuditLog auditLog = AuditLog.builder()
                .siteId(siteId)
                .user(user)
                .actorName(actorName)
                .reason(reason)
                .itemCount(itemCount)
                .totalQuantityMoved(0)
                .productSummary(shipmentNumber != null ? "Shipment " + shipmentNumber : null)
                .shipmentId(shipmentId)
                .shipmentNumber(shipmentNumber)
                .fieldChanges(fieldChanges)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .overrideReason(overrideReason)
                .build();

        AuditLog saved = auditLogRepository.save(auditLog);
        log.info("Created shipment audit log: {} for reason: {} shipment: {}", saved.getId(), reason, shipmentNumber);

        broadcastService.broadcastAuditLogCreated(siteId, null);

        return saved;
    }

    /**
     * Persist updates to an existing audit log (e.g., to finalize counts/reason after a multi-item action).
     */
    @Transactional
    public AuditLog save(AuditLog auditLog) {
        return auditLogRepository.save(auditLog);
    }

    /**
     * Load a single audit log of a site by id, for a caller that needs to mutate and re-save it.
     */
    public java.util.Optional<AuditLog> findById(UUID siteId, UUID id) {
        return auditLogRepository.findByIdAndSiteId(id, siteId);
    }

    /**
     * Delete an audit log (used to discard a parent audit row when a multi-item action turned out
     * to produce no actual movements — i.e. orphan cleanup, never used for normal history pruning).
     */
    @Transactional
    public void delete(AuditLog auditLog) {
        auditLogRepository.delete(auditLog);
    }

    /**
     * Recent audit logs of one site, for the activity feed.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findRecent(UUID siteId, OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        return auditLogRepository.findBySiteIdAndCreatedAtBetweenOrderByCreatedAtDesc(siteId, from, to, pageable);
    }

    /**
     * Get paginated audit logs with filters
     */
    @Transactional(readOnly = true)
    public Page<AuditLogDTO> getAuditLogs(UUID siteId, AuditLogFilterDTO filters, Pageable pageable) {
        filters.setSiteId(siteId);
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<AuditLog> auditLogs = auditLogRepository.findAll(
                AuditLogSpecifications.withFilters(filters),
                sortedPageable
        );

        return auditLogs.map(auditLogMapper::toDTO);
    }

    /**
     * Get audit log detail by ID (includes all movements)
     */
    @Transactional(readOnly = true)
    public AuditLogDetailDTO getAuditLogDetail(UUID siteId, UUID auditLogId) {
        AuditLog auditLog = auditLogRepository.findByIdAndSiteId(auditLogId, siteId)
                .orElseThrow(() -> new EntityNotFoundException("Audit log not found: " + auditLogId));

        // Fetch movements for this audit log with eager loading of items
        List<StockMovement> movements = inventoryQueries.findByAuditLogIdWithItem(auditLogId, siteId);

        return auditLogMapper.toDetailDTO(auditLog, movements);
    }
}
