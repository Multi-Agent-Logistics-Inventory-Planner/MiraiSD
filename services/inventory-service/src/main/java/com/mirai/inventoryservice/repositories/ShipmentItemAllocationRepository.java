package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.shipment.ShipmentItemAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShipmentItemAllocationRepository extends JpaRepository<ShipmentItemAllocation, UUID> {
    List<ShipmentItemAllocation> findByShipmentItem_Id(UUID shipmentItemId);
}
