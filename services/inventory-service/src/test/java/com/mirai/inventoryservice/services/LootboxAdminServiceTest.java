package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.lootbox.BulkUpdateTierProbabilitiesRequestDTO;
import com.mirai.inventoryservice.dtos.requests.lootbox.UpsertLootboxRequestDTO;
import com.mirai.inventoryservice.dtos.requests.lootbox.UpsertPrizeRequestDTO;
import com.mirai.inventoryservice.exceptions.LootboxException;
import com.mirai.inventoryservice.models.lootbox.Lootbox;
import com.mirai.inventoryservice.models.lootbox.LootboxPrize;
import com.mirai.inventoryservice.models.lootbox.LootboxTier;
import com.mirai.inventoryservice.repositories.CoinAdjustmentRepository;
import com.mirai.inventoryservice.repositories.LootboxPlayRepository;
import com.mirai.inventoryservice.repositories.LootboxPrizeRepository;
import com.mirai.inventoryservice.repositories.LootboxRepository;
import com.mirai.inventoryservice.repositories.LootboxTierRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Covers the invariant that tier active-state stays in sync with its probability and with
 * the active-state of its prizes — the bug that surfaced as "active tiers sum to 50, not 100"
 * when an inactive tier still appeared in the admin editor.
 *
 * All tiers in these tests belong to a single test crate, since the sum-to-100 rule is now
 * enforced per-crate.
 */
@ExtendWith(MockitoExtension.class)
class LootboxAdminServiceTest {

    @Mock private LootboxRepository lootboxRepository;
    @Mock private LootboxTierRepository lootboxTierRepository;
    @Mock private LootboxPrizeRepository lootboxPrizeRepository;
    @Mock private LootboxPlayRepository lootboxPlayRepository;
    @Mock private CoinAdjustmentRepository coinAdjustmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private LootboxService lootboxService;

    @InjectMocks private LootboxAdminService adminService;

    private Lootbox crate;
    private LootboxTier common;
    private LootboxTier rare;
    private LootboxTier epic;
    private LootboxTier legendary;

    @BeforeEach
    void setUp() {
        crate = Lootbox.builder()
                .id(UUID.randomUUID())
                .name("Test Crate")
                .cost(1)
                .active(true)
                .sortOrder(0)
                .build();

        // Snapshot of the stuck state: COMMON inactive at 0%, the other three active and summing to 100.
        common = tier("COMMON", new BigDecimal("0.00"), false);
        rare = tier("RARE", new BigDecimal("80.00"), true);
        epic = tier("EPIC", new BigDecimal("16.00"), true);
        legendary = tier("LEGENDARY", new BigDecimal("4.00"), true);

        lenient().when(lootboxTierRepository.findByLootboxIdOrderBySortOrderAscNameAsc(crate.getId()))
                .thenReturn(List.of(common, rare, epic, legendary));
    }

    @Test
    @DisplayName("bulkUpdate flips an inactive tier to active when it receives prob > 0")
    void bulkUpdateActivatesTierGivenProbability() {
        when(lootboxPrizeRepository.countActiveByTierId(any())).thenReturn(1L);
        wireTierLookups();
        when(lootboxService.sumActiveTierProbabilities(crate.getId()))
                .thenReturn(new BigDecimal("100.00"));

        adminService.bulkUpdateTierProbabilities(new BulkUpdateTierProbabilitiesRequestDTO(
                crate.getId(),
                List.of(
                        new BulkUpdateTierProbabilitiesRequestDTO.TierProbability(common.getId(),    new BigDecimal("50.00")),
                        new BulkUpdateTierProbabilitiesRequestDTO.TierProbability(rare.getId(),      new BigDecimal("30.00")),
                        new BulkUpdateTierProbabilitiesRequestDTO.TierProbability(epic.getId(),      new BigDecimal("16.00")),
                        new BulkUpdateTierProbabilitiesRequestDTO.TierProbability(legendary.getId(), new BigDecimal("4.00"))
                )));

        assertTrue(common.getActive(), "COMMON should auto-activate once it carries weight");
        assertEquals(new BigDecimal("50.00"), common.getProbabilityPct());
    }

    @Test
    @DisplayName("bulkUpdate rejects prob > 0 on a tier with no active prizes")
    void bulkUpdateRejectsWeightOnTierWithNoActivePrizes() {
        when(lootboxPrizeRepository.countActiveByTierId(common.getId())).thenReturn(0L);
        wireTierLookups();

        LootboxException ex = assertThrows(LootboxException.class, () ->
                adminService.bulkUpdateTierProbabilities(new BulkUpdateTierProbabilitiesRequestDTO(
                        crate.getId(),
                        List.of(
                                new BulkUpdateTierProbabilitiesRequestDTO.TierProbability(
                                        common.getId(), new BigDecimal("50.00"))
                        ))));
        assertTrue(ex.getMessage().contains("COMMON"));
    }

