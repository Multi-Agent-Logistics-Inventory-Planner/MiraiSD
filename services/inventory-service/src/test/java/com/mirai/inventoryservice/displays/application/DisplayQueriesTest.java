package com.mirai.inventoryservice.displays.application;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.displays.domain.MachineDisplay;
import com.mirai.inventoryservice.displays.infrastructure.MachineDisplayRepository;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.sites.domain.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * .specs/phase-7-displays/log.md T-3: unit coverage for the read facade external callers
 * (AnalyticsService, ProductReportBundleService) now use instead of MachineDisplayRepository
 * directly.
 */
@ExtendWith(MockitoExtension.class)
class DisplayQueriesTest {

    @Mock private MachineDisplayRepository machineDisplayRepository;

    private DisplayQueries displayQueries;

    @BeforeEach
    void setUp() {
        displayQueries = new DisplayQueries(machineDisplayRepository);
    }

    @Test
    void displayDaysByProduct_mapsNativeRowsToATypedMap_includingPartialPeriodOverlap() {
        UUID productA = UUID.randomUUID();
        UUID productB = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now().minusDays(30);
        OffsetDateTime end = OffsetDateTime.now();

        // productA: a full-period display (30.0 days). productB: a display that started 5 days
        // before the window and is still active -- the native query clamps this to the window,
        // so its display_days is 30.0 too even though the display itself has been up 35 days.
        when(machineDisplayRepository.calculateDisplayDaysByProduct(start, end))
                .thenReturn(List.of(
                        new Object[]{productA, 30.0},
                        new Object[]{productB, 30.0}));

        Map<UUID, Double> result = displayQueries.displayDaysByProduct(start, end);

        assertThat(result).hasSize(2);
        assertThat(result.get(productA)).isEqualTo(30.0);
        assertThat(result.get(productB)).isEqualTo(30.0);
    }

    @Test
    void displayDaysByProduct_emptyWhenNoDisplaysInRange() {
        OffsetDateTime start = OffsetDateTime.now().minusDays(30);
        OffsetDateTime end = OffsetDateTime.now();
        when(machineDisplayRepository.calculateDisplayDaysByProduct(start, end))
                .thenReturn(List.of());

        assertThat(displayQueries.displayDaysByProduct(start, end)).isEmpty();
    }

    @Test
    void isProductOnDisplay_delegatesToTheExistsQuery() {
        UUID productId = UUID.randomUUID();
        when(machineDisplayRepository.existsByProduct_IdAndEndedAtIsNull(productId)).thenReturn(true);

        assertThat(displayQueries.isProductOnDisplay(productId)).isTrue();
    }

    @Test
    void isProductOnDisplay_falseWhenNoActiveDisplay() {
        UUID productId = UUID.randomUUID();
        when(machineDisplayRepository.existsByProduct_IdAndEndedAtIsNull(productId)).thenReturn(false);

        assertThat(displayQueries.isProductOnDisplay(productId)).isFalse();
    }

    @Test
    void findActiveByProduct_mapsEntitiesToSummariesWithoutLeakingTheEntity() {
        UUID productId = UUID.randomUUID();
        UUID machineId = UUID.randomUUID();
        Location location = new Location();
        location.setId(UUID.randomUUID());
        OffsetDateTime startedAt = OffsetDateTime.now();

        Product product = new Product();
        product.setId(productId);

        MachineDisplay display = MachineDisplay.builder()
                .id(UUID.randomUUID())
                .location(location)
                .locationType(LocationType.SINGLE_CLAW_MACHINE)
                .machineId(machineId)
                .product(product)
                .startedAt(startedAt)
                .build();

        when(machineDisplayRepository.findActiveByProduct_Id(productId)).thenReturn(List.of(display));

        List<ActiveDisplaySummary> result = displayQueries.findActiveByProduct(productId);

        assertThat(result).hasSize(1);
        ActiveDisplaySummary summary = result.get(0);
        assertThat(summary.displayId()).isEqualTo(display.getId());
        assertThat(summary.locationId()).isEqualTo(location.getId());
        assertThat(summary.locationType()).isEqualTo(LocationType.SINGLE_CLAW_MACHINE);
        assertThat(summary.machineId()).isEqualTo(machineId);
        assertThat(summary.startedAt()).isEqualTo(startedAt);
    }

    @Test
    void findActiveByProduct_emptyWhenNoActiveDisplays() {
        UUID productId = UUID.randomUUID();
        when(machineDisplayRepository.findActiveByProduct_Id(productId)).thenReturn(List.of());

        assertThat(displayQueries.findActiveByProduct(productId)).isEmpty();
    }
}
