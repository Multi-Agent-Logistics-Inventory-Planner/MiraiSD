package com.mirai.inventoryservice.inventory.api;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * .specs/phase-6-inventory 6c, T-6c-12 checkpoint self-review finding: proves that a transfer's
 * *implicit* {@code destinationLocationId} naming a location at a different site than the trusted
 * {@code AuthorizedSiteContext} is rejected with no permanent write, closing a test-coverage gap
 * {@code SiteInventoryMutationControllerAtomicityIT}'s cross-site test only covered for the
 * *source* inventory id.
 *
 * <p>{@code StockMovementService.transferInventory(siteId, request)} (T-6c-12) validates only the
 * source's site before delegating to the un-scoped {@code transferInventory(request)}; the
 * cross-site guard for the destination is T-6c-4's pre-existing {@code requireSameSite} inside
 * {@code executeTransfer}. That check runs *after* {@code LocationInventoryRepository
 * .insertLocationInventoryIfAbsent} has already speculatively {@code INSERT}ed a destination row
 * for a not-yet-existing (location, product) pair (review-driven fix round 2, T-6c-6 P1 findings)
 * — so a real write briefly happens against the foreign site before the whole
 * {@code @Transactional} method rolls back. Requires real Postgres (not H2, which this session
 * confirmed rejects the native {@code INSERT ... ON CONFLICT} statement outright with a syntax
 * error before {@code requireSameSite} ever runs — a false-positive pass for the wrong reason,
 * caught during this checkpoint's own self-review and fixed by moving this proof here).
 */
class SiteInventoryMutationCrossSiteDestinationIT extends BaseKafkaIntegrationTest {

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

    @AfterEach
    void cleanup() {
        commandIdempotencyRepository.deleteAll();
        eventOutboxRepository.deleteAll();
        stockMovementRepository.deleteAll();
        locationInventoryRepository.deleteAll();
        AuthorizedSiteContextHolder.clear();
    }

    @Test
    @WithMockUser(roles = {"ADMIN", "ASSISTANT_MANAGER", "EMPLOYEE"})
    void transfer_foreignSiteImplicitDestinationLocation_rollsBackTheSpeculativeInsert() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Site site = siteRepository.save(Site.builder().code("XSD-A-" + suffix).name("Source Site").build());
        Site otherSite = siteRepository.save(Site.builder().code("XSD-B-" + suffix).name("Other Site").build());
        UUID userId = UUID.randomUUID();

        Category category = categoryRepository.save(Category.builder()
                .name("Cross Site Dest Category " + suffix).slug("cross-site-dest-category-" + suffix).build());
        Product product = productRepository.save(Product.builder()
                .sku("XSITEDEST-" + suffix).name("Cross Site Dest Product")
                .category(category).isActive(true).quantity(20).build());

        StorageLocation storage = storageLocationRepository.save(StorageLocation.builder()
                .site(site).code("BOX_BINS").name("Box Bins")
                .hasDisplay(false).isDisplayOnly(false).displayOrder(1).build());
        Location location = locationRepository.save(Location.builder()
                .storageLocation(storage).locationCode("XSITE-DEST-" + suffix).build());
        LocationInventory inventory = locationInventoryRepository.save(LocationInventory.builder()
                .location(location).site(site).product(product).quantity(20).build());

        StorageLocation otherStorage = storageLocationRepository.save(StorageLocation.builder()
                .site(otherSite).code("BOX_BINS").name("Box Bins")
                .hasDisplay(false).isDisplayOnly(false).displayOrder(1).build());
        Location otherLocation = locationRepository.save(Location.builder()
                .storageLocation(otherStorage).locationCode("XSITE-DEST-OTHER-" + suffix).build());

        AuthorizedSiteContextHolder.set(new AuthorizedSiteContext(
                userId, site.getId(), "ADMIN", Set.of(), false, "test-correlation"));

        TransferInventoryRequestDTO request = TransferInventoryRequestDTO.builder()
                .sourceLocationType(LocationType.BOX_BIN)
                .sourceInventoryId(inventory.getId())
                .destinationLocationType(LocationType.BOX_BIN)
                .destinationLocationId(otherLocation.getId())
                .quantity(5)
                .build();

        assertThatThrownBy(() -> controller.transferSiteInventory(site.getId(), "key-cross-site-dest-1", request))
                .isInstanceOf(RuntimeException.class);

        // Scoped to this test's own product/inventory rather than the whole table: the shared
        // Testcontainers Postgres instance persists real commits from every other IT class in the
        // same JVM run (no enclosing rolled-back transaction here, matching
        // StockMovementOutboxAtomicityIT's javadoc), so a blanket findAll().isEmpty() would be a
        // false failure if any unrelated class left residue. No EventOutbox row can exist for a
        // StockMovement that itself was never persisted, so proving the movement absent is
        // sufficient without a separate, harder-to-scope outbox query.
        assertThat(stockMovementRepository.findByItem_IdOrderByAtDesc(product.getId())).isEmpty();
        assertThat(locationInventoryRepository.findById(inventory.getId()).orElseThrow().getQuantity()).isEqualTo(20);
        assertThat(locationInventoryRepository.findByLocation_IdAndProduct_Id(otherLocation.getId(), product.getId()))
                .isEmpty();
        assertThat(commandIdempotencyRepository.findBySiteIdAndUserIdAndIdempotencyKey(
                site.getId(), userId, "key-cross-site-dest-1")).isEmpty();
    }
}