    @Test
    @DisplayName("bulkUpdate deactivates a tier when its probability drops to 0")
    void bulkUpdateDeactivatesTierAtZero() {
        wireTierLookups();
        when(lootboxService.sumActiveTierProbabilities(crate.getId()))
                .thenReturn(new BigDecimal("100.00"));

        adminService.bulkUpdateTierProbabilities(new BulkUpdateTierProbabilitiesRequestDTO(
                crate.getId(),
                List.of(
                        new BulkUpdateTierProbabilitiesRequestDTO.TierProbability(rare.getId(), BigDecimal.ZERO)
                )));

        assertEquals(false, rare.getActive(), "RARE should auto-deactivate once it carries no weight");
    }

    @Test
    @DisplayName("re-activating a prize re-activates its inactive tier")
    void updatePrizeReactivatesInactiveTier() {
        LootboxPrize prize = LootboxPrize.builder()
                .id(UUID.randomUUID())
                .tier(common)
                .name("Pito Sticker")
                .active(false)
                .build();
        when(lootboxPrizeRepository.findById(prize.getId())).thenReturn(Optional.of(prize));
        when(lootboxPrizeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lootboxTierRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adminService.updatePrize(prize.getId(), new UpsertPrizeRequestDTO(
                common.getId(), "Pito Sticker", null, null, true));

        assertTrue(common.getActive(), "Reactivating the only prize should reactivate its tier");
        // Probability stays at 0 — admin still has to redistribute weight.
        assertEquals(new BigDecimal("0.00"), common.getProbabilityPct());
    }

    private void wireTierLookups() {
        lenient().when(lootboxTierRepository.findById(common.getId())).thenReturn(Optional.of(common));
        lenient().when(lootboxTierRepository.findById(rare.getId())).thenReturn(Optional.of(rare));
        lenient().when(lootboxTierRepository.findById(epic.getId())).thenReturn(Optional.of(epic));
        lenient().when(lootboxTierRepository.findById(legendary.getId())).thenReturn(Optional.of(legendary));
    }

    private LootboxTier tier(String name, BigDecimal prob, boolean active) {
        return LootboxTier.builder()
                .id(UUID.randomUUID())
                .lootbox(crate)
                .name(name)
                .probabilityPct(prob)
                .sortOrder(0)
                .active(active)
                .build();
    }

    // ----- Crate CRUD invariants -----

    @Test
    @DisplayName("createCrate accepts NULL window as unbounded")
    void createCrateAcceptsNullWindow() {
        when(lootboxRepository.save(any())).thenAnswer(inv -> {
            Lootbox c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(lootboxTierRepository.findByLootboxIdOrderBySortOrderAscNameAsc(any())).thenReturn(List.of());

        var dto = adminService.createCrate(new UpsertLootboxRequestDTO(
                "Holiday Crate", "Festive", null, 3,
                null, null, true, null, 0));

        assertEquals("Holiday Crate", dto.name());
        assertEquals(3, dto.cost());
    }

    @Test
    @DisplayName("createCrate rejects end <= start")
    void createCrateRejectsEndBeforeStart() {
        OffsetDateTime start = OffsetDateTime.parse("2026-12-01T00:00:00-05:00");
        OffsetDateTime end   = OffsetDateTime.parse("2026-11-30T23:59:59-05:00");

        LootboxException ex = assertThrows(LootboxException.class, () ->
                adminService.createCrate(new UpsertLootboxRequestDTO(
                        "Bad Crate", null, null, 1, start, end, true, null, 0)));
        assertTrue(ex.getMessage().contains("ends_at"));
    }

    @Test
    @DisplayName("createCrate accepts cost = 0 (free-spin promo)")
    void createCrateAcceptsZeroCost() {
        when(lootboxRepository.save(any())).thenAnswer(inv -> {
            Lootbox c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(lootboxTierRepository.findByLootboxIdOrderBySortOrderAscNameAsc(any())).thenReturn(List.of());

        var dto = adminService.createCrate(new UpsertLootboxRequestDTO(
                "Free Spin Crate", null, null, 0, null, null, true, null, 0));
        assertEquals(0, dto.cost());
    }

    @Test
    @DisplayName("deleteCrate refuses to delete a crate with recorded plays")
    void deleteCrateRefusesWhenPlaysExist() {
        UUID crateId = UUID.randomUUID();
        Lootbox crateWithPlays = Lootbox.builder().id(crateId).name("Old").cost(1).active(true).build();
        when(lootboxRepository.findById(crateId)).thenReturn(Optional.of(crateWithPlays));
        when(lootboxPlayRepository.countByLootboxId(crateId)).thenReturn(5L);

        LootboxException ex = assertThrows(LootboxException.class,
                () -> adminService.deleteCrate(crateId));
        assertTrue(ex.getMessage().contains("5"));
    }
}
