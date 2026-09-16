package com.mirai.inventoryservice.inventory.api;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.inventory.application.StockMovementService;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.repositories.EventOutboxRepository;
import com.mirai.inventoryservice.shared.idempotency.CommandIdempotencyRepository;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContext;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContextHolder;
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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * .specs/phase-6-inventory 6c, T-6c-12: proves the v1 mutation controller's addition of
 * {@code CommandIdempotencyService} does not weaken the atomicity {@code StockMovementOutboxAtomicityIT}
 * already proved for the underlying service call — the idempotency record itself now commits or
 * rolls back together with the inventory/movement/outbox writes it guards (Q-6c-3) — and proves
 * the new same-site rejection happens before any write, exactly as {@code InventoryOperationsSiteIT}
 * and the T-6c-4 preconditions already established for the un-scoped paths.
 *
 * <p>Invokes {@link SiteInventoryMutationController}'s methods directly (not through MockMvc): this
 * class exists to prove transactional/idempotent *effects*, which is orthogonal to the HTTP
 * plumbing already covered by {@code SiteInventoryMutationControllerSecurityIT}. Manually drives
 * {@link AuthorizedSiteContextHolder} since no servlet filter runs in a direct bean-method call —
 * the same reason {@code StockMovementOutboxAtomicityIT} doesn't extend {@code BaseIntegrationTest}
 * (which would wrap every test in one outer rolled-back transaction, masking the very commit/rollback
 * behavior under test).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@WithMockUser(roles = {"ADMIN", "ASSISTANT_MANAGER", "EMPLOYEE"})
class SiteInventoryMutationControllerAtomicityIT {

    @Autowired private SiteInventoryMutationController controller;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private EventOutboxRepository eventOutboxRepository;
    @Autowired private LocationInventoryRepository locationInventoryRepository;
    @Autowired private CommandIdempotencyRepository commandIdempotencyRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SiteRepository siteRepository;

    @SpyBean private ProductRepository spiedProductRepository;
    @SpyBean private StockMovementService spiedStockMovementService;

    private Site site;
    private StorageLocation storage;
    private LocationInventory inventory;
    private UUID userId;

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        site = siteRepository.save(Site.builder().code("SITE-MUT-" + suffix).name("Mutation Site").build());
        userId = UUID.randomUUID();

        Category category = categoryRepository.save(Category.builder()
                .name("Mutation Atomicity Category " + suffix)
                .slug("mutation-atomicity-category-" + suffix)
                .build());
        Product product = productRepository.save(Product.builder()
                .sku("MUTATOM-" + suffix).name("Mutation Atomicity Product")
                .category(category).isActive(true).quantity(20).build());

        storage = storageLocationRepository.save(StorageLocation.builder()
                .site(site).code("BOX_BINS").name("Box Bins")
                .hasDisplay(false).isDisplayOnly(false).displayOrder(1).build());
        Location location = locationRepository.save(Location.builder()
                .storageLocation(storage).locationCode("MUT-" + suffix).build());

