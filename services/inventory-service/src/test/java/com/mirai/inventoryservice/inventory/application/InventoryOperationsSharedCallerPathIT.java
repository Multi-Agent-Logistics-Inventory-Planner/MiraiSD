package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.KujiType;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.dtos.requests.ReceiveShipmentRequestDTO;
import com.mirai.inventoryservice.dtos.requests.ShipmentItemRequestDTO;
import com.mirai.inventoryservice.dtos.requests.ShipmentRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.CloseKujiBoxRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.NewKujiBoxTierDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.OpenKujiBoxRequestDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiBoxResponseDTO;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import com.mirai.inventoryservice.models.shipment.Shipment;
import com.mirai.inventoryservice.services.KujiBoxService;
import com.mirai.inventoryservice.services.ShipmentService;
import com.mirai.inventoryservice.sites.domain.Location;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * .specs/phase-6-inventory/log.md T-5 (6a task list test plan): proves the find-or-create/
 * delete-on-zero {@code location_inventory} path {@link InventoryOperations} now shares between
 * {@link ShipmentService} and {@link KujiBoxService} behaves correctly against a real database
 * for both callers — not just against {@link InventoryOperationsTest}'s mocks. Drives each
 * caller's real, already-production-reachable public API (no synthetic harness, unlike
 * {@code InventoryOperationsCallerTransactionIT}, which proves transaction propagation rather
 * than this end-state correctness) end to end:
 *
 * <ul>
 *   <li>{@code ShipmentService.receiveShipment} find-or-creates a {@code LocationInventory} row
 *       at the destination, then {@code undoReceiveShipmentItem} reverses the full received
 *       quantity, deleting that row (delete-on-zero).</li>
 *   <li>{@code KujiBoxService.openBox}'s source-removal branch decrements a pre-seeded source row
 *       to exactly zero, deleting it (delete-on-zero); {@code closeBox}'s leftover-transfer-out
 *       branch then find-or-creates a fresh row at a different destination that never had
 *       inventory before (find-or-create).</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class InventoryOperationsSharedCallerPathIT {

    @Autowired private ShipmentService shipmentService;
    @Autowired private KujiBoxService kujiBoxService;
    @Autowired private LocationInventoryRepository locationInventoryRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SiteRepository siteRepository;

    private Category category;
    private Site site;
    private StorageLocation boxBins;

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        category = categoryRepository.save(Category.builder()
                .name("Shared Path Test Category " + suffix)
                .slug("shared-path-test-category-" + suffix)
                .build());

        site = siteRepository.findByCode("MAIN")
                .orElseGet(() -> siteRepository.save(Site.builder().code("MAIN").name("Main").build()));

        boxBins = storageLocationRepository.findByCodeAndSite_Code("BOX_BINS", "MAIN")
                .orElseGet(() -> storageLocationRepository.save(StorageLocation.builder()
                        .site(site)
                        .code("BOX_BINS")
                        .name("Box Bins")
                        .hasDisplay(false)
                        .isDisplayOnly(false)
                        .displayOrder(1)
                        .build()));
    }

    @AfterEach
    void cleanup() {
        locationInventoryRepository.deleteAll();
    }

    private Location newLocation(String suffix) {
        return locationRepository.save(Location.builder()
                .storageLocation(boxBins)
                .locationCode("SHARED-" + suffix)
                .build());
    }

    private Product newProduct(String suffix) {
        return productRepository.save(Product.builder()
                .sku("SHARED-" + suffix)
                .name("Shared Path Test Product " + suffix)
                .category(category)
                .isActive(true)
                .quantity(0)
                .build());
    }

    @Test
    void shipmentService_receiveThenFullyUndo_findsOrCreatesThenDeletesOnZero() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Product product = newProduct("ship-" + suffix);
        Location destination = newLocation("ship-dest-" + suffix);

        assertThat(locationInventoryRepository.findByLocation_IdAndProduct_Id(destination.getId(), product.getId()))
                .isEmpty();

        Shipment shipment = shipmentService.createShipment(ShipmentRequestDTO.builder()
                .shipmentNumber("SHARED-SHIP-" + suffix)
                .status(ShipmentStatus.PENDING)
                .orderDate(LocalDate.now())
                .items(List.of(ShipmentItemRequestDTO.builder()
                        .itemId(product.getId())
                        .orderedQuantity(5)
                        .build()))
                .build());
        UUID shipmentItemId = shipment.getItems().get(0).getId();

        // Receive: no LocationInventory row exists yet at `destination` -- addToInventory must
        // find-or-create one, not assume it already exists.
        shipmentService.receiveShipment(shipment.getId(), ReceiveShipmentRequestDTO.builder()
                .actualDeliveryDate(LocalDate.now())
                .itemReceipts(List.of(ReceiveShipmentRequestDTO.ItemReceiptDTO.builder()
                        .shipmentItemId(shipmentItemId)
                        .allocations(List.of(ReceiveShipmentRequestDTO.DestinationAllocationDTO.builder()
                                .locationType(LocationType.BOX_BIN)
                                .locationId(destination.getId())
                                .quantity(5)
                                .build()))
                        .build()))
                .build());

        LocationInventory afterReceive = locationInventoryRepository
                .findByLocation_IdAndProduct_Id(destination.getId(), product.getId())
                .orElseThrow(() -> new AssertionError("receiveShipment must have created the LocationInventory row"));
        assertThat(afterReceive.getQuantity()).isEqualTo(5);

        // Undo the full receipt: removeFromInventory must delete the row once it drains to zero,
        // not leave a zero-quantity row behind.
        shipmentService.undoReceiveShipmentItem(shipment.getId(), shipmentItemId, null, "Test Actor");

        Optional<LocationInventory> afterUndo = locationInventoryRepository
                .findByLocation_IdAndProduct_Id(destination.getId(), product.getId());
        assertThat(afterUndo).isEmpty();
    }

    @Test
    void kujiBoxService_openThenClose_deletesSourceOnZeroThenFindsOrCreatesDestination() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Product parentProduct = newProduct("kuji-parent-" + suffix);
        parentProduct.setKujiType(KujiType.CUSTOM);
        parentProduct = productRepository.save(parentProduct);
        Product prizeProduct = newProduct("kuji-prize-" + suffix);
        Location boxLocation = newLocation("kuji-box-" + suffix);
        Location sourceLocation = newLocation("kuji-src-" + suffix);
        Location destinationLocation = newLocation("kuji-dest-" + suffix);

        // Seed source inventory at exactly the tier's total quantity, so openBox's source
        // removal drains it to zero.
        locationInventoryRepository.save(LocationInventory.builder()
                .location(sourceLocation).site(site).product(prizeProduct).quantity(5).build());
        // Destination never had inventory for this product -- closeBox's leftover transfer-out
        // must find-or-create, not assume the row exists.
        assertThat(locationInventoryRepository
                .findByLocation_IdAndProduct_Id(destinationLocation.getId(), prizeProduct.getId()))
                .isEmpty();

        UUID actorId = UUID.randomUUID();
        KujiBoxResponseDTO opened = kujiBoxService.openBox(OpenKujiBoxRequestDTO.builder()
                .productId(parentProduct.getId())
                .locationId(boxLocation.getId())
                .actorId(actorId)
                .tiers(List.of(NewKujiBoxTierDTO.builder()
                        .label("Tier A")
                        .linkedProductId(prizeProduct.getId())
                        .sourceLocationId(sourceLocation.getId())
                        .activeCount(5)
                        .inactiveCount(0)
                        .build()))
                .build());

        // openBox's source-removal branch drained the source row to exactly zero -- delete-on-zero.
        assertThat(locationInventoryRepository
                .findByLocation_IdAndProduct_Id(sourceLocation.getId(), prizeProduct.getId()))
                .isEmpty();

        UUID tierId = opened.getTiers().get(0).getId();

        kujiBoxService.closeBox(opened.getId(), CloseKujiBoxRequestDTO.builder()
                .actorId(actorId)
                .transferOutTargets(List.of(CloseKujiBoxRequestDTO.TierTransferDestination.builder()
                        .tierId(tierId)
                        .destinationLocationId(destinationLocation.getId())
                        .build()))
                .build());

        // closeBox's leftover-transfer-out branch created a brand-new row at the destination --
        // find-or-create.
        LocationInventory atDestination = locationInventoryRepository
                .findByLocation_IdAndProduct_Id(destinationLocation.getId(), prizeProduct.getId())
                .orElseThrow(() -> new AssertionError("closeBox must have created the destination LocationInventory row"));
        assertThat(atDestination.getQuantity()).isEqualTo(5);
    }
}
