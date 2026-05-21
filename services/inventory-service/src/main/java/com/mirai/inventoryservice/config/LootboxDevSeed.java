package com.mirai.inventoryservice.config;

import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.UserRole;
import com.mirai.inventoryservice.models.lootbox.CoinEconomyConfig;
import com.mirai.inventoryservice.models.lootbox.Lootbox;
import com.mirai.inventoryservice.models.lootbox.LootboxPrize;
import com.mirai.inventoryservice.models.lootbox.LootboxTier;
import com.mirai.inventoryservice.repositories.CoinEconomyConfigRepository;
import com.mirai.inventoryservice.repositories.LootboxPrizeRepository;
import com.mirai.inventoryservice.repositories.LootboxRepository;
import com.mirai.inventoryservice.repositories.LootboxTierRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Dev-only seed: ensures one ADMIN user exists (for the no-auth fallback in the lootbox
 * controllers) and that at least one crate + tiers + prizes is present so /play can roll
 * something. Only runs under the `dev` profile, never in prod.
 *
 * The prod path is the V42 Flyway migration. Dev uses ddl-auto=update which creates the
 * schema but doesn't run our Flyway seed block, so this component plugs that hole.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class LootboxDevSeed {

    private static final String DEV_ADMIN_EMAIL = "dev-admin@local.test";

    private final UserRepository userRepository;
    private final LootboxRepository lootboxRepository;
    private final LootboxTierRepository lootboxTierRepository;
    private final LootboxPrizeRepository lootboxPrizeRepository;
    private final CoinEconomyConfigRepository coinEconomyConfigRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        seedDevAdminUser();
        seedDefaultCrate();
        seedTestCrate();
        seedCoinEconomyConfig();
    }

    private void seedDevAdminUser() {
        if (userRepository.findByEmail(DEV_ADMIN_EMAIL).isPresent()) return;
        User user = User.builder()
                .fullName("Dev Admin")
                .email(DEV_ADMIN_EMAIL)
                .role(UserRole.ADMIN)
                .build();
        userRepository.save(user);
        log.info("[dev-seed] inserted dev admin user {}", DEV_ADMIN_EMAIL);
    }

    private void seedDefaultCrate() {
        if (lootboxRepository.count() > 0) return;

        Lootbox crate = lootboxRepository.save(Lootbox.builder()
                .name("Mirai Mystery Crate")
                .description("The original mystery crate.")
                .cost(1)
                .active(true)
                .sortOrder(0)
                .build());

        LootboxTier common    = saveTier(crate, "COMMON",    "70.00", "#9CA3AF", 1);
        LootboxTier rare      = saveTier(crate, "RARE",      "20.00", "#3B82F6", 2);
        LootboxTier epic      = saveTier(crate, "EPIC",       "8.00", "#A855F7", 3);
        LootboxTier legendary = saveTier(crate, "LEGENDARY",  "2.00", "#F59E0B", 4);

        savePrize(common,    "Pito Sticker",   "Classic sticker");
        savePrize(rare,      "Coffee Voucher", "$5 coffee shop card");
        savePrize(epic,      "Lunch on Pito",  "Free lunch up to $25");
        savePrize(legendary, "Day Off",        "A free PTO day");

        log.info("[dev-seed] inserted default crate ({}), 4 tiers, 4 starter prizes",
                crate.getId());
    }

    /**
     * Second dev-only crate with a short date window so the selector / countdown / expiry
     * logic can be exercised locally without manual admin clicks. Skipped in prod (this
     * whole component is dev-profile-only).
     */
    private void seedTestCrate() {
        if (lootboxRepository.count() >= 2) return;

        Lootbox crate = lootboxRepository.save(Lootbox.builder()
                .name("Dev Test Crate")
                .description("Short-window crate for testing selector + countdown.")
                .cost(2)
                .startsAt(OffsetDateTime.now().minusHours(1))
                .endsAt(OffsetDateTime.now().plusDays(7))
                .active(true)
                .sortOrder(1)
                .build());

        LootboxTier common = saveTier(crate, "COMMON", "60.00", "#9CA3AF", 1);
        LootboxTier rare   = saveTier(crate, "RARE",   "40.00", "#3B82F6", 2);

        savePrize(common, "Test Sticker",  "Dev-only sticker prize");
        savePrize(rare,   "Test Gift Card", "Dev-only gift card");

        log.info("[dev-seed] inserted dev test crate ({}), 2 tiers, 2 prizes",
                crate.getId());
    }

    private void seedCoinEconomyConfig() {
        if (coinEconomyConfigRepository.existsById(CoinEconomyConfig.SINGLETON_ID)) return;
        coinEconomyConfigRepository.save(CoinEconomyConfig.builder()
                .id(CoinEconomyConfig.SINGLETON_ID)
                .reviewCoinRate(1)
                .build());
        log.info("[dev-seed] inserted coin_economy_config singleton (rate=1)");
    }

    private LootboxTier saveTier(Lootbox crate, String name, String pct, String color, int sortOrder) {
        return lootboxTierRepository.save(LootboxTier.builder()
                .lootbox(crate)
                .name(name)
                .probabilityPct(new BigDecimal(pct))
                .displayColor(color)
                .sortOrder(sortOrder)
                .active(true)
                .build());
    }

    private void savePrize(LootboxTier tier, String name, String description) {
        lootboxPrizeRepository.save(LootboxPrize.builder()
                .tier(tier)
                .name(name)
                .description(description)
                .active(true)
                .build());
    }
}
