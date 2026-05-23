package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.LootboxException;
import com.mirai.inventoryservice.exceptions.UserNotFoundException;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.lootbox.CoinEconomyConfig;
import com.mirai.inventoryservice.repositories.CoinEconomyConfigRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Reads and writes the singleton coin-economy config. The "who changed it and
 * when" is captured on the config row itself (updated_by + updated_at); we
 * intentionally do not emit an audit_logs entry — coin-rate adjustments aren't
 * meaningful to the main inventory audit feed.
 *
 * Mid-batch rate changes are not a concern in Java (single SELECT per balance
 * compute is fine); the Python messaging-service is the only place that
 * intentionally snapshots the value at batch start.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoinEconomyService {

    private final CoinEconomyConfigRepository coinEconomyConfigRepository;
    private final UserRepository userRepository;

    /** Returns the current review-to-coin rate. */
    @Transactional(readOnly = true)
    public int getReviewRate() {
        return loadConfig().getReviewCoinRate();
    }

    /** Full current config (rate + last-changed metadata) for the admin GET. */
    @Transactional(readOnly = true)
    public CoinEconomyConfig getConfig() {
        return loadConfig();
    }

    /**
     * Update the review-to-coin rate. Persists the new value plus updated_by /
     * updated_at on the singleton row.
     */
    @Transactional
    public CoinEconomyConfig setReviewRate(int newRate, UUID updatedByUserId) {
        if (newRate < 0) {
            throw new LootboxException("Review coin rate must be >= 0.");
        }
        User admin = userRepository.findById(updatedByUserId)
                .orElseThrow(() -> new UserNotFoundException("Admin user not found: " + updatedByUserId));

        CoinEconomyConfig config = loadConfig();
        int previousRate = config.getReviewCoinRate();
        if (previousRate == newRate) {
            return config;
        }
        config.setReviewCoinRate(newRate);
        config.setUpdatedBy(admin);
        coinEconomyConfigRepository.save(config);

        log.info("Coin rate changed by user {} ({}): {} -> {}",
                admin.getId(), admin.getFullName(), previousRate, newRate);
        return config;
    }

    private CoinEconomyConfig loadConfig() {
        return coinEconomyConfigRepository.findById(CoinEconomyConfig.SINGLETON_ID)
                .orElseThrow(() -> new EntityNotFoundException(
                        "coin_economy_config singleton row missing (id=1)"));
    }
}
