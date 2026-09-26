package com.mirai.inventoryservice.displays.application;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.KujiType;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.displays.domain.MachineDisplay;
import com.mirai.inventoryservice.displays.infrastructure.MachineDisplayRepository;
import com.mirai.inventoryservice.dtos.requests.kuji.CloseKujiBoxRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.NewKujiBoxTierDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.OpenKujiBoxRequestDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiBoxResponseDTO;
import com.mirai.inventoryservice.models.enums.KujiBoxStatus;
import com.mirai.inventoryservice.repositories.KujiBoxRepository;
import com.mirai.inventoryservice.services.KujiBoxService;
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
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * .specs/phase-7-displays/log.md T-3: proves {@link DisplayOperations}'s MANDATORY-propagation
 * contract (it never opens its own transaction, matching {@code InventoryOperationsCallerTransactionIT}'s
 * precedent for the same claim about {@code InventoryOperations}), plus its own write behavior
 * (site derivation, idempotent attach, ends-if-active) and the real {@code KujiBoxService}
 * open/close round trip that is its only production caller.
 *
 * <p>Deliberately does NOT extend {@code BaseIntegrationTest} for the same reason
 * {@code InventoryOperationsCallerTransactionIT} doesn't: the rollback-together assertion needs a
 * real, distinct transaction boundary per test, not one shared outer rolled-back transaction.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(DisplayOperationsIT.CallerTransactionHarness.class)
class DisplayOperationsIT {

    @Autowired private DisplayOperations displayOperations;
    @Autowired private KujiBoxService kujiBoxService;
    @Autowired private MachineDisplayRepository machineDisplayRepository;
    @Autowired private KujiBoxRepository kujiBoxRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SiteRepository siteRepository;
    @Autowired private CallerTransactionHarness harness;

    private Site site;
    private Location machineLocation;
    private Product product;

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        site = siteRepository.findByCode("MAIN")
                .orElseGet(() -> siteRepository.save(Site.builder().code("MAIN").name("Main").build()));

        StorageLocation clawMachines = storageLocationRepository.findByCodeAndSite_Code("SINGLE_CLAW", "MAIN")
                .orElseGet(() -> storageLocationRepository.save(StorageLocation.builder()
                        .site(site)
                        .code("SINGLE_CLAW")
                        .name("Single Claw Machines")
                        .hasDisplay(true)
                        .isDisplayOnly(true)
                        .displayOrder(1)
                        .build()));

        machineLocation = locationRepository.save(Location.builder()
                .storageLocation(clawMachines)
                .locationCode("DISPLAYOPS-" + suffix)
                .build());

        Category category = categoryRepository.save(Category.builder()
                .name("Display Ops Test Category " + suffix)
                .slug("display-ops-test-category-" + suffix)
                .build());

