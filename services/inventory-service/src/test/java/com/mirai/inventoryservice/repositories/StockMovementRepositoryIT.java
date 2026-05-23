package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import com.mirai.inventoryservice.models.Category;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.enums.KujiBoxStatus;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.kuji.KujiBox;
import com.mirai.inventoryservice.models.kuji.KujiBoxTier;
import com.mirai.inventoryservice.models.Site;
import com.mirai.inventoryservice.models.storage.Location;
import com.mirai.inventoryservice.models.storage.StorageLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link StockMovementRepository#aggregateKujiDailyPayouts}, the
 * native query backing the "Value paid out per day" chart. Exercises the snapshot-prefer
 * COALESCE ordering against real Postgres JSONB so we catch any SQL syntax or operator
 * regressions H2 can't surface.
 */
class StockMovementRepositoryIT extends BaseKafkaIntegrationTest {

    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SiteRepository siteRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private KujiBoxRepository kujiBoxRepository;
    @Autowired private KujiBoxTierRepository kujiBoxTierRepository;

    @Test
    @DisplayName("aggregate prefers metadata.unit_value snapshot over live tier.price")
    void aggregatePrefersSnapshotOverLiveTierPrice() {
        TierFixture fixture = seedTierWithPrice(new BigDecimal("99.00"));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // Snapshot row: drew 2 slips at $25 each (snapshotted). Live tier price is $99 —
        // the snapshot must win, contributing 2 × 25 = 50.
        saveKujiPrizeWon(fixture.boxId, fixture.tier.getId(), fixture.parent, now, 2, new BigDecimal("25.00"));
        // Legacy row: 3 slips with no unit_value snapshot, so the SQL must fall through
        // to the live tier.price ($99), contributing 3 × 99 = 297.
        saveKujiPrizeWon(fixture.boxId, fixture.tier.getId(), fixture.parent, now, 3, null);

        LocalDate today = now.toLocalDate();
        List<Object[]> rows = stockMovementRepository.aggregateKujiDailyPayouts(
                fixture.boxId, today.minusDays(1), today.plusDays(1), "UTC");

        assertThat(rows).hasSize(1);
        int slipCount = ((Number) rows.get(0)[1]).intValue();
        BigDecimal valueWon = (BigDecimal) rows.get(0)[2];
        assertThat(slipCount).isEqualTo(5);
        assertThat(valueWon).isEqualByComparingTo(new BigDecimal("347.00"));
    }

    @Test
    @DisplayName("aggregate subtracts reversal value using the reversal's own snapshot")
    void aggregateSubtractsReversalUsingSnapshot() {
        TierFixture fixture = seedTierWithPrice(new BigDecimal("99.00"));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        saveKujiPrizeWon(fixture.boxId, fixture.tier.getId(), fixture.parent, now, 2, new BigDecimal("25.00"));
        // Reversal carries its own unit_value snapshot (mirroring the original draw),
        // so subtraction uses the snapshot value not the mutated live price.
        saveKujiDrawReversed(fixture.boxId, fixture.tier.getId(), fixture.parent, now, 2, new BigDecimal("25.00"));

        // Mutate the live tier price after both rows are persisted — must not affect totals.
        fixture.tier.setPrice(new BigDecimal("9999.00"));
        kujiBoxTierRepository.save(fixture.tier);

        LocalDate today = now.toLocalDate();
        List<Object[]> rows = stockMovementRepository.aggregateKujiDailyPayouts(
                fixture.boxId, today.minusDays(1), today.plusDays(1), "UTC");

        assertThat(rows).hasSize(1);
        BigDecimal valueWon = (BigDecimal) rows.get(0)[2];
        assertThat(valueWon).isEqualByComparingTo(BigDecimal.ZERO);
        int slipCount = ((Number) rows.get(0)[1]).intValue();
        assertThat(slipCount).isZero();
    }

    // ===================== helpers =====================

    private record TierFixture(UUID boxId, KujiBoxTier tier, Product parent) {}

    private TierFixture seedTierWithPrice(BigDecimal price) {
        String catSuffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = categoryRepository.save(Category.builder()
                .name("kuji-cat-" + catSuffix)
                .slug("kuji-cat-" + catSuffix)
                .build());
        Product parent = productRepository.save(Product.builder()
                .name("Kuji Parent " + UUID.randomUUID())
                .sku("PARENT-" + UUID.randomUUID().toString().substring(0, 6))
                .category(category)
                .quantity(0)
                .reorderPoint(0)
                .build());
        String siteCode = "AGG-" + UUID.randomUUID().toString().substring(0, 4);
        Site site = siteRepository.save(Site.builder()
                .code(siteCode).name("Agg Test Site").build());
        StorageLocation storage = storageLocationRepository.save(StorageLocation.builder()
                .site(site).code("AGG-STO-" + UUID.randomUUID().toString().substring(0, 4))
                .name("Agg Storage").hasDisplay(false).isDisplayOnly(false).displayOrder(1).build());
        Location location = locationRepository.save(Location.builder()
                .storageLocation(storage)
                .locationCode("AGG-LOC-" + UUID.randomUUID().toString().substring(0, 4))
                .build());

        KujiBox box = kujiBoxRepository.save(KujiBox.builder()
                .product(parent)
                .location(location)
                .status(KujiBoxStatus.OPEN)
                .openedAt(OffsetDateTime.now().minusDays(1))
                .build());

        KujiBoxTier tier = kujiBoxTierRepository.save(KujiBoxTier.builder()
                .box(box)
                .label("Tier A")
                .activeCount(10)
                .inactiveCount(0)
                .drawnCount(0)
                .price(price)
                .build());
        return new TierFixture(box.getId(), tier, parent);
    }

    private void saveKujiPrizeWon(
            UUID boxId, UUID tierId, Product item, OffsetDateTime at, int slipQty, BigDecimal unitValue
    ) {
        saveMovement(StockMovementReason.KUJI_PRIZE_WON, boxId, tierId, item, at, slipQty, unitValue);
    }

    private void saveKujiDrawReversed(
            UUID boxId, UUID tierId, Product item, OffsetDateTime at, int slipQty, BigDecimal unitValue
    ) {
        saveMovement(StockMovementReason.KUJI_DRAW_REVERSED, boxId, tierId, item, at, slipQty, unitValue);
    }

    private void saveMovement(
            StockMovementReason reason,
            UUID boxId,
            UUID tierId,
            Product item,
            OffsetDateTime at,
            int slipQty,
            BigDecimal unitValue
    ) {
        AuditLog log = auditLogRepository.save(AuditLog.builder()
                .reason(reason)
                .itemCount(1)
                .totalQuantityMoved(slipQty)
                .productSummary("test")
                .build());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("kuji_box_id", boxId.toString());
        metadata.put("kuji_box_tier_id", tierId.toString());
        metadata.put("slip_quantity", slipQty);
        if (unitValue != null) {
            metadata.put("unit_value", unitValue);
        }
        stockMovementRepository.save(StockMovement.builder()
                .auditLog(log)
                .item(item)
                .locationType(LocationType.NOT_ASSIGNED)
                .previousQuantity(0)
                .currentQuantity(0)
                .quantityChange(0)
                .reason(reason)
                .at(at)
                .metadata(metadata)
                .build());
    }
}
