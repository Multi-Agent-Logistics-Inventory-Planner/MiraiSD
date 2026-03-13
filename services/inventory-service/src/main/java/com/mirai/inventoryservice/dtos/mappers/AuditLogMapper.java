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
    private final GachaponRepository gachaponRepository;
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
            GachaponRepository gachaponRepository,
            PusherMachineRepository pusherMachineRepository) {
        this.userRepository = userRepository;
        this.boxBinRepository = boxBinRepository;
        this.singleClawMachineRepository = singleClawMachineRepository;
        this.doubleClawMachineRepository = doubleClawMachineRepository;
        this.keychainMachineRepository = keychainMachineRepository;
        this.cabinetRepository = cabinetRepository;
        this.rackRepository = rackRepository;
        this.fourCornerMachineRepository = fourCornerMachineRepository;
        this.gachaponRepository = gachaponRepository;
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

        // Look up location codes - try movement's type first, then search all types
        String fromCode = findLocationCode(movement.getFromLocationId(), movement.getLocationType(), locationCodes);
        String toCode = findLocationCode(movement.getToLocationId(), movement.getLocationType(), locationCodes);

        // For NOT_ASSIGNED locations, use "NA" as the code
        if (movement.getLocationType() == LocationType.NOT_ASSIGNED) {
            if (movement.getFromLocationId() != null && fromCode == null) {
                fromCode = "NA";
            }
            if (movement.getToLocationId() != null && toCode == null && movement.getFromLocationId() == null) {
                // This is a subtract from NOT_ASSIGNED, toLocationId points to destination
                // but we're the source, so we should show NA as from
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

    /**
     * Find location code by ID, first checking the preferred type, then all types.
     * This handles cross-type transfers where toLocationId may belong to a different type.
     */
    private String findLocationCode(UUID locationId, LocationType preferredType,
            Map<LocationType, Map<UUID, String>> locationCodes) {
        if (locationId == null) {
            return null;
        }

        // Try preferred type first
        Map<UUID, String> preferredCodes = locationCodes.getOrDefault(preferredType, Collections.emptyMap());
        String code = preferredCodes.get(locationId);
        if (code != null) {
            return code;
        }

        // Search all types for cross-type transfers
        for (Map<UUID, String> codes : locationCodes.values()) {
            code = codes.get(locationId);
            if (code != null) {
                return code;
            }
        }

        return null;
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
        Set<UUID> crossTypeLocationIds = new HashSet<>(); // IDs that need cross-type lookup

        for (StockMovement movement : movements) {
            LocationType type = movement.getLocationType();
            locationIdsByType.computeIfAbsent(type, k -> new HashSet<>());

            if (movement.getFromLocationId() != null) {
                locationIdsByType.get(type).add(movement.getFromLocationId());
            }
            if (movement.getToLocationId() != null) {
                locationIdsByType.get(type).add(movement.getToLocationId());
                // NOT_ASSIGNED transfers may have toLocationId in a different type
                if (type == LocationType.NOT_ASSIGNED) {
                    crossTypeLocationIds.add(movement.getToLocationId());
                }
            }
        }

        Map<LocationType, Map<UUID, String>> result = new EnumMap<>(LocationType.class);

        // Fetch codes for each type's own IDs
        for (Map.Entry<LocationType, Set<UUID>> entry : locationIdsByType.entrySet()) {
            LocationType type = entry.getKey();
            Set<UUID> ids = entry.getValue();

            if (!ids.isEmpty() && type != LocationType.NOT_ASSIGNED) {
                result.put(type, fetchLocationCodesForType(type, ids));
            }
        }

        // For cross-type transfers, find unresolved IDs in other location types
        if (!crossTypeLocationIds.isEmpty()) {
            // Remove IDs already resolved
            for (Map<UUID, String> codes : result.values()) {
                crossTypeLocationIds.removeAll(codes.keySet());
            }

            // Query remaining types for unresolved IDs
            if (!crossTypeLocationIds.isEmpty()) {
                for (LocationType type : LocationType.values()) {
                    if (type != LocationType.NOT_ASSIGNED && !result.containsKey(type)) {
                        Map<UUID, String> codes = fetchLocationCodesForType(type, crossTypeLocationIds);
                        if (!codes.isEmpty()) {
                            result.put(type, codes);
                            crossTypeLocationIds.removeAll(codes.keySet());
                            if (crossTypeLocationIds.isEmpty()) break;
                        }
                    }
                }
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
            case FOUR_CORNER_MACHINE -> fourCornerMachineRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(FourCornerMachine::getId, FourCornerMachine::getFourCornerMachineCode));
            case GACHAPON -> gachaponRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(Gachapon::getId, Gachapon::getGachaponCode));
            case PUSHER_MACHINE -> pusherMachineRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(PusherMachine::getId, PusherMachine::getPusherMachineCode));
            case WINDOW, NOT_ASSIGNED -> new HashMap<>(); // No location codes for NOT_ASSIGNED or WINDOW (no audit display)
        };
    }
}
