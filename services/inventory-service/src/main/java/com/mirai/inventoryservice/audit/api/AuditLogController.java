package com.mirai.inventoryservice.audit.api;

import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.audit.api.AuditLogDTO;
import com.mirai.inventoryservice.audit.api.AuditLogDetailDTO;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.identity.application.LegacyMainSiteContextResolver;
import com.mirai.inventoryservice.audit.application.AuditLogService;
import lombok.RequiredArgsConstructor;
import com.mirai.inventoryservice.shared.web.PageLimits;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final LegacyMainSiteContextResolver legacyMainSiteContextResolver;

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
            @RequestParam(required = false) List<StockMovementReason> reasons,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID locationId
    ) {
        AuditLogFilterDTO filters = AuditLogFilterDTO.builder()
                .search(search)
                .actorId(actorId)
                .reason(reason)
                .reasons(reasons)
                .fromDate(fromDate)
                .toDate(toDate)
                .productId(productId)
                .locationId(locationId)
                .build();

        Page<AuditLogDTO> auditLogs = auditLogService.getAuditLogs(
                legacyMainSiteContextResolver.requireMain().siteId(), filters, PageRequest.of(page, PageLimits.clampSize(size)));
        return ResponseEntity.ok(auditLogs);
    }

    /**
     * Get audit log detail by ID (includes all movements)
     */
    @GetMapping("/{id}")
    public ResponseEntity<AuditLogDetailDTO> getAuditLogDetail(@PathVariable UUID id) {
        AuditLogDetailDTO detail = auditLogService.getAuditLogDetail(legacyMainSiteContextResolver.requireMain().siteId(), id);
        return ResponseEntity.ok(detail);
    }
}
