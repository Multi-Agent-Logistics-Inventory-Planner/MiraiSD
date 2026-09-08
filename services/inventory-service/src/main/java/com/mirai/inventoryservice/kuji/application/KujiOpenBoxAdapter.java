package com.mirai.inventoryservice.kuji.application;

import com.mirai.inventoryservice.catalog.application.OpenKujiBoxPort;
import com.mirai.inventoryservice.models.enums.KujiBoxStatus;
import com.mirai.inventoryservice.repositories.KujiBoxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * {@link OpenKujiBoxPort} implementation backed by the Kuji box repository. Kept as a thin,
 * dedicated adapter (rather than added to {@code KujiBoxService}) so the port's single
 * responsibility stays easy to find.
 */
@Component
@RequiredArgsConstructor
class KujiOpenBoxAdapter implements OpenKujiBoxPort {

    private final KujiBoxRepository kujiBoxRepository;

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findProductIdsWithOpenBox() {
        return new HashSet<>(kujiBoxRepository.findProductIdsWithStatus(KujiBoxStatus.OPEN));
    }
}
