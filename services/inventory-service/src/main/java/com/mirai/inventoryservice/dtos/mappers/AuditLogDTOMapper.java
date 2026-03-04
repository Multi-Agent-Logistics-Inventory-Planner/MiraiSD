package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.AuditLogDTO;
import com.mirai.inventoryservice.dtos.responses.AuditLogDetailDTO;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.services.StockMovementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AuditLogDTOMapper {

    private final StockMovementService stockMovementService;

    /**
     * Map AuditLog entity to list view DTO.
     * Uses the denormalized productSummary field — no lazy-loading of movements needed.
     */
    public AuditLogDTO toDTO(AuditLog auditLog) {
        String productSummary = auditLog.getProductSummary() != null
                ? auditLog.getProductSummary()
                : (auditLog.getItemCount() > 1 ? auditLog.getItemCount() + " products" : "1 product");

        return AuditLogDTO.builder()
                .id(auditLog.getId())
                .actorId(auditLog.getActorId())
                .actorName(auditLog.getActorName())
                .reason(auditLog.getReason())
                .primaryFromLocationCode(auditLog.getPrimaryFromLocationCode())
                .primaryToLocationCode(auditLog.getPrimaryToLocationCode())
                .itemCount(auditLog.getItemCount())
                .totalQuantityMoved(auditLog.getTotalQuantityMoved())
                .notes(auditLog.getNotes())
                .createdAt(auditLog.getCreatedAt())
                .productSummary(productSummary)
                .build();
    }

    /**
     * Map AuditLog entity to detail view DTO (includes movements).
     * Location codes are resolved once per unique location ID, not once per movement.
     */
    public AuditLogDetailDTO toDetailDTO(AuditLog auditLog, List<StockMovement> movements) {
        // Pre-resolve all unique location IDs in a single pass to avoid
        // calling resolveLocationCode (a native SQL query) once per movement.
        Map<UUID, String> locationCodeCache = new HashMap<>();
        for (StockMovement m : movements) {
            if (m.getFromLocationId() != null) {
                locationCodeCache.computeIfAbsent(m.getFromLocationId(),
                        id -> stockMovementService.resolveLocationCode(id, m.getLocationType()));
            }
            if (m.getToLocationId() != null) {
                locationCodeCache.computeIfAbsent(m.getToLocationId(),
                        id -> stockMovementService.resolveLocationCode(id, m.getLocationType()));
            }
        }

        List<AuditLogDetailDTO.MovementDetailDTO> movementDTOs = movements.stream()
                .map(m -> toMovementDetailDTO(m, locationCodeCache))
                .collect(Collectors.toList());

        return AuditLogDetailDTO.builder()
                .id(auditLog.getId())
                .actorId(auditLog.getActorId())
                .actorName(auditLog.getActorName())
                .reason(auditLog.getReason())
                .primaryFromLocationCode(auditLog.getPrimaryFromLocationCode())
                .primaryToLocationCode(auditLog.getPrimaryToLocationCode())
                .itemCount(auditLog.getItemCount())
                .totalQuantityMoved(auditLog.getTotalQuantityMoved())
                .notes(auditLog.getNotes())
                .createdAt(auditLog.getCreatedAt())
                .movements(movementDTOs)
                .build();
    }

    private AuditLogDetailDTO.MovementDetailDTO toMovementDetailDTO(
            StockMovement movement, Map<UUID, String> locationCodeCache) {
        return AuditLogDetailDTO.MovementDetailDTO.builder()
                .id(movement.getId())
                .itemId(movement.getItem().getId())
                .itemSku(movement.getItem().getSku())
                .itemName(movement.getItem().getName())
                .fromLocationCode(movement.getFromLocationId() != null
                        ? locationCodeCache.get(movement.getFromLocationId()) : null)
                .toLocationCode(movement.getToLocationId() != null
                        ? locationCodeCache.get(movement.getToLocationId()) : null)
                .previousQuantity(movement.getPreviousQuantity())
                .currentQuantity(movement.getCurrentQuantity())
                .quantityChange(movement.getQuantityChange())
                .build();
    }

    /**
     * Map list of AuditLog entities to DTOs
     */
    public List<AuditLogDTO> toDTOList(List<AuditLog> auditLogs) {
        return auditLogs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}
