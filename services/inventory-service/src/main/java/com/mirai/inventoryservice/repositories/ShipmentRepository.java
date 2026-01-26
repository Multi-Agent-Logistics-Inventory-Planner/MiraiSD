package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import com.mirai.inventoryservice.models.shipment.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    Optional<Shipment> findByShipmentNumber(String shipmentNumber);

    List<Shipment> findByStatusOrderByCreatedAtDesc(ShipmentStatus status);

    List<Shipment> findBySupplierNameContainingIgnoreCaseOrderByCreatedAtDesc(String supplierName);

    List<Shipment> findByOrderDateBetweenOrderByOrderDateDesc(LocalDate startDate, LocalDate endDate);

    List<Shipment> findByExpectedDeliveryDateBetweenOrderByExpectedDeliveryDateAsc(LocalDate startDate, LocalDate endDate);

    @Query("SELECT DISTINCT s FROM Shipment s JOIN s.items i WHERE i.item.id = :productId ORDER BY s.orderDate DESC")
    List<Shipment> findByItemsContainingProduct(@Param("productId") UUID productId);
}
