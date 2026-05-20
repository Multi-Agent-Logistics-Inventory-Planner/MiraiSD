package com.mirai.inventoryservice.config;

import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.UserRole;
import com.mirai.inventoryservice.models.lootbox.LootboxPrize;
import com.mirai.inventoryservice.models.lootbox.LootboxTier;
import com.mirai.inventoryservice.repositories.LootboxPrizeRepository;
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
import java.util.List;
import java.util.UUID;

/**
 * Dev-only seed: ensures one ADMIN user exists (for the no-auth fallback in the lootbox
 * controllers) and that at least one tier + prize is present so /play can roll something.
 * Only runs under the `dev` profile, never in prod.
 *
 * The prod path is the V40 Flyway migration. Dev uses ddl-auto=update which creates the
 * schema but doesn't run our Flyway seed block, so this component plugs that hole.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class LootboxDevSeed {

    private static final UUID DEV_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String DEV_ADMIN_EMAIL = "dev-admin@local.test";

    private final UserRepository userRepository;
    private final LootboxTierRepository lootboxTierRepository;
    private final LootboxPrizeRepository lootboxPrizeRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        seedDevAdminUser();
        seedTiersAndPrizes();
    }

    private void seedDevAdminUser() {
        if (userRepository.findByEmail(DEV_ADMIN_EMAIL).isPresent()) return;
        User user = User.builder()
                .id(DEV_ADMIN_ID)
                .fullName("Dev Admin")
                .email(DEV_ADMIN_EMAIL)
                .role(UserRole.ADMIN)
                .build();
        userRepository.save(user);
        log.info("[dev-seed] inserted dev admin user {}", DEV_ADMIN_EMAIL);
    }

    private void seedTiersAndPrizes() {
        if (lootboxTierRepository.count() > 0) return;

        LootboxTier common = saveTier("COMMON", "70.00", "#9CA3AF", 1);
        LootboxTier rare = saveTier("RARE", "20.00", "#3B82F6", 2);
        LootboxTier epic = saveTier("EPIC", "8.00", "#A855F7", 3);
        LootboxTier legendary = saveTier("LEGENDARY", "2.00", "#F59E0B", 4);

        savePrize(common, "Pito Sticker", "Classic sticker");
        savePrize(rare, "Coffee Voucher", "$5 coffee shop card");
        savePrize(epic, "Lunch on Pito", "Free lunch up to $25");
        savePrize(legendary, "Day Off", "A free PTO day");

        log.info("[dev-seed] inserted 4 tiers + 4 starter prizes");
    }

    private LootboxTier saveTier(String name, String pct, String color, int sortOrder) {
        return lootboxTierRepository.save(LootboxTier.builder()
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
