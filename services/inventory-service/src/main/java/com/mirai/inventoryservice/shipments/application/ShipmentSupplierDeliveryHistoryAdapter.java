package com.mirai.inventoryservice.shipments.application;

import com.mirai.inventoryservice.catalog.application.SupplierDeliveryHistoryPort;
import com.mirai.inventoryservice.repositories.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * {@link SupplierDeliveryHistoryPort} implementation backed by shipment history. Kept as a
 * thin, dedicated adapter (rather than added to {@code ShipmentService}) so the port's single
 * responsibility stays easy to find.
 */
@Component
@RequiredArgsConstructor
class ShipmentSupplierDeliveryHistoryAdapter implements SupplierDeliveryHistoryPort {

    private final ShipmentRepository shipmentRepository;

    @Override
    @Transactional(readOnly = true)
    public Object[] findLastDeliveredSupplier(UUID productId) {
        return shipmentRepository.findLastDeliveredSupplierByProductId(productId);
    }
}
