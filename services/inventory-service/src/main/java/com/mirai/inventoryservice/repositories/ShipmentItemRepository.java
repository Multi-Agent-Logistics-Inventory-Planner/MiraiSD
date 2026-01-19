package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.shipment.ShipmentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShipmentItemRepository extends JpaRepository<ShipmentItem, UUID> {
    List<ShipmentItem> findByShipment_Id(UUID shipmentId);

    List<ShipmentItem> findByItem_Id(UUID productId);
}
