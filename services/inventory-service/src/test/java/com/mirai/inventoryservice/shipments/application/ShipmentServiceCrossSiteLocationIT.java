package com.mirai.inventoryservice.shipments.application;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.shipments.api.ReceiveShipmentRequestDTO;
import com.mirai.inventoryservice.shipments.api.ShipmentItemRequestDTO;
import com.mirai.inventoryservice.shipments.api.ShipmentRequestDTO;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import com.mirai.inventoryservice.shipments.domain.Shipment;
import com.mirai.inventoryservice.shipments.domain.ShipmentItem;
import com.mirai.inventoryservice.shipments.domain.ShipmentItemAllocation;
import com.mirai.inventoryservice.shipments.infrastructure.ShipmentItemAllocationRepository;
import com.mirai.inventoryservice.shipments.infrastructure.ShipmentItemRepository;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.LocationNotFoundException;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.sites.infrastructure.LocationRepository;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import com.mirai.inventoryservice.sites.infrastructure.StorageLocationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Review finding (.specs/phase-7-shipments T-4): {@code ShipmentService.addToInventory} looked
 * up a receive allocation's destination location by id alone ({@code locationRepository.
 * findById}), with no check that the location actually belongs to the shipment's own site. A
 * shipment authorized for one site could therefore add real stock at another site's location
 * while the allocation row was labeled with the shipment's site -- a tenant-isolation bug, not
 * just a mislabeled record. Proves the fix: {@code addToInventory} now resolves the location via
 * {@code findByIdAndSite_Id}, so a foreign-site location id is rejected before any write.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ShipmentServiceCrossSiteLocationIT {

    @Autowired private ShipmentService shipmentService;
    @Autowired private LocationInventoryRepository locationInventoryRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SiteRepository siteRepository;
    @Autowired private ShipmentItemRepository shipmentItemRepository;
    @Autowired private ShipmentItemAllocationRepository shipmentItemAllocationRepository;

    private Category category;
    private Site main;
    private Site second;
    private Location mainSiteLocation;
    private Location secondSiteLocation;

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        category = categoryRepository.save(Category.builder()
                .name("Cross Site Location Category " + suffix)
                .slug("cross-site-location-category-" + suffix)
                .build());

        main = siteRepository.findByCode("MAIN")
                .orElseGet(() -> siteRepository.save(Site.builder().code("MAIN").name("Main").build()));
        second = siteRepository.findByCode("XSITE-" + suffix)
                .orElseGet(() -> siteRepository.save(Site.builder().code("XSITE-" + suffix).name("Cross Site " + suffix).build()));

        StorageLocation mainStorage = storageLocationRepository.save(StorageLocation.builder()
                .site(main)
                .code("BOX_BINS_MAIN_" + suffix)
                .name("Main Box Bins " + suffix)
                .hasDisplay(false)
                .isDisplayOnly(false)
                .displayOrder(1)
                .build());
        mainSiteLocation = locationRepository.save(Location.builder()
                .storageLocation(mainStorage)
                .locationCode("MAIN-" + suffix)
                .build());

        StorageLocation mainNotAssignedStorage = storageLocationRepository.findByCodeAndSite_Code("NOT_ASSIGNED", "MAIN")
                .orElseGet(() -> storageLocationRepository.save(StorageLocation.builder()
                        .site(main)
                        .code("NOT_ASSIGNED")
                        .name("Not Assigned")
                        .hasDisplay(false)
                        .isDisplayOnly(false)
                        .displayOrder(99)
                        .build()));
        locationRepository.findByLocationCodeAndStorageLocation_Id("NA", mainNotAssignedStorage.getId())
                .orElseGet(() -> locationRepository.save(Location.builder()
                        .storageLocation(mainNotAssignedStorage)
                        .locationCode("NA")
                        .build()));

        StorageLocation secondStorage = storageLocationRepository.save(StorageLocation.builder()
                .site(second)
                .code("BOX_BINS_" + suffix)
                .name("Second Site Box Bins " + suffix)
                .hasDisplay(false)
                .isDisplayOnly(false)
                .displayOrder(1)
                .build());
        secondSiteLocation = locationRepository.save(Location.builder()
                .storageLocation(secondStorage)
                .locationCode("XSITE-" + suffix)
                .build());
    }

    @AfterEach
    void cleanup() {
        locationInventoryRepository.deleteAll();
    }

    private Product newProduct(String suffix) {
        return productRepository.save(Product.builder()
                .sku("XSITE-" + suffix)
                .name("Cross Site Location Product " + suffix)
                .category(category)
                .isActive(true)
                .quantity(0)
                .build());
    }

    @Test
    void receivingAgainstAnotherSitesLocationIsRejectedBeforeAnyWrite() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Product product = newProduct(suffix);

        Shipment shipment = shipmentService.createShipment(main.getId(), ShipmentRequestDTO.builder()
                .shipmentNumber("XSITE-SHIP-" + suffix)
                .status(ShipmentStatus.PENDING)
                .orderDate(LocalDate.now())
                .items(List.of(ShipmentItemRequestDTO.builder()
                        .itemId(product.getId())
                        .orderedQuantity(5)
                        .build()))
                .build());
        UUID shipmentItemId = shipment.getItems().get(0).getId();

        ReceiveShipmentRequestDTO request = ReceiveShipmentRequestDTO.builder()
                .actualDeliveryDate(LocalDate.now())
                .itemReceipts(List.of(ReceiveShipmentRequestDTO.ItemReceiptDTO.builder()
                        .shipmentItemId(shipmentItemId)
                        .allocations(List.of(ReceiveShipmentRequestDTO.DestinationAllocationDTO.builder()
                                .locationType(LocationType.BOX_BIN)
                                .locationId(secondSiteLocation.getId())
                                .quantity(5)
                                .build()))
                        .build()))
                .build();

        // Authorized for MAIN, but the allocation names a location that belongs to `second`.
        assertThatThrownBy(() -> shipmentService.receiveShipment(main.getId(), shipment.getId(), request))
                .isInstanceOf(LocationNotFoundException.class);

        assertThat(locationInventoryRepository.findByLocation_IdAndProduct_Id(secondSiteLocation.getId(), product.getId()))
                .as("no stock must have been written at the foreign-site location")
                .isEmpty();
    }

    @Test
    void createShipmentRejectsAForeignSiteDestinationLocation() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Product product = newProduct(suffix);

        ShipmentRequestDTO request = ShipmentRequestDTO.builder()
                .shipmentNumber("XSITE-CREATE-" + suffix)
                .status(ShipmentStatus.PENDING)
                .orderDate(LocalDate.now())
                .items(List.of(ShipmentItemRequestDTO.builder()
                        .itemId(product.getId())
                        .orderedQuantity(5)
                        .destinationLocationType(LocationType.BOX_BIN)
                        .destinationLocationId(secondSiteLocation.getId())
                        .build()))
                .build();

        // Authorized for MAIN, but the item names a destination location that belongs to `second`.
        assertThatThrownBy(() -> shipmentService.createShipment(main.getId(), request))
                .isInstanceOf(LocationNotFoundException.class);
    }

    @Test
    void undoRejectsWhenAnAllocationsRecordedLocationBelongsToAnotherSite() {
        // Simulates legacy data: V70 inherited the shipment's site onto every item/allocation
        // without validating each allocation's own location, so a pre-existing allocation row can
        // still carry site=MAIN alongside a locationId that genuinely belongs to `second`. Receive
        // itself can no longer create this shape (see the rejection test above); this proves undo
        // is defended against it anyway, for data that already existed before this fix shipped.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Product product = newProduct(suffix);

        Shipment shipment = shipmentService.createShipment(main.getId(), ShipmentRequestDTO.builder()
                .shipmentNumber("XSITE-UNDO-" + suffix)
                .status(ShipmentStatus.PENDING)
                .orderDate(LocalDate.now())
                .items(List.of(ShipmentItemRequestDTO.builder()
                        .itemId(product.getId())
                        .orderedQuantity(5)
                        .build()))
                .build());
        UUID shipmentItemId = shipment.getItems().get(0).getId();

        // A legitimate receive at MAIN's own location first, so there is something to undo.
        shipmentService.receiveShipment(main.getId(), shipment.getId(), ReceiveShipmentRequestDTO.builder()
                .actualDeliveryDate(LocalDate.now())
                .itemReceipts(List.of(ReceiveShipmentRequestDTO.ItemReceiptDTO.builder()
                        .shipmentItemId(shipmentItemId)
                        .allocations(List.of(ReceiveShipmentRequestDTO.DestinationAllocationDTO.builder()
                                .locationType(LocationType.BOX_BIN)
                                .locationId(mainSiteLocation.getId())
                                .quantity(5)
                                .build()))
                        .build()))
                .build());

        // Corrupt the persisted allocation's locationId to the foreign-site location, reproducing
        // the legacy shape directly (not through any service method, which now rejects it).
        ShipmentItem shipmentItem = shipmentItemRepository.findById(shipmentItemId).orElseThrow();
        List<ShipmentItemAllocation> allocations = shipmentItemAllocationRepository.findByShipmentItem_Id(shipmentItemId);
        assertThat(allocations).hasSize(1);
        ShipmentItemAllocation allocation = allocations.get(0);
        allocation.setLocationId(secondSiteLocation.getId());
        shipmentItemAllocationRepository.save(allocation);

        assertThatThrownBy(() -> shipmentService.undoReceiveShipmentItem(main.getId(), shipment.getId(), shipmentItemId, null, "Test Actor"))
                .isInstanceOf(com.mirai.inventoryservice.inventory.domain.InsufficientInventoryException.class);

        assertThat(locationInventoryRepository.findByLocation_IdAndProduct_Id(mainSiteLocation.getId(), product.getId()))
                .as("the real MAIN-site stock must be untouched by the rejected undo")
                .hasValueSatisfying(inv -> assertThat(inv.getQuantity()).isEqualTo(5));
    }

    /**
     * Review finding (.specs/phase-7-shipments T-4): an allocation with {@code locationType =
     * NOT_ASSIGNED} but a (nonsensical, possibly foreign-site) {@code locationId} was persisted
     * with that raw id unvalidated -- the actual stock write always went to the caller's own
     * NOT_ASSIGNED bucket (site-safe), but the allocation row itself carried an unchecked foreign
     * reference. Proves the fix: the stored {@code locationId} is normalized to null whenever
     * {@code locationType} is NOT_ASSIGNED, regardless of what the request sent, using the new
     * multi-allocation request form.
     */
    @Test
    void notAssignedAllocationNormalizesAStrayForeignSiteLocationId_multiAllocationForm() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Product product = newProduct(suffix);

        Shipment shipment = shipmentService.createShipment(main.getId(), ShipmentRequestDTO.builder()
                .shipmentNumber("XSITE-NA-" + suffix)
                .status(ShipmentStatus.PENDING)
                .orderDate(LocalDate.now())
                .items(List.of(ShipmentItemRequestDTO.builder()
                        .itemId(product.getId())
                        .orderedQuantity(5)
                        .build()))
                .build());
        UUID shipmentItemId = shipment.getItems().get(0).getId();

        shipmentService.receiveShipment(main.getId(), shipment.getId(), ReceiveShipmentRequestDTO.builder()
                .actualDeliveryDate(LocalDate.now())
                .itemReceipts(List.of(ReceiveShipmentRequestDTO.ItemReceiptDTO.builder()
                        .shipmentItemId(shipmentItemId)
                        .allocations(List.of(ReceiveShipmentRequestDTO.DestinationAllocationDTO.builder()
                                .locationType(LocationType.NOT_ASSIGNED)
                                .locationId(secondSiteLocation.getId())
                                .quantity(5)
                                .build()))
                        .build()))
                .build());

        List<ShipmentItemAllocation> allocations = shipmentItemAllocationRepository.findByShipmentItem_Id(shipmentItemId);
        assertThat(allocations).hasSize(1);
        assertThat(allocations.get(0).getLocationId())
                .as("a NOT_ASSIGNED allocation must never persist a stray locationId")
                .isNull();
        assertThat(locationInventoryRepository.findByLocation_IdAndProduct_Id(secondSiteLocation.getId(), product.getId()))
                .as("no stock must have been written at the foreign-site location")
                .isEmpty();
    }

    /**
     * Same normalization, exercised through the legacy single-destination receive request form
     * (no {@code allocations} list; {@code destinationLocationType}/{@code destinationLocationId}
     * on the receipt itself).
     */
    @Test
    void notAssignedAllocationNormalizesAStrayForeignSiteLocationId_legacyForm() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Product product = newProduct(suffix);

        Shipment shipment = shipmentService.createShipment(main.getId(), ShipmentRequestDTO.builder()
                .shipmentNumber("XSITE-NA-LEGACY-" + suffix)
                .status(ShipmentStatus.PENDING)
                .orderDate(LocalDate.now())
                .items(List.of(ShipmentItemRequestDTO.builder()
                        .itemId(product.getId())
                        .orderedQuantity(5)
                        .build()))
                .build());
        UUID shipmentItemId = shipment.getItems().get(0).getId();

        shipmentService.receiveShipment(main.getId(), shipment.getId(), ReceiveShipmentRequestDTO.builder()
                .actualDeliveryDate(LocalDate.now())
                .itemReceipts(List.of(ReceiveShipmentRequestDTO.ItemReceiptDTO.builder()
                        .shipmentItemId(shipmentItemId)
                        .destinationLocationType(LocationType.NOT_ASSIGNED)
                        .destinationLocationId(secondSiteLocation.getId())
                        .receivedQuantity(5)
                        .build()))
                .build());

        List<ShipmentItemAllocation> allocations = shipmentItemAllocationRepository.findByShipmentItem_Id(shipmentItemId);
        assertThat(allocations).hasSize(1);
        assertThat(allocations.get(0).getLocationId())
                .as("a NOT_ASSIGNED allocation must never persist a stray locationId, even via the legacy request form")
                .isNull();
        assertThat(locationInventoryRepository.findByLocation_IdAndProduct_Id(secondSiteLocation.getId(), product.getId()))
                .as("no stock must have been written at the foreign-site location")
                .isEmpty();
    }
}
