package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.ShipmentItemAllocationResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ShipmentItemResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ShipmentResponseDTO;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.shipment.Shipment;
import com.mirai.inventoryservice.models.shipment.ShipmentItem;
import com.mirai.inventoryservice.models.shipment.ShipmentItemAllocation;
import com.mirai.inventoryservice.models.storage.Location;
import com.mirai.inventoryservice.repositories.LocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Decorator that enriches ShipmentMapper DTOs with location codes using batch fetching
 * to avoid N+1 query problems.
 *
 * Uses the unified Location table for all location types.
 */
@Component
public class ShipmentMapperDecorator {

    private final ShipmentMapper delegate;
    private final LocationRepository locationRepository;

    @Autowired
    public ShipmentMapperDecorator(
            @Qualifier("shipmentMapperImpl") ShipmentMapper delegate,
            LocationRepository locationRepository) {
        this.delegate = delegate;
        this.locationRepository = locationRepository;
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
        Set<UUID> locationIds = collectLocationIds(shipment.getItems());

        // Batch fetch location codes using unified Location table
        Map<UUID, String> locationCodes = batchFetchLocationCodes(locationIds);

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
        Set<UUID> allLocationIds = new HashSet<>();
        for (Shipment shipment : shipments) {
            if (shipment.getItems() != null) {
                mergeLocationIds(allLocationIds, collectLocationIds(shipment.getItems()));
            }
        }

        // Batch fetch location codes using unified Location table
        Map<UUID, String> locationCodes = batchFetchLocationCodes(allLocationIds);

        // Enrich all DTOs
        for (ShipmentResponseDTO dto : dtos) {
            if (dto.getItems() != null) {
                enrichAllocationsWithCodes(dto.getItems(), locationCodes);
            }
        }

        return dtos;
    }

    /**
     * Collect all unique location IDs from shipment item allocations.
     */
    private Set<UUID> collectLocationIds(List<ShipmentItem> items) {
        Set<UUID> locationIds = new HashSet<>();

        for (ShipmentItem item : items) {
            if (item.getAllocations() != null) {
                for (ShipmentItemAllocation allocation : item.getAllocations()) {
                    if (allocation.getLocationId() != null) {
                        locationIds.add(allocation.getLocationId());
                    }
                }
            }
        }

        return locationIds;
    }

    private void mergeLocationIds(Set<UUID> target, Set<UUID> source) {
        target.addAll(source);
    }

    /**
     * Batch fetch all location codes using the unified Location table.
     * Single query replaces 10 type-specific repository calls.
     */
    private Map<UUID, String> batchFetchLocationCodes(Set<UUID> locationIds) {
        if (locationIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return locationRepository.findAllById(locationIds).stream()
                .collect(Collectors.toMap(Location::getId, Location::getLocationCode));
    }

    private void enrichAllocationsWithCodes(List<ShipmentItemResponseDTO> items,
                                            Map<UUID, String> locationCodes) {
        for (ShipmentItemResponseDTO item : items) {
            if (item.getAllocations() != null) {
                for (ShipmentItemAllocationResponseDTO allocation : item.getAllocations()) {
                    if (allocation.getLocationId() != null) {
                        String code = locationCodes.get(allocation.getLocationId());
                        allocation.setLocationCode(code != null ? code : "NA");
                    } else if (allocation.getLocationType() == LocationType.NOT_ASSIGNED) {
                        allocation.setLocationCode("NA");
                    }
                }
            }
        }
    }
}
