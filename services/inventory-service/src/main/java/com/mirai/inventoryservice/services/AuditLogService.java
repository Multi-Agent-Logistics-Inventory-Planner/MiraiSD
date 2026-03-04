package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.mappers.AuditLogDTOMapper;
import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.dtos.responses.AuditLogDTO;
import com.mirai.inventoryservice.dtos.responses.AuditLogDetailDTO;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.repositories.AuditLogRepository;
import com.mirai.inventoryservice.repositories.AuditLogSpecifications;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AuditLogDTOMapper auditLogMapper;
    private final UserRepository userRepository;

    /**
     * Create a new audit log entry
     */
    @Transactional
    public AuditLog createAuditLog(
            UUID actorId,
            StockMovementReason reason,
            UUID primaryFromLocationId,
            String primaryFromLocationCode,
            UUID primaryToLocationId,
            String primaryToLocationCode,
            int itemCount,
            int totalQuantityMoved,
            String notes
    ) {
        String actorName = null;
        if (actorId != null) {
            actorName = userRepository.findById(actorId)
                    .map(User::getFullName)
                    .orElse(null);
        }

        AuditLog auditLog = AuditLog.builder()
                .actorId(actorId)
                .actorName(actorName)
                .reason(reason)
                .primaryFromLocationId(primaryFromLocationId)
                .primaryFromLocationCode(primaryFromLocationCode)
                .primaryToLocationId(primaryToLocationId)
                .primaryToLocationCode(primaryToLocationCode)
                .itemCount(itemCount)
                .totalQuantityMoved(totalQuantityMoved)
                .notes(notes)
                .build();

        AuditLog saved = auditLogRepository.save(auditLog);
        log.info("Created audit log: {} for reason: {}", saved.getId(), reason);
        return saved;
    }

    /**
     * Get paginated audit logs with filters
     */
    @Transactional(readOnly = true)
    public Page<AuditLogDTO> getAuditLogs(AuditLogFilterDTO filters, Pageable pageable) {
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
    public AuditLogDetailDTO getAuditLogDetail(UUID auditLogId) {
        AuditLog auditLog = auditLogRepository.findById(auditLogId)
                .orElseThrow(() -> new EntityNotFoundException("Audit log not found: " + auditLogId));

        // Fetch movements for this audit log with eager loading of items
        List<StockMovement> movements = stockMovementRepository.findByAuditLogIdWithItem(auditLogId);

        return auditLogMapper.toDetailDTO(auditLog, movements);
    }
}
