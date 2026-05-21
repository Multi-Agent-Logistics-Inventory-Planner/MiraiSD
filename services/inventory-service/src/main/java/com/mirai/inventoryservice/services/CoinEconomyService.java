package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.LootboxException;
import com.mirai.inventoryservice.exceptions.UserNotFoundException;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.lootbox.CoinEconomyConfig;
import com.mirai.inventoryservice.repositories.AuditLogRepository;
import com.mirai.inventoryservice.repositories.CoinEconomyConfigRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads and writes the singleton coin-economy config. The setter persists the
 * change AND inserts an audit_logs entry (reason = COIN_RATE_CHANGED) so admins
 * can see who flipped the rate and when — no parallel history table needed.
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
    private final AuditLogRepository auditLogRepository;
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
     * Update the review-to-coin rate. Writes an audit_logs row capturing the
     * before/after value in field_changes and bumps updated_at + updated_by.
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

        List<Map<String, Object>> fieldChanges = List.of(Map.of(
                "field", "review_coin_rate",
                "previous", previousRate,
                "new", newRate
        ));
        AuditLog entry = AuditLog.builder()
                .user(admin)
                .actorName(admin.getFullName())
                .reason(StockMovementReason.COIN_RATE_CHANGED)
                .itemCount(0)
                .totalQuantityMoved(0)
                .productSummary("Review coin rate: " + previousRate + " -> " + newRate)
                .fieldChanges(fieldChanges)
                .build();
        auditLogRepository.save(entry);

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