        inventory = locationInventoryRepository.save(LocationInventory.builder()
                .location(location).site(site).product(product).quantity(20).build());
    }

    @AfterEach
    void cleanup() {
        // No enclosing test transaction (see class javadoc), so clear real commits between tests.
        commandIdempotencyRepository.deleteAll();
        eventOutboxRepository.deleteAll();
        stockMovementRepository.deleteAll();
        locationInventoryRepository.deleteAll();
        AuthorizedSiteContextHolder.clear();
    }

    private void setContext() {
        AuthorizedSiteContextHolder.set(new AuthorizedSiteContext(
                userId, site.getId(), "ADMIN", Set.of(), false, "test-correlation"));
    }

    private BatchAdjustStockRequestDTO adjustRequest(int quantityChange) {
        return BatchAdjustStockRequestDTO.builder()
                .locationType(LocationType.BOX_BIN)
                .locationId(inventory.getLocation().getId())
                .reason(com.mirai.inventoryservice.models.enums.StockMovementReason.SALE)
                .adjustments(List.of(BatchAdjustLineDTO.builder()
                        .inventoryId(inventory.getId())
                        .quantityChange(quantityChange)
                        .build()))
                .build();
    }

    @Test
    void adjust_whenUnderlyingWriteFails_rollsBackInventoryMovementOutboxAndIdempotencyRecordTogether() {
        setContext();
        doThrow(new RuntimeException("simulated ProductRepository.saveAll failure"))
                .when(spiedProductRepository).saveAll(any());

        assertThatThrownBy(() -> controller.adjustSiteInventory(site.getId(), "key-fail-1", adjustRequest(-20)))
                .isInstanceOf(RuntimeException.class);

        assertThat(stockMovementRepository.findAll()).isEmpty();
        assertThat(eventOutboxRepository.findAll()).isEmpty();
        assertThat(locationInventoryRepository.findById(inventory.getId())).isPresent();
        assertThat(locationInventoryRepository.findById(inventory.getId()).orElseThrow().getQuantity()).isEqualTo(20);
        // Q-6c-3: no idempotency row survives a failed attempt.
        assertThat(commandIdempotencyRepository.findBySiteIdAndUserIdAndIdempotencyKey(
                site.getId(), userId, "key-fail-1")).isEmpty();
    }

    @Test
    void adjust_onSuccess_inventoryMovementOutboxAndIdempotencyRecordAllCommitTogether() {
        setContext();

        var response = controller.adjustSiteInventory(site.getId(), "key-success-1", adjustRequest(-5));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(stockMovementRepository.findAll()).hasSize(1);
        assertThat(eventOutboxRepository.findAll()).hasSize(1);
        assertThat(locationInventoryRepository.findById(inventory.getId()).orElseThrow().getQuantity()).isEqualTo(15);
        assertThat(commandIdempotencyRepository.findBySiteIdAndUserIdAndIdempotencyKey(
                site.getId(), userId, "key-success-1")).isPresent();
    }

    @Test
    void adjust_replayWithSameKeyAndBody_doesNotRerunTheCommandOrDuplicateEffects() {
        setContext();
        BatchAdjustStockRequestDTO request = adjustRequest(-5);

        var first = controller.adjustSiteInventory(site.getId(), "key-replay-1", request);
        var second = controller.adjustSiteInventory(site.getId(), "key-replay-1", request);

        assertThat(second.getStatusCode()).isEqualTo(first.getStatusCode());
        assertThat(stockMovementRepository.findAll()).hasSize(1);
        assertThat(eventOutboxRepository.findAll()).hasSize(1);
        assertThat(locationInventoryRepository.findById(inventory.getId()).orElseThrow().getQuantity()).isEqualTo(15);
        verify(spiedStockMovementService, times(1)).batchAdjustInventory(any(UUID.class), any(UUID.class), any(BatchAdjustStockRequestDTO.class));
    }

    @Test
    void adjust_clientSuppliedActorIdIsIgnoredInFavorOfTheAuthenticatedPrincipal() {
        setContext();
        UUID spoofedActorId = UUID.randomUUID();
        assertThat(spoofedActorId).isNotEqualTo(userId);
        BatchAdjustStockRequestDTO request = adjustRequest(-5);
        request.setActorId(spoofedActorId);

        controller.adjustSiteInventory(site.getId(), "key-actor-spoof-1", request);

        List<com.mirai.inventoryservice.inventory.domain.StockMovement> movements =
                stockMovementRepository.findByItem_IdOrderByAtDesc(inventory.getProduct().getId());
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getActorId())
                .as("persisted actor must be the authenticated principal, never the request body's actorId")
                .isEqualTo(userId)
                .isNotEqualTo(spoofedActorId);
    }

    @Test
    void transfer_clientSuppliedActorIdIsIgnoredInFavorOfTheAuthenticatedPrincipal() {
        setContext();
        Location destinationLocation = locationRepository.save(Location.builder()
                .storageLocation(storage).locationCode("MUT-DEST-" + UUID.randomUUID()).build());
        LocationInventory destination = locationInventoryRepository.save(LocationInventory.builder()
                .location(destinationLocation).site(site).product(inventory.getProduct()).quantity(0).build());
        UUID spoofedActorId = UUID.randomUUID();
        assertThat(spoofedActorId).isNotEqualTo(userId);
        TransferInventoryRequestDTO request = TransferInventoryRequestDTO.builder()
                .sourceLocationType(LocationType.BOX_BIN)
                .sourceInventoryId(inventory.getId())
                .destinationLocationType(LocationType.BOX_BIN)
                .destinationInventoryId(destination.getId())
                .quantity(3)
                .actorId(spoofedActorId)
                .build();

        controller.transferSiteInventory(site.getId(), "key-transfer-actor-spoof-1", request);

        List<com.mirai.inventoryservice.inventory.domain.StockMovement> movements =
                stockMovementRepository.findByItem_IdOrderByAtDesc(inventory.getProduct().getId());
        assertThat(movements).isNotEmpty();
        assertThat(movements)
                .as("persisted actor must be the authenticated principal, never the request body's actorId")
                .allSatisfy(m -> assertThat(m.getActorId()).isEqualTo(userId).isNotEqualTo(spoofedActorId));
    }

    @Test
    void createItem_onSuccess_persistsInventoryMovementOutboxAndIdempotencyRecordTogether() {
        setContext();
        Location location = locationRepository.save(Location.builder()
                .storageLocation(storage).locationCode("MUT-CREATE-" + UUID.randomUUID().toString().substring(0, 8)).build());
        Product product = productRepository.save(Product.builder()
                .sku("MUTCREATE-" + UUID.randomUUID()).name("Mutation Create Product")
                .category(inventory.getProduct().getCategory()).isActive(true).quantity(0).build());
        CreateLocationInventoryRequestDTO request = CreateLocationInventoryRequestDTO.builder()
                .productId(product.getId()).quantity(7).build();

        var response = controller.createSiteLocationInventoryItem(site.getId(), location.getId(), "key-create-1", request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().getQuantity()).isEqualTo(7);
        assertThat(locationInventoryRepository.findByLocation_IdAndProduct_Id(location.getId(), product.getId()))
                .isPresent();
        assertThat(stockMovementRepository.findByItem_IdOrderByAtDesc(product.getId())).hasSize(1);
        assertThat(eventOutboxRepository.findAll()).hasSize(1);
        assertThat(commandIdempotencyRepository.findBySiteIdAndUserIdAndIdempotencyKey(
                site.getId(), userId, "key-create-1")).isPresent();
    }

    @Test
    void createItem_replayWithSameKey_doesNotCreateASecondRow() {
        setContext();
        Location location = locationRepository.save(Location.builder()
                .storageLocation(storage).locationCode("MUT-CREATE-REPLAY-" + UUID.randomUUID().toString().substring(0, 8)).build());
        Product product = productRepository.save(Product.builder()
                .sku("MUTCREATEREPLAY-" + UUID.randomUUID()).name("Mutation Create Replay Product")
                .category(inventory.getProduct().getCategory()).isActive(true).quantity(0).build());
        CreateLocationInventoryRequestDTO request = CreateLocationInventoryRequestDTO.builder()
                .productId(product.getId()).quantity(4).build();

        controller.createSiteLocationInventoryItem(site.getId(), location.getId(), "key-create-replay-1", request);
        controller.createSiteLocationInventoryItem(site.getId(), location.getId(), "key-create-replay-1", request);

        assertThat(locationInventoryRepository.findByLocation_IdAndProduct_Id(location.getId(), product.getId()))
                .isPresent();
        assertThat(stockMovementRepository.findByItem_IdOrderByAtDesc(product.getId())).hasSize(1);
    }

    @Test
    void createItem_foreignSiteLocation_rejectsBeforeAnyWrite() {
        Site otherSite = siteRepository.save(Site.builder().code("SITE-MUT-CR-O-" + UUID.randomUUID().toString().substring(0, 4)).name("Other Site").build());
        AuthorizedSiteContextHolder.set(new AuthorizedSiteContext(
                userId, otherSite.getId(), "ADMIN", Set.of(), false, "test-correlation"));
        CreateLocationInventoryRequestDTO request = CreateLocationInventoryRequestDTO.builder()
                .productId(inventory.getProduct().getId()).quantity(3).build();

        assertThatThrownBy(() -> controller.createSiteLocationInventoryItem(
                otherSite.getId(), inventory.getLocation().getId(), "key-create-foreign-1", request))
                .isInstanceOf(RuntimeException.class);

        assertThat(eventOutboxRepository.findAll()).isEmpty();
        assertThat(commandIdempotencyRepository.findBySiteIdAndUserIdAndIdempotencyKey(
                otherSite.getId(), userId, "key-create-foreign-1")).isEmpty();
    }

    @Test
    void deleteItem_onSuccess_removesInventoryAndCommitsMovementOutboxAndIdempotencyRecord() {
        setContext();

        var response = controller.deleteSiteLocationInventoryItem(
                site.getId(), inventory.getLocation().getId(), inventory.getId(), "key-delete-1");

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(locationInventoryRepository.findById(inventory.getId())).isEmpty();
        assertThat(stockMovementRepository.findByItem_IdOrderByAtDesc(inventory.getProduct().getId())).hasSize(1);
        assertThat(eventOutboxRepository.findAll()).hasSize(1);
        assertThat(commandIdempotencyRepository.findBySiteIdAndUserIdAndIdempotencyKey(
                site.getId(), userId, "key-delete-1")).isPresent();
    }

    @Test
    void deleteItem_replayWithSameKey_doesNotDoubleEmit() {
        setContext();

        controller.deleteSiteLocationInventoryItem(
                site.getId(), inventory.getLocation().getId(), inventory.getId(), "key-delete-replay-1");
        controller.deleteSiteLocationInventoryItem(
                site.getId(), inventory.getLocation().getId(), inventory.getId(), "key-delete-replay-1");

        assertThat(stockMovementRepository.findByItem_IdOrderByAtDesc(inventory.getProduct().getId())).hasSize(1);
        assertThat(eventOutboxRepository.findAll()).hasSize(1);
    }

    @Test
    void deleteItem_locationRowMismatch_rejectsWithNoWrite() {
        setContext();
        Location otherLocation = locationRepository.save(Location.builder()
                .storageLocation(storage).locationCode("MUT-DEL-MISMATCH-" + UUID.randomUUID().toString().substring(0, 8)).build());

        assertThatThrownBy(() -> controller.deleteSiteLocationInventoryItem(
                site.getId(), otherLocation.getId(), inventory.getId(), "key-delete-mismatch-1"))
                .isInstanceOf(RuntimeException.class);

        assertThat(locationInventoryRepository.findById(inventory.getId())).isPresent();
        assertThat(eventOutboxRepository.findAll()).isEmpty();
    }

    @Test
    void batchTransfer_onSuccess_commitsBothMovementsOutboxAndIdempotencyRecordTogether() {
        setContext();
        Location destLocation = locationRepository.save(Location.builder()
                .storageLocation(storage).locationCode("MUT-BT-DST-" + UUID.randomUUID().toString().substring(0, 8)).build());
        LocationInventory destination = locationInventoryRepository.save(LocationInventory.builder()
                .location(destLocation).site(site).product(inventory.getProduct()).quantity(0).build());
        BatchTransferInventoryRequestDTO request = BatchTransferInventoryRequestDTO.builder()
                .transfers(List.of(TransferInventoryRequestDTO.builder()
                        .sourceLocationType(LocationType.BOX_BIN)
                        .sourceInventoryId(inventory.getId())
                        .destinationLocationType(LocationType.BOX_BIN)
                        .destinationInventoryId(destination.getId())
                        .quantity(5)
                        .build()))
                .build();

        var response = controller.batchTransferSiteInventory(site.getId(), "key-batch-transfer-1", request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(locationInventoryRepository.findById(inventory.getId()).orElseThrow().getQuantity()).isEqualTo(15);
        assertThat(locationInventoryRepository.findById(destination.getId()).orElseThrow().getQuantity()).isEqualTo(5);
        assertThat(stockMovementRepository.findByItem_IdOrderByAtDesc(inventory.getProduct().getId())).hasSize(2);
        assertThat(eventOutboxRepository.findAll()).hasSize(2);
        assertThat(commandIdempotencyRepository.findBySiteIdAndUserIdAndIdempotencyKey(
                site.getId(), userId, "key-batch-transfer-1")).isPresent();
    }

    @Test
    void batchTransfer_whenMidBatchWriteFails_rollsBackBothLinesTogether() {
        setContext();
        Location destLocation1 = locationRepository.save(Location.builder()
                .storageLocation(storage).locationCode("MUT-BT-D1-" + UUID.randomUUID().toString().substring(0, 8)).build());
        Location destLocation2 = locationRepository.save(Location.builder()
                .storageLocation(storage).locationCode("MUT-BT-D2-" + UUID.randomUUID().toString().substring(0, 8)).build());
        LocationInventory destination1 = locationInventoryRepository.save(LocationInventory.builder()
                .location(destLocation1).site(site).product(inventory.getProduct()).quantity(0).build());
        LocationInventory destination2 = locationInventoryRepository.save(LocationInventory.builder()
                .location(destLocation2).site(site).product(inventory.getProduct()).quantity(0).build());
        BatchTransferInventoryRequestDTO request = BatchTransferInventoryRequestDTO.builder()
                .transfers(List.of(
                        TransferInventoryRequestDTO.builder()
                                .sourceLocationType(LocationType.BOX_BIN).sourceInventoryId(inventory.getId())
                                .destinationLocationType(LocationType.BOX_BIN).destinationInventoryId(destination1.getId())
                                .quantity(3).build(),
                        TransferInventoryRequestDTO.builder()
                                .sourceLocationType(LocationType.BOX_BIN).sourceInventoryId(inventory.getId())
                                .destinationLocationType(LocationType.BOX_BIN).destinationInventoryId(destination2.getId())
                                .quantity(999).build()))
                .build();

        assertThatThrownBy(() -> controller.batchTransferSiteInventory(site.getId(), "key-batch-transfer-fail-1", request))
                .isInstanceOf(RuntimeException.class);

        assertThat(locationInventoryRepository.findById(inventory.getId()).orElseThrow().getQuantity()).isEqualTo(20);
        assertThat(locationInventoryRepository.findById(destination1.getId()).orElseThrow().getQuantity()).isEqualTo(0);
        assertThat(stockMovementRepository.findAll()).isEmpty();
        assertThat(eventOutboxRepository.findAll()).isEmpty();
        assertThat(commandIdempotencyRepository.findBySiteIdAndUserIdAndIdempotencyKey(
                site.getId(), userId, "key-batch-transfer-fail-1")).isEmpty();
    }

    @Test
    void batchTransfer_foreignSiteSource_rejectsBeforeAnyWrite() {
        Site otherSite = siteRepository.save(Site.builder().code("SITE-MUT-BT-O-" + UUID.randomUUID().toString().substring(0, 4)).name("Other Site").build());
        AuthorizedSiteContextHolder.set(new AuthorizedSiteContext(
                userId, otherSite.getId(), "ADMIN", Set.of(), false, "test-correlation"));
        BatchTransferInventoryRequestDTO request = BatchTransferInventoryRequestDTO.builder()
                .transfers(List.of(TransferInventoryRequestDTO.builder()
                        .sourceLocationType(LocationType.BOX_BIN)
                        .sourceInventoryId(inventory.getId())
                        .destinationLocationType(LocationType.BOX_BIN)
                        .destinationLocationId(UUID.randomUUID())
                        .quantity(1)
                        .build()))
                .build();

        assertThatThrownBy(() -> controller.batchTransferSiteInventory(otherSite.getId(), "key-batch-transfer-foreign-1", request))
                .isInstanceOf(RuntimeException.class);

        assertThat(locationInventoryRepository.findById(inventory.getId()).orElseThrow().getQuantity()).isEqualTo(20);
        assertThat(eventOutboxRepository.findAll()).isEmpty();
    }

    @Test
    void adjust_foreignSiteInventoryId_rejectsBeforeAnyWrite() {
        Site otherSite = siteRepository.save(Site.builder().code("SITE-MUT-OTHER").name("Other Site").build());
        AuthorizedSiteContextHolder.set(new AuthorizedSiteContext(
                userId, otherSite.getId(), "ADMIN", Set.of(), false, "test-correlation"));

        assertThatThrownBy(() -> controller.adjustSiteInventory(otherSite.getId(), "key-foreign-1", adjustRequest(-5)))
                .isInstanceOf(RuntimeException.class);

        assertThat(stockMovementRepository.findAll()).isEmpty();
        assertThat(eventOutboxRepository.findAll()).isEmpty();
        assertThat(locationInventoryRepository.findById(inventory.getId()).orElseThrow().getQuantity()).isEqualTo(20);
        assertThat(commandIdempotencyRepository.findBySiteIdAndUserIdAndIdempotencyKey(
                otherSite.getId(), userId, "key-foreign-1")).isEmpty();
    }
}
