package com.mirai.inventoryservice.kuji.application;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.KujiType;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.displays.infrastructure.MachineDisplayRepository;
import com.mirai.inventoryservice.kuji.api.AddKujiTierRequestDTO;
import com.mirai.inventoryservice.kuji.api.KujiBoxResponseDTO;
import com.mirai.inventoryservice.kuji.api.NewKujiBoxTierDTO;
import com.mirai.inventoryservice.kuji.api.OpenKujiBoxRequestDTO;
import com.mirai.inventoryservice.kuji.domain.KujiBoxTier;
import com.mirai.inventoryservice.kuji.infrastructure.KujiBoxRepository;
import com.mirai.inventoryservice.kuji.infrastructure.KujiBoxTierRepository;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tiers must carry their box's site: the site_id column is NOT NULL once the constrain
 * migration is applied, so a tier written without it fails the whole open-box or add-tier call.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class KujiBoxTierSiteIT {

    @Autowired private KujiBoxService kujiBoxService;
    @Autowired private KujiBoxRepository kujiBoxRepository;
    @Autowired private KujiBoxTierRepository kujiBoxTierRepository;
    @Autowired private MachineDisplayRepository machineDisplayRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SiteRepository siteRepository;

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
                .locationCode("TIERSITE-" + suffix)
                .build());

        Category category = categoryRepository.save(Category.builder()
                .name("Tier Site Test Category " + suffix)
                .slug("tier-site-test-category-" + suffix)
                .build());

        product = productRepository.save(Product.builder()
                .sku("TIERSITE-" + suffix)
                .name("Tier Site Test Product")
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
    void openBox_writesEachTierWithTheBoxSite() {
        KujiBoxResponseDTO opened = openBoxWithOneTier();

        List<KujiBoxTier> tiers = tiersOf(opened.getId());

        assertThat(tiers).hasSize(1);
        assertThat(tiers.get(0).getSiteId()).isEqualTo(site.getId());
    }

    @Test
    void addTier_writesTheTierWithTheBoxSite() {
        KujiBoxResponseDTO opened = openBoxWithOneTier();

        kujiBoxService.addTier(site.getId(), opened.getId(), AddKujiTierRequestDTO.builder()
                .actorId(UUID.randomUUID())
                .label("Added Tier")
                .autoCreate(true)
                .productName("Added Prize " + UUID.randomUUID().toString().substring(0, 8))
                .activeCount(2)
                .inactiveCount(0)
                .build());

        List<KujiBoxTier> tiers = tiersOf(opened.getId());

        assertThat(tiers).hasSize(2);
        assertThat(tiers).extracting(KujiBoxTier::getSiteId).containsOnly(site.getId());
    }

    private KujiBoxResponseDTO openBoxWithOneTier() {
        return kujiBoxService.openBox(site.getId(), OpenKujiBoxRequestDTO.builder()
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
                .build());
    }

    private List<KujiBoxTier> tiersOf(UUID boxId) {
        return kujiBoxTierRepository.findAll().stream()
                .filter(t -> boxId.equals(t.getBox().getId()))
                .toList();
    }
}
