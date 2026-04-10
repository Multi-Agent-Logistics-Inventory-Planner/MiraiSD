package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.shipment.ShipmentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ShipmentItemRepository extends JpaRepository<ShipmentItem, UUID> {
    List<ShipmentItem> findByShipment_Id(UUID shipmentId);

    List<ShipmentItem> findByItem_Id(UUID productId);

    long countByItem_Id(UUID productId);

    /**
     * Used by the Product Assistant detail bundle. JOIN FETCH the parent shipment
     * so we never N+1 on the shipment relation when mapping to the DTO.
     * Filters by the shipment's actualDeliveryDate (falling back to orderDate
     * when the shipment is not yet received so nothing relevant is dropped).
     */
    @Query("SELECT si FROM ShipmentItem si JOIN FETCH si.shipment s " +
            "WHERE si.item.id = :productId " +
            "AND COALESCE(s.actualDeliveryDate, s.orderDate) >= :since " +
            "ORDER BY COALESCE(s.actualDeliveryDate, s.orderDate) DESC")
    List<ShipmentItem> findRecentByItemIdWithShipment(
            @Param("productId") UUID productId,
            @Param("since") LocalDate since);
}
