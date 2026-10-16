package com.mirai.inventoryservice.lootbox.application;

import com.mirai.inventoryservice.lootbox.api.BulkUpdateTierProbabilitiesRequestDTO;
import com.mirai.inventoryservice.lootbox.api.UpsertLootboxRequestDTO;
import com.mirai.inventoryservice.lootbox.api.UpsertPrizeRequestDTO;
import com.mirai.inventoryservice.exceptions.LootboxException;
import com.mirai.inventoryservice.lootbox.domain.Lootbox;
import com.mirai.inventoryservice.lootbox.domain.LootboxPrize;
import com.mirai.inventoryservice.lootbox.domain.LootboxTier;
import com.mirai.inventoryservice.lootbox.infrastructure.CoinAdjustmentRepository;
import com.mirai.inventoryservice.lootbox.infrastructure.LootboxPlayRepository;
import com.mirai.inventoryservice.lootbox.infrastructure.LootboxPrizeRepository;
import com.mirai.inventoryservice.lootbox.infrastructure.LootboxRepository;
import com.mirai.inventoryservice.lootbox.infrastructure.LootboxTierRepository;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @Mock private UserSiteMembershipRepository userSiteMembershipRepository;
    @Mock private LootboxService lootboxService;
    @Mock private EntityManager em;
    @Mock private Query lockQuery;

    // Use a real tier-lifecycle helper backed by the mocked repos so the side effects
    // it produces (tier active toggle + rebalance) are observable in assertions.
    private LootboxTierLifecycle tierLifecycle;
    private LootboxAdminService adminService;

    private final UUID siteId = UUID.randomUUID();

    private Lootbox crate;
    private LootboxTier common;
    private LootboxTier rare;
    private LootboxTier epic;
    private LootboxTier legendary;

    @BeforeEach
    void setUp() {
        tierLifecycle = new LootboxTierLifecycle(lootboxTierRepository, lootboxPrizeRepository);
        adminService = new LootboxAdminService(
                lootboxRepository, lootboxTierRepository, lootboxPrizeRepository,
                lootboxPlayRepository, coinAdjustmentRepository, userRepository,
                userSiteMembershipRepository, lootboxService, tierLifecycle, em);
        lenient().when(em.createNativeQuery(any(String.class))).thenReturn(lockQuery);
        lenient().when(lockQuery.setParameter(any(String.class), any())).thenReturn(lockQuery);
        lenient().when(lockQuery.getSingleResult()).thenReturn(1);

        crate = Lootbox.builder()
                .id(UUID.randomUUID())
                .siteId(siteId)
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
        wireCrateLookup();
        wireTierLookups();
        when(lootboxService.sumActiveTierProbabilities(crate.getId()))
                .thenReturn(new BigDecimal("100.00"));

        adminService.bulkUpdateTierProbabilities(siteId, new BulkUpdateTierProbabilitiesRequestDTO(
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
        wireCrateLookup();
        wireTierLookups();

        LootboxException ex = assertThrows(LootboxException.class, () ->
                adminService.bulkUpdateTierProbabilities(siteId, new BulkUpdateTierProbabilitiesRequestDTO(
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
        wireCrateLookup();
        wireTierLookups();
        when(lootboxService.sumActiveTierProbabilities(crate.getId()))
                .thenReturn(new BigDecimal("100.00"));

        adminService.bulkUpdateTierProbabilities(siteId, new BulkUpdateTierProbabilitiesRequestDTO(
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
                .siteId(siteId)
                .name("Pito Sticker")
                .active(false)
                .build();
        when(lootboxPrizeRepository.findByIdAndSiteId(prize.getId(), siteId)).thenReturn(Optional.of(prize));
        when(lootboxPrizeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lootboxTierRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adminService.updatePrize(siteId, prize.getId(), new UpsertPrizeRequestDTO(
                common.getId(), "Pito Sticker", null, null, true, null));

        assertTrue(common.getActive(), "Reactivating the only prize should reactivate its tier");
        // Probability stays at 0 — admin still has to redistribute weight.
        assertEquals(new BigDecimal("0.00"), common.getProbabilityPct());
    }

    private void wireCrateLookup() {
        lenient().when(lootboxRepository.findByIdAndSiteId(crate.getId(), siteId)).thenReturn(Optional.of(crate));
    }

    private void wireTierLookups() {
        lenient().when(lootboxTierRepository.findByIdAndSiteId(common.getId(), siteId)).thenReturn(Optional.of(common));
        lenient().when(lootboxTierRepository.findByIdAndSiteId(rare.getId(), siteId)).thenReturn(Optional.of(rare));
        lenient().when(lootboxTierRepository.findByIdAndSiteId(epic.getId(), siteId)).thenReturn(Optional.of(epic));
        lenient().when(lootboxTierRepository.findByIdAndSiteId(legendary.getId(), siteId)).thenReturn(Optional.of(legendary));
    }

    private LootboxTier tier(String name, BigDecimal prob, boolean active) {
        return LootboxTier.builder()
                .id(UUID.randomUUID())
                .lootbox(crate)
                .siteId(siteId)
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

        var dto = adminService.createCrate(siteId, new UpsertLootboxRequestDTO(
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
                adminService.createCrate(siteId, new UpsertLootboxRequestDTO(
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

        var dto = adminService.createCrate(siteId, new UpsertLootboxRequestDTO(
                "Free Spin Crate", null, null, 0, null, null, true, null, 0));
        assertEquals(0, dto.cost());
    }

    // ----- Tier / Prize hard-delete (V45: FK CASCADE on tier_id, SET NULL on plays.prize_id) -----

    @Test
    @DisplayName("deleteTier hard-deletes the tier row (no soft-delete fallback)")
    void deleteTierHardDeletes() {
        when(lootboxTierRepository.findByIdAndSiteId(rare.getId(), siteId)).thenReturn(Optional.of(rare));
        lenient().when(lootboxTierRepository.findByLootboxIdOrderBySortOrderAscNameAsc(crate.getId()))
                .thenReturn(List.of(common, epic, legendary));

        adminService.deleteTier(siteId, rare.getId());

        verify(lootboxTierRepository).delete(rare);
        verify(lootboxTierRepository, never()).save(rare);
        assertTrue(rare.getActive(), "deleteTier should not flip active before delete — the row is going away");
    }

    @Test
    @DisplayName("deleteTier no longer rejects tiers with active prizes (FK cascade handles it)")
    void deleteTierAllowsTierWithActivePrizes() {
        when(lootboxTierRepository.findByIdAndSiteId(rare.getId(), siteId)).thenReturn(Optional.of(rare));
        lenient().when(lootboxTierRepository.findByLootboxIdOrderBySortOrderAscNameAsc(crate.getId()))
                .thenReturn(List.of(common, epic, legendary));

        adminService.deleteTier(siteId, rare.getId());

        verify(lootboxTierRepository).delete(rare);
    }

    @Test
    @DisplayName("deletePrize hard-deletes the prize row (no soft-delete fallback)")
    void deletePrizeHardDeletes() {
        LootboxPrize prize = LootboxPrize.builder()
                .id(UUID.randomUUID())
                .tier(rare)
                .siteId(siteId)
                .name("Coffee Voucher")
                .active(true)
                .build();
        when(lootboxPrizeRepository.findByIdAndSiteId(prize.getId(), siteId)).thenReturn(Optional.of(prize));
        when(lootboxPrizeRepository.countActiveByTierId(rare.getId())).thenReturn(1L);

        adminService.deletePrize(siteId, prize.getId());

        verify(lootboxPrizeRepository).delete(prize);
        verify(lootboxPrizeRepository, never()).save(prize);
        assertTrue(prize.getActive(), "deletePrize should not flip active before delete — the row is going away");
    }

    @Test
    @DisplayName("deletePrize deactivates its tier when no active prizes remain")
    void deletePrizeDeactivatesEmptyTier() {
        rare.setActive(true);
        LootboxPrize prize = LootboxPrize.builder()
                .id(UUID.randomUUID())
                .tier(rare)
                .siteId(siteId)
                .name("Coffee Voucher")
                .active(true)
                .build();
        when(lootboxPrizeRepository.findByIdAndSiteId(prize.getId(), siteId)).thenReturn(Optional.of(prize));
        when(lootboxPrizeRepository.countActiveByTierId(rare.getId())).thenReturn(0L);
        // LootboxTierLifecycle.maybeDeactivateTier looks the tier up by plain findById (it's only
        // ever called with a tier id already validated in this same site-scoped operation).
        when(lootboxTierRepository.findById(rare.getId())).thenReturn(Optional.of(rare));
        lenient().when(lootboxTierRepository.findByLootboxIdOrderBySortOrderAscNameAsc(crate.getId()))
                .thenReturn(List.of(common, rare, epic, legendary));

        adminService.deletePrize(siteId, prize.getId());

        verify(lootboxPrizeRepository).delete(prize);
        assertEquals(false, rare.getActive(), "tier with no active prizes should auto-deactivate");
    }

    @Test
    @DisplayName("deleteCrate refuses to delete a crate with recorded plays")
    void deleteCrateRefusesWhenPlaysExist() {
        UUID crateId = UUID.randomUUID();
        Lootbox crateWithPlays = Lootbox.builder().id(crateId).siteId(siteId).name("Old").cost(1).active(true).build();
        when(lootboxRepository.findByIdAndSiteId(crateId, siteId)).thenReturn(Optional.of(crateWithPlays));
        when(lootboxPlayRepository.countByLootboxId(crateId)).thenReturn(5L);

        LootboxException ex = assertThrows(LootboxException.class,
                () -> adminService.deleteCrate(siteId, crateId));
        assertTrue(ex.getMessage().contains("5"));
    }

    // ----- Per-prize quantity / stock (V46) -----

    @Test
    @DisplayName("createPrize with quantity = 0 starts the prize as inactive (pre-depleted)")
    void createPrizeWithZeroQuantityStartsInactive() {
        when(lootboxTierRepository.findByIdAndSiteId(rare.getId(), siteId)).thenReturn(Optional.of(rare));
        when(lootboxPrizeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = adminService.createPrize(siteId, new UpsertPrizeRequestDTO(
                rare.getId(), "Coffee Voucher", null, null, true, 0));

        assertEquals(0, dto.quantity());
        assertEquals(false, dto.active(), "quantity=0 at create time should pre-deplete the prize");
    }

    @Test
    @DisplayName("createPrize carries a positive quantity through onto the persisted prize")
    void createPrizePositiveQuantity() {
        when(lootboxTierRepository.findByIdAndSiteId(rare.getId(), siteId)).thenReturn(Optional.of(rare));
        when(lootboxPrizeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = adminService.createPrize(siteId, new UpsertPrizeRequestDTO(
                rare.getId(), "Limited Voucher", null, null, true, 5));

        assertEquals(5, dto.quantity());
        assertTrue(dto.active());
    }

    @Test
    @DisplayName("updatePrize: setting quantity to 0 retires a depleted prize and deactivates its tier")
    void updatePrizeQuantityZeroRetiresAndDeactivatesTier() {
        rare.setActive(true);
        LootboxPrize prize = LootboxPrize.builder()
                .id(UUID.randomUUID())
                .tier(rare)
                .siteId(siteId)
                .name("Coffee Voucher")
                .active(true)
                .quantity(1)
                .build();
        when(lootboxPrizeRepository.findByIdAndSiteId(prize.getId(), siteId)).thenReturn(Optional.of(prize));
        when(lootboxPrizeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lootboxPrizeRepository.countActiveByTierId(rare.getId())).thenReturn(0L);
        // LootboxTierLifecycle.maybeDeactivateTier looks the tier up by plain findById (it's only
        // ever called with a tier id already validated in this same site-scoped operation).
        when(lootboxTierRepository.findById(rare.getId())).thenReturn(Optional.of(rare));
        lenient().when(lootboxTierRepository.findByLootboxIdOrderBySortOrderAscNameAsc(crate.getId()))
                .thenReturn(List.of(common, rare, epic, legendary));

        adminService.updatePrize(siteId, prize.getId(), new UpsertPrizeRequestDTO(
                rare.getId(), "Coffee Voucher", null, null, null, 0));

        assertEquals(0, prize.getQuantity());
        assertEquals(false, prize.getActive(), "quantity=0 should auto-retire the prize");
        assertEquals(false, rare.getActive(), "tier with no active prizes should auto-deactivate");
    }

    @Test
    @DisplayName("updatePrize: restocking a depleted prize (quantity > 0) reactivates it")
    void updatePrizeRestockReactivates() {
        LootboxPrize prize = LootboxPrize.builder()
                .id(UUID.randomUUID())
                .tier(common)
                .siteId(siteId)
                .name("Sticker")
                .active(false)
                .quantity(0)
                .build();
        when(lootboxPrizeRepository.findByIdAndSiteId(prize.getId(), siteId)).thenReturn(Optional.of(prize));
        when(lootboxPrizeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lootboxTierRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adminService.updatePrize(siteId, prize.getId(), new UpsertPrizeRequestDTO(
                common.getId(), "Sticker", null, null, null, 3));

        assertEquals(3, prize.getQuantity());
        assertTrue(prize.getActive(), "Restocking should auto-reactivate the prize");
        assertTrue(common.getActive(), "Tier should auto-reactivate when its sole prize comes back");
    }

    @Test
    @DisplayName("updatePrize: lowering a positive quantity does not toggle active state")
    void updatePrizeLowerNonZeroKeepsActive() {
        LootboxPrize prize = LootboxPrize.builder()
                .id(UUID.randomUUID())
                .tier(rare)
                .siteId(siteId)
                .name("Voucher")
                .active(true)
                .quantity(5)
                .build();
        when(lootboxPrizeRepository.findByIdAndSiteId(prize.getId(), siteId)).thenReturn(Optional.of(prize));
        when(lootboxPrizeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adminService.updatePrize(siteId, prize.getId(), new UpsertPrizeRequestDTO(
                rare.getId(), "Voucher", null, null, null, 2));

        assertEquals(2, prize.getQuantity());
        assertTrue(prize.getActive(), "Lowering to a positive quantity should not retire the prize");
    }
}
