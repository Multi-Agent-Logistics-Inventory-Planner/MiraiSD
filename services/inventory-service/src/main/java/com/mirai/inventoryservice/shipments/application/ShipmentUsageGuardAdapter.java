package com.mirai.inventoryservice.shipments.application;

import com.mirai.inventoryservice.catalog.application.ShipmentUsageGuardPort;
import com.mirai.inventoryservice.repositories.ShipmentItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** {@link ShipmentUsageGuardPort} implementation backed by the shipment item repository. */
@Component
@RequiredArgsConstructor
class ShipmentUsageGuardAdapter implements ShipmentUsageGuardPort {

    private final ShipmentItemRepository shipmentItemRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean isUsedInShipment(UUID productId) {
        return shipmentItemRepository.countByItem_Id(productId) > 0;
    }
}
