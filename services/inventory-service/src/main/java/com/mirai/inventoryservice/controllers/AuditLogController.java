package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.dtos.responses.AuditLogDTO;
import com.mirai.inventoryservice.dtos.responses.AuditLogDetailDTO;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * Get paginated audit logs with optional filters
     */
    @GetMapping
    public ResponseEntity<Page<AuditLogDTO>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) StockMovementReason reason,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID locationId
    ) {
        AuditLogFilterDTO filters = AuditLogFilterDTO.builder()
                .search(search)
                .actorId(actorId)
                .reason(reason)
                .fromDate(fromDate)
                .toDate(toDate)
                .productId(productId)
                .locationId(locationId)
                .build();

        Page<AuditLogDTO> auditLogs = auditLogService.getAuditLogs(filters, PageRequest.of(page, size));
        return ResponseEntity.ok(auditLogs);
    }

    /**
     * Get audit log detail by ID (includes all movements)
     */
    @GetMapping("/{id}")
    public ResponseEntity<AuditLogDetailDTO> getAuditLogDetail(@PathVariable UUID id) {
        AuditLogDetailDTO detail = auditLogService.getAuditLogDetail(id);
        return ResponseEntity.ok(detail);
    }
}
