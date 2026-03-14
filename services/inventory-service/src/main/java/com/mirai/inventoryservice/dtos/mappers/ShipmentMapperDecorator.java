package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.ShipmentItemAllocationResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ShipmentItemResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ShipmentResponseDTO;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.shipment.Shipment;
import com.mirai.inventoryservice.models.shipment.ShipmentItem;
import com.mirai.inventoryservice.models.shipment.ShipmentItemAllocation;
import com.mirai.inventoryservice.models.storage.*;
import com.mirai.inventoryservice.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Decorator that enriches ShipmentMapper DTOs with location codes using batch fetching
 * to avoid N+1 query problems.
 */
@Component
public class ShipmentMapperDecorator {

    private final ShipmentMapper delegate;
    private final BoxBinRepository boxBinRepository;
    private final RackRepository rackRepository;
    private final CabinetRepository cabinetRepository;
    private final SingleClawMachineRepository singleClawMachineRepository;
    private final DoubleClawMachineRepository doubleClawMachineRepository;
    private final KeychainMachineRepository keychainMachineRepository;
    private final FourCornerMachineRepository fourCornerMachineRepository;
    private final GachaponRepository gachaponRepository;
    private final PusherMachineRepository pusherMachineRepository;
    private final WindowRepository windowRepository;

    @Autowired
    public ShipmentMapperDecorator(
            @Qualifier("shipmentMapperImpl") ShipmentMapper delegate,
            BoxBinRepository boxBinRepository,
            RackRepository rackRepository,
            CabinetRepository cabinetRepository,
            SingleClawMachineRepository singleClawMachineRepository,
            DoubleClawMachineRepository doubleClawMachineRepository,
            KeychainMachineRepository keychainMachineRepository,
            FourCornerMachineRepository fourCornerMachineRepository,
            GachaponRepository gachaponRepository,
            PusherMachineRepository pusherMachineRepository,
            WindowRepository windowRepository) {
        this.delegate = delegate;
        this.boxBinRepository = boxBinRepository;
        this.rackRepository = rackRepository;
        this.cabinetRepository = cabinetRepository;
        this.singleClawMachineRepository = singleClawMachineRepository;
        this.doubleClawMachineRepository = doubleClawMachineRepository;
        this.keychainMachineRepository = keychainMachineRepository;
        this.fourCornerMachineRepository = fourCornerMachineRepository;
        this.gachaponRepository = gachaponRepository;
        this.pusherMachineRepository = pusherMachineRepository;
        this.windowRepository = windowRepository;
    }

    /**
     * Converts a Shipment entity to DTO with location codes populated via batch fetching.
     */
    public ShipmentResponseDTO toResponseDTOWithLocationCodes(Shipment shipment) {
        ShipmentResponseDTO dto = delegate.toResponseDTO(shipment);
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            return dto;
        }

        // Collect all location IDs from all allocations
        Map<LocationType, Set<UUID>> locationIdsByType = collectLocationIds(shipment.getItems());

        // Batch fetch location codes
        Map<LocationType, Map<UUID, String>> locationCodes = batchFetchLocationCodes(locationIdsByType);

        // Enrich allocations with location codes
        enrichAllocationsWithCodes(dto.getItems(), locationCodes);

        return dto;
    }

    /**
     * Converts a list of Shipment entities to DTOs with location codes populated.
     */
    public List<ShipmentResponseDTO> toResponseDTOListWithLocationCodes(List<Shipment> shipments) {
        if (shipments == null || shipments.isEmpty()) {
            return Collections.emptyList();
        }

        List<ShipmentResponseDTO> dtos = delegate.toResponseDTOList(shipments);

        // Collect all location IDs from all shipments
        Map<LocationType, Set<UUID>> locationIdsByType = new EnumMap<>(LocationType.class);
        for (Shipment shipment : shipments) {
            if (shipment.getItems() != null) {
                mergeLocationIds(locationIdsByType, collectLocationIds(shipment.getItems()));
            }
        }

        // Batch fetch location codes
        Map<LocationType, Map<UUID, String>> locationCodes = batchFetchLocationCodes(locationIdsByType);

        // Enrich all DTOs
        for (ShipmentResponseDTO dto : dtos) {
            if (dto.getItems() != null) {
                enrichAllocationsWithCodes(dto.getItems(), locationCodes);
            }
        }

        return dtos;
    }

    private Map<LocationType, Set<UUID>> collectLocationIds(List<ShipmentItem> items) {
        Map<LocationType, Set<UUID>> result = new EnumMap<>(LocationType.class);

        for (ShipmentItem item : items) {
            if (item.getAllocations() != null) {
                for (ShipmentItemAllocation allocation : item.getAllocations()) {
                    if (allocation.getLocationId() != null &&
                            allocation.getLocationType() != null &&
                            allocation.getLocationType() != LocationType.NOT_ASSIGNED) {
                        result.computeIfAbsent(allocation.getLocationType(), k -> new HashSet<>())
                                .add(allocation.getLocationId());
                    }
                }
            }
        }

        return result;
    }

    private void mergeLocationIds(Map<LocationType, Set<UUID>> target, Map<LocationType, Set<UUID>> source) {
        for (Map.Entry<LocationType, Set<UUID>> entry : source.entrySet()) {
            target.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).addAll(entry.getValue());
        }
    }

    private Map<LocationType, Map<UUID, String>> batchFetchLocationCodes(Map<LocationType, Set<UUID>> locationIdsByType) {
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
            case RACK -> rackRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(Rack::getId, Rack::getRackCode));
            case CABINET -> cabinetRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(Cabinet::getId, Cabinet::getCabinetCode));
            case SINGLE_CLAW_MACHINE -> singleClawMachineRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(SingleClawMachine::getId, SingleClawMachine::getSingleClawMachineCode));
            case DOUBLE_CLAW_MACHINE -> doubleClawMachineRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(DoubleClawMachine::getId, DoubleClawMachine::getDoubleClawMachineCode));
            case KEYCHAIN_MACHINE -> keychainMachineRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(KeychainMachine::getId, KeychainMachine::getKeychainMachineCode));
            case FOUR_CORNER_MACHINE -> fourCornerMachineRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(FourCornerMachine::getId, FourCornerMachine::getFourCornerMachineCode));
            case GACHAPON -> gachaponRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(Gachapon::getId, Gachapon::getGachaponCode));
            case PUSHER_MACHINE -> pusherMachineRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(PusherMachine::getId, PusherMachine::getPusherMachineCode));
            case WINDOW -> windowRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(Window::getId, Window::getWindowCode));
            case NOT_ASSIGNED -> Collections.emptyMap();
        };
    }

    private void enrichAllocationsWithCodes(List<ShipmentItemResponseDTO> items,
                                            Map<LocationType, Map<UUID, String>> locationCodes) {
        for (ShipmentItemResponseDTO item : items) {
            if (item.getAllocations() != null) {
                for (ShipmentItemAllocationResponseDTO allocation : item.getAllocations()) {
                    if (allocation.getLocationType() == LocationType.NOT_ASSIGNED) {
                        allocation.setLocationCode("NA");
                    } else if (allocation.getLocationId() != null) {
                        Map<UUID, String> codes = locationCodes.get(allocation.getLocationType());
                        if (codes != null) {
                            allocation.setLocationCode(codes.get(allocation.getLocationId()));
                        }
                    }
                }
            }
        }
    }
}
