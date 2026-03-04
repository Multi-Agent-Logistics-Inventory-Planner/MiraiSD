package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.AuditLogDTO;
import com.mirai.inventoryservice.dtos.responses.AuditLogDetailDTO;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.services.StockMovementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AuditLogDTOMapper {

    private final StockMovementService stockMovementService;

    /**
     * Map AuditLog entity to list view DTO
     */
    public AuditLogDTO toDTO(AuditLog auditLog) {
        String productSummary;
        if (auditLog.getItemCount() == 1) {
            // Try to get the single product name from movements
            List<StockMovement> movements = auditLog.getMovements();
            if (movements != null && !movements.isEmpty()) {
                productSummary = movements.get(0).getItem().getName();
            } else {
                productSummary = "1 product";
            }
        } else {
            productSummary = auditLog.getItemCount() + " products";
        }

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
     * Map AuditLog entity to detail view DTO (includes movements)
     */
    public AuditLogDetailDTO toDetailDTO(AuditLog auditLog, List<StockMovement> movements) {
        List<AuditLogDetailDTO.MovementDetailDTO> movementDTOs = movements.stream()
                .map(this::toMovementDetailDTO)
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

    private AuditLogDetailDTO.MovementDetailDTO toMovementDetailDTO(StockMovement movement) {
        String fromLocationCode = null;
        String toLocationCode = null;

        if (movement.getFromLocationId() != null) {
            fromLocationCode = stockMovementService.resolveLocationCode(
                    movement.getFromLocationId(),
                    movement.getLocationType()
            );
        }

        if (movement.getToLocationId() != null) {
            toLocationCode = stockMovementService.resolveLocationCode(
                    movement.getToLocationId(),
                    movement.getLocationType()
            );
        }

        return AuditLogDetailDTO.MovementDetailDTO.builder()
                .id(movement.getId())
                .itemId(movement.getItem().getId())
                .itemSku(movement.getItem().getSku())
                .itemName(movement.getItem().getName())
                .fromLocationCode(fromLocationCode)
                .toLocationCode(toLocationCode)
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
