package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.dtos.requests.BatchDisplaySwapRequestDTO;
import com.mirai.inventoryservice.dtos.requests.RenewDisplayRequestDTO;
import com.mirai.inventoryservice.dtos.requests.SwapMachineDisplayRequestDTO;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import com.mirai.inventoryservice.models.MachineDisplay;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.repositories.AuditLogRepository;
import com.mirai.inventoryservice.repositories.MachineDisplayRepository;
import com.mirai.inventoryservice.repositories.NotificationRepository;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.sites.infrastructure.LocationRepository;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import com.mirai.inventoryservice.sites.infrastructure.StorageLocationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * .specs/phase-6-inventory 6c, T-6c-4 P1 fix (2026-09-12 review, two rounds): several
 * {@link MachineDisplayService} methods look a display up globally by id and then mutate it
 * without checking that it actually belongs to the machine the caller claims. Round 1 fixed
 * {@code batchSwapDisplay}'s {@code displayIdsFromTarget}/{@code displayIdsToTarget} branches.
 * Round 2's review reproduced the same class of bug via {@code displayIdsToRemove} (naming MAIN
 * machines while supplying a foreign-site display id still ended and attributed the change to
 * MAIN) — fixed the same way, and a proactive sweep of every other global-by-id display lookup in
 * the class found the identical bug in {@code renewDisplays} and {@code swapDisplay} too, fixed
 * before a third review round could reproduce them independently. {@code batchClearDisplays} was
 * checked and found NOT to have this bug: it derives the expected machine from the displays
 * themselves (the first display's machine, matched against the rest) rather than accepting a
 * separately claimed machine id, so there is no caller-supplied "expected machine" for a mismatch
 * to slip past.
 *
 * <p>This is the required persisted, two-site integration proof for all four fixed paths: real
 * Postgres-free H2 rows (no native SQL is involved in this path, matching {@code
 * InventoryOperationsSiteIT}'s reasoning for using {@code @ActiveProfiles("test")} rather than
 * Testcontainers), a real {@link MachineDisplayService} bean (not mocks), and assertions that each
 * bypass attempt is rejected with the display, its stock movements, its audit trail and its
 * notifications all left exactly as they were.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class MachineDisplayServiceCrossSiteSwapIT {

    @Autowired private MachineDisplayService machineDisplayService;
    @Autowired private MachineDisplayRepository machineDisplayRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SiteRepository siteRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private NotificationRepository notificationRepository;

    @AfterEach
    void cleanup() {
        machineDisplayRepository.deleteAll();
    }

    @Test
    void batchSwapDisplay_rejectsForeignSiteDisplayId_inDisplayIdsFromTarget() {
        Fixture f = newFixture();
        MachineDisplay foreignDisplay = saveDisplay(f.foreignMachine, f.product);

        // Bypass attempt: machineId/targetMachineId are both MAIN (passes requireSameSite), but
        // the display named in displayIdsFromTarget actually belongs to the SECOND-site machine,
        // not to targetMachineId (machineB).
        BatchDisplaySwapRequestDTO request = BatchDisplaySwapRequestDTO.builder()
                .machineId(f.machineA.getId())
                .locationType(LocationType.SINGLE_CLAW_MACHINE)
                .targetMachineId(f.machineB.getId())
                .targetLocationType(LocationType.SINGLE_CLAW_MACHINE)
                .displayIdsFromTarget(List.of(foreignDisplay.getId()))
                .build();

        assertRejectedWithNoSideEffects(request, foreignDisplay, f.machineA.getId(),
                "does not belong to target machine");
    }

    @Test
    void batchSwapDisplay_rejectsForeignSiteDisplayId_inDisplayIdsToTarget() {
        Fixture f = newFixture();
        MachineDisplay foreignDisplay = saveDisplay(f.foreignMachine, f.product);

        // Bypass attempt: machineId/targetMachineId are both MAIN, but the display named in
        // displayIdsToTarget actually belongs to the SECOND-site machine, not to machineA.
        BatchDisplaySwapRequestDTO request = BatchDisplaySwapRequestDTO.builder()
                .machineId(f.machineA.getId())
                .locationType(LocationType.SINGLE_CLAW_MACHINE)
                .targetMachineId(f.machineB.getId())
                .targetLocationType(LocationType.SINGLE_CLAW_MACHINE)
                .displayIdsToTarget(List.of(foreignDisplay.getId()))
                .build();

        assertRejectedWithNoSideEffects(request, foreignDisplay, f.machineB.getId(),
                "does not belong to source machine");
    }

    @Test
    void batchSwapDisplay_rejectsForeignSiteDisplayId_inDisplayIdsToRemove() {
        Fixture f = newFixture();
        MachineDisplay foreignDisplay = saveDisplay(f.foreignMachine, f.product);

        // Reproduces the round-2 review finding: naming only MAIN machines (no target machine at
        // all) while supplying a foreign-site display id in displayIdsToRemove ended and
        // attributed the change to MAIN, because requireSameSite is only checked when a
        // targetMachineId is present -- displayIdsToRemove has no site guard of its own at all,
        // only the per-display ownership check added by this fix.
        BatchDisplaySwapRequestDTO request = BatchDisplaySwapRequestDTO.builder()
                .machineId(f.machineA.getId())
                .locationType(LocationType.SINGLE_CLAW_MACHINE)
                .displayIdsToRemove(List.of(foreignDisplay.getId()))
                .build();

        assertRejectedWithNoSideEffects(request, foreignDisplay, f.machineA.getId(),
                "does not belong to source machine");
    }

    @Test
    void renewDisplays_rejectsForeignSiteDisplayId() {
        Fixture f = newFixture();
        MachineDisplay foreignDisplay = saveDisplay(f.foreignMachine, f.product);
        long movementsBefore = stockMovementRepository.count();
        long auditLogsBefore = auditLogRepository.count();
        long notificationsBefore = notificationRepository.count();

        RenewDisplayRequestDTO request = RenewDisplayRequestDTO.builder()
                .machineId(f.machineA.getId())
                .locationType(LocationType.SINGLE_CLAW_MACHINE)
                .displayIds(List.of(foreignDisplay.getId()))
                .build();

        assertThatThrownBy(() -> machineDisplayService.renewDisplays(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to machine");

        MachineDisplay reloaded = machineDisplayRepository.findById(foreignDisplay.getId()).orElseThrow();
        assertThat(reloaded.getEndedAt()).isNull();
        assertThat(machineDisplayRepository.findActiveByLocationTypeAndMachineId(
                LocationType.SINGLE_CLAW_MACHINE, f.machineA.getId())).isEmpty();
        assertThat(stockMovementRepository.count()).isEqualTo(movementsBefore);
        assertThat(auditLogRepository.count()).isEqualTo(auditLogsBefore);
        assertThat(notificationRepository.count()).isEqualTo(notificationsBefore);
    }

    @Test
    void swapDisplay_rejectsForeignSiteOutgoingDisplayId() {
        Fixture f = newFixture();
        MachineDisplay foreignDisplay = saveDisplay(f.foreignMachine, f.product);
        Product incomingProduct = productRepository.save(Product.builder()
                .sku("SWAP-INCOMING-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Incoming Product")
                .category(f.category)
                .isActive(true)
                .quantity(0)
                .build());
        long movementsBefore = stockMovementRepository.count();
        long auditLogsBefore = auditLogRepository.count();
        long notificationsBefore = notificationRepository.count();

        SwapMachineDisplayRequestDTO request = SwapMachineDisplayRequestDTO.builder()
                .machineId(f.machineA.getId())
                .locationType(LocationType.SINGLE_CLAW_MACHINE)
                .outgoingDisplayId(foreignDisplay.getId())
                .incomingProductId(incomingProduct.getId())
                .build();

        assertThatThrownBy(() -> machineDisplayService.swapDisplay(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to machine");

        MachineDisplay reloaded = machineDisplayRepository.findById(foreignDisplay.getId()).orElseThrow();
        assertThat(reloaded.getEndedAt()).isNull();
        assertThat(machineDisplayRepository.findActiveByLocationTypeAndMachineId(
                LocationType.SINGLE_CLAW_MACHINE, f.machineA.getId())).isEmpty();
        assertThat(stockMovementRepository.count()).isEqualTo(movementsBefore);
        assertThat(auditLogRepository.count()).isEqualTo(auditLogsBefore);
        assertThat(notificationRepository.count()).isEqualTo(notificationsBefore);
    }

    private void assertRejectedWithNoSideEffects(
            BatchDisplaySwapRequestDTO request,
            MachineDisplay foreignDisplay,
            UUID machineExpectedEmpty,
            String expectedMessageFragment
    ) {
        long movementsBefore = stockMovementRepository.count();
        long auditLogsBefore = auditLogRepository.count();
        long notificationsBefore = notificationRepository.count();

        assertThatThrownBy(() -> machineDisplayService.batchSwapDisplay(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessageFragment);

        MachineDisplay reloaded = machineDisplayRepository.findById(foreignDisplay.getId()).orElseThrow();
        assertThat(reloaded.getEndedAt()).isNull();
        assertThat(reloaded.getMachineId()).isEqualTo(foreignDisplay.getMachineId());
        assertThat(machineDisplayRepository.findActiveByLocationTypeAndMachineId(
                LocationType.SINGLE_CLAW_MACHINE, machineExpectedEmpty))
                .isEmpty(); // no display was ever created at the machine the bypass targeted

        assertThat(stockMovementRepository.count()).isEqualTo(movementsBefore);
        assertThat(auditLogRepository.count()).isEqualTo(auditLogsBefore);
        assertThat(notificationRepository.count()).isEqualTo(notificationsBefore);
    }

    /** Two MAIN machines (A, B) plus a foreign SECOND-site machine, and a shared category. */
    private record Fixture(Location machineA, Location machineB, Location foreignMachine,
                            Category category, Product product) {}

    private Fixture newFixture() {
        Site main = siteRepository.findByCode("MAIN")
                .orElseGet(() -> siteRepository.save(Site.builder().code("MAIN").name("Main").build()));
        Site second = siteRepository.findByCode("SECOND")
                .orElseGet(() -> siteRepository.save(Site.builder().code("SECOND").name("Second").build()));
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Location machineA = saveMachine(main, "SWAP-A-" + suffix);
        Location machineB = saveMachine(main, "SWAP-B-" + suffix);
        Location foreignMachine = saveMachine(second, "SWAP-FOREIGN-" + suffix);

        Category category = categoryRepository.save(Category.builder()
                .name("Swap Category " + suffix)
                .slug("swap-category-" + suffix)
                .build());
        Product product = productRepository.save(Product.builder()
                .sku("SWAP-" + suffix)
                .name("Swap Product " + suffix)
                .category(category)
                .isActive(true)
                .quantity(0)
                .build());

        return new Fixture(machineA, machineB, foreignMachine, category, product);
    }

    private MachineDisplay saveDisplay(Location machine, Product product) {
        return machineDisplayRepository.save(MachineDisplay.builder()
                .location(machine)
                .locationType(LocationType.SINGLE_CLAW_MACHINE)
                .machineId(machine.getId())
                .product(product)
                .startedAt(OffsetDateTime.now())
                .build());
    }

    private Location saveMachine(Site site, String code) {
        StorageLocation storage = storageLocationRepository.save(StorageLocation.builder()
                .site(site)
                .code(code)
                .name("Machine " + code)
                .hasDisplay(true)
                .isDisplayOnly(true)
                .displayOrder(0)
                .build());
        return locationRepository.save(Location.builder()
                .storageLocation(storage)
                .locationCode(code)
                .build());
    }
}
