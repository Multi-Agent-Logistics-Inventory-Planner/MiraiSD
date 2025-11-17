package com.pm.inventoryservice.dtos.mappers;

import com.pm.inventoryservice.dtos.responses.StockMovementResponseDTO;
import com.pm.inventoryservice.models.StockMovement;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class StockMovementMapper {

    // Entity --> Response DTO
    public StockMovementResponseDTO toDTO(StockMovement movement) {
        if (movement == null) return null;

        return StockMovementResponseDTO.builder()
                .id(movement.getId())
                .itemId(movement.getItemId())
                .locationType(movement.getLocationType())
                .fromBoxId(movement.getFromBoxId())
                .toBoxId(movement.getToBoxId())
                .quantityChange(movement.getQuantityChange())
                .reason(movement.getReason())
                .actorId(movement.getActorId())
                .at(movement.getAt())
                .metadata(movement.getMetadata())
                // Location codes will be resolved in service layer if needed
                .build();
    }

    public List<StockMovementResponseDTO> toDTOList(List<StockMovement> movements) {
        return movements.stream().map(this::toDTO).collect(Collectors.toList());
    }
}
