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

        StorageLocation storage = storageLocationRepository.save(StorageLocation.builder()
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
        verify(spiedStockMovementService, times(1)).batchAdjustInventory(any(UUID.class), any(BatchAdjustStockRequestDTO.class));
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
