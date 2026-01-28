package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.AuditLogEntryDTO;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.storage.*;
import com.mirai.inventoryservice.repositories.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AuditLogMapper {
    private final UserRepository userRepository;
    private final BoxBinRepository boxBinRepository;
    private final SingleClawMachineRepository singleClawMachineRepository;
    private final DoubleClawMachineRepository doubleClawMachineRepository;
    private final KeychainMachineRepository keychainMachineRepository;
    private final CabinetRepository cabinetRepository;
    private final RackRepository rackRepository;
    private final FourCornerMachineRepository fourCornerMachineRepository;
    private final PusherMachineRepository pusherMachineRepository;

    public AuditLogMapper(
            UserRepository userRepository,
            BoxBinRepository boxBinRepository,
            SingleClawMachineRepository singleClawMachineRepository,
            DoubleClawMachineRepository doubleClawMachineRepository,
            KeychainMachineRepository keychainMachineRepository,
            CabinetRepository cabinetRepository,
            RackRepository rackRepository,
            FourCornerMachineRepository fourCornerMachineRepository,
            PusherMachineRepository pusherMachineRepository) {
        this.userRepository = userRepository;
        this.boxBinRepository = boxBinRepository;
        this.singleClawMachineRepository = singleClawMachineRepository;
        this.doubleClawMachineRepository = doubleClawMachineRepository;
        this.keychainMachineRepository = keychainMachineRepository;
        this.cabinetRepository = cabinetRepository;
        this.rackRepository = rackRepository;
        this.fourCornerMachineRepository = fourCornerMachineRepository;
        this.pusherMachineRepository = pusherMachineRepository;
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
        Map<LocationType, Map<UUID, String>> locationCodes = batchFetchLocationCodes(movements);

        return movements.stream()
                .map(movement -> toDTO(movement, actorNames, locationCodes))
                .toList();
    }

    private AuditLogEntryDTO toDTO(
            StockMovement movement,
            Map<UUID, String> actorNames,
            Map<LocationType, Map<UUID, String>> locationCodes) {

        Map<UUID, String> locationCodesForType = locationCodes.getOrDefault(
                movement.getLocationType(), Collections.emptyMap());

        return AuditLogEntryDTO.builder()
                .id(movement.getId())
                .locationType(movement.getLocationType())
                .itemId(movement.getItem().getId())
                .itemSku(movement.getItem().getSku())
                .itemName(movement.getItem().getName())
                .fromLocationId(movement.getFromLocationId())
                .fromLocationCode(locationCodesForType.get(movement.getFromLocationId()))
                .toLocationId(movement.getToLocationId())
                .toLocationCode(locationCodesForType.get(movement.getToLocationId()))
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

    private Map<LocationType, Map<UUID, String>> batchFetchLocationCodes(List<StockMovement> movements) {
        Map<LocationType, Set<UUID>> locationIdsByType = new EnumMap<>(LocationType.class);

        for (StockMovement movement : movements) {
            LocationType type = movement.getLocationType();
            locationIdsByType.computeIfAbsent(type, k -> new HashSet<>());

            if (movement.getFromLocationId() != null) {
                locationIdsByType.get(type).add(movement.getFromLocationId());
            }
            if (movement.getToLocationId() != null) {
                locationIdsByType.get(type).add(movement.getToLocationId());
            }
        }

        Map<LocationType, Map<UUID, String>> result = new EnumMap<>(LocationType.class);

        for (Map.Entry<LocationType, Set<UUID>> entry : locationIdsByType.entrySet()) {
            LocationType type = entry.getKey();
            Set<UUID> ids = entry.getValue();

            if (!ids.isEmpty()) {
                result.put(type, fetchLocationCodesForType(type, ids));
            }
        }

        return result;
    }

    private Map<UUID, String> fetchLocationCodesForType(LocationType type, Set<UUID> ids) {
        return switch (type) {
            case BOX_BIN -> boxBinRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(BoxBin::getId, BoxBin::getBoxBinCode));
            case SINGLE_CLAW_MACHINE -> singleClawMachineRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(SingleClawMachine::getId, SingleClawMachine::getSingleClawMachineCode));
            case DOUBLE_CLAW_MACHINE -> doubleClawMachineRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(DoubleClawMachine::getId, DoubleClawMachine::getDoubleClawMachineCode));
            case KEYCHAIN_MACHINE -> keychainMachineRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(KeychainMachine::getId, KeychainMachine::getKeychainMachineCode));
            case CABINET -> cabinetRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(Cabinet::getId, Cabinet::getCabinetCode));
            case RACK -> rackRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(Rack::getId, Rack::getRackCode));
<<<<<<< HEAD
            case NOT_ASSIGNED -> new HashMap<>(); // No location codes for NOT_ASSIGNED
=======
            case FOUR_CORNER_MACHINE -> fourCornerMachineRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(FourCornerMachine::getId, FourCornerMachine::getFourCornerMachineCode));
            case PUSHER_MACHINE -> pusherMachineRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(PusherMachine::getId, PusherMachine::getPusherMachineCode));
>>>>>>> origin/main
        };
    }
}
