package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.AuditLogEntryDTO;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.storage.Location;
import com.mirai.inventoryservice.repositories.LocationRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Mapper for converting StockMovement entities to AuditLogEntryDTOs.
 * Uses the unified Location table for resolving location codes.
 */
@Component
public class AuditLogMapper {
    private final UserRepository userRepository;
    private final LocationRepository locationRepository;

    public AuditLogMapper(
            UserRepository userRepository,
            LocationRepository locationRepository) {
        this.userRepository = userRepository;
        this.locationRepository = locationRepository;
    }

    /**
     * Batch converts a page of StockMovement entities to AuditLogEntryDTOs.
     * Uses batch fetching to avoid N+1 query problems.
     */
    public Page<AuditLogEntryDTO> toAuditLogEntryDTOPage(Page<StockMovement> movements) {
        List<AuditLogEntryDTO> dtos = toAuditLogEntryDTOList(movements.getContent());
        return new PageImpl<>(dtos, movements.getPageable(), movements.getTotalElements());
    }

    /**
     * Batch converts a list of StockMovement entities to AuditLogEntryDTOs.
     * Uses batch fetching to avoid N+1 query problems.
     */
    public List<AuditLogEntryDTO> toAuditLogEntryDTOList(List<StockMovement> movements) {
        if (movements.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, String> actorNames = batchFetchActorNames(movements);
        Map<UUID, String> locationCodes = batchFetchLocationCodes(movements);

        return movements.stream()
                .map(movement -> toDTO(movement, actorNames, locationCodes))
                .toList();
    }

    private AuditLogEntryDTO toDTO(
            StockMovement movement,
            Map<UUID, String> actorNames,
            Map<UUID, String> locationCodes) {

        String fromCode = movement.getFromLocationId() != null
                ? locationCodes.get(movement.getFromLocationId())
                : null;
        String toCode = movement.getToLocationId() != null
                ? locationCodes.get(movement.getToLocationId())
                : null;

        // For NOT_ASSIGNED locations, use "NA" as the code if not found
        if (movement.getLocationType() == LocationType.NOT_ASSIGNED) {
            if (movement.getFromLocationId() != null && fromCode == null) {
                fromCode = "NA";
            }
            if (movement.getToLocationId() != null && toCode == null && movement.getFromLocationId() == null) {
                fromCode = "NA";
            }
        }

        return AuditLogEntryDTO.builder()
                .id(movement.getId())
                .locationType(movement.getLocationType())
                .itemId(movement.getItem().getId())
                .itemSku(movement.getItem().getSku())
                .itemName(movement.getItem().getName())
                .fromLocationId(movement.getFromLocationId())
                .fromLocationCode(fromCode)
                .toLocationId(movement.getToLocationId())
                .toLocationCode(toCode)
                .previousQuantity(movement.getPreviousQuantity())
                .currentQuantity(movement.getCurrentQuantity())
                .quantityChange(movement.getQuantityChange())
                .reason(movement.getReason())
                .actorId(movement.getActorId())
                .actorName(actorNames.get(movement.getActorId()))
                .at(movement.getAt())
                .build();
    }

    private Map<UUID, String> batchFetchActorNames(List<StockMovement> movements) {
        Set<UUID> actorIds = movements.stream()
                .map(StockMovement::getActorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (actorIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }

    /**
     * Batch fetch all location codes using the unified Location table.
     * Collects all unique location IDs from from/to fields and fetches them in one query.
     */
    private Map<UUID, String> batchFetchLocationCodes(List<StockMovement> movements) {
        Set<UUID> locationIds = new HashSet<>();

        for (StockMovement movement : movements) {
            if (movement.getFromLocationId() != null) {
                locationIds.add(movement.getFromLocationId());
            }
            if (movement.getToLocationId() != null) {
                locationIds.add(movement.getToLocationId());
            }
        }

        if (locationIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // Single query to unified locations table
        return locationRepository.findAllById(locationIds).stream()
                .collect(Collectors.toMap(Location::getId, Location::getLocationCode));
    }
}
