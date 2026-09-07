package com.mirai.inventoryservice.kuji.application;

import com.mirai.inventoryservice.catalog.application.KujiBoxCleanupPort;
import com.mirai.inventoryservice.repositories.KujiBoxRepository;
import com.mirai.inventoryservice.repositories.KujiBoxTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** {@link KujiBoxCleanupPort} implementation backed by the Kuji box and tier repositories. */
@Component
@RequiredArgsConstructor
class KujiBoxCleanupAdapter implements KujiBoxCleanupPort {

    private final KujiBoxRepository kujiBoxRepository;
    private final KujiBoxTierRepository kujiBoxTierRepository;

    @Override
    @Transactional
    public Result deleteBoxesAndTiersForProduct(UUID productId) {
        // Tiers first: Hibernate generated its own FK on kuji_box_tiers.box_id without
        // ON DELETE CASCADE, so a bulk delete on KujiBox first would hit a constraint violation.
        int deletedTiers = kujiBoxTierRepository.deleteByBoxProductId(productId);
        int deletedBoxes = kujiBoxRepository.deleteByProductId(productId);
        return new Result(deletedBoxes, deletedTiers);
    }
}
