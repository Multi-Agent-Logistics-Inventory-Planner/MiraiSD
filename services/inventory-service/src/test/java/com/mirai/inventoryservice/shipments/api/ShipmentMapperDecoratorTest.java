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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Review finding (.specs/phase-7-shipments T-4), fixed in two rounds:
 * <ul>
 *   <li>Round 2: {@code batchFetchLocationCodes} used to look up every allocation's location
 *       globally ({@code locationRepository.findAllById}), with no site filter -- a legacy
 *       allocation whose {@code locationId} genuinely belongs to a different site (possible even
 *       after the create/receive-time guards, since V70's backfill only validated parent/child
 *       site agreement, not each allocation's own location) would have its real location code
 *       disclosed in the response.</li>
 *   <li>Round 3: masking only the code and leaving the raw {@code locationId}/{@code
 *       destinationLocationId} in the response still discloses a foreign site's real location
 *       identity, and the item-level {@code destinationLocationId} was not considered for masking
 *       at all. The fetch itself is now also site-scoped in the query ({@code
 *       findByIdInAndSite_Id}), not fetched globally and filtered after.</li>
 * </ul>
 * Proves both directly against the decorator, without needing a full receive flow.
 */
@ExtendWith(MockitoExtension.class)
class ShipmentMapperDecoratorTest {

    @Mock private ShipmentMapper delegate;
    @Mock private LocationService locationService;

    private ShipmentMapperDecorator decorator;

    private UUID mainSiteId;
    private UUID ownLocationId;
    private UUID foreignLocationId;

    @BeforeEach
    void setUp() {
        decorator = new ShipmentMapperDecorator(delegate, locationService);
        mainSiteId = UUID.randomUUID();
        ownLocationId = UUID.randomUUID();
        foreignLocationId = UUID.randomUUID();
    }

    @Test
    void doesNotDiscloseAForeignSiteLocationsCode() {
        // collectLocationIds reads from the Shipment *entity*'s allocations (not the response
        // DTO's), so the entity graph must carry the same location ids the DTO fixture below uses.
        ShipmentItemAllocation ownEntityAllocation = ShipmentItemAllocation.builder()
                .locationId(ownLocationId).locationType(LocationType.BOX_BIN).quantity(5).build();
        ShipmentItemAllocation foreignEntityAllocation = ShipmentItemAllocation.builder()
                .locationId(foreignLocationId).locationType(LocationType.BOX_BIN).quantity(3).build();
        ShipmentItem entityItem = ShipmentItem.builder()
                .allocations(List.of(ownEntityAllocation, foreignEntityAllocation))
                .build();
        Shipment shipment = Shipment.builder().id(UUID.randomUUID()).items(List.of(entityItem)).build();

        ShipmentItemAllocationResponseDTO ownAllocation = ShipmentItemAllocationResponseDTO.builder()
                .locationId(ownLocationId)
                .locationType(LocationType.BOX_BIN)
                .quantity(5)
                .build();
        ShipmentItemAllocationResponseDTO foreignAllocation = ShipmentItemAllocationResponseDTO.builder()
                .locationId(foreignLocationId)
                .locationType(LocationType.BOX_BIN)
                .quantity(3)
                .build();
        ShipmentItemResponseDTO itemDto = ShipmentItemResponseDTO.builder()
                .allocations(List.of(ownAllocation, foreignAllocation))
                .build();
        ShipmentResponseDTO responseDto = ShipmentResponseDTO.builder()
                .items(List.of(itemDto))
                .build();

        when(delegate.toResponseDTO(shipment)).thenReturn(responseDto);
        // The fetch is itself site-scoped now: only the caller's own site's location comes back.
        when(locationService.findCodesByIdsAndSite(any(), org.mockito.ArgumentMatchers.eq(mainSiteId)))
                .thenReturn(Map.of(ownLocationId, "OWN-CODE"));

        ShipmentResponseDTO result = decorator.toResponseDTOWithLocationCodes(mainSiteId, shipment);

        ShipmentItemAllocationResponseDTO ownResult = result.getItems().get(0).getAllocations().get(0);
        ShipmentItemAllocationResponseDTO foreignResult = result.getItems().get(0).getAllocations().get(1);

        assertThat(ownResult.getLocationCode()).isEqualTo("OWN-CODE");
        assertThat(ownResult.getLocationId())
                .as("the caller's own-site location id is fine to disclose")
                .isEqualTo(ownLocationId);

        assertThat(foreignResult.getLocationCode())
                .as("a foreign-site location's real code must never be disclosed")
                .isEqualTo("NA");
        assertThat(foreignResult.getLocationId())
                .as("a foreign-site location's raw id must also never be disclosed")
                .isNull();
    }

    @Test
    void masksAForeignSiteDestinationLocationIdOnTheItemItself() {
        ShipmentItem entityItem = ShipmentItem.builder()
                .destinationLocationId(foreignLocationId)
                .destinationLocationType(LocationType.BOX_BIN)
                .allocations(List.of())
                .build();
        Shipment shipment = Shipment.builder().id(UUID.randomUUID()).items(List.of(entityItem)).build();

        ShipmentItemResponseDTO itemDto = ShipmentItemResponseDTO.builder()
                .destinationLocationId(foreignLocationId)
                .destinationLocationType(LocationType.BOX_BIN)
                .allocations(List.of())
                .build();
        ShipmentResponseDTO responseDto = ShipmentResponseDTO.builder()
                .items(List.of(itemDto))
                .build();

        when(delegate.toResponseDTO(shipment)).thenReturn(responseDto);
        // The item's destination location belongs to a different site, so the site-scoped fetch
        // returns nothing for it.
        when(locationService.findCodesByIdsAndSite(any(), org.mockito.ArgumentMatchers.eq(mainSiteId)))
                .thenReturn(Map.of());

        ShipmentResponseDTO result = decorator.toResponseDTOWithLocationCodes(mainSiteId, shipment);

        assertThat(result.getItems().get(0).getDestinationLocationId())
                .as("a foreign-site destinationLocationId must never be disclosed")
                .isNull();
    }
}
