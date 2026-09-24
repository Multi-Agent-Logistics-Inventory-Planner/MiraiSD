package com.mirai.inventoryservice.shipments.api;

import com.mirai.inventoryservice.dtos.mappers.ShipmentMapper;
import com.mirai.inventoryservice.shipments.api.ShipmentItemAllocationResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ShipmentItemResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ShipmentResponseDTO;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.shipments.domain.Shipment;
import com.mirai.inventoryservice.shipments.domain.ShipmentItem;
import com.mirai.inventoryservice.shipments.domain.ShipmentItemAllocation;
import com.mirai.inventoryservice.sites.application.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Decorator that enriches ShipmentMapper DTOs with location codes using batch fetching
 * to avoid N+1 query problems.
 *
 * Uses the unified Location table for all location types.
 */
@Component
public class ShipmentMapperDecorator {

    private final ShipmentMapper delegate;
    private final LocationService locationService;

    @Autowired
    public ShipmentMapperDecorator(
            @Qualifier("shipmentMapperImpl") ShipmentMapper delegate,
            LocationService locationService) {
        this.delegate = delegate;
        this.locationService = locationService;
    }

    /**
     * Converts a Shipment entity to DTO with location codes populated via batch fetching,
     * scoped to the caller's authorized site (.specs/phase-7-shipments AC-2, review finding): a
     * legacy allocation whose {@code locationId} genuinely belongs to a different site (V70
     * inherited the parent's site without validating each allocation's own location) must not
     * leak that other site's real location code into the response.
     */
    public ShipmentResponseDTO toResponseDTOWithLocationCodes(UUID siteId, Shipment shipment) {
        ShipmentResponseDTO dto = delegate.toResponseDTO(shipment);
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            return dto;
        }

        // Collect all location IDs from all allocations
        Set<UUID> locationIds = collectLocationIds(shipment.getItems());

        // Batch fetch location codes using unified Location table
        Map<UUID, String> locationCodes = batchFetchLocationCodes(siteId, locationIds);

        // Enrich allocations with location codes
        enrichAllocationsWithCodes(dto.getItems(), locationCodes);

        return dto;
    }

    /**
     * Converts a list of Shipment entities to DTOs with location codes populated, scoped to the
     * caller's authorized site (see {@link #toResponseDTOWithLocationCodes(UUID, Shipment)}).
     */
    public List<ShipmentResponseDTO> toResponseDTOListWithLocationCodes(UUID siteId, List<Shipment> shipments) {
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
        Map<UUID, String> locationCodes = batchFetchLocationCodes(siteId, allLocationIds);

        // Enrich all DTOs
        for (ShipmentResponseDTO dto : dtos) {
            if (dto.getItems() != null) {
                enrichAllocationsWithCodes(dto.getItems(), locationCodes);
            }
        }

        return dtos;
    }

    /**
     * Collect all unique location IDs from shipment items' destination locations and their
     * allocations, so both are covered by the same site-scoped fetch/mask pass
     * (.specs/phase-7-shipments AC-2, review finding: a legacy item's {@code
     * destinationLocationId} was previously left out of this collection entirely, so it was never
     * even considered for masking).
     */
    private Set<UUID> collectLocationIds(List<ShipmentItem> items) {
        Set<UUID> locationIds = new HashSet<>();

        for (ShipmentItem item : items) {
            if (item.getDestinationLocationId() != null) {
                locationIds.add(item.getDestinationLocationId());
            }
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
     * Batch fetch all location codes using the unified Location table, scoped to the caller's
     * site in the query itself ({@link LocationRepository#findByIdInAndSite_Id}) rather than
     * fetched globally and filtered afterward -- a foreign-site location row is never
     * materialized at all, not just excluded from the result.
     */
    private Map<UUID, String> batchFetchLocationCodes(UUID siteId, Set<UUID> locationIds) {
        return locationService.findCodesByIdsAndSite(locationIds, siteId);
    }

    /**
     * Enriches allocations with location codes and masks any {@code locationId}/{@code
     * destinationLocationId} that didn't resolve against {@code locationCodes} -- i.e. belongs to
     * a different site than the caller's (.specs/phase-7-shipments AC-2, review finding: masking
     * only the human-readable code and leaving the raw id in the response still discloses a
     * foreign site's real location identity).
     */
    private void enrichAllocationsWithCodes(List<ShipmentItemResponseDTO> items,
                                            Map<UUID, String> locationCodes) {
        for (ShipmentItemResponseDTO item : items) {
            if (item.getDestinationLocationId() != null && !locationCodes.containsKey(item.getDestinationLocationId())) {
                item.setDestinationLocationId(null);
            }
            if (item.getAllocations() != null) {
                for (ShipmentItemAllocationResponseDTO allocation : item.getAllocations()) {
                    if (allocation.getLocationId() != null) {
                        String code = locationCodes.get(allocation.getLocationId());
                        if (code != null) {
                            allocation.setLocationCode(code);
                        } else {
                            allocation.setLocationCode("NA");
                            allocation.setLocationId(null);
                        }
                    } else if (allocation.getLocationType() == LocationType.NOT_ASSIGNED) {
                        allocation.setLocationCode("NA");
                    }
                }
            }
        }
    }
}
