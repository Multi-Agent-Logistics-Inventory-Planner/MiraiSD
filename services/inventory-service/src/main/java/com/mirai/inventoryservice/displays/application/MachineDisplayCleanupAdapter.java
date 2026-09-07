package com.mirai.inventoryservice.displays.application;

import com.mirai.inventoryservice.catalog.application.MachineDisplayCleanupPort;
import com.mirai.inventoryservice.repositories.MachineDisplayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

/** {@link MachineDisplayCleanupPort} implementation backed by the machine display repository. */
@Component
@RequiredArgsConstructor
class MachineDisplayCleanupAdapter implements MachineDisplayCleanupPort {

    private final MachineDisplayRepository machineDisplayRepository;

    @Override
    @Transactional
    public void deleteDisplaysForProduct(UUID productId) {
        machineDisplayRepository.deleteByProduct_Id(productId);
    }

    @Override
    @Transactional
    public void deleteDisplaysForProducts(Collection<UUID> productIds) {
        machineDisplayRepository.deleteAllByProductIdIn(productIds);
    }
}