        product = productRepository.save(Product.builder()
                .sku("DISPLAYOPS-" + suffix)
                .name("Display Ops Test Product")
                .category(category)
                .kujiType(KujiType.CUSTOM)
                .isActive(true)
                .quantity(0)
                .build());
    }

    @AfterEach
    void cleanup() {
        kujiBoxRepository.deleteAll();
        machineDisplayRepository.deleteAll();
    }

    @Test
    void attachProductAtMachine_isIdempotentForExistingActivePair() {
        UUID first = harness.attachTwice(machineLocation, product);

        assertThat(machineDisplayRepository.findActiveByLocationTypeAndMachineId(
                com.mirai.inventoryservice.models.enums.LocationType.SINGLE_CLAW_MACHINE,
                machineLocation.getId())).hasSize(1);
        assertThat(machineDisplayRepository.findById(first)).isPresent();
    }

    @Test
    void attachProductAtMachine_setsSiteFromLocation() {
        UUID displayId = harness.attach(machineLocation, product);

        MachineDisplay saved = machineDisplayRepository.findById(displayId).orElseThrow();
        assertThat(saved.getSite()).isNotNull();
        assertThat(saved.getSite().getId()).isEqualTo(site.getId());
    }

    @Test
    void endIfActive_returnsFalseForAlreadyEndedDisplay() {
        UUID displayId = harness.attach(machineLocation, product);
        boolean firstEnd = harness.endIfActive(displayId);
        boolean secondEnd = harness.endIfActive(displayId);

        assertThat(firstEnd).isTrue();
        assertThat(secondEnd).isFalse();
    }

    @Test
    void mandatoryPropagation_throwsWhenCalledOutsideATransaction() {
        assertThatThrownBy(() -> displayOperations.requireActive(UUID.randomUUID()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void attachProductAtMachine_whenCallerTransactionFailsAfterward_rollsBack() {
        assertThatThrownBy(() -> harness.attachThenFail(machineLocation, product))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated caller failure after attachProductAtMachine");

        assertThat(machineDisplayRepository.findActiveByLocationTypeAndMachineId(
                com.mirai.inventoryservice.models.enums.LocationType.SINGLE_CLAW_MACHINE,
                machineLocation.getId())).isEmpty();
    }

    @Test
    void openBoxThenCloseBox_throughKujiBoxService_leavesTheAutoAttachedDisplayEnded() {
        OpenKujiBoxRequestDTO openRequest = OpenKujiBoxRequestDTO.builder()
                .productId(product.getId())
                .locationId(machineLocation.getId())
                .tiers(List.of(NewKujiBoxTierDTO.builder()
                        .label("Tier A")
                        .autoCreate(true)
                        .productName("Auto Prize " + UUID.randomUUID().toString().substring(0, 8))
                        .activeCount(1)
                        .inactiveCount(0)
                        .build()))
                .actorId(UUID.randomUUID())
                .build();

        KujiBoxResponseDTO opened = kujiBoxService.openBox(openRequest);
        assertThat(opened.getMachineDisplayId()).isNotNull();

        MachineDisplay attachedAfterOpen = machineDisplayRepository.findById(opened.getMachineDisplayId()).orElseThrow();
        assertThat(attachedAfterOpen.getEndedAt()).isNull();
        assertThat(attachedAfterOpen.getSite()).isNotNull();

        CloseKujiBoxRequestDTO closeRequest = CloseKujiBoxRequestDTO.builder()
                .actorId(openRequest.getActorId())
                .build();
        KujiBoxResponseDTO closed = kujiBoxService.closeBox(opened.getId(), closeRequest);

        assertThat(closed.getStatus()).isEqualTo(KujiBoxStatus.CLOSED);
        MachineDisplay attachedAfterClose = machineDisplayRepository.findById(opened.getMachineDisplayId()).orElseThrow();
        assertThat(attachedAfterClose.getEndedAt()).isNotNull();
    }

    /**
     * Stand-in for a production caller (today, only {@code KujiBoxService}): opens its own
     * transaction, calls {@link DisplayOperations} partway through, then either returns normally
     * or fails -- proving MANDATORY propagation joins the caller's transaction rather than opening
     * its own, matching {@code InventoryOperationsCallerTransactionIT.CallerTransactionHarness}.
     */
    @TestComponent
    static class CallerTransactionHarness {
        private final DisplayOperations displayOperations;

        CallerTransactionHarness(DisplayOperations displayOperations) {
            this.displayOperations = displayOperations;
        }

        @Transactional
        public UUID attach(Location location, Product product) {
            return displayOperations.attachProductAtMachine(
                    location.getId(), product.getId(), UUID.randomUUID(), OffsetDateTime.now());
        }

        @Transactional
        public UUID attachTwice(Location location, Product product) {
            UUID first = displayOperations.attachProductAtMachine(
                    location.getId(), product.getId(), UUID.randomUUID(), OffsetDateTime.now());
            UUID second = displayOperations.attachProductAtMachine(
                    location.getId(), product.getId(), UUID.randomUUID(), OffsetDateTime.now());
            if (!first.equals(second)) {
                throw new IllegalStateException("attachProductAtMachine was not idempotent");
            }
            return first;
        }

        @Transactional
        public boolean endIfActive(UUID displayId) {
            return displayOperations.endIfActive(displayId, OffsetDateTime.now());
        }

        @Transactional
        public void attachThenFail(Location location, Product product) {
            displayOperations.attachProductAtMachine(
                    location.getId(), product.getId(), UUID.randomUUID(), OffsetDateTime.now());
            throw new RuntimeException("simulated caller failure after attachProductAtMachine");
        }
    }
}
