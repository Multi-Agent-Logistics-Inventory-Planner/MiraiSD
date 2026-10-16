package com.mirai.inventoryservice.audit.api;

import com.mirai.inventoryservice.audit.application.AuditLogService;
import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContextHolder;
import lombok.RequiredArgsConstructor;
import com.mirai.inventoryservice.shared.web.PageLimits;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Site-scoped audit-log routes. {@code siteId} is declared on each handler so the generated client
 * exposes it, but the trusted value always comes from {@link AuthorizedSiteContextHolder}.
 */
@RestController
@RequestMapping("/api/v1/sites/{siteId}/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
public class SiteAuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<Page<AuditLogDTO>> listSiteAuditLogs(
            @PathVariable UUID siteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) StockMovementReason reason,
            @RequestParam(required = false) List<StockMovementReason> reasons,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID locationId) {
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
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        return ResponseEntity.ok(auditLogService.getAuditLogs(contextSiteId, filters, PageRequest.of(page, PageLimits.clampSize(size))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogDetailDTO> getSiteAuditLogDetail(
            @PathVariable UUID siteId,
            @PathVariable UUID id) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        return ResponseEntity.ok(auditLogService.getAuditLogDetail(contextSiteId, id));
    }
}
